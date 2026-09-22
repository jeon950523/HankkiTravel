package kr.hankkitravel.nutrition.application;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class NutritionNameNormalizer {
    private static final Pattern SPACE = Pattern.compile("\\s+");

    public String normalize(String value) {
        if (value == null) return "";
        return SPACE.matcher(value.replace('_', ' ').trim()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }
}
