package zechs.zplex.sync.utils.ext

fun String.nullIfNA() = if (this == "N/A") null else this

inline fun <T> String.nullIfNAOrElse(block: (String) -> T): T? =
    if (this == "N/A") null else block(this)