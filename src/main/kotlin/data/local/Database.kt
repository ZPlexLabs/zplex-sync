package zechs.zplex.sync.data.local

import java.sql.Connection

interface Database {
    fun connect()
    fun disconnect()
    fun getConnection(): Connection
}