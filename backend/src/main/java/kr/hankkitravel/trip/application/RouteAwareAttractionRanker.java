package kr.hankkitravel.trip.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure, deterministic scoring. Missing evidence is excluded from the denominator instead of becoming zero. */
final class RouteAwareAttractionRanker {
    enum Perspective { NEARBY_COURSE, SIGNATURE_COURSE }
    record Weights(int route,int focus,int mobility,int relevance,int demand) {
        Weights { if(route<0||focus<0||mobility<0||relevance<0||demand<0||route+focus+mobility+relevance+demand<1)throw new IllegalArgumentException("관광지 추천 가중치를 확인하세요."); }
    }
    record Input(String contentId,Integer routeScore,Integer focusScore,Integer mobilityScore,int relevanceScore,
            Integer demandScore,TripPlannerView.RouteBurden routeBurden,Long distanceMeters,
            TripPlannerView.TransitSummary transit,List<String> mobilityEvidence,List<String> reasons,List<String> cautions) { }
    record Ranked(Input input,int overallScore,int evidenceCoverage) { }

    private final Weights nearby;
    private final Weights signature;
    RouteAwareAttractionRanker(Weights nearby,Weights signature){this.nearby=nearby;this.signature=signature;}

    List<Ranked> rank(List<Input> candidates,Perspective perspective){
        Weights weights=perspective==Perspective.NEARBY_COURSE?nearby:signature;
        Comparator<Ranked> scoreOrder=Comparator
                .comparingInt(Ranked::overallScore).reversed().thenComparing(Comparator.comparingInt(Ranked::evidenceCoverage).reversed())
                .thenComparing(value->value.input().contentId(),RouteAwareAttractionRanker::stableIdCompare);
        Comparator<Ranked> order=perspective==Perspective.NEARBY_COURSE
                ?Comparator.comparing((Ranked value)->value.input().routeScore()!=null).reversed().thenComparing(scoreOrder):scoreOrder;
        return candidates.stream().map(input->score(input,weights)).sorted(order).toList();
    }
    private Ranked score(Input input,Weights weights){
        var points=new ArrayList<Integer>();var evaluated=new ArrayList<Integer>();
        add(points,evaluated,input.routeScore(),weights.route());
        add(points,evaluated,input.focusScore(),weights.focus());
        add(points,evaluated,input.mobilityScore(),weights.mobility());
        add(points,evaluated,input.relevanceScore(),weights.relevance());
        add(points,evaluated,input.demandScore(),weights.demand());
        int used=evaluated.stream().mapToInt(Integer::intValue).sum();
        int total=weights.route()+weights.focus()+weights.mobility()+weights.relevance()+weights.demand();
        int score=used==0?0:BigDecimal.valueOf(points.stream().mapToInt(Integer::intValue).sum())
                .divide(BigDecimal.valueOf(used),0,RoundingMode.HALF_UP).intValue();
        int coverage=BigDecimal.valueOf(used*100L).divide(BigDecimal.valueOf(total),0,RoundingMode.HALF_UP).intValue();
        return new Ranked(input,score,coverage);
    }
    private void add(List<Integer> points,List<Integer> evaluated,Integer score,int weight){
        if(score==null||weight==0)return;evaluated.add(weight);points.add(score*weight);
    }
    private static int stableIdCompare(String a,String b){try{return new java.math.BigInteger(a).compareTo(new java.math.BigInteger(b));}catch(Exception e){return a.compareTo(b);}}
}
