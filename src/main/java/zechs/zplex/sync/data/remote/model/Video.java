package zechs.zplex.sync.data.remote.model;

public record Video(
        String key,
        Boolean official,
        String published_at,
        String site,
        String type
) {
}