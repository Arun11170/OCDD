package com.cropadvisor

import android.content.Context
import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Leaf damage estimate with plain OpenCV (no ML), same steps as severity.py:
 *  1. healthy green pixels  2. leaf outline (largest green blobs, holes filled)
 *  3. damage = inside the outline but not healthy green.
 * severity % = damage area / leaf area. It is an estimate, shown in bands.
 */
object Severity {
    data class Result(
        val ok: Boolean,
        val percent: Float = 0f,
        val band: Band = Band.VERY_LIGHT,
        val leafFraction: Float = 0f,
        val tooSmall: Boolean = false,
        val tooBig: Boolean = false,
        val overlay: Bitmap? = null,
    )

    private var ready = false
    private fun init(): Boolean {
        if (!ready) ready = try { OpenCVLoader.initLocal() } catch (e: Throwable) { false }
        return ready
    }


    /**
     * True when the photo looks like a wide view (field, many plants, texture everywhere) and not a close-up:
     * sharp detail is spread across the whole frame instead of sitting on one object.
     */
    fun wideShot(photo: Bitmap): Boolean {
        if (!init()) return false
        return try {
            val k = 256f / maxOf(photo.width, photo.height)
            val small = if (k < 1f) Bitmap.createScaledBitmap(photo, (photo.width * k).toInt(), (photo.height * k).toInt(), true) else photo
            val w = small.width; val h = small.height
            val rgba = Mat(); Utils.bitmapToMat(small, rgba)
            val gray = Mat(); Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            val g32 = Mat(); gray.convertTo(g32, CvType.CV_32F)
            Imgproc.GaussianBlur(g32, g32, Size(3.0, 3.0), 0.0)
            val lap = Mat(); Imgproc.Laplacian(g32, lap, CvType.CV_32F)
            val ab = Mat(); Core.absdiff(lap, Scalar(0.0), ab)
            val sh = Mat(); Imgproc.GaussianBlur(ab, sh, Size(0.0, 0.0), 8.0)
            val a = FloatArray(w * h); sh.get(0, 0, a); a.sort()
            val p25 = a[(0.25 * (a.size - 1)).toInt()]; val p90 = a[(0.9 * (a.size - 1)).toInt()]
            for (m in listOf(rgba, gray, g32, lap, ab, sh)) m.release()
            p90 > 1e-3f && p25 / p90 >= 0.27f
        } catch (t: Throwable) { false }
    }

    /** Below this share of the leaf, a diseased leaf is treated as "damage could not be measured" instead of "0%". */
    const val MIN_REAL_PCT = 0.5f

    fun bandOf(pct: Float) = when {
        pct < 5f -> Band.VERY_LIGHT
        pct < 15f -> Band.MILD
        pct < 35f -> Band.MODERATE
        else -> Band.SEVERE
    }

    fun estimate(photo: Bitmap, ctx: Context? = null, maxSide: Int = 512): Result {
        if (!init()) return Result(false)
        val k = maxSide.toFloat() / maxOf(photo.width, photo.height)
        val small: Bitmap = if (k < 1f) Bitmap.createScaledBitmap(photo, (photo.width * k).toInt(), (photo.height * k).toInt(), true)
        else (photo.copy(Bitmap.Config.ARGB_8888, false) ?: photo)
        val w = small.width
        val h = small.height

        val rgba = Mat()
        Utils.bitmapToMat(small, rgba)
        val bgr = Mat()
        Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
        Imgproc.GaussianBlur(bgr, bgr, Size(5.0, 5.0), 0.0)
        val hsv = Mat()
        Imgproc.cvtColor(bgr, hsv, Imgproc.COLOR_BGR2HSV)

        // 1. healthy green mask (per pixel; OpenCV hue is 0..180, green is about 33..90)
        val bgrBytes = ByteArray(w * h * 3)
        val hsvBytes = ByteArray(w * h * 3)
        bgr.get(0, 0, bgrBytes)
        hsv.get(0, 0, hsvBytes)
        val healthyArr = ByteArray(w * h)
        for (i in 0 until w * h) {
            val b = bgrBytes[3 * i].toInt() and 0xFF
            val g = bgrBytes[3 * i + 1].toInt() and 0xFF
            val r = bgrBytes[3 * i + 2].toInt() and 0xFF
            val hue = hsvBytes[3 * i].toInt() and 0xFF
            val sat = hsvBytes[3 * i + 1].toInt() and 0xFF
            val v = hsvBytes[3 * i + 2].toInt() and 0xFF
            val exg = 2 * g - r - b
            if (exg > 18 && hue in 33..90 && sat > 40 && v > 40) healthyArr[i] = 1
        }
        val healthy = Mat(h, w, CvType.CV_8UC1)
        healthy.put(0, 0, healthyArr)

        // 1b. if the trained leaf finder is in the app, measure damage inside the leaf it found
        val segMask = if (ctx != null) LeafSeg.mask(ctx, small) else null
        if (segMask != null) {
            val lesionMask = if (ctx != null) LesionSeg.mask(ctx, small) else null     // trained lesion finder, if its file is in the app
            return fromLeafMask(small, w, h, hsvBytes, segMask, lesionMask, listOf(rgba, bgr, hsv, healthy))
        }

        // 2. leaf outline (fallback: guessed from green pixels)
        val kSize = maxOf(5, (0.08 * maxOf(w, h)).toInt() or 1)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(kSize.toDouble(), kSize.toDouble()))
        val closed = Mat()
        Imgproc.morphologyEx(healthy, closed, Imgproc.MORPH_CLOSE, kernel)

        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        val n = Imgproc.connectedComponentsWithStats(closed, labels, stats, centroids, 8, CvType.CV_32S)
        if (n <= 1) return release(Result(false), rgba, bgr, hsv, healthy, closed, labels, stats, centroids)

        var biggest = 0
        val areas = IntArray(n)
        for (i in 1 until n) {
            areas[i] = stats.get(i, Imgproc.CC_STAT_AREA)[0].toInt()
            if (areas[i] > biggest) biggest = areas[i]
        }
        val outline = Mat.zeros(h, w, CvType.CV_8UC1)
        val one = Mat()
        for (i in 1 until n) {
            if (areas[i] >= 0.15 * biggest) {          // keep leaflets of compound leaves
                Core.compare(labels, Scalar(i.toDouble()), one, Core.CMP_EQ)
                Core.bitwise_or(outline, one, outline)
            }
        }
        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(outline, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        val filled = Mat.zeros(h, w, CvType.CV_8UC1)
        Imgproc.drawContours(filled, contours, -1, Scalar(255.0), Imgproc.FILLED)

        // 3. damage = inside outline, not healthy green; open() removes specks
        val filledArr = ByteArray(w * h)
        filled.get(0, 0, filledArr)
        val dmgArr = ByteArray(w * h)
        for (i in 0 until w * h) {
            if (filledArr[i].toInt() != 0 && healthyArr[i].toInt() == 0) dmgArr[i] = 1
        }
        val dmg = Mat(h, w, CvType.CV_8UC1)
        dmg.put(0, 0, dmgArr)
        val k3 = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
        val dmgOpen = Mat()
        Imgproc.morphologyEx(dmg, dmgOpen, Imgproc.MORPH_OPEN, k3)
        dmgOpen.get(0, 0, dmgArr)

        val leafArea = Core.countNonZero(filled)
        val dmgArea = Core.countNonZero(dmgOpen)
        val pct = 100f * dmgArea / maxOf(leafArea, 1)
        val leafFrac = leafArea.toFloat() / (w * h)

        // overlay: red tint on damaged pixels
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            if (dmgArr[i].toInt() != 0) {
                val c = px[i]
                val r = ((c shr 16 and 0xFF) * 0.45f + 255 * 0.55f).toInt()
                val g = ((c shr 8 and 0xFF) * 0.45f).toInt()
                val b = ((c and 0xFF) * 0.45f).toInt()
                px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        val overlay = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)

        val res = Result(
            ok = true, percent = pct, band = bandOf(pct), leafFraction = leafFrac,
            tooSmall = leafFrac < 0.08f, tooBig = leafFrac > 0.97f, overlay = overlay,
        )
        return release(res, rgba, bgr, hsv, healthy, closed, labels, stats, centroids, outline, one, filled, dmg, dmgOpen, kernel, k3)
    }

