package com.keywordrecord.keyboard

object WordDictionary {

    private val words: List<String> = """
        the be to of and a in that have i it for not on with he as you do at this but his by
        from they we say her she or an will my one all would there their what so up out if about
        who get which go me when make can like time no just him know take people into year your
        good some could them see other than then now look only come its over think also back after
        use two how our work first well way even new want because any these give day most us is
        hello hi hey thanks thank you please sorry yes yeah ok okay sure maybe love great nice
        cool awesome amazing wonderful beautiful happy sad angry tired hungry thirsty fine help
        need want going gonna come went came was were been being am are is was were have has had
        doing done made make makes making think thought knows knew know see saw seen look looked
        looking go goes going went gone get gets got getting give gave given take took taken tell
        told told ask asked asked call called called try tried tried feel felt feeling find found
        found keep kept keeping let lets left put puts putting mean meant means seem seemed seems
        leave left leaving turn turned turning start started starting show showed shown hear heard
        hearing play played playing run ran running move moved moving live lived living believe
        believed believing hold held holding bring brought bringing happen happened happening write
        wrote written provide provided providing sit sat sitting stand stood standing lose lost
        losing pay paid paying meet met meeting include included including continue continued
        set sets setting learn learned learning change changed changing lead led leading understand
        understood understanding watch watched watching follow followed following stop stopped
        stopping create created creating speak spoke spoken read read reading allow allowed allowing
        add added adding spend spent spending grow grew grown open opened opening walk walked walking
        win won winning offer offered offering remember remembered remembering consider considered
        considering appear appeared appearing buy bought buying wait waited waiting serve served
        serving die died dying send sent sending expect expected expecting build built building stay
        stayed staying fall fell falling cut cut cutting reach reached reaching kill killed killing
        remain remained remaining suggest suggested suggesting raise raised raising pass passed passing
        sell sold selling require required requiring report reported reporting decide decided deciding
        pull pulled pulling return returned returning explain explained explaining hope hoped hoping
        develop developed developing carry carried carrying break broke broken receive received receiving
        agree agreed agreeing support supported supporting hit hit hitting produce produced producing
        eat ate eaten cover covered covering catch caught catching draw drew drawn choose chose chosen
        fail failed failing fight fought fighting save saved saving end ended ending join joined joining
        reduce reduced reducing establish established establishing
        morning night afternoon evening today tomorrow yesterday week month year always never sometimes
        often usually really very much many more most less little few every each someone something
        somewhere somehow everything everyone everywhere nothing nobody nowhere phone message text email
        home house school college office work job friend family mother father brother sister son daughter
        husband wife boyfriend girlfriend baby child children man woman boy girl person people world life
        money food water coffee tea lunch dinner breakfast name number address city country state place
        weather music movie video game photo picture story news information question answer problem
        reason idea plan party meeting appointment doctor hospital store market shop bank card cash
        price cost free cheap expensive online internet website app application download install update
        password account login logout sign register profile settings notification alert reminder calendar
        map location direction traffic travel trip flight train bus car bike road street building room
        door window table chair bed kitchen bathroom bedroom living dining garden park beach mountain
        river lake ocean sea sky sun moon star cloud rain snow wind hot cold warm cool spring summer
        autumn winter monday tuesday wednesday thursday friday saturday sunday january february march
        april may june july august september october november december
        whatsapp youtube google facebook instagram twitter snapchat telegram discord zoom teams slack
        amazon flipkart paytm phonepe gpay netflix spotify chrome firefox edge windows android iphone
        samsung apple realme xiaomi oppo vivo laptop computer keyboard mouse screen monitor printer
        camera speaker headphone bluetooth wifi network server database record keyboard strike records
    """.trim().split(Regex("\\s+")).distinct()

    private val wordRank: Map<String, Int> = words.withIndex().associate { (index, word) -> word to index }

