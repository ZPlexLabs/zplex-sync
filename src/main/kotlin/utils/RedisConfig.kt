package zechs.zplex.sync.utils

import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.RedisClient
import redis.clients.jedis.SslOptions

object RedisConfig {

    fun buildClient(): RedisClient {
        val host = AppConfig.get("zplex.cache.host")
        val port = AppConfig.get("zplex.cache.port").toIntOrNull()
            ?: throw IllegalStateException("zplex.cache.port must be a valid integer")

        val username = AppConfig.get("zplex.cache.username")
        val password = AppConfig.get("zplex.cache.password")

        val clientConfig = DefaultJedisClientConfig.builder()
            .user(username)
            .password(password)
            .sslOptions(SslOptions.builder().build())
            .build()

        return RedisClient.builder()
            .hostAndPort(host, port)
            .clientConfig(clientConfig)
            .build()
    }
}