    /**
     * Damage measured inside the leaf found by the leaf finder (same steps as the Python check in sevtest/pp.py):
     *  - keep only leaf regions that contain real green (soil / hands that were mistaken for leaf are dropped)
     *  - shrink the leaf by a thin rim (cut edges, brown fringe, background bleed)
     *  - damage = not green (hue 35-95, sat >= 30, val >= 30), not shade (dark green, or dark and grey)
     *  - open() with 5x5 removes thin red / pink veins and specks.
     */
    private fun fromLeafMask(small: Bitmap, w: Int, h: Int, hsvBytes: ByteArray, leafIn: ByteArray, lesion: ByteArray?, mats: List<Mat>): Result {
        val n = w * h
        val healthyA = BooleanArray(n)
        for (i in 0 until n) {
            val hue = hsvBytes[3 * i].toInt() and 0xFF
            val sat = hsvBytes[3 * i + 1].toInt() and 0xFF
            val v = hsvBytes[3 * i + 2].toInt() and 0xFF
            healthyA[i] = hue in 35..95 && sat >= 30 && v >= 30
        }
        val leafMat = Mat(h, w, CvType.CV_8UC1)
        leafMat.put(0, 0, leafIn)
        val bin = Mat()
        Imgproc.threshold(leafMat, bin, 0.0, 1.0, Imgproc.THRESH_BINARY)
        val labels = Mat(); val stats = Mat(); val cents = Mat()
        val cnt = Imgproc.connectedComponentsWithStats(bin, labels, stats, cents, 8, CvType.CV_32S)
        val extra = mutableListOf(leafMat, bin, labels, stats, cents)
        if (cnt <= 1) return release(Result(false), *(mats + extra).toTypedArray())
        val lab = IntArray(n)
        labels.get(0, 0, lab)
        val area = IntArray(cnt); val green = IntArray(cnt)
        for (i in 0 until n) { val l = lab[i]; if (l > 0) { area[l]++; if (healthyA[i]) green[l]++ } }
        val big = (1 until cnt).maxOf { area[it] }
        val keepL = BooleanArray(cnt) { it > 0 && area[it] >= 0.05 * big && green[it] >= 0.05 * area[it] }
        val keepArr = ByteArray(n)
        for (i in 0 until n) if (keepL[lab[i]]) keepArr[i] = 1
        val keep = Mat(h, w, CvType.CV_8UC1); keep.put(0, 0, keepArr)
        val r = maxOf(2, (0.012 * maxOf(w, h)).toInt())
        val kRim = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size((2 * r + 1).toDouble(), (2 * r + 1).toDouble()))
        val inner = Mat()
        Imgproc.erode(keep, inner, kRim)
        val innerArr = ByteArray(n)
        inner.get(0, 0, innerArr)
        extra += listOf(keep, kRim, inner)

