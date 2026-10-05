package com.cropadvisor

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

    fun bandOf(pct: Float) = when {
        pct < 5f -> Band.VERY_LIGHT
        pct < 15f -> Band.MILD
        pct < 35f -> Band.MODERATE
        else -> Band.SEVERE
    }

    fun estimate(photo: Bitmap, maxSide: Int = 512): Result {
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

        // 2. leaf outline
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

    private fun release(r: Result, vararg mats: Mat): Result {
        for (m in mats) m.release()
        return r
    }
}
