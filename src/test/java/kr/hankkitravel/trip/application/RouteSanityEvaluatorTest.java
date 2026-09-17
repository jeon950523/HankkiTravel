package kr.hankkitravel.trip.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteSanityEvaluatorTest {
    private final RouteSanityEvaluator evaluator=new RouteSanityEvaluator(decimal(60),decimal(120),2,decimal(20),decimal(40),decimal(120),decimal(240));

    @Test void publicTransitNormalCautionAndHighUseOnlyCurrentRouteEvidence(){
        assertThat(evaluator.evaluate(List.of(transit("20",0))).summary().severity()).isEqualTo("NORMAL");
        assertThat(evaluator.evaluate(List.of(transit("60",1))).summary().severity()).isEqualTo("CAUTION");
        var high=evaluator.evaluate(List.of(transit("195",3))).summary();
        assertThat(high.severity()).isEqualTo("HIGH");
        assertThat(high.totalTransitMinutes()).isEqualByComparingTo("195");
        assertThat(high.totalTransfers()).isEqualTo(3);
    }

    @Test void carNormalCautionAndHighUseOnlyStraightLineDistance(){
        assertThat(evaluator.evaluate(List.of(car(5_000))).summary().severity()).isEqualTo("NORMAL");
        assertThat(evaluator.evaluate(List.of(car(20_000))).summary().severity()).isEqualTo("CAUTION");
        var high=evaluator.evaluate(List.of(car(43_000))).summary();
        assertThat(high.severity()).isEqualTo("HIGH");
        assertThat(high.totalStraightDistanceMeters()).isEqualTo(43_000);
        assertThat(high.totalTransitMinutes()).isNull();
    }

    @Test void detectsLongestLegAndDayTotalBurdenDeterministically(){
        var legs=List.of(transit("70",1),transit("55",0));
        var first=evaluator.evaluate(legs).summary();var second=evaluator.evaluate(legs).summary();
        assertThat(first).isEqualTo(second);
        assertThat(first.severity()).isEqualTo("CAUTION");
        assertThat(first.totalTransitMinutes()).isEqualByComparingTo("125");
        assertThat(first.longestLeg().legIndex()).isZero();
        assertThat(first.longestLeg().durationMinutes()).isEqualByComparingTo("70");
        assertThat(first.suggestedActions()).containsExactly("NEAR_RESTAURANT","KEEP_ITINERARY");
    }

    @Test void partialAndUnknownLegsAreExcludedInsteadOfCountedAsZero(){
        var result=evaluator.evaluate(List.of(transit("25",1),unavailable())).summary();
        assertThat(result.state()).isEqualTo("PARTIAL");
        assertThat(result.evaluatedLegCount()).isOne();
        assertThat(result.totalLegCount()).isEqualTo(2);
        assertThat(result.evidenceCoverage()).isEqualTo(50);
        assertThat(result.totalTransitMinutes()).isEqualByComparingTo("25");
        assertThat(evaluator.evaluate(List.of(unavailable())).summary().totalTransitMinutes()).isNull();
    }

    @Test void suggestsContextAwareReentryWithoutMutatingAnyLeg(){
        var result=evaluator.evaluate(List.of(transit("195",3,"DAY_FOCUS","LUNCH"),transit("130",1,"LUNCH","AFTERNOON_ACTIVITY"),transit("130",1,"DINNER","STAY")));
        assertThat(result.summary().suggestedActions()).containsExactly("NEAR_RESTAURANT","NEAR_ATTRACTION","NEAR_STAY","KEEP_ITINERARY");
        assertThat(result.legs()).hasSize(3);
    }

    @Test void rejectsNonPositiveOrInvertedThresholds(){
        assertThatThrownBy(()->new RouteSanityEvaluator(decimal(0),decimal(120),2,decimal(20),decimal(40),decimal(120),decimal(240))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new RouteSanityEvaluator(decimal(120),decimal(60),2,decimal(20),decimal(40),decimal(120),decimal(240))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new RouteSanityEvaluator(decimal(60),decimal(120),0,decimal(20),decimal(40),decimal(120),decimal(240))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new RouteSanityEvaluator(decimal(60),decimal(120),2,decimal(40),decimal(20),decimal(120),decimal(240))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new RouteSanityEvaluator(decimal(60),decimal(120),2,decimal(20),decimal(40),decimal(240),decimal(120))).isInstanceOf(IllegalArgumentException.class);
    }

    private static BigDecimal decimal(long value){return BigDecimal.valueOf(value);}
    private static TripPlannerView.Leg transit(String minutes,int transfers){return transit(minutes,transfers,"DAY_FOCUS","LUNCH");}
    private static TripPlannerView.Leg transit(String minutes,int transfers,String from,String to){return new TripPlannerView.Leg(from,to,"PUBLIC_TRANSIT",new BigDecimal(minutes),transfers,500,900,10_000L,"CURRENT_DATA","https://map.kakao.test","NOT_EVALUATED",List.of());}
    private static TripPlannerView.Leg car(long meters){return new TripPlannerView.Leg("DAY_FOCUS","LUNCH","CAR",null,0,0,0,meters,"STRAIGHT_LINE_REFERENCE",null,"NOT_EVALUATED",List.of());}
    private static TripPlannerView.Leg unavailable(){return new TripPlannerView.Leg("LUNCH","DINNER","PUBLIC_TRANSIT",null,0,0,0,null,"UNAVAILABLE",null,"NOT_EVALUATED",List.of());}
}