        // out-of-focus background (blurred leaves, soil) is not the leaf being photographed: keep only the sharp region
        val gray = Mat(); val g32 = Mat(); val lap = Mat(); val lapAbs = Mat(); val shM = Mat()
        extra += listOf(gray, g32, lap, lapAbs, shM)
        Imgproc.cvtColor(mats[1], gray, Imgproc.COLOR_BGR2GRAY)
        gray.convertTo(g32, CvType.CV_32F)
        Imgproc.GaussianBlur(g32, g32, Size(3.0, 3.0), 0.0)
        Imgproc.Laplacian(g32, lap, CvType.CV_32F)
        Core.absdiff(lap, Scalar(0.0), lapAbs)
        Imgproc.GaussianBlur(lapAbs, shM, Size(0.0, 0.0), maxOf(4.0, 0.03 * maxOf(w, h)))
        val sh = FloatArray(n)
        shM.get(0, 0, sh)
        val vals = ArrayList<Float>()
        for (i in 0 until n) if (innerArr[i].toInt() != 0) vals.add(sh[i])
        if (vals.size > 50) {
            vals.sort()
            val ref = vals[(0.9 * (vals.size - 1)).toInt()]
            val focusArr = ByteArray(n)
            for (i in 0 until n) if (innerArr[i].toInt() != 0 && sh[i] >= 0.35f * ref) focusArr[i] = 1
            val focus = Mat(h, w, CvType.CV_8UC1); focus.put(0, 0, focusArr)
            val k9 = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(9.0, 9.0))
            val focusOpen = Mat()
            Imgproc.morphologyEx(focus, focusOpen, Imgproc.MORPH_OPEN, k9)
            val fl = Mat(); val fst = Mat(); val fc = Mat()
            val fn = Imgproc.connectedComponentsWithStats(focusOpen, fl, fst, fc, 8, CvType.CV_32S)
            extra += listOf(focus, k9, focusOpen, fl, fst, fc)
            if (fn > 1) {
                val flab = IntArray(n); fl.get(0, 0, flab)
                val farea = IntArray(fn)
                for (i in 0 until n) if (flab[i] > 0) farea[flab[i]]++
                val fbig = (1 until fn).maxOf { farea[it] }
                for (i in 0 until n) if (innerArr[i].toInt() != 0 && (flab[i] == 0 || farea[flab[i]] < 0.2 * fbig)) innerArr[i] = 0
            }
        }

        // a hand or fingers at the edge of the photo are not leaf
        val skinArr = ByteArray(n)
        for (i in 0 until n) {
            val hue = hsvBytes[3 * i].toInt() and 0xFF
            val sat = hsvBytes[3 * i + 1].toInt() and 0xFF
            val v = hsvBytes[3 * i + 2].toInt() and 0xFF
            if (hue in 3..22 && sat in 45..215 && v >= 110) skinArr[i] = 1
        }
        val skin = Mat(h, w, CvType.CV_8UC1); skin.put(0, 0, skinArr)
        val k7 = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(7.0, 7.0))
        val skinOpen = Mat()
        Imgproc.morphologyEx(skin, skinOpen, Imgproc.MORPH_OPEN, k7)
        val sl = Mat(); val sst = Mat(); val sc = Mat()
        val sn = Imgproc.connectedComponentsWithStats(skinOpen, sl, sst, sc, 8, CvType.CV_32S)
        extra += listOf(skin, k7, skinOpen, sl, sst, sc)
        if (sn > 1) {
            val slab = IntArray(n); sl.get(0, 0, slab)
            val handL = BooleanArray(sn)
            for (i in 1 until sn) {
                val x0 = sst.get(i, Imgproc.CC_STAT_LEFT)[0].toInt()
                val y0 = sst.get(i, Imgproc.CC_STAT_TOP)[0].toInt()
                val ww = sst.get(i, Imgproc.CC_STAT_WIDTH)[0].toInt()
                val hh = sst.get(i, Imgproc.CC_STAT_HEIGHT)[0].toInt()
                val ar = sst.get(i, Imgproc.CC_STAT_AREA)[0].toInt()
                handL[i] = ar >= 0.005 * n && (x0 <= 1 || y0 <= 1 || x0 + ww >= w - 1 || y0 + hh >= h - 1)
            }
            if (handL.any { it }) {
                val handArr = ByteArray(n)
                for (i in 0 until n) if (slab[i] > 0 && handL[slab[i]]) handArr[i] = 1
                val hand = Mat(h, w, CvType.CV_8UC1); hand.put(0, 0, handArr)
                val k9b = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
                val handBig = Mat()
                Imgproc.dilate(hand, handBig, k9b)
                extra += listOf(hand, k9b, handBig)
                val hb = ByteArray(n); handBig.get(0, 0, hb)
                for (i in 0 until n) if (hb[i].toInt() != 0) innerArr[i] = 0
            }
        }
        var leafArea = 0
        for (i in 0 until n) if (innerArr[i].toInt() != 0) leafArea++
        if (leafArea < 0.03 * n) return release(Result(false), *(mats + extra).toTypedArray())

        val dmgArr = ByteArray(n)
        for (i in 0 until n) {
            if (innerArr[i].toInt() == 0) continue
            if (lesion != null) { if (lesion[i].toInt() != 0) dmgArr[i] = 1; continue }   // trained lesion finder decides
            if (healthyA[i]) continue
            val hue = hsvBytes[3 * i].toInt() and 0xFF
            val sat = hsvBytes[3 * i + 1].toInt() and 0xFF
            val v = hsvBytes[3 * i + 2].toInt() and 0xFF
            val shade = (hue in 35..95 && v < 60) || (v < 55 && sat < 80)
            if (!shade) dmgArr[i] = 1
        }
        val dmg = Mat(h, w, CvType.CV_8UC1)
        dmg.put(0, 0, dmgArr)
        val kOpen = if (lesion != null) 3.0 else 5.0
        val k5 = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(kOpen, kOpen))
        val dmgOpen = Mat()
        Imgproc.morphologyEx(dmg, dmgOpen, Imgproc.MORPH_OPEN, k5)
        dmgOpen.get(0, 0, dmgArr)
        val dmgArea = Core.countNonZero(dmgOpen)
        val pct = 100f * dmgArea / maxOf(leafArea, 1)
        val leafFrac = leafArea.toFloat() / n

        val px = IntArray(n)
        small.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]
            val r0 = c shr 16 and 0xFF
            val g0 = c shr 8 and 0xFF
            val b0 = c and 0xFF
            // only the damaged part of the leaf is marked; everything else stays as photographed
            if (dmgArr[i].toInt() != 0) {
                px[i] = (0xFF shl 24) or (((r0 * 0.35f + 255 * 0.65f).toInt()) shl 16) or (((g0 * 0.35f).toInt()) shl 8) or (b0 * 0.35f).toInt()
            }
        }
        val overlay = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        val res = Result(
            ok = true, percent = pct, band = bandOf(pct), leafFraction = leafFrac,
            tooSmall = leafFrac < 0.08f, tooBig = leafFrac > 0.97f, overlay = overlay,
        )
        return release(res, *(mats + extra + listOf(dmg, dmgOpen, k5)).toTypedArray())
    }

    private fun release(r: Result, vararg mats: Mat): Result {
        for (m in mats) m.release()
        return r
    }
}
