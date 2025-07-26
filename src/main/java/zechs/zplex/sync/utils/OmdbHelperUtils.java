package zechs.zplex.sync.utils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class OmdbHelperUtils {
    public static BigDecimal parseOmdbRating(String rating) {
        if (rating == null) {
            return null;
        }
        if (rating.equalsIgnoreCase("N/A")) {
            return null;
        }
        return BigDecimal.valueOf(Double.parseDouble(rating));
    }

    public static Integer parseOmdbVotes(String votes) {
        return Integer.parseInt(votes.trim().replace(",", ""));
    }

    public static String parseParentalRating(String rated) {
        if (rated == null) {
            return null;
        }
        if (rated.equalsIgnoreCase("N/A")) {
            return null;
        }
        return rated;
    }


    public static Integer parseRuntime(String runtime) {
        if (runtime == null) {
            return null;
        }
        if (runtime.equalsIgnoreCase("N/A")) {
            return null;
        }
        return Integer.parseInt(runtime.split(" ")[0]);
    }

    public static Long parseReleasedDate(String date) {
        if (date == null) {
            return null;
        }
        if (date.equalsIgnoreCase("N/A")) {
            return null;
        }
        return parseDateToEpoch(date, "dd MMM yyyy");
    }

    public static Long parseDateToEpoch(String dateStr, String pattern) {
        // Default pattern if no pattern is provided
        if (pattern == null || pattern.isEmpty()) {
            pattern = "dd MMM yyyy";
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat(pattern, Locale.ENGLISH);

        try {
            Date date = dateFormat.parse(dateStr);
            return date != null ? date.getTime() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
