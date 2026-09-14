package kr.hankkitravel.restaurant.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import kr.hankkitravel.restaurant.model.NormalizedMenuCandidate;
import org.springframework.stereotype.Component;

@Component
public class MenuNormalizer {
    public static final String VERSION = "menu-normalizer-v1";
    private static final Pattern DELIMITER = Pattern.compile("[,;/|·\\n\\r]+");
    private static final Pattern PRICE = Pattern.compile("(?:\\d{1,3}(?:,\\d{3})+|\\d+)\\s*원");
    private static final Pattern LEADING_ORDER = Pattern.compile("^\\s*\\d+[.)]\\s*");
    private static final Pattern PARENTHETICAL_NOISE = Pattern.compile("\\([^)]*(?:\\d|원|ml|g|인분|hot|iced)[^)]*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPACE = Pattern.compile("\\s+");

    public List<NormalizedMenuCandidate> normalize(String firstMenu, String treatMenu) {
        Map<String, NormalizedMenuCandidate> unique = new LinkedHashMap<>();
        collect(unique, firstMenu, "FIRST_MENU");
        collect(unique, treatMenu, "TREAT_MENU");
        return List.copyOf(unique.values());
    }

    private void collect(Map<String, NormalizedMenuCandidate> unique, String rawText, String sourceField) {
        if (rawText == null || rawText.isBlank()) return;
        String protectedThousands = rawText.replaceAll("(?<=\\d),(?=\\d{3}\\b)", "");
        for (String fragment : DELIMITER.split(protectedThousands)) {
            String raw = fragment == null ? "" : fragment.trim();
            String normalized = canonicalize(raw);
            if (normalized == null) continue;
            unique.putIfAbsent(normalized.toLowerCase(Locale.ROOT), new NormalizedMenuCandidate(raw, normalized, sourceField));
        }
    }

    private String canonicalize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.replace('_', ' ');
        value = LEADING_ORDER.matcher(value).replaceFirst("");
        value = PARENTHETICAL_NOISE.matcher(value).replaceAll(" ");
        value = PRICE.matcher(value).replaceAll(" ");
        value = SPACE.matcher(value).replaceAll(" ").trim();
        if (value.isBlank() || value.length() > 300 || value.matches("[-–—]+")
                || value.equalsIgnoreCase("메뉴") || value.equalsIgnoreCase("없음") || value.equalsIgnoreCase("미상")) return null;
        return value;
    }
}
