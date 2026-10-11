package com.cropadvisor

import java.text.Normalizer

/** What a farmer can ask for by voice. */
enum class Cmd { CAMERA, GALLERY, LISTEN, NEW_CHECK, BACK, HELP, CALL, DIARY, WEATHER, STAGES, SETTINGS, LIBRARY, PLOT, YELLOW }

/**
 * Voice navigation: the phone's own speech recogniser turns speech into text, and this object matches key words to a command.
 * Every language also understands the English words, because many farmers mix them in ("photo", "camera", "help").
 * The word lists are short everyday words, written by machine: they need a native-speaker check (see the review pack).
 */
object VoiceNav {
    // Checked in this order: the more specific commands first ("photo from gallery" must not open the camera).
    private val order = listOf(Cmd.GALLERY, Cmd.YELLOW, Cmd.STAGES, Cmd.PLOT, Cmd.LIBRARY, Cmd.DIARY, Cmd.WEATHER, Cmd.SETTINGS, Cmd.HELP, Cmd.CALL, Cmd.NEW_CHECK, Cmd.LISTEN, Cmd.BACK, Cmd.CAMERA)

    // LANG COMMAND word,word,word
    private const val TABLE = """
EN CAMERA photo,camera,picture,snap,click
EN GALLERY gallery,choose,pick,select
EN LISTEN listen,read,speak,play,say
EN NEW_CHECK new,again,another,restart
EN BACK back,home,close,return,exit
EN HELP help,helpline
EN CALL call,expert,kisan,phone
EN DIARY diary,notes,record
EN WEATHER weather,rain,forecast
EN STAGES stage,calendar
EN SETTINGS setting
EN LIBRARY library,book,disease list,diseases
EN PLOT plot,field,farm
EN YELLOW yellow,nutrient
HI CAMERA फोटो,फ़ोटो,कैमरा,तस्वीर,खींच
HI GALLERY गैलरी,चुन
HI LISTEN सुन,पढ़,पढ,बोल,सुनाओ,सुनाइए
HI NEW_CHECK नई,नया,फिर,दोबारा,दुबारा
HI BACK वापस,पीछे,होम,बंद
HI HELP मदद,सहायता,हेल्प
HI CALL कॉल,कॉल,फोन,फ़ोन,विशेषज्ञ,किसान
HI DIARY डायरी,नोट
HI WEATHER मौसम,बारिश
HI STAGES अवस्था,चरण,स्टेज
HI SETTINGS सेटिंग
HI LIBRARY लाइब्रेरी,पुस्तकालय,बीमारी,रोग
HI PLOT प्लॉट,खेत
HI YELLOW पीले,पीला,पीली
BN CAMERA ছবি,ফটো,ক্যামেরা
BN GALLERY গ্যালারি,বাছ
BN LISTEN শুন,পড়,বল
BN NEW_CHECK নতুন,আবার
BN BACK ফিরে,পেছনে,হোম,বন্ধ
BN HELP সাহায্য,হেল্প
BN CALL কল,ফোন,বিশেষজ্ঞ,কিষান
BN DIARY ডায়েরি,নোট
BN WEATHER আবহাওয়া,বৃষ্টি
BN STAGES পর্যায়,স্টেজ,ধাপ
BN SETTINGS সেটিং
BN LIBRARY লাইব্রেরি,বই,রোগ
BN PLOT প্লট,জমি,ক্ষেত
BN YELLOW হলুদ
TE CAMERA ఫోటో,ఫొటో,కెమెరా,చిత్రం
TE GALLERY గ్యాలరీ,ఎంచు
TE LISTEN వినండి,విను,చదవ,చెప్ప
TE NEW_CHECK కొత్త,మళ్ళీ,మళ్లీ
TE BACK వెనక్కి,వెనుకకు,హోమ్,మూసివేయి
TE HELP సహాయం,హెల్ప్
TE CALL కాల్,ఫోన్,నిపుణ,కిసాన్
TE DIARY డైరీ,నోట్
TE WEATHER వాతావరణం,వర్షం
TE STAGES దశ,స్టేజ్
TE SETTINGS సెట్టింగ్
TE LIBRARY లైబ్రరీ,పుస్తక,వ్యాధి,తెగులు
TE PLOT ప్లాట్,పొలం
TE YELLOW పసుపు
MR CAMERA फोटो,कॅमेरा,कैमेरा
MR GALLERY गॅलरी,गैलरी,निवड
MR LISTEN ऐक,वाच,सांग
MR NEW_CHECK नवीन,नवा,पुन्हा
MR BACK मागे,परत,होम,बंद
MR HELP मदत,हेल्प
MR CALL कॉल,फोन,तज्ज्ञ,किसान
MR DIARY डायरी,नोंद
MR WEATHER हवामान,पाऊस
MR STAGES अवस्था,टप्पा,स्टेज
MR SETTINGS सेटिंग
MR LIBRARY लायब्ररी,ग्रंथालय,रोग,पुस्तक
MR PLOT प्लॉट,शेत
MR YELLOW पिवळ
TA CAMERA புகைப்பட,போட்டோ,கேமரா,படம்
TA GALLERY கேலரி,தேர்
TA LISTEN கேள்,படி,சொல்
TA NEW_CHECK புதிய,மீண்டும்
TA BACK பின்,திரும்ப,முகப்பு,மூடு,ஹோம்
TA HELP உதவி,ஹெல்ப்
TA CALL அழை,கால்,போன்,நிபுணர்,கிசான்
TA DIARY நாட்குறிப்பு,டைரி,டயரி,டையரி,டைறி,குறிப்பு
TA WEATHER வானிலை,மழை
TA STAGES நிலை,கட்ட,ஸ்டேஜ்
TA SETTINGS அமைப்பு,செட்டிங்
TA LIBRARY நூலகம்,லைப்ரரி,நோய்
TA PLOT பிளாட்,வயல்,நிலம்
TA YELLOW மஞ்சள்
GU CAMERA ફોટો,કેમેરા,તસવીર
GU GALLERY ગેલેરી,પસંદ
GU LISTEN સાંભળ,વાંચ,બોલ
GU NEW_CHECK નવું,નવી,ફરી
GU BACK પાછ,હોમ,બંધ
GU HELP મદદ,હેલ્પ,સહાય
GU CALL કૉલ,કોલ,ફોન,નિષ્ણાત,કિસાન
GU DIARY ડાયરી,નોંધ
GU WEATHER હવામાન,વરસાદ
GU STAGES અવસ્થા,તબક્કો,સ્ટેજ
GU SETTINGS સેટિંગ
GU LIBRARY લાઇબ્રેરી,પુસ્તકાલય,રોગ
GU PLOT પ્લોટ,ખેત
GU YELLOW પીળ
UR CAMERA فوٹو,کیمرہ,تصویر
UR GALLERY گیلری,منتخب,چن
UR LISTEN سن,پڑھ,بول
UR NEW_CHECK نیا,نئی,دوبارہ,پھر
UR BACK واپس,پیچھے,ہوم,بند
UR HELP مدد,ہیلپ
UR CALL کال,فون,ماہر,کسان
UR DIARY ڈائری,نوٹ
UR WEATHER موسم,بارش
UR STAGES مرحلہ,اسٹیج,مراحل
UR SETTINGS سیٹنگ,ترتیب
UR LIBRARY لائبریری,کتاب,بیماری
UR PLOT پلاٹ,کھیت
UR YELLOW پیلے,پیلا,پیلی
KN CAMERA ಫೋಟೋ,ಕ್ಯಾಮೆರಾ,ಚಿತ್ರ
KN GALLERY ಗ್ಯಾಲರಿ,ಆಯ್ಕೆ
KN LISTEN ಕೇಳ,ಓದ,ಹೇಳ
KN NEW_CHECK ಹೊಸ,ಮತ್ತೆ
KN BACK ಹಿಂದೆ,ವಾಪಸ್,ಹೋಮ್,ಮುಚ್ಚ
KN HELP ಸಹಾಯ,ಹೆಲ್ಪ್
KN CALL ಕರೆ,ಕಾಲ್,ಫೋನ್,ತಜ್ಞ,ಕಿಸಾನ್
KN DIARY ಡೈರಿ,ಟಿಪ್ಪಣಿ
KN WEATHER ಹವಾಮಾನ,ಮಳೆ
KN STAGES ಹಂತ,ಸ್ಟೇಜ್,ಅವಸ್ಥೆ
KN SETTINGS ಸೆಟ್ಟಿಂಗ್
KN LIBRARY ಗ್ರಂಥಾಲಯ,ಲೈಬ್ರರಿ,ರೋಗ
KN PLOT ಪ್ಲಾಟ್,ಹೊಲ
KN YELLOW ಹಳದಿ
OR CAMERA ଫଟୋ,କ୍ୟାମେରା,ଛବି
OR GALLERY ଗ୍ୟାଲେରୀ,ବାଛ
OR LISTEN ଶୁଣ,ପଢ,କୁହ
OR NEW_CHECK ନୂଆ,ନୂତନ,ପୁଣି
OR BACK ପଛକୁ,ଫେର,ହୋମ,ବନ୍ଦ
OR HELP ସାହାଯ୍ୟ,ହେଲ୍ପ
OR CALL କଲ,ଫୋନ,ବିଶେଷଜ୍ଞ,କିଷାନ
OR DIARY ଡାଏରୀ,ଟିପ୍ପଣୀ
OR WEATHER ପାଗ,ବର୍ଷା,ପାଣିପାଗ
OR STAGES ପର୍ଯ୍ୟାୟ,ସ୍ଟେଜ୍,ଅବସ୍ଥା
OR SETTINGS ସେଟିଂ
OR LIBRARY ଲାଇବ୍ରେରୀ,ପୁସ୍ତକ,ରୋଗ
OR PLOT ପ୍ଲଟ୍,ଜମି,ଚାଷ
OR YELLOW ହଳଦିଆ,ହଳଦୀ
ML CAMERA ഫോട്ടോ,ക്യാമറ,ചിത്രം
ML GALLERY ഗാലറി,തിരഞ്ഞെടുക്ക
ML LISTEN കേൾ,വായി,പറയ
ML NEW_CHECK പുതിയ,വീണ്ടും
ML BACK പിന്നോട്ട്,തിരികെ,ഹോം,അടയ്ക്ക
ML HELP സഹായം,ഹെൽപ്
ML CALL വിളി,കോൾ,ഫോൺ,വിദഗ്ധ,കിസാൻ
ML DIARY ഡയറി,കുറിപ്പ്
ML WEATHER കാലാവസ്ഥ,മഴ
ML STAGES ഘട്ടം,സ്റ്റേജ്,അവസ്ഥ
ML SETTINGS ക്രമീകരണ,സെറ്റിംഗ്
ML LIBRARY ലൈബ്രറി,ഗ്രന്ഥശാല,രോഗം
ML PLOT പ്ലോട്ട്,വയൽ,കൃഷിയിടം
ML YELLOW മഞ്ഞ
PA CAMERA ਫੋਟੋ,ਕੈਮਰਾ,ਤਸਵੀਰ
PA GALLERY ਗੈਲਰੀ,ਚੁਣ
PA LISTEN ਸੁਣ,ਪੜ੍ਹ,ਬੋਲ
PA NEW_CHECK ਨਵਾਂ,ਨਵੀਂ,ਦੁਬਾਰਾ,ਫਿਰ
PA BACK ਵਾਪਸ,ਪਿੱਛੇ,ਹੋਮ,ਬੰਦ
PA HELP ਮਦਦ,ਹੈਲਪ
PA CALL ਕਾਲ,ਫੋਨ,ਮਾਹਿਰ,ਕਿਸਾਨ
PA DIARY ਡਾਇਰੀ,ਨੋਟ
PA WEATHER ਮੌਸਮ,ਮੀਂਹ
PA STAGES ਪੜਾਅ,ਸਟੇਜ,ਅਵਸਥਾ
PA SETTINGS ਸੈਟਿੰਗ
PA LIBRARY ਲਾਇਬ੍ਰੇਰੀ,ਕਿਤਾਬ,ਬਿਮਾਰੀ
PA PLOT ਪਲਾਟ,ਖੇਤ
PA YELLOW ਪੀਲ
AS CAMERA ফটো,কেমেৰা,ছবি
AS GALLERY গেলাৰী,বাছ
AS LISTEN শুন,পঢ়,ক'
AS NEW_CHECK নতুন,পুনৰ
AS BACK উভতি,পিছলৈ,হোম,বন্ধ
AS HELP সহায়,হেল্প
AS CALL কল,ফোন,বিশেষজ্ঞ,কিষাণ
AS DIARY ডায়েৰী,টোকা
AS WEATHER বতৰ,বৰষুণ
AS STAGES পৰ্যায়,স্তৰ,ষ্টেজ
AS SETTINGS ছেটিং
AS LIBRARY লাইব্ৰেৰী,কিতাপ,ৰোগ
AS PLOT প্লট,পথাৰ,খেতি
AS YELLOW হালধীয়া
EN CAMERA photograph,pic,image,scan,capture,shoot,snapshot,diagnose,detect,identify,check leaf,check plant
EN GALLERY album,saved,upload,existing,from phone,files,my photos
EN LISTEN aloud,hear,voice,tell me,narrate,out loud
EN NEW_CHECK next,fresh,reset,another leaf,one more,start over
EN BACK previous,go home,cancel,stop,dismiss,go back
EN HELP support,assist,guide,how to,what can i say,instructions
EN CALL dial,ring,agriculture officer,toll free,1800,krishi,contact,talk to
EN DIARY journal,log,notebook,note down,write down,my notes
EN WEATHER climate,temperature,storm,humid,hailstorm,monsoon,wind,will it rain
EN STAGES growth,phase,sowing,flowering,harvest,when to spray,crop calendar
EN SETTINGS preferences,options,configure,configuration
EN LIBRARY encyclopedia,catalog,disease info,pest list,all diseases,learn about,information
EN PLOT plots,report,my fields,land,acre,survey,field report
EN YELLOW yellowing,yellowish,chlorosis,nitrogen,deficiency,pale leaves
HI CAMERA पिक्चर,क्लिक,स्नैप,स्कैन,जांच,जाँच,पहचान,खींचो,उतार,फोटो खींच,पत्ता दिखा
HI GALLERY एल्बम,अपलोड,सेव,पुरानी,मोबाइल से,चुनो,चुनें,फाइल
HI LISTEN सुनाइए,सुना,बताओ,बताइए,आवाज,आवाज़,जोर से,ज़ोर से,पढ़कर,पढकर
HI NEW_CHECK अगला,दूसरा,दूसरी,एक और,फिर से,रीसेट,नए सिरे
HI BACK लौट,पीछे जा,मुख्य,रद्द,रुक,बाहर,वापिस,हटो
HI HELP कैसे,गाइड,समझाओ,समझा,सहयोग,हेल्पलाइन,मार्गदर्शन
HI CALL डायल,बात करो,बात करनी,संपर्क,कृषि अधिकारी,अधिकारी,टोल फ्री,एक्सपर्ट,फोन करो,बुलाओ
HI DIARY जर्नल,नोटबुक,लिखो,रिकॉर्ड,हिसाब,खाता,बही,डायरी में
HI WEATHER तापमान,आंधी,तूफान,तूफ़ान,ओले,गर्मी,ठंड,पाला,बादल,हवा,मानसून,बरसात,वर्षा
HI STAGES बुवाई,बोआई,फूल,कटाई,कैलेंडर,विकास,दशा,फसल चक्र
HI SETTINGS सेटिंग्स,विकल्प,पसंद,व्यवस्था
HI LIBRARY किताब,जानकारी,रोगों,बीमारियाँ,बीमारियां,कीट,कीड़े,सूची,विश्वकोश
HI PLOT खेतों,जमीन,ज़मीन,रिपोर्ट,एकड़,प्लाट,बीघा,सर्वे
HI YELLOW पीलापन,पीली पत्ती,पीले पत्ते,नाइट्रोजन,कमी,पोषक,पोषण
BN CAMERA ফটো তোল,তোলো,তুলুন,ক্লিক,স্ক্যান,চিত্র,পরীক্ষা,শনাক্ত,ছবি তোল
BN GALLERY গ্যালারী,বাছাই,নির্বাচন,অ্যালবাম,আপলোড,সংরক্ষিত
BN LISTEN শোনা,শোনাও,পড়ো,বলো,জোরে,আওয়াজ
BN NEW_CHECK পরবর্তী,আরেকটি,আরেক,রিসেট
BN BACK পিছনে,বাতিল,থামো,প্রস্থান
BN HELP সহায়তা,কীভাবে,গাইড
BN CALL কৃষি,কর্মকর্তা,যোগাযোগ,কথা বল,ডায়াল
BN DIARY ডাইরি,নোট,খাতা,লেখো,রেকর্ড,জার্নাল
BN WEATHER তাপমাত্রা,ঝড়,শিলাবৃষ্টি,মেঘ,বাতাস,বর্ষা,পূর্বাভাস
BN STAGES বপন,ফসল কাটা,ক্যালেন্ডার,বৃদ্ধি
BN SETTINGS সেটিংস,বিকল্প,পছন্দ
BN LIBRARY গ্রন্থাগার,রোগের,পোকা,তালিকা,তথ্য
BN PLOT ক্ষেত,খেত,রিপোর্ট,প্রতিবেদন,একর
BN YELLOW নাইট্রোজেন,ঘাটতি,পুষ্টি
TE CAMERA ఫొటో,పిక్,క్లిక్,స్కాన్,తీయి,తీసుకో,గుర్తించు,పరీక్ష,చిత్రం
TE GALLERY ఎంచుకో,ఎంపిక,ఆల్బమ్,అప్‌లోడ్,సేవ్
TE LISTEN వినిపించు,విను,చదువు,చెప్పు,వినండి,గట్టిగా,చదివి
TE NEW_CHECK కొత్త,మళ్ళీ,మరలా,తర్వాత,ఇంకొకటి,మరొకటి,రీసెట్
TE BACK వెనక్కి,వెనుకకు,హోమ్,మూసివేయి,ముగించు,రద్దు,ఆపు
TE HELP సహాయం,హెల్ప్,సాయం,సహాయము,ఎలా,గైడ్
TE CALL కాల్,ఫోన్,నిపుణుడు,నిపుణులు,కిసాన్,అధికారి,సంప్రదించు,మాట్లాడు,డయల్
TE DIARY డైరీ,గమనిక,నోట్స్,నోట్,రాయి,రికార్డు,జర్నల్
TE WEATHER వాతావరణం,వర్షం,వాన,ఉష్ణోగ్రత,తుఫాను,గాలి,ఎండ,వడగళ్ళు,రుతుపవనాలు
TE STAGES దశ,దశలు,స్టేజ్,విత్తనం,విత్తు,పూత,కోత,క్యాలెండర్,పెరుగుదల
TE SETTINGS సెట్టింగ్,సెట్టింగ్స్,ఎంపికలు,అమరికలు
TE LIBRARY గ్రంథాలయం,లైబ్రరీ,పుస్తకం,వ్యాధి,వ్యాధులు,తెగులు,తెగుళ్లు,పురుగు,జాబితా,సమాచారం
TE PLOT ప్లాట్,పొలం,భూమి,నివేదిక,రిపోర్ట్,ఎకరా,చేను
TE YELLOW పసుపు,పసుపుపచ్చ,నత్రజని,లోపం,పోషక
MR CAMERA फोटो,कॅमेरा,चित्र,प्रतिमा,काढ,क्लिक,स्कॅन,तपास,ओळख
MR GALLERY गॅलरी,निवड,निवडा,अल्बम,अपलोड,जतन
MR LISTEN ऐक,वाच,सांग,ऐकव,आवाज,मोठ्याने
MR NEW_CHECK नवीन,नवा,नवी,पुन्हा,परत,पुढचा,दुसरा,आणखी
MR BACK मागे,परत जा,होम,मुख्य,बंद,रद्द,थांब
MR HELP मदत,हेल्प,सहाय्य,सहकार्य,कसे,मार्गदर्शन
MR CALL कॉल,फोन,तज्ज्ञ,तज्ञ,किसान,कृषी,अधिकारी,संपर्क,बोला
MR DIARY डायरी,नोंद,नोंदी,वही,जर्नल,लिही
MR WEATHER हवामान,पाऊस,तापमान,वादळ,गारपीट,ढग,मान्सून,अंदाज
MR STAGES अवस्था,टप्पा,टप्पे,स्टेज,पेरणी,फुलोरा,काढणी,कॅलेंडर,वाढ
MR SETTINGS सेटिंग,सेटिंग्ज,पर्याय,मांडणी
MR LIBRARY ग्रंथालय,लायब्ररी,पुस्तक,रोग,आजार,कीड,किडी,यादी,माहिती
MR PLOT प्लॉट,शेत,जमीन,अहवाल,एकर,रिपोर्ट
MR YELLOW पिवळ,पिवळा,पिवळी,पिवळे,नायट्रोजन,कमतरता
TA CAMERA புகைப்படம்,கேமிரா,படமெடு,கிளிக்,ஸ்கேன்,சோதி,கண்டறி,அடையாளம்
TA GALLERY கேலரி,தேர்வு,தேர்ந்தெடு,ஆல்பம்,பதிவேற்று,சேமித்த
TA LISTEN கேளு,படித்து,சொல்லு,குரல்,ஒலி,உரக்க
TA NEW_CHECK புதுசு,மறுபடி,அடுத்த,வேறு,இன்னொரு,ரீசெட்
TA BACK பின்னால்,திரும்பு,வெளியேறு,நிறுத்து,ரத்து
TA HELP ஹெல்ப்,உதவு,எப்படி,வழிகாட்டி,ஆதரவு
TA CALL அழைப்பு,தொலைபேசி,வல்லுநர்,அதிகாரி,தொடர்பு,பேசு,டயல்
TA DIARY குறிப்புகள்,பதிவு,ஜர்னல்,எழுது,நோட்
TA WEATHER வெப்பநிலை,புயல்,காற்று,வெயில்,பனி,ஆலங்கட்டி,பருவமழை,முன்னறிவிப்பு,தட்பவெப்ப
TA STAGES கட்டம்,விதைப்பு,பூக்கும்,அறுவடை,காலண்டர்,வளர்ச்சி,பருவம்
TA SETTINGS அமைப்புகள்,செட்டிங்ஸ்,விருப்பங்கள்
TA LIBRARY புத்தகம்,நோய்கள்,பூச்சி,பூச்சிகள்,பட்டியல்,தகவல்
TA PLOT தோட்டம்,அறிக்கை,ரிப்போர்ட்,ஏக்கர்,சர்வே
TA YELLOW மஞ்சளாக,நைட்ரஜன்,குறைபாடு,ஊட்டச்சத்து
GU CAMERA ફોટા,પાડ,ખેંચ,ક્લિક,સ્કેન,તપાસ,ઓળખ
GU GALLERY ગેલરી,આલ્બમ,અપલોડ,સેવ
GU LISTEN સંભળાવ,વાંચ,કહે,અવાજ,મોટેથી
GU NEW_CHECK નવો,ફરીથી,બીજું,આગળનું,રીસેટ
GU BACK પાછળ,રદ,અટક,બહાર
GU HELP કેવી રીતે,માર્ગદર્શન
GU CALL અધિકારી,સંપર્ક,વાત કર,ડાયલ
GU DIARY નોંધો,જર્નલ,લખ,રેકોર્ડ,ચોપડો
GU WEATHER તાપમાન,વાવાઝોડું,કરા,વાદળ,પવન,ચોમાસું,આગાહી
GU STAGES તબક્કા,વાવણી,ફૂલ,લણણી,કેલેન્ડર,વૃદ્ધિ
GU SETTINGS સેટિંગ્સ,વિકલ્પ,પસંદગી
GU LIBRARY લાયબ્રેરી,પુસ્તક,રોગો,જીવાત,યાદી,માહિતી
GU PLOT ખેતર,જમીન,અહેવાલ,રિપોર્ટ,એકર
GU YELLOW પીળા,પીળું,નાઇટ્રોજન,ઉણપ,પોષક
UR CAMERA کیمرا,کھینچ,کلک,اسکین,جانچ,پہچان,تصویر لو
UR GALLERY البم,اپ لوڈ,محفوظ
UR LISTEN سنا,سناؤ,بتاؤ,آواز,بلند
UR NEW_CHECK نیے,اگلا,دوسرا,ری سیٹ
UR BACK منسوخ,رک,باہر
UR HELP ہیلپ,رہنمائی,کیسے,گائیڈ
UR CALL افسر,رابطہ,بات کر,ڈائل
UR DIARY نوٹس,لکھ,ریکارڈ,جرنل
UR WEATHER درجہ حرارت,طوفان,ژالہ باری,بادل,ہوا,مون سون,پیشگوئی
UR STAGES مراحل,سٹیج,بوائی,پھول,کٹائی,کیلنڈر,بڑھوتری
UR SETTINGS سیٹنگز,ترتیبات,اختیارات
UR LIBRARY کتب خانہ,بیماریاں,کیڑے,فہرست,معلومات
UR PLOT زمین,رپورٹ,ایکڑ
UR YELLOW پیلاہٹ,نائٹروجن,کمی,غذائیت
KN CAMERA ಕ್ಯಾಮರಾ,ಕ್ಲಿಕ್,ಸ್ಕ್ಯಾನ್,ತೆಗೆ,ಪರೀಕ್ಷೆ,ಗುರುತಿಸು
KN GALLERY ಆಯ್ಕೆಮಾಡು,ಆಲ್ಬಮ್,ಅಪ್‌ಲೋಡ್,ಉಳಿಸಿದ
KN LISTEN ಕೇಳು,ಕೇಳಿಸು,ಓದು,ಹೇಳು,ಧ್ವನಿ,ಗಟ್ಟಿಯಾಗಿ
KN NEW_CHECK ಹೊಸದು,ಮರು,ಮುಂದಿನ,ಇನ್ನೊಂದು,ರೀಸೆಟ್
KN BACK ಹಿಂತಿರುಗು,ಮುಚ್ಚು,ರದ್ದು,ನಿಲ್ಲಿಸು
KN HELP ನೆರವು,ಹೇಗೆ,ಮಾರ್ಗದರ್ಶಿ
KN CALL ತಜ್ಞರು,ಅಧಿಕಾರಿ,ಸಂಪರ್ಕ,ಮಾತನಾಡು,ಡಯಲ್
KN DIARY ನೋಟ್ಸ್,ನೋಟ್,ಬರೆ,ದಾಖಲೆ,ಜರ್ನಲ್
KN WEATHER ತಾಪಮಾನ,ಚಂಡಮಾರುತ,ಗಾಳಿ,ಮೋಡ,ಆಲಿಕಲ್ಲು,ಮುಂಗಾರು,ಮುನ್ಸೂಚನೆ
KN STAGES ಹಂತಗಳು,ಬಿತ್ತನೆ,ಹೂವು,ಕೊಯ್ಲು,ಕ್ಯಾಲೆಂಡರ್,ಬೆಳವಣಿಗೆ
KN SETTINGS ಸೆಟ್ಟಿಂಗ್ಸ್,ಆಯ್ಕೆಗಳು,ಹೊಂದಾಣಿಕೆ
KN LIBRARY ಪುಸ್ತಕ,ರೋಗಗಳು,ಕೀಟ,ಕೀಟಗಳು,ಪಟ್ಟಿ,ಮಾಹಿತಿ
KN PLOT ಜಮೀನು,ವರದಿ,ರಿಪೋರ್ಟ್,ಎಕರೆ
KN YELLOW ಸಾರಜನಕ,ಕೊರತೆ,ಪೋಷಕ
OR CAMERA ଉଠାଅ,କ୍ଲିକ୍,ସ୍କାନ୍,ଯାଞ୍ଚ,ଚିହ୍ନଟ
OR GALLERY ବାଛନ୍ତୁ,ଆଲବମ୍,ଅପଲୋଡ୍,ସେଭ୍
OR LISTEN ଶୁଣାଅ,ପଢ଼,କହ,ଜୋରରେ
OR NEW_CHECK ନୂତନ,ପୁନର୍ବାର,ପରବର୍ତ୍ତୀ,ରିସେଟ୍
OR BACK ଫେରିଯାଅ,ବାତିଲ,ରୁହ
OR HELP ସହାୟତା,କେମିତି,ଗାଇଡ୍
OR CALL ଅଧିକାରୀ,ଯୋଗାଯୋଗ,କଥା,ଡାଏଲ୍
OR DIARY ଡାଇରି,ନୋଟ୍,ଲେଖ,ରେକର୍ଡ,ଜର୍ନାଲ୍
OR WEATHER ତାପମାତ୍ରା,ଝଡ଼,ଝଡ,ମେଘ,ପବନ,ମୌସୁମୀ,ପୂର୍ବାନୁମାନ
OR STAGES ସ୍ଟେଜ୍,ବୁଣା,ଫୁଲ,ଅମଳ,କ୍ୟାଲେଣ୍ଡର,ବୃଦ୍ଧି
OR SETTINGS ସେଟିଂସ୍,ବିକଳ୍ପ
OR LIBRARY ପୁସ୍ତକାଳୟ,ବହି,ପୋକ,ତାଲିକା,ସୂଚନା
OR PLOT ଚାଷଜମି,ରିପୋର୍ଟ,ଏକର
OR YELLOW ନାଇଟ୍ରୋଜେନ,ଅଭାବ,ପୋଷକ
ML CAMERA ഫോട്ടോയെടുക്ക,ക്ലിക്ക്,സ്കാൻ,എടുക്ക,പരിശോധ,തിരിച്ചറി
ML GALLERY തിരഞ്ഞെടുപ്പ്,ആൽബം,അപ്‌ലോഡ്,സേവ്
ML LISTEN കേൾക്ക,വായിക്ക,പറയൂ,ശബ്ദ,ഉറക്കെ
ML NEW_CHECK അടുത്ത,മറ്റൊന്ന്,റീസെറ്റ്
ML BACK മടങ്ങ,റദ്ദ്,നിർത്ത
ML HELP സഹായ,എങ്ങനെ,ഗൈഡ്
ML CALL വിദഗ്ധ,ഉദ്യോഗസ്ഥ,ബന്ധപ്പെ,സംസാരി,ഡയൽ
ML DIARY നോട്ട്,നോട്ടുകൾ,എഴുത,രേഖ,ജേണൽ
ML WEATHER താപനില,കൊടുങ്കാറ്റ്,കാറ്റ്,മേഘ,ആലിപ്പഴ,മൺസൂൺ,പ്രവചന
ML STAGES ഘട്ടങ്ങൾ,വിതയ്ക്ക,പൂക്ക,വിളവെടുപ്പ്,കലണ്ടർ,വളർച്ച
ML SETTINGS സെറ്റിംഗ്സ്,ഓപ്ഷൻ
ML LIBRARY പുസ്തക,രോഗങ്ങൾ,കീട,പട്ടിക,വിവരം
ML PLOT സ്ഥലം,റിപ്പോർട്ട്,ഏക്കർ
ML YELLOW നൈട്രജൻ,കുറവ്,പോഷക
PA CAMERA ਖਿੱਚ,ਕਲਿੱਕ,ਸਕੈਨ,ਜਾਂਚ,ਪਛਾਣ
PA GALLERY ਗੇਲਰੀ,ਚੁਣੋ,ਐਲਬਮ,ਅਪਲੋਡ,ਸੇਵ
PA LISTEN ਸੁਣਾ,ਦੱਸ,ਆਵਾਜ਼,ਉੱਚੀ
PA NEW_CHECK ਨਵਾ,ਅਗਲਾ,ਇੱਕ ਹੋਰ,ਰੀਸੈੱਟ
PA BACK ਮੁੜ,ਰੱਦ,ਰੁਕ
PA HELP ਸਹਾਇਤਾ,ਕਿਵੇਂ,ਗਾਈਡ
PA CALL ਅਧਿਕਾਰੀ,ਸੰਪਰਕ,ਗੱਲ,ਡਾਇਲ
PA DIARY ਨੋਟਸ,ਲਿਖ,ਰਿਕਾਰਡ,ਜਰਨਲ
PA WEATHER ਤਾਪਮਾਨ,ਤੂਫ਼ਾਨ,ਤੂਫਾਨ,ਬੱਦਲ,ਹਵਾ,ਗੜੇ,ਭਵਿੱਖਬਾਣੀ
PA STAGES ਬਿਜਾਈ,ਫੁੱਲ,ਕਟਾਈ,ਕੈਲੰਡਰ,ਵਾਧਾ
PA SETTINGS ਸੈਟਿੰਗਜ਼,ਸੈਟਿੰਗਾਂ,ਵਿਕਲਪ
PA LIBRARY ਲਾਇਬਰੇਰੀ,ਬਿਮਾਰੀਆਂ,ਕੀੜੇ,ਸੂਚੀ,ਜਾਣਕਾਰੀ
PA PLOT ਜ਼ਮੀਨ,ਰਿਪੋਰਟ,ਏਕੜ
PA YELLOW ਪੀਲਾ,ਪੀਲੇ,ਪੀਲੀ,ਨਾਈਟ੍ਰੋਜਨ,ਕਮੀ
AS CAMERA তোলা,ক্লিক,স্কেন,পৰীক্ষা,চিনাক্ত
AS GALLERY বাছনি,এলবাম,আপলোড,সংৰক্ষিত
AS LISTEN শুনাওক,পঢ়ক,কওক,মাত,ডাঙৰকৈ
AS NEW_CHECK আকৌ,পৰৱৰ্তী,আন এটা,ৰিছেট
AS BACK পিছত,বাতিল
AS HELP সাহায্য,কেনেকৈ,গাইড
AS CALL বিষয়া,যোগাযোগ,কথা,ডায়েল
AS DIARY নোট,লিখা,ৰেকৰ্ড,জাৰ্নেল
AS WEATHER উষ্ণতা,ধুমুহা,বতাহ,ডাৱৰ,শিলাবৃষ্টি,মৌচুমী,পূৰ্বানুমান
AS STAGES সিঁচা,ফুল,চপোৱা,কেলেণ্ডাৰ,বৃদ্ধি
AS SETTINGS ছেটিংছ,বিকল্প
AS LIBRARY গ্ৰন্থাগাৰ,পোক,তালিকা,তথ্য
AS PLOT মাটি,প্ৰতিবেদন,ৰিপৰ্ট,বিঘা
AS YELLOW নাইট্ৰজেন,অভাৱ,পুষ্টি
"""

