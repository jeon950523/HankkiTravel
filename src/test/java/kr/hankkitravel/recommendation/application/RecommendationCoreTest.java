package kr.hankkitravel.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import kr.hankkitravel.recommendation.application.RecommendationCore.Candidate;
import kr.hankkitravel.recommendation.application.RecommendationCore.FamilyContext;
import kr.hankkitravel.recommendation.application.RecommendationCore.MenuEvidence;
import kr.hankkitravel.recommendation.application.RecommendationCore.Perspective;
import kr.hankkitravel.recommendation.application.RecommendationCore.RequestContext;

class RecommendationCoreTest {
    private final RecommendationCore core = new RecommendationCore();
    private final FamilyContext sodiumFamily = new FamilyContext("CAR", "REQUIRED", 15, true, "AVOID", List.of("SODIUM", "INGREDIENT_CHECK"));
    private final RequestContext mealFirst = new RequestContext(null, List.of(), false, null);

    @Test void sameInputProducesTheSameStableOrderAndLowNutritionDoesNotScore() {
        var lowSodium = candidate("10", "낮은 기준", "주차 가능", List.of(menu("국밥", "HIGH", "100")));
        var highSodium = candidate("20", "높은 기준", "주차 가능", List.of(menu("국밥", "HIGH", "300")));
        var noReference = candidate("30", "근거 없음", "주차 가능", List.of(menu("국밥", "LOW", null)));

        var first = core.rank(core.score(List.of(highSodium, noReference, lowSodium), sodiumFamily, mealFirst), Perspective.BALANCED);
        var second = core.rank(core.score(List.of(highSodium, noReference, lowSodium), sodiumFamily, mealFirst), Perspective.BALANCED);

        assertThat(first).extracting(item -> item.candidate().candidate().contentId()).containsExactly("10", "30", "20");
        assertThat(second).extracting(item -> item.candidate().candidate().contentId()).containsExactly("10", "30", "20");
        var missing = first.stream().filter(item -> item.candidate().candidate().contentId().equals("30")).findFirst().orElseThrow();
        assertThat(missing.candidate().dimensions().get(RecommendationCore.MEAL).state())
                .isEqualTo(RecommendationCore.EvidenceState.NOT_EVALUATED);
        assertThat(missing.candidate().checks()).anyMatch(text -> text.contains("원재료"));
        assertThat(missing.candidate().informationEvidence()).isEqualTo(RecommendationCore.InformationEvidence.REFERENCE);
    }

    @Test void hardRulesExcludeOnlyExplicitFactsAndUnknownParkingStaysComparable() {
        var unknownParking = candidate("1", "현재 식당", null, List.of(menu("비빔밥", "HIGH", "150")));
        var unavailableParking = candidate("2", "현재 식당", "주차 불가", List.of(menu("비빔밥", "HIGH", "150")));
        var strictMenu = candidate("3", "현재 식당", "주차 가능", List.of(menu("땅콩 국수", "HIGH", "150")));

        assertThat(core.hardDecision(unknownParking, sodiumFamily, mealFirst).included()).isTrue();
        assertThat(core.hardDecision(unavailableParking, sodiumFamily, mealFirst).included()).isFalse();
        assertThat(core.hardDecision(strictMenu, sodiumFamily,
                new RequestContext(null, List.of("땅콩"), false, null)).included()).isFalse();
    }

    private Candidate candidate(String id, String title, String parking, List<MenuEvidence> menus) {
        return new Candidate(id, title, "테스트 주소", null, parking, menus, null);
    }
    private MenuEvidence menu(String name, String level, String sodium) {
        return new MenuEvidence(name, level, sodium == null ? null : new BigDecimal(sodium), null, null,
                "HIGH".equals(level) ? name : null);
    }
}
