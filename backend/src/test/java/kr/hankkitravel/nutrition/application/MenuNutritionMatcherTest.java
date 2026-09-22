package kr.hankkitravel.nutrition.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import kr.hankkitravel.nutrition.model.NutritionFood;
import kr.hankkitravel.nutrition.model.NutritionMatchLevel;
import kr.hankkitravel.nutrition.model.NutritionReviewState;
import kr.hankkitravel.nutrition.persistence.NutritionFoodMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MenuNutritionMatcherTest {
    private NutritionFoodMapper foods;
    private MenuNutritionMatcher matcher;

    @BeforeEach void setUp() {
        foods = mock(NutritionFoodMapper.class);
        matcher = new MenuNutritionMatcher(foods, new NutritionNameNormalizer());
    }

    @Test void exactIsHighAndApprovedAliasIsMediumWhenReferenceIsUnique() {
        var bibimbap = food(11);
        var banquetNoodles = food(12);
        when(foods.findActiveByNormalizedName("비빔밥")).thenReturn(List.of(bibimbap));
        when(foods.findActiveByNormalizedName("잔치국수")).thenReturn(List.of(banquetNoodles));

        var exact = matcher.match("비빔밥");
        var alias = matcher.match("멸치국수");

        assertThat(exact.nutritionFoodId()).isEqualTo(11L);
        assertThat(exact.matchLevel()).isEqualTo(NutritionMatchLevel.HIGH);
        assertThat(alias.nutritionFoodId()).isEqualTo(12L);
        assertThat(alias.matchLevel()).isEqualTo(NutritionMatchLevel.MEDIUM);
        assertThat(alias.matchMethod()).isEqualTo("APPROVED_ALIAS");
        assertThat(alias.reviewState()).isEqualTo(NutritionReviewState.AUTO_MATCHED);
    }

    @Test void curatedProxyIsMediumAndGenericOrAmbiguousMenusCannotAutoMatch() {
        var pork = food(21);
        var kimchiStewOne = food(31);
        var kimchiStewTwo = food(32);
        when(foods.findActiveByNormalizedName("흑돼지구이")).thenReturn(List.of());
        when(foods.findActiveByNormalizedName("돼지고기구이")).thenReturn(List.of(pork));
        when(foods.findActiveByNormalizedName("김치찌개")).thenReturn(List.of(kimchiStewOne, kimchiStewTwo));

        var proxy = matcher.match("흑돼지구이");
        var generic = matcher.match("정식");
        var ambiguous = matcher.match("김치찌개");

        assertThat(proxy.nutritionFoodId()).isEqualTo(21L);
        assertThat(proxy.matchLevel()).isEqualTo(NutritionMatchLevel.MEDIUM);
        assertThat(proxy.reviewState()).isEqualTo(NutritionReviewState.AUTO_MATCHED);
        assertThat(generic.matchLevel()).isEqualTo(NutritionMatchLevel.NONE);
        assertThat(generic.nutritionFoodId()).isNull();
        assertThat(ambiguous.matchLevel()).isEqualTo(NutritionMatchLevel.LOW);
        assertThat(ambiguous.nutritionFoodId()).isNull();
        assertThat(ambiguous.reviewState()).isEqualTo(NutritionReviewState.REVIEW_REQUIRED);
    }

    @Test void fuzzyCandidatesStayReviewOnly() {
        when(foods.findActiveByNormalizedName("제주불고기")).thenReturn(List.of());
        when(foods.findCandidateNamesByToken("제주불고기")).thenReturn(List.of("소불고기"));

        var decision = matcher.match("제주불고기");

        assertThat(decision.matchLevel()).isEqualTo(NutritionMatchLevel.LOW);
        assertThat(decision.nutritionFoodId()).isNull();
        assertThat(decision.matchMethod()).isEqualTo("FUZZY_CANDIDATE_ONLY");
    }

    private NutritionFood food(long id) {
        var food = mock(NutritionFood.class);
        when(food.getId()).thenReturn(id);
        return food;
    }
}