    private fun norm(t: String): String =
        Normalizer.normalize(t, Normalizer.Form.NFC).lowercase().replace("‍", "").replace("‌", "")

    private val words: Map<String, Map<Cmd, List<String>>> by lazy {
        val m = HashMap<String, HashMap<Cmd, MutableList<String>>>()
        TABLE.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val p = line.split(' ', limit = 3)
            if (p.size == 3) {
                val c = try { Cmd.valueOf(p[1]) } catch (e: Exception) { null } ?: return@forEach
                m.getOrPut(p[0]) { HashMap() }.getOrPut(c) { ArrayList() }.addAll(p[2].split(',').map { norm(it.trim()) }.filter { it.isNotEmpty() })
            }
        }
        m
    }

    /** The command in [heard], or null. Tries the farmer's language first, then English. */
    fun match(lang: Lang, heard: String): Cmd? {
        val t = norm(heard)
        if (t.isBlank()) return null
        val own = words[lang.name]
        val en = words["EN"]
        for (c in order) {
            if (own?.get(c)?.any { it in t } == true) return c
            if (en?.get(c)?.any { it in t } == true) return c
        }
        return fuzzy(own, en, t)
    }

    /** Speech engines spell loan words their own way (டைரி / டயரி), so a word that is one or two letters off still counts. */
    private fun fuzzy(own: Map<Cmd, List<String>>?, en: Map<Cmd, List<String>>?, t: String): Cmd? {
        val tokens = t.split(Regex("[^\\p{L}\\p{M}]+")).filter { it.length >= 3 }
        if (tokens.isEmpty()) return null
        for (c in order) {
            val list = (own?.get(c) ?: emptyList()) + (en?.get(c) ?: emptyList())
            for (w in list) {
                if (w.length < 4) continue
                val allowed = if (w.length >= 7) 2 else 1
                for (tok in tokens) {
                    if (kotlin.math.abs(tok.length - w.length) > allowed) continue
                    if (editDistance(tok, w, allowed) <= allowed) return c
                }
            }
        }
        return null
    }

    /** Levenshtein distance, giving up (returns limit+1) as soon as it must exceed [limit]. */
    internal fun editDistance(a: String, b: String, limit: Int): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > limit) return limit + 1
            prev = cur
        }
        return prev[b.length]
    }

    private val langNames: Map<Lang, List<String>> = mapOf(
        Lang.EN to listOf("english", "अंग्रेज़ी", "अंग्रेजी"),
        Lang.HI to listOf("hindi", "हिन्दी", "हिंदी"),
        Lang.BN to listOf("bengali", "bangla", "বাংলা", "बंगाली", "बांग्ला"),
        Lang.TE to listOf("telugu", "తెలుగు", "तेलुगु", "तेलुगू"),
        Lang.MR to listOf("marathi", "मराठी"),
        Lang.TA to listOf("tamil", "தமிழ்", "तमिल"),
        Lang.GU to listOf("gujarati", "ગુજરાતી", "गुजराती"),
        Lang.UR to listOf("urdu", "اردو", "उर्दू"),
        Lang.KN to listOf("kannada", "ಕನ್ನಡ", "कन्नड़", "कन्नड"),
        Lang.OR to listOf("odia", "oriya", "ଓଡ଼ିଆ", "ओड़िया", "उड़िया"),
        Lang.ML to listOf("malayalam", "മലയാളം", "मलयालम"),
        Lang.PA to listOf("punjabi", "ਪੰਜਾਬੀ", "पंजाबी"),
        Lang.AS to listOf("assamese", "অসমীয়া", "असमिया"),
    )

    /** "Tamil", "தமிழ்", "हिंदी में" ... : the language the farmer asked for, or null. */
    fun matchLang(heard: String): Lang? {
        val t = norm(heard)
        return langNames.entries.firstOrNull { (_, names) -> names.any { norm(it) in t } }?.key
    }

    /** Every table word of every language, for the meaning-matcher's example list. */
    fun allWords(maxPerList: Int = 8): List<Pair<Cmd, String>> = words.values.flatMap { m -> m.flatMap { (c, ws) -> ws.take(maxPerList).map { c to it } } }.distinct()

    /** First hit among several recogniser guesses. */
    fun matchAny(lang: Lang, guesses: List<String>): Cmd? = guesses.firstNotNullOfOrNull { match(lang, it) }

    /** Words shown on the "say something" prompt (examples). */
    fun examples(lang: Lang): String = when (lang) {
        Lang.EN -> "take photo, gallery, help, diary, weather, Tamil"
        Lang.HI -> "फोटो लें, गैलरी, मदद, डायरी, मौसम, तमिल"
        else -> words[lang.name]?.let { m -> listOf(Cmd.CAMERA, Cmd.GALLERY, Cmd.HELP, Cmd.DIARY, Cmd.WEATHER).mapNotNull { m[it]?.firstOrNull() }.joinToString(", ") }
            ?: "photo, help, diary"
    }
}