    private val bigrams: Map<String, List<String>> = mapOf(
        "i" to listOf("am", "will", "have", "think", "want", "can", "would", "love", "need", "hope"),
        "you" to listOf("are", "can", "will", "have", "should", "know", "want", "need", "too", "please"),
        "how" to listOf("are", "is", "do", "can", "to", "about", "much", "many", "long", "was"),
        "what" to listOf("is", "are", "do", "did", "can", "will", "about", "time", "happened", "the"),
        "thank" to listOf("you", "god", "so", "very", "thanks"),
        "thanks" to listOf("for", "a", "so", "again", "you"),
        "good" to listOf("morning", "night", "afternoon", "evening", "job", "luck", "idea", "day", "work"),
        "have" to listOf("a", "to", "been", "you", "fun", "nice", "good", "any", "no", "the"),
        "are" to listOf("you", "we", "they", "there", "the", "going", "not", "sure", "ready", "here"),
        "is" to listOf("it", "this", "there", "the", "a", "not", "good", "ready", "available", "working"),
        "the" to listOf("best", "same", "first", "last", "next", "only", "most", "other", "new", "way"),
        "a" to listOf("lot", "bit", "few", "good", "great", "new", "long", "little", "nice", "big"),
        "to" to listOf("be", "do", "go", "get", "see", "make", "the", "a", "know", "help"),
        "in" to listOf("the", "a", "my", "this", "case", "fact", "order", "time", "front", "addition"),
        "on" to listOf("the", "my", "a", "this", "time", "way", "monday", "friday", "sunday", "it"),
        "it" to listOf("is", "was", "will", "would", "could", "should", "seems", "looks", "takes", "works"),
        "we" to listOf("are", "will", "can", "have", "should", "need", "want", "could", "would", "must"),
        "they" to listOf("are", "will", "have", "can", "were", "said", "want", "need", "should", "would"),
        "he" to listOf("is", "was", "will", "has", "had", "can", "would", "should", "said", "wants"),
        "she" to listOf("is", "was", "will", "has", "had", "can", "would", "said", "wants", "needs"),
        "my" to listOf("name", "phone", "friend", "family", "home", "work", "god", "love", "life", "best"),
        "your" to listOf("name", "phone", "help", "friend", "work", "time", "life", "best", "welcome", "turn"),
        "see" to listOf("you", "the", "if", "what", "how", "this", "that", "soon", "later", "ya"),
        "let" to listOf("me", "us", "you", "go", "know", "see", "try", "hope", "start", "begin"),
        "can" to listOf("you", "i", "we", "they", "he", "she", "it", "not", "help", "do"),
        "will" to listOf("be", "do", "go", "get", "see", "help", "you", "not", "try", "call"),
        "do" to listOf("you", "not", "it", "the", "this", "that", "what", "how", "i", "we"),
        "not" to listOf("sure", "yet", "now", "good", "bad", "working", "available", "possible", "ready", "true"),
        "please" to listOf("help", "let", "call", "send", "check", "wait", "come", "tell", "give", "confirm"),
        "sorry" to listOf("for", "about", "to", "i", "but", "again", "man", "buddy", "sir", "maam"),
        "hello" to listOf("there", "everyone", "friend", "sir", "maam", "how", "hi", "again", "world", "team"),
        "hi" to listOf("there", "everyone", "friend", "how", "again", "team", "guys", "all", "sir", "maam"),
        "hey" to listOf("there", "how", "guys", "everyone", "friend", "man", "team", "what", "you", "all"),
        "love" to listOf("you", "it", "this", "that", "to", "the", "so", "too", "ya", "u"),
        "happy" to listOf("birthday", "to", "for", "day", "new", "year", "weekend", "holiday", "anniversary", "life"),
        "nice" to listOf("to", "work", "day", "one", "job", "meeting", "talk", "chat", "photo", "picture"),
        "great" to listOf("job", "work", "day", "idea", "news", "thanks", "help", "time", "to", "see"),
        "ok" to listOf("thanks", "sure", "fine", "good", "great", "cool", "done", "will", "let", "no"),
        "okay" to listOf("thanks", "sure", "fine", "good", "great", "cool", "done", "will", "let", "no"),
        "yes" to listOf("please", "sir", "maam", "of", "course", "sure", "thanks", "i", "we", "ok"),
        "no" to listOf("problem", "worries", "thanks", "way", "need", "idea", "clue", "doubt", "issue", "more"),
        "where" to listOf("are", "is", "do", "did", "can", "will", "should", "to", "the", "you"),
        "when" to listOf("are", "is", "do", "did", "can", "will", "should", "to", "the", "you"),
        "why" to listOf("are", "is", "do", "did", "can", "will", "should", "not", "the", "you"),
        "who" to listOf("is", "are", "was", "were", "can", "will", "should", "to", "the", "you"),
        "call" to listOf("me", "you", "us", "back", "now", "later", "soon", "the", "when", "if"),
        "send" to listOf("me", "you", "it", "the", "a", "photo", "picture", "message", "link", "details"),
        "come" to listOf("here", "on", "back", "to", "with", "over", "soon", "now", "along", "in"),
        "go" to listOf("to", "for", "on", "with", "ahead", "back", "home", "out", "there", "now"),
        "get" to listOf("it", "the", "a", "back", "to", "ready", "started", "going", "some", "more"),
        "make" to listOf("sure", "it", "a", "the", "sense", "time", "me", "us", "your", "my"),
        "take" to listOf("care", "it", "time", "a", "the", "me", "you", "this", "that", "long"),
        "wait" to listOf("for", "a", "the", "me", "please", "till", "until", "here", "there", "minute"),
        "look" to listOf("at", "for", "into", "like", "good", "great", "nice", "here", "there", "up"),
        "talk" to listOf("to", "about", "with", "soon", "later", "now", "more", "again", "you", "me"),
        "tell" to listOf("me", "you", "us", "the", "him", "her", "them", "about", "when", "if"),
        "hope" to listOf("you", "so", "to", "this", "that", "it", "all", "everything", "things", "see"),
        "wish" to listOf("you", "me", "us", "the", "all", "a", "good", "best", "luck", "well"),
        "need" to listOf("to", "help", "a", "the", "some", "more", "your", "my", "it", "this"),
        "want" to listOf("to", "a", "the", "some", "more", "your", "my", "it", "this", "that"),
        "just" to listOf("a", "the", "got", "wanted", "need", "like", "saw", "did", "now", "checking"),
        "really" to listOf("good", "nice", "great", "sorry", "appreciate", "love", "like", "want", "need", "help"),
        "very" to listOf("good", "nice", "much", "well", "sorry", "happy", "important", "useful", "helpful", "bad"),
        "too" to listOf("much", "late", "early", "bad", "good", "many", "long", "far", "soon", "busy"),
        "also" to listOf("have", "need", "want", "like", "know", "think", "see", "get", "try", "check"),
        "still" to listOf("here", "there", "waiting", "working", "not", "trying", "looking", "need", "want", "have"),
        "already" to listOf("have", "did", "done", "know", "told", "sent", "got", "seen", "tried", "left"),
        "maybe" to listOf("later", "tomorrow", "next", "we", "i", "you", "can", "should", "will", "not"),
        "today" to listOf("is", "was", "will", "i", "we", "you", "the", "a", "good", "great"),
        "tomorrow" to listOf("is", "will", "i", "we", "you", "at", "morning", "evening", "afternoon", "night"),
        "message" to listOf("me", "you", "him", "her", "them", "when", "if", "back", "again", "later"),
        "photo" to listOf("of", "and", "from", "to", "please", "send", "share", "looks", "is", "was"),
        "video" to listOf("of", "and", "from", "to", "call", "chat", "link", "is", "was", "please"),
        "link" to listOf("to", "for", "of", "the", "please", "send", "share", "is", "was", "here"),
        "details" to listOf("about", "of", "for", "please", "send", "share", "are", "is", "here", "below"),
        "meeting" to listOf("at", "on", "with", "is", "was", "will", "tomorrow", "today", "next", "scheduled"),
        "time" to listOf("to", "for", "is", "was", "will", "please", "now", "again", "and", "or"),
        "work" to listOf("on", "for", "with", "at", "is", "was", "today", "tomorrow", "hard", "fine"),
        "home" to listOf("now", "soon", "at", "from", "and", "is", "was", "safe", "sweet", "work"),
        "back" to listOf("to", "soon", "now", "later", "home", "here", "again", "in", "at", "with"),
        "soon" to listOf("as", "please", "enough", "possible", "available", "ready", "see", "call", "text", "message"),
        "later" to listOf("today", "tonight", "tomorrow", "please", "see", "call", "text", "message", "talk", "check"),
        "now" to listOf("is", "was", "i", "we", "you", "please", "what", "how", "where", "the"),
        "again" to listOf("please", "soon", "later", "thanks", "sorry", "hello", "hi", "see", "call", "try"),
        "here" to listOf("is", "are", "we", "you", "i", "the", "to", "please", "come", "go"),
        "there" to listOf("is", "are", "was", "were", "will", "you", "we", "they", "please", "go"),
        "with" to listOf("you", "me", "us", "him", "her", "them", "the", "a", "my", "your"),
        "for" to listOf("you", "me", "us", "the", "a", "now", "today", "tomorrow", "sure", "real"),
        "about" to listOf("it", "this", "that", "the", "you", "me", "us", "time", "to", "your"),
        "because" to listOf("of", "i", "you", "we", "they", "it", "the", "this", "that", "reason"),
        "but" to listOf("i", "you", "we", "they", "it", "not", "still", "also", "now", "then"),
        "and" to listOf("i", "you", "we", "they", "it", "the", "a", "my", "your", "then"),
        "or" to listOf("not", "maybe", "something", "someone", "anything", "anyone", "else", "later", "sooner", "the"),
        "if" to listOf("you", "i", "we", "they", "it", "not", "possible", "needed", "required", "any"),
        "so" to listOf("much", "many", "good", "bad", "sorry", "happy", "glad", "far", "long", "soon"),
        "as" to listOf("soon", "well", "much", "many", "possible", "required", "needed", "usual", "always", "per"),
        "at" to listOf("the", "a", "my", "your", "home", "work", "least", "most", "once", "all"),
        "by" to listOf("the", "a", "my", "your", "now", "then", "way", "chance", "far", "time"),
        "of" to listOf("the", "a", "my", "your", "course", "time", "day", "week", "month", "year"),
        "up" to listOf("to", "with", "for", "on", "at", "now", "soon", "here", "there", "and"),
        "down" to listOf("to", "the", "here", "there", "now", "for", "with", "and", "or", "please"),
        "out" to listOf("of", "for", "to", "there", "here", "now", "soon", "and", "or", "please"),
        "off" to listOf("to", "the", "now", "soon", "for", "and", "or", "please", "work", "duty"),
        "over" to listOf("there", "here", "now", "to", "the", "and", "or", "please", "soon", "time"),
        "into" to listOf("the", "a", "my", "your", "it", "this", "that", "account", "details", "more"),
        "through" to listOf("the", "a", "my", "your", "it", "this", "that", "app", "link", "process"),
        "after" to listOf("that", "this", "the", "a", "my", "your", "work", "school", "lunch", "dinner"),
        "before" to listOf("that", "this", "the", "a", "my", "your", "work", "school", "lunch", "dinner"),
        "during" to listOf("the", "a", "my", "your", "work", "school", "meeting", "call", "day", "time"),
        "while" to listOf("i", "you", "we", "they", "it", "the", "a", "my", "your", "waiting"),
        "since" to listOf("then", "yesterday", "morning", "last", "the", "a", "my", "your", "when", "time"),
        "until" to listOf("then", "tomorrow", "further", "notice", "the", "a", "my", "your", "time", "now"),
        "between" to listOf("us", "you", "me", "them", "the", "a", "my", "your", "work", "time"),
        "without" to listOf("you", "me", "us", "them", "the", "a", "my", "your", "any", "further"),
        "within" to listOf("the", "a", "my", "your", "time", "day", "week", "month", "year", "range"),
        "against" to listOf("the", "a", "my", "your", "it", "this", "that", "all", "each", "every"),
        "among" to listOf("the", "a", "my", "your", "us", "them", "all", "other", "many", "few"),
        "around" to listOf("the", "a", "my", "your", "here", "there", "time", "corner", "world", "clock"),
        "behind" to listOf("the", "a", "my", "your", "it", "this", "that", "scene", "back", "time"),
        "beside" to listOf("the", "a", "my", "your", "it", "this", "that", "me", "you", "point"),
        "beyond" to listOf("the", "a", "my", "your", "it", "this", "that", "doubt", "expectation", "reach"),
        "despite" to listOf("the", "a", "my", "your", "it", "this", "that", "all", "everything", "efforts"),
        "except" to listOf("for", "the", "a", "my", "your", "it", "this", "that", "me", "you"),
        "inside" to listOf("the", "a", "my", "your", "it", "this", "that", "box", "room", "house"),
        "outside" to listOf("the", "a", "my", "your", "it", "this", "that", "box", "room", "house"),
        "toward" to listOf("the", "a", "my", "your", "it", "this", "that", "end", "goal", "future"),
        "under" to listOf("the", "a", "my", "your", "it", "this", "that", "table", "bed", "control"),
        "upon" to listOf("the", "a", "my", "your", "it", "this", "that", "arrival", "request", "notice"),
        "within" to listOf("the", "a", "my", "your", "time", "range", "limit", "scope", "area", "reach")
    )

    fun getCompletions(prefix: String, limit: Int = 5): List<String> {
        if (prefix.length < 1) return emptyList()
        val lower = prefix.lowercase()
        return words
            .asSequence()
            .filter { it.length > lower.length && it.startsWith(lower) }
            .sortedBy { wordRank[it] ?: Int.MAX_VALUE }
            .take(limit)
            .toList()
    }

    fun getNextWords(previousWord: String, limit: Int = 5): List<String> {
        val lower = previousWord.lowercase().trim()
        if (lower.isEmpty()) {
            return listOf("I", "The", "How", "What", "Thanks")
        }
        val predicted = bigrams[lower] ?: emptyList()
        return predicted.take(limit)
    }

    fun hasCompletions(prefix: String): Boolean {
        return getCompletions(prefix, 1).isNotEmpty()
    }
}
