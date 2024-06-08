package zechs.zplex.sync.utils

import java.net.URI
import java.net.URISyntaxException
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class DatabaseConnector(
    private val databaseUrl: String
) {

    var connection: Connection? = null
        private set

    @Throws(SQLException::class, URISyntaxException::class)
    fun connect() {
        val dbUri = URI(databaseUrl)
        val username = dbUri.userInfo.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
        val password = dbUri.userInfo.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
        val dbUrl = ("jdbc:postgresql://" + dbUri.host + ":" + dbUri.port + dbUri.path
                + "?sslmode=require")

        connection = DriverManager.getConnection(dbUrl, username, password)
        println("Connected to database: " + dbUri.path.substring(1))
    }

    @Throws(SQLException::class)
    fun disconnect() {
        connection?.let {
            if (!it.isClosed) {
                it.close()
                println("Disconnected from database")
            }
        } ?: run { println("No connection to close") }
    }

}
