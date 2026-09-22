package kr.hankkitravel.nutrition.application;

import java.util.Map;
import java.util.Set;
import kr.hankkitravel.nutrition.model.NutritionMatchDecision;
import kr.hankkitravel.nutrition.model.NutritionMatchLevel;
import kr.hankkitravel.nutrition.model.NutritionReviewState;
import kr.hankkitravel.nutrition.persistence.NutritionFoodMapper;
import org.springframework.stereotype.Component;

/** Deterministic precision-first matching. Fuzzy candidates can never become HIGH automatically. */
@Component
public class MenuNutritionMatcher {
    public static final String RULE_VERSION = "menu-nutrition-rules-v1";
    private static final Set<String> GENERIC = Set.of("정식", "세트", "코스", "국", "고기", "메뉴");
    private static final Map<String, String> APPROVED_ALIAS = Map.of(
            "멸치국수", "잔치국수", "비빔밀면", "비빔국수", "오겹살", "오겹살구이");
    private static final Map<String, String> CURATED_PROXY = Map.of("흑돼지구이", "돼지고기구이");
    private final NutritionFoodMapper foods;
    private final NutritionNameNormalizer names;

    public MenuNutritionMatcher(NutritionFoodMapper foods, NutritionNameNormalizer names) {
        this.foods = foods; this.names = names;
    }

    public NutritionMatchDecision match(String menuName) {
        String normalized = names.normalize(menuName);
        if (normalized.isBlank() || GENERIC.contains(normalized)) return none("GENERIC_MENU_BLOCKED");
        var exact = unique(normalized, NutritionMatchLevel.HIGH, "EXACT_NORMALIZED", normalized);
        if (exact != null) return exact;
        String alias = APPROVED_ALIAS.get(normalized);
        if (alias != null) {
            var byAlias = unique(alias, NutritionMatchLevel.MEDIUM, "APPROVED_ALIAS", normalized + "->" + alias);
            if (byAlias != null) return byAlias;
        }
        String proxy = CURATED_PROXY.get(normalized);
        if (proxy != null) {
            var byProxy = unique(proxy, NutritionMatchLevel.MEDIUM, "CURATED_PROXY", normalized + "->" + proxy);
            if (byProxy != null) return byProxy;
        }
        String token = normalized.replaceAll("[^가-힣a-z0-9]", " ").trim().split("\\s+")[0];
        if (token.length() >= 2 && !foods.findCandidateNamesByToken(token).isEmpty()) {
            return low("FUZZY_CANDIDATE_ONLY", token);
        }
        return none("NO_CONSERVATIVE_MATCH");
    }

    private NutritionMatchDecision unique(String name, NutritionMatchLevel level, String method, String evidence) {
        var candidates = foods.findActiveByNormalizedName(name);
        if (candidates.isEmpty()) return null;
        if (candidates.size() > 1) return low("AMBIGUOUS_DUPLICATE_REFERENCE", name);
        var food = candidates.getFirst();
        return new NutritionMatchDecision(food.getId(), level, method, NutritionReviewState.AUTO_MATCHED, evidence);
    }

    private NutritionMatchDecision low(String method, String evidence) {
        return new NutritionMatchDecision(null, NutritionMatchLevel.LOW, method, NutritionReviewState.REVIEW_REQUIRED, evidence);
    }

    private NutritionMatchDecision none(String method) {
        return new NutritionMatchDecision(null, NutritionMatchLevel.NONE, method, NutritionReviewState.REVIEW_REQUIRED, null);
    }
}
