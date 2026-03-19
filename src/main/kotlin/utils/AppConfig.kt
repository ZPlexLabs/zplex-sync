package zechs.zplex.sync.utils

import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

object AppConfig {

    private val properties = Properties()
    private val cache = ConcurrentHashMap<String, String>()

    private val placeholderRegex = "\\$\\{([^}]+)}".toRegex()

    init {
        loadBaseProperties()
        loadProfileOverrides()
    }

    private fun loadBaseProperties() {
        val stream = javaClass.classLoader.getResourceAsStream("application.properties")
            ?: throw IllegalStateException("application.properties not found")

        properties.load(stream)
    }

    private fun loadProfileOverrides() {
        val profile = System.getProperty("app.env") ?: return

        val fileName = "application.$profile.properties"
        val stream = javaClass.classLoader.getResourceAsStream(fileName)
            ?: throw IllegalStateException("$fileName not found")

        properties.load(stream)
    }

    fun get(key: String): String {
        return cache.getOrPut(key) {
            val raw = properties.getProperty(key)
                ?: throw IllegalArgumentException("Missing config key: $key")

            resolve(raw)
        }
    }

    fun getOrNull(key: String): String? =
        properties.getProperty(key)?.let { resolve(it) }

    fun getInt(key: String): Int =
        get(key).toIntOrNull()
            ?: throw IllegalArgumentException("Config '$key' must be a valid integer")

    fun getBoolean(key: String): Boolean =
        get(key).toBooleanStrictOrNull()
            ?: throw IllegalArgumentException("Config '$key' must be true/false")

    private fun resolve(value: String): String {
        var result = value

        repeat(5) { // prevent infinite recursion
            if (!placeholderRegex.containsMatchIn(result)) return result

            result = placeholderRegex.replace(result) { match ->
                resolvePlaceholder(match.groupValues[1])
            }
        }

        return result
    }

    private fun resolvePlaceholder(content: String): String {
        val parts = content.split(":", limit = 2)
        val key = parts[0]
        val default = parts.getOrNull(1)

        return System.getenv(key)
            ?: System.getProperty(key)
            ?: properties.getProperty(key)?.let { resolve(it) }
            ?: default
            ?: throw IllegalArgumentException(
                "Could not resolve placeholder '$key' and no default provided"
            )
    }
}