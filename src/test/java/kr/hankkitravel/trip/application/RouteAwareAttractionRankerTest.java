package kr.hankkitravel.trip.application;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteAwareAttractionRankerTest {
    private final RouteAwareAttractionRanker ranker=new RouteAwareAttractionRanker(
            new RouteAwareAttractionRanker.Weights(50,20,15,15,0),
            new RouteAwareAttractionRanker.Weights(20,15,0,40,25));

    @Test void nearbyAndSignatureUseDifferentConfiguredEvidenceWeights(){
        var close=input("1",95,70,90,55,40);var landmark=input("2",35,80,35,100,100);
        assertThat(ranker.rank(List.of(close,landmark),RouteAwareAttractionRanker.Perspective.NEARBY_COURSE).getFirst().input().contentId()).isEqualTo("1");
        assertThat(ranker.rank(List.of(close,landmark),RouteAwareAttractionRanker.Perspective.SIGNATURE_COURSE).getFirst().input().contentId()).isEqualTo("2");
    }

    @Test void missingRouteIsNotScoredAsZeroAndOrderingIsDeterministic(){
        var missing=input("20",null,80,null,90,70);var tied=input("10",null,80,null,90,70);
        var first=ranker.rank(List.of(missing,tied),RouteAwareAttractionRanker.Perspective.NEARBY_COURSE);
        var second=ranker.rank(List.of(tied,missing),RouteAwareAttractionRanker.Perspective.NEARBY_COURSE);
        assertThat(first).extracting(value->value.input().contentId()).containsExactly("10","20");
        assertThat(second).extracting(value->value.input().contentId()).containsExactly("10","20");
        assertThat(first.getFirst().overallScore()).isGreaterThan(0);assertThat(first.getFirst().evidenceCoverage()).isLessThan(100);
    }

    @Test void sameCandidateReceivesPerspectiveSpecificScore(){
        var candidate=input("1",95,70,90,55,40);
        var nearby=ranker.rank(List.of(candidate),RouteAwareAttractionRanker.Perspective.NEARBY_COURSE).getFirst();
        var signature=ranker.rank(List.of(candidate),RouteAwareAttractionRanker.Perspective.SIGNATURE_COURSE).getFirst();
        assertThat(nearby.input().contentId()).isEqualTo(signature.input().contentId());
        assertThat(nearby.overallScore()).isNotEqualTo(signature.overallScore());
    }

    private RouteAwareAttractionRanker.Input input(String id,Integer route,Integer focus,Integer mobility,int relevance,Integer demand){
        return new RouteAwareAttractionRanker.Input(id,route,focus,mobility,relevance,demand,null,null,null,List.of(),List.of(),List.of());
    }
}
