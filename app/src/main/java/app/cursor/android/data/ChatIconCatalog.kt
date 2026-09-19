package app.cursor.android.data

data class ChatIconDefinition(
    val id: String,
    val label: String,
    val keywords: Set<String>,
)

object ChatIconCatalog {
    const val DEFAULT_ID = "chat"

    val all: List<ChatIconDefinition> = listOf(
        ChatIconDefinition(
            "chat",
            "Chat",
            setOf("chat", "message", "conversation", "talk", "ask"),
        ),
        ChatIconDefinition(
            "code",
            "Code",
            setOf(
                "code", "kotlin", "java", "python", "swift", "refactor", "compile",
                "function", "class", "typescript", "javascript", "rust", "go",
            ),
        ),
        ChatIconDefinition(
            "bug",
            "Bug",
            setOf("bug", "crash", "error", "fix", "exception", "fail", "broken"),
        ),
        ChatIconDefinition(
            "design",
            "Design",
            setOf("ui", "ux", "design", "theme", "color", "layout", "style", "icon"),
        ),
        ChatIconDefinition(
            "lock",
            "Security",
            setOf("auth", "login", "password", "security", "token", "oauth", "key"),
        ),
        ChatIconDefinition(
            "cloud",
            "Cloud",
            setOf("cloud", "api", "http", "network", "server", "endpoint", "sync"),
        ),
        ChatIconDefinition(
            "phone",
            "Mobile",
            setOf("android", "ios", "mobile", "phone", "compose", "swiftui"),
        ),
        ChatIconDefinition(
            "computer",
            "Computer",
            setOf("machine", "desktop", "mac", "worker", "laptop", "computer"),
        ),
        ChatIconDefinition(
            "folder",
            "Files",
            setOf("file", "folder", "path", "directory", "cache"),
        ),
        ChatIconDefinition(
            "database",
            "Database",
            setOf("database", "sql", "room", "datastore", "query", "table"),
        ),
        ChatIconDefinition(
            "test",
            "Tests",
            setOf("test", "junit", "spec", "coverage", "assert"),
        ),
        ChatIconDefinition(
            "settings",
            "Settings",
            setOf("settings", "config", "preference", "option"),
        ),
        ChatIconDefinition(
            "image",
            "Image",
            setOf("image", "photo", "screenshot", "bitmap", "camera"),
        ),
        ChatIconDefinition(
            "translate",
            "Language",
            setOf("i18n", "translate", "locale", "language", "string"),
        ),
        ChatIconDefinition(
            "speed",
            "Performance",
            setOf("perf", "performance", "slow", "speed", "lag", "optimize"),
        ),
        ChatIconDefinition(
            "git",
            "Git",
            setOf("git", "merge", "branch", "pr", "commit", "rebase", "review"),
        ),
        ChatIconDefinition(
            "terminal",
            "Terminal",
            setOf("terminal", "shell", "cli", "command", "bash", "script"),
        ),
        ChatIconDefinition(
            "rocket",
            "Release",
            setOf("deploy", "release", "launch", "ship", "publish"),
        ),
        ChatIconDefinition(
            "docs",
            "Docs",
            setOf("doc", "docs", "readme", "write", "comment", "markdown"),
        ),
        ChatIconDefinition(
            "search",
            "Search",
            setOf("search", "find", "query", "filter", "index"),
        ),
    )

    private val byId = all.associateBy { it.id }

    fun resolve(id: String?): ChatIconDefinition = byId[id] ?: byId.getValue(DEFAULT_ID)

    fun guess(title: String): String {
        val tokens = title.lowercase()
            .split(Regex("[^a-z0-9+]+"))
            .filter { it.length >= 2 }
        if (tokens.isEmpty()) return DEFAULT_ID
        val tokenSet = tokens.toSet()
        val blob = tokens.joinToString(" ")
        var bestId = DEFAULT_ID
        var bestScore = 0
        for (icon in all) {
            var score = 0
            for (keyword in icon.keywords) {
                score += when {
                    tokenSet.contains(keyword) -> keyword.length * 3
                    keyword.length >= 4 && tokenSet.any { it.startsWith(keyword) } ->
                        keyword.length * 2
                    keyword.length >= 4 && blob.contains(keyword) -> keyword.length
                    else -> 0
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestId = icon.id
            }
        }
        return bestId
    }

    fun iconIdFor(title: String, overrideId: String?): String {
        val trimmed = overrideId?.trim().orEmpty()
        return if (trimmed.isNotEmpty() && byId.containsKey(trimmed)) trimmed else guess(title)
    }
}
