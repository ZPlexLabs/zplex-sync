package zechs.zplex.sync.data.remote.model;

/* After some analysis, the following fields have been determined to be non-null 
Actors, Country, Director, Genre, Language, Plot, Poster, Ratings, Released, Response, Runtime, Title, Type, Writer, Year, imdbID, imdbRating, imdbVotes*/
public record OmdbMovieResponse(
        String imdbID,
        String imdbRating,
        String imdbVotes,
        String Plot,
        String Rated,
        String Released,
        String Runtime,
        String Title,
        String Type,
        String Year
) {
}