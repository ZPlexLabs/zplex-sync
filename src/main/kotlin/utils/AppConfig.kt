package zechs.zplex.sync.utils

import java.util.Properties

object AppConfig {

    private val properties = Properties()

    init {
        loadBaseProperties()
        loadProfileOverrides()
    }

    private fun loadBaseProperties() {
        val baseStream = object {}.javaClass.classLoader.getResourceAsStream("application.properties")
            ?: throw IllegalStateException("application.properties not found")

        properties.load(baseStream)
    }

    private fun loadProfileOverrides() {
        val profile = System.getProperty("app.env") ?: return

        val fileName = "application.$profile.properties"

        val profileStream = object {}.javaClass.classLoader.getResourceAsStream(fileName)
            ?: throw IllegalStateException("$fileName not found")

        properties.load(profileStream)
    }

    fun get(key: String): String {
        return properties.getProperty(key) ?: throw IllegalArgumentException("Missing config key: $key")
    }
}