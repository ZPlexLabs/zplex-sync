package zechs.zplex.sync.data.remote.model;

import zechs.zplex.sync.data.remote.model_enum.Gender;

public record Cast(
        String character,
        Integer gender,
        Integer id,
        String name,
        Integer order,
       String profile_path
) {

    public Gender getGender() {
        return switch (gender) {
            case 1 -> Gender.Male;
            case 2 -> Gender.Female;
            default -> Gender.Other;
        };
    }

}