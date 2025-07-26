package zechs.zplex.sync.data.remote.model;

import java.util.List;

public record Credits(
        List<Cast> cast,
        List<Crew> crew
) {
}
