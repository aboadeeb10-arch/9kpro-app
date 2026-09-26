package tv.ninekpro.app.data

/** Arabic / Hebrew names for the big clubs (API-Football sends English). Unknown teams keep the English name. */
object TeamNames {
    private val ar = mapOf(
        "Real Madrid" to "ريال مدريد", "Barcelona" to "برشلونة", "Atletico Madrid" to "أتلتيكو مدريد", "Sevilla" to "إشبيلية", "Valencia" to "فالنسيا",
        "Manchester City" to "مانشستر سيتي", "Manchester United" to "مانشستر يونايتد", "Liverpool" to "ليفربول", "Arsenal" to "آرسنال", "Chelsea" to "تشيلسي", "Tottenham" to "توتنهام", "Newcastle" to "نيوكاسل", "Aston Villa" to "أستون فيلا",
        "Bayern Munich" to "بايرن ميونخ", "Borussia Dortmund" to "بوروسيا دورتموند", "Bayer Leverkusen" to "باير ليفركوزن", "RB Leipzig" to "لايبزيغ",
        "Juventus" to "يوفنتوس", "Inter" to "إنتر ميلان", "AC Milan" to "ميلان", "Napoli" to "نابولي", "AS Roma" to "روما", "Lazio" to "لاتسيو", "Atalanta" to "أتالانتا",
        "Paris Saint Germain" to "باريس سان جيرمان", "Marseille" to "مارسيليا", "Lyon" to "ليون", "Monaco" to "موناكو",
        "Al Hilal" to "الهلال", "Al Nassr" to "النصر", "Al Ittihad" to "الاتحاد", "Al Ahli" to "الأهلي", "Al Shabab" to "الشباب", "Al Ahly" to "الأهلي", "Zamalek" to "الزمالك", "Pyramids" to "بيراميدز",
        "Al Sadd" to "السد", "Al Ain" to "العين", "Raja Casablanca" to "الرجاء", "Wydad AC" to "الوداد", "ES Tunis" to "الترجي",
        "Maccabi Tel Aviv" to "مكابي تل أبيب", "Maccabi Haifa" to "مكابي حيفا", "Hapoel Beer Sheva" to "هبوعيل بئر السبع", "Hapoel Tel Aviv" to "هبوعيل تل أبيب", "Beitar Jerusalem" to "بيتار القدس", "Bnei Sakhnin" to "أبناء سخنين",
        "Ajax" to "أياكس", "PSV Eindhoven" to "آيندهوفن", "Benfica" to "بنفيكا", "FC Porto" to "بورتو", "Sporting CP" to "سبورتينغ لشبونة", "Galatasaray" to "غلطة سراي", "Fenerbahce" to "فنربخشة", "Celtic" to "سلتيك",
    )
    private val he = mapOf(
        "Real Madrid" to "ריאל מדריד", "Barcelona" to "ברצלונה", "Atletico Madrid" to "אתלטיקו מדריד", "Manchester City" to "מנצ'סטר סיטי", "Manchester United" to "מנצ'סטר יונייטד", "Liverpool" to "ליברפול", "Arsenal" to "ארסנל", "Chelsea" to "צ'לסי", "Tottenham" to "טוטנהאם",
        "Bayern Munich" to "באיירן מינכן", "Borussia Dortmund" to "דורטמונד", "Juventus" to "יובנטוס", "Inter" to "אינטר", "AC Milan" to "מילאן", "Napoli" to "נאפולי", "AS Roma" to "רומא", "Paris Saint Germain" to "פאריס סן ז'רמן",
        "Maccabi Tel Aviv" to "מכבי תל אביב", "Maccabi Haifa" to "מכבי חיפה", "Hapoel Beer Sheva" to "הפועל באר שבע", "Hapoel Tel Aviv" to "הפועל תל אביב", "Beitar Jerusalem" to "בית\"ר ירושלים", "Bnei Sakhnin" to "בני סכנין", "Maccabi Netanya" to "מכבי נתניה", "Hapoel Haifa" to "הפועל חיפה",
        "Al Hilal" to "אל הילאל", "Al Nassr" to "אל נאסר", "Al Ahly" to "אל אהלי", "Zamalek" to "זמאלכ", "Ajax" to "אייאקס", "Benfica" to "בנפיקה", "FC Porto" to "פורטו", "Galatasaray" to "גלאטסראיי", "Celtic" to "סלטיק",
    )
    fun local(en: String, lang: String): String = when (lang) { "ar" -> ar[en] ?: en; "iw", "he" -> he[en] ?: en; else -> en }
    /** Words worth matching in EPG titles: drop FC/CF/SC/Al and short words. */
    fun tokens(en: String): List<String> = en.split(' ', '-', '.').map { it.trim() }.filter { it.length >= 3 && it.lowercase() !in setOf("fc", "cf", "sc", "ac", "as", "al", "the", "club", "united", "city", "real", "saint", "sporting", "athletic", "eindhoven") }
}
