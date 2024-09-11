package zechs.zplex.sync.data.model.tmdb

data class ProductionCompany(
    val id: Int,
    val logo_path: String?,
    val name: String,
    val origin_country: String
) {
    fun toStudio() = Studio(
        id = id,
        name = name,
        logo = logo_path,
        country = origin_country
    )
}