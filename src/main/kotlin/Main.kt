package zechs.zplex.sync

import zechs.zplex.sync.data.repository.IndexingService
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val profile = when {
        args.contains("--dev") -> "dev"
        else -> null
    }
    if (profile != null) {
        System.setProperty("app.env", profile)
    }
    IndexingService().invoke()
    exitProcess(0)
}