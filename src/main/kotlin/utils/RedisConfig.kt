package zechs.zplex.sync.utils

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object RedisConfig {

    fun buildRedisUrl(): String {
        val host = AppConfig.get("zplex.cache.host")
        val port = AppConfig.get("zplex.cache.port").toIntOrNull()
            ?: throw IllegalStateException("zplex.cache.port must be a valid integer")

        val username = AppConfig.get("zplex.cache.username")
        val password = AppConfig.get("zplex.cache.password")

        val encodedUsername = urlEncode(username)
        val encodedPassword = urlEncode(password)

        return "rediss://$encodedUsername:$encodedPassword@$host:$port"
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}