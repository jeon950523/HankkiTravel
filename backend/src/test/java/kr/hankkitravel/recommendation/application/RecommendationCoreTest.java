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

        assertThat(first).extracting(item -> item.candidate().candidate().contentId()).containsExactly("30", "10", "20");
        assertThat(second).extracting(item -> item.candidate().candidate().contentId()).containsExactly("30", "10", "20");
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

    @Test void v2WeightsNormalizeOnlyEvaluatedDimensionsAndExposeCoverage() {
        var candidate = candidate("11", "제주 향토 식당", "주차 가능", List.of(menu("국밥", "HIGH", "100")));
        var ranked = core.rank(core.score(List.of(candidate), sodiumFamily, mealFirst), Perspective.BALANCED).getFirst();

        assertThat(new RecommendationScoringProperties().weights().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(100);
        assertThat(ranked.compatibilityScore()).isBetween(0, 100);
        assertThat(ranked.evidenceCoverage()).isEqualTo(60);
        assertThat(ranked.candidate().dimensions().get(RecommendationCore.AREA_DEMAND_SIGNAL).state())
                .isEqualTo(RecommendationCore.EvidenceState.NOT_EVALUATED);
        assertThat(ranked.candidate().dimensions().get(RecommendationCore.REVIEW_SIGNAL).state())
                .isEqualTo(RecommendationCore.EvidenceState.NOT_EVALUATED);
    }

    @Test void explicitAllergenConflictIsExcludedButUnknownNeedsPhoneCheck() {
        var family = new FamilyContext("CAR", "NO_PREFERENCE", 30, false, "NO_PREFERENCE", List.of("NONE"),
                List.of("땅콩"), List.of());
        var conflict = candidate("12", "식당", null, List.of(menu("땅콩 국수", "LOW", null)));
        var unknown = candidate("13", "식당", null, List.of(menu("국수", "LOW", null)));

        assertThat(core.hardDecision(conflict, family, mealFirst).included()).isFalse();
        assertThat(core.hardDecision(unknown, family, mealFirst).included()).isTrue();
        assertThat(core.score(List.of(unknown), family, mealFirst).getFirst().checks())
                .anyMatch(text -> text.contains("전화"));
    }

    @Test void demandEvidenceActivatesOnlyAreaDimensionAndReviewRemainsUnevaluated() {
        var base=candidate("14","식당","주차 가능",List.of(menu("국밥","HIGH","100")));
        var demand=new RecommendationCore.AreaDemandEvidence(true,78,"202608","공식 지역 방문 수요예요.",List.of("공식 출처"));
        var candidate=base.withAreaDemand(demand);
        var scored=core.score(List.of(candidate),sodiumFamily,mealFirst).getFirst();
        assertThat(scored.dimensions().get(RecommendationCore.AREA_DEMAND_SIGNAL).score()).isEqualTo(78);
        assertThat(scored.dimensions().get(RecommendationCore.REVIEW_SIGNAL).state())
                .isEqualTo(RecommendationCore.EvidenceState.NOT_EVALUATED);
    }

    @Test void onlyHighOrApprovedMediumNutritionCanScore() {
        var autoMedium=new MenuEvidence("국밥","MEDIUM",new BigDecimal("100"),null,null,"국밥","AUTO_MATCHED");
        var approvedMedium=new MenuEvidence("국밥","MEDIUM",new BigDecimal("100"),null,null,"국밥","APPROVED");
        assertThat(core.score(List.of(candidate("15","식당",null,List.of(autoMedium))),sodiumFamily,mealFirst)
                .getFirst().dimensions().get(RecommendationCore.MEAL).evaluated()).isFalse();
        assertThat(core.score(List.of(candidate("16","식당",null,List.of(approvedMedium))),sodiumFamily,mealFirst)
                .getFirst().dimensions().get(RecommendationCore.MEAL).evaluated()).isTrue();
    }

    private Candidate candidate(String id, String title, String parking, List<MenuEvidence> menus) {
        return new Candidate(id, title, "테스트 주소", null, parking, menus, null);
    }
    private MenuEvidence menu(String name, String level, String sodium) {
        return new MenuEvidence(name, level, sodium == null ? null : new BigDecimal(sodium), null, null,
                "HIGH".equals(level) ? name : null);
    }
}
