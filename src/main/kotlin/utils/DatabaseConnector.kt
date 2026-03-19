package zechs.zplex.sync.utils

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class DatabaseConnector {

    var connection: Connection? = null
        private set

    @Throws(SQLException::class)
    fun connect() {
        val host = AppConfig.get("zplex.db.host")
            .trim()
            .also {
                require(it.isNotBlank()) { "Database host is blank" }
                require(!it.contains("\n")) { "Invalid host format" }
            }

        val port = AppConfig.get("zplex.db.port")
            .trim()
            .toIntOrNull()
            ?: throw IllegalArgumentException("Database port must be numeric")

        require(port in 1..65535) { "Database port out of valid range (1–65535)" }

        val username = AppConfig.get("zplex.db.username")
            .trim()
            .also { require(it.isNotBlank()) { "Database username is blank" } }

        val password = AppConfig.get("zplex.db.password")

        val jdbcUrl = "jdbc:postgresql://$host:$port/zplex?sslmode=require"

        connection = DriverManager.getConnection(
            jdbcUrl,
            username,
            password
        )

        println("Connected to database at $host:$port (SSL required)")
    }

    @Throws(SQLException::class)
    fun disconnect() {
        connection?.let {
            if (!it.isClosed) {
                it.close()
                println("Disconnected from database")
            }
        } ?: println("No connection to close")
    }
}