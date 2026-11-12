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

    // ---------------- matching a fixture against guide (EPG) titles ----------------
    /** Lower-case, no accents (Atlético → atletico, أ → ا), punctuation → spaces, single spaces. Applied to team names, aliases and EPG titles alike. */
    fun norm(s: String): String {
        val d = try { java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "") } catch (e: Throwable) { s }
        return d.lowercase().replace(Regex("[-_./'’`´\"&(),:+!]+"), " ").replace(Regex("\\s+"), " ").trim()
    }
    /** Club words that say nothing on their own (ESPN sends "Manchester City", the guide may say "Man City"). */
    private val stop = setOf("fc", "cf", "sc", "ac", "as", "al", "the", "club", "de", "of", "cp", "afc", "fk", "sk", "sv", "cd", "ud", "rc", "ss", "us", "bk", "if", "kv", "sd", "fsv", "vfb", "vfl", "tsg", "ca", "rb", "bc", "og", "ogc", "rcd", "cfr", "nk", "hnk", "sl", "clube", "futebol", "calcio", "1899", "1904", "1909", "fk", "sk")
    /** Other spellings the guide uses. Key = normalised English name (ESPN / API-Football). A list starting with "!" means: match by these spellings ONLY (never by the single word, e.g. "milan" would also hit Inter Milan). */
    private val alias: Map<String, List<String>> = mapOf(
        "paris saint germain" to listOf("psg", "paris sg", "باريس سان جيرمان", "باريس", "פאריס"),
        "manchester city" to listOf("man city", "man c", "مان سيتي", "مانشستر سيتي", "מנצ'סטר סיטי", "מנצסטר סיטי", "מן סיטי"),
        "manchester united" to listOf("man utd", "man united", "man u", "manchester utd", "مان يونايتد", "مانشستر يونايتد", "מנצ'סטר יונייטד", "מנצסטר יונייטד", "מן יונייטד"),
        "tottenham hotspur" to listOf("tottenham", "spurs", "توتنهام", "טוטנהאם"),
        "newcastle united" to listOf("newcastle", "نيوكاسل", "ניוקאסל"),
        "west ham united" to listOf("west ham", "وست هام", "ווסטהאם", "ווסט האם"),
        "wolverhampton wanderers" to listOf("wolves", "wolverhampton", "ولفرهامبتون", "וולבס"),
        "nottingham forest" to listOf("nottm forest", "nottingham", "نوتنغهام", "נוטינגהאם"),
        "brighton hove albion" to listOf("brighton", "برايتون", "ברייטון"),
        "leicester city" to listOf("leicester", "ليستر", "לסטר"),
        "aston villa" to listOf("أستون فيلا", "אסטון וילה"),
        "crystal palace" to listOf("palace", "كريستال بالاس", "קריסטל פאלאס"),
        "liverpool" to listOf("ليفربول", "ליברפול"), "arsenal" to listOf("آرسنال", "ارسنال", "ארסנל"), "chelsea" to listOf("تشيلسي", "צ'לסי", "צלסי"), "everton" to listOf("إيفرتون", "אברטון"),
        "internazionale" to listOf("!", "inter", "إنتر", "انتر", "אינטר"),
        "ac milan" to listOf("!", "ac milan", "a c milan", "milan ac", "ميلان", "מילאן"),
        "juventus" to listOf("juve", "يوفنتوس", "יובנטוס"), "napoli" to listOf("نابولي", "נאפולי"), "as roma" to listOf("roma", "روما", "רומא"), "lazio" to listOf("لاتسيو", "לאציו"), "atalanta" to listOf("أتالانتا", "אטלנטה"), "fiorentina" to listOf("فيورنتينا", "פיורנטינה"),
        "atletico madrid" to listOf("atletico", "atleti", "أتلتيكو", "اتلتيكو", "אתלטיקו"),
        "real madrid" to listOf("ريال مدريد", "ריאל מדריד"),
        "barcelona" to listOf("barca", "برشلونة", "برشلونه", "ברצלונה"),
        "athletic club" to listOf("athletic bilbao", "bilbao", "بلباو", "בילבאו"), "real sociedad" to listOf("sociedad", "سوسيداد", "סוסיאדד"), "real betis" to listOf("betis", "بيتيس", "בטיס"), "sevilla" to listOf("إشبيلية", "اشبيلية", "סביליה"), "valencia" to listOf("فالنسيا", "ולנסיה"), "villarreal" to listOf("فياريال", "ויאריאל"),
        "bayern munich" to listOf("bayern", "بايرن", "באיירן", "באיירן מינכן"), "borussia dortmund" to listOf("dortmund", "bvb", "دورتموند", "דורטמונד"), "bayer leverkusen" to listOf("leverkusen", "ليفركوزن", "לברקוזן"), "rb leipzig" to listOf("leipzig", "لايبزيغ", "لايبزج", "לייפציג"), "eintracht frankfurt" to listOf("frankfurt", "فرانكفورت", "פרנקפורט"), "borussia monchengladbach" to listOf("gladbach", "monchengladbach", "غلادباخ"), "vfb stuttgart" to listOf("stuttgart", "شتوتغارت"),
        "olympique marseille" to listOf("marseille", "مارسيليا", "מארסיי"), "olympique lyonnais" to listOf("lyon", "ليون", "ליון"), "as monaco" to listOf("monaco", "موناكو", "מונאקו"), "lille" to listOf("ليل"),
        "sporting cp" to listOf("sporting lisbon", "sporting lisboa", "سبورتينغ لشبونة", "ספורטינג ליסבון"), "fc porto" to listOf("porto", "بورتو", "פורטו"), "benfica" to listOf("بنفيكا", "בנפיקה"),
        "ajax amsterdam" to listOf("ajax", "أياكس", "اياكس", "אייאקס"), "psv eindhoven" to listOf("psv", "آيندهوفن", "איינדהובן"), "feyenoord" to listOf("فينورد", "פיינורד"),
        "fenerbahce" to listOf("fener", "فنربخشة", "فنربهتشه", "פנרבחצ'ה"), "besiktas" to listOf("بشكتاش", "בשיקטאש"), "galatasaray" to listOf("غلطة سراي", "جالطة سراي", "غلطه سراي", "גלאטסראיי"), "trabzonspor" to listOf("طرابزون"),
        "celtic" to listOf("سلتيك", "סלטיק"), "rangers" to listOf("رينجرز"),
        "al hilal" to listOf("الهلال", "אל הילאל"), "al nassr" to listOf("النصر", "אל נאסר"), "al ittihad" to listOf("الاتحاد", "الإتحاد"), "al ahli" to listOf("الأهلي", "الاهلي"), "al shabab" to listOf("الشباب"), "al ettifaq" to listOf("الاتفاق"), "al taawoun" to listOf("التعاون"), "al fateh" to listOf("الفتح"), "al qadsiah" to listOf("القادسية"), "al khaleej" to listOf("الخليج"), "al riyadh" to listOf("الرياض"), "damac" to listOf("ضمك"), "al fayha" to listOf("الفيحاء"), "al okhdood" to listOf("الأخدود"), "al raed" to listOf("الرائد"), "al wehda" to listOf("الوحدة"),
        "al ahly" to listOf("الأهلي", "الاهلي", "אל אהלי"), "zamalek" to listOf("الزمالك", "זמאלכ"), "pyramids" to listOf("بيراميدز"), "al masry" to listOf("المصري"), "ismaily" to listOf("الإسماعيلي", "الاسماعيلي"),
        "al sadd" to listOf("السد"), "al duhail" to listOf("الدحيل"), "al rayyan" to listOf("الريان"), "al ain" to listOf("العين"), "al wahda" to listOf("الوحدة"), "al jazira" to listOf("الجزيرة"), "shabab al ahli" to listOf("شباب الأهلي"), "al wasl" to listOf("الوصل"),
        "wydad" to listOf("wydad casablanca", "الوداد"), "raja casablanca" to listOf("raja", "الرجاء"), "esperance de tunis" to listOf("esperance", "الترجي"), "etoile du sahel" to listOf("النجم الساحلي"), "club africain" to listOf("الإفريقي", "الافريقي"), "mc alger" to listOf("مولودية الجزائر"), "cr belouizdad" to listOf("شباب بلوزداد"),
        "maccabi tel aviv" to listOf("maccabi ta", "maccabi t a", "مكابي تل أبيب", "מכבי תל אביב", "מכבי ת א"), "hapoel tel aviv" to listOf("hapoel ta", "هبوعيل تل أبيب", "הפועל תל אביב", "הפועל ת א"), "hapoel be er sheva" to listOf("hapoel beer sheva", "beer sheva", "هبوعيل بئر السبع", "הפועל באר שבע", "הפועל ב ש"), "maccabi haifa" to listOf("مكابي حيفا", "מכבי חיפה"), "hapoel haifa" to listOf("هبوعيل حيفا", "הפועל חיפה"), "beitar jerusalem" to listOf("beitar", "بيتار القدس", "בית ר ירושלים", "ביתר ירושלים"), "bnei sakhnin" to listOf("sakhnin", "سخنين", "בני סכנין"), "maccabi netanya" to listOf("netanya", "مكابي نتانيا", "מכבי נתניה"), "maccabi petah tikva" to listOf("petah tikva", "מכבי פתח תקווה"), "hapoel petah tikva" to listOf("הפועל פתח תקווה"), "ironi kiryat shmona" to listOf("kiryat shmona", "קרית שמונה"), "ashdod" to listOf("אשדוד"), "hapoel jerusalem" to listOf("הפועל ירושלים"), "bnei reineh" to listOf("بني ريينة", "בני ריינה"),
        "saudi arabia" to listOf("السعودية", "ערב הסעודית"), "egypt" to listOf("مصر", "מצרים"), "morocco" to listOf("المغرب", "מרוקו"), "algeria" to listOf("الجزائر", "אלג'יריה"), "tunisia" to listOf("تونس", "תוניסיה"), "qatar" to listOf("قطر", "קטאר"), "iraq" to listOf("العراق", "עיראק"), "jordan" to listOf("الأردن", "الاردن", "ירדן"), "palestine" to listOf("فلسطين", "פלסטין"), "israel" to listOf("إسرائيل", "اسرائيل", "ישראל"), "lebanon" to listOf("لبنان", "לבנון"), "syria" to listOf("سوريا", "سورية", "סוריה"), "united arab emirates" to listOf("uae", "emirates", "الإمارات", "الامارات"), "kuwait" to listOf("الكويت"), "bahrain" to listOf("البحرين"), "oman" to listOf("عمان"),
        "england" to listOf("إنجلترا", "انجلترا", "אנגליה"), "france" to listOf("فرنسا", "צרפת"), "germany" to listOf("ألمانيا", "المانيا", "גרמניה"), "spain" to listOf("إسبانيا", "اسبانيا", "ספרד"), "italy" to listOf("إيطاليا", "ايطاليا", "איטליה"), "portugal" to listOf("البرتغال", "פורטוגל"), "brazil" to listOf("البرازيل", "ברזיל"), "argentina" to listOf("الأرجنتين", "الارجنتين", "ארגנטינה"), "netherlands" to listOf("holland", "هولندا", "הולנד"), "belgium" to listOf("بلجيكا", "בלגיה"), "croatia" to listOf("كرواتيا", "קרואטיה"), "turkiye" to listOf("turkey", "تركيا", "טורקיה"), "turkey" to listOf("turkiye", "تركيا", "טורקיה"), "usa" to listOf("united states", "أمريكا", "امريكا", "ארה ב"), "united states" to listOf("usa", "أمريكا", "امريكا", "ארה ב"),
    )
    /** A test "does this guide title name this team?" for one team (ESPN / API-Football English name). Pass the title through [norm] first.
     *  True when a known spelling appears, or when EVERY meaningful word of the name appears ("Manchester City" needs both words, so a Manchester United programme no longer counts). */
    fun matcher(en: String): (String) -> Boolean {
        val n = norm(en)
        val als = ArrayList<String>()
        var strict = false
        fun addAll(l: List<String>) { for (x in l) { if (x == "!") strict = true else als.add(norm(x)) } }
        alias[n]?.let { addAll(it) }
        if (als.isEmpty()) for ((k, v) in alias) if (k.length >= 5 && (n.contains(k) || (k.contains(n) && n.length >= 5))) addAll(v)   // "Paris Saint-Germain FC" → key "paris saint germain"
        ar[en]?.let { als.add(norm(it)) }; he[en]?.let { als.add(norm(it)) }
        val toks = n.split(' ').filter { it.length >= 3 && it !in stop }
        val full = if (strict) "" else n
        return { t ->
            val tt = " $t "
            // short words must stand alone ("inter" must not hit "international", "roma" not "romania"); longer ones may be part of a word (Arabic و/ال prefixes)
            als.any { a -> a.isNotEmpty() && tt.contains(if (a.length <= 5) " $a " else a) } ||
                (full.isNotEmpty() && tt.contains(" $full ")) ||
                (!strict && toks.isNotEmpty() && toks.all { tok -> if (tok.length <= 5) tt.contains(" $tok ") else tt.contains(tok) })
        }
    }
}
