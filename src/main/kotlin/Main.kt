package zechs.zplex.sync

import zechs.zplex.sync.data.repository.IndexingService
import kotlin.system.exitProcess

fun main() {
    IndexingService().invoke()
    exitProcess(1)
}