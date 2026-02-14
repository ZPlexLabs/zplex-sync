package zechs.zplex.sync.utils

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object RedisConfig {

    fun buildRedisUrl(): String {
        val host = requireEnv("REDIS_HOST")
        val port = requireEnv("REDIS_PORT").toIntOrNull()
            ?: throw IllegalStateException("REDIS_PORT must be a valid integer")

        val username = requireEnv("REDIS_USERNAME")
        val password = requireEnv("REDIS_PASSWORD")

        val encodedUsername = urlEncode(username)
        val encodedPassword = urlEncode(password)

        return "rediss://$encodedUsername:$encodedPassword@$host:$port"
    }

    private fun requireEnv(key: String): String =
        System.getenv(key)
            ?: throw IllegalStateException("Missing required environment variable: $key")

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}