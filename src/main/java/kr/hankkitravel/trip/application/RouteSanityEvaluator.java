package kr.hankkitravel.trip.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class RouteSanityEvaluator {
    static final String NORMAL="NORMAL",CAUTION="CAUTION",HIGH="HIGH",NOT_EVALUATED="NOT_EVALUATED";
    private final BigDecimal transitWarn;private final BigDecimal transitCritical;private final int transferWarn;
    private final long carWarnMeters;private final long carCriticalMeters;
    private final BigDecimal dayWarn;private final BigDecimal dayCritical;

    RouteSanityEvaluator(
            @Value("${planner.route-sanity.public-transit.warn-minutes:60}")BigDecimal transitWarn,
            @Value("${planner.route-sanity.public-transit.critical-minutes:120}")BigDecimal transitCritical,
            @Value("${planner.route-sanity.public-transit.warn-transfers:2}")int transferWarn,
            @Value("${planner.route-sanity.car.warn-distance-km:20}")BigDecimal carWarnKm,
            @Value("${planner.route-sanity.car.critical-distance-km:40}")BigDecimal carCriticalKm,
            @Value("${planner.route-sanity.day.warn-total-transit-minutes:120}")BigDecimal dayWarn,
            @Value("${planner.route-sanity.day.critical-total-transit-minutes:240}")BigDecimal dayCritical){
        if(nonPositive(transitWarn)||nonPositive(transitCritical)||transitWarn.compareTo(transitCritical)>=0||transferWarn<1
                ||nonPositive(carWarnKm)||nonPositive(carCriticalKm)||carWarnKm.compareTo(carCriticalKm)>=0
                ||nonPositive(dayWarn)||nonPositive(dayCritical)||dayWarn.compareTo(dayCritical)>=0)
            throw new IllegalArgumentException("이동 현실성 임계값 설정이 올바르지 않습니다.");
        this.transitWarn=transitWarn;this.transitCritical=transitCritical;this.transferWarn=transferWarn;
        this.carWarnMeters=kilometersToMeters(carWarnKm);this.carCriticalMeters=kilometersToMeters(carCriticalKm);
        this.dayWarn=dayWarn;this.dayCritical=dayCritical;
    }

    Result evaluate(List<TripPlannerView.Leg> source){
        var legs=new ArrayList<TripPlannerView.Leg>(source.size());var evaluated=new ArrayList<IndexedLeg>();
        BigDecimal transitTotal=BigDecimal.ZERO;long straightTotal=0,walkingTotal=0;int transfers=0;
        for(int index=0;index<source.size();index++){
            var leg=source.get(index);String severity=NOT_EVALUATED;var reasons=new ArrayList<String>();
            if(transitEvidence(leg)){
                severity=transitSeverity(leg);transitTotal=transitTotal.add(leg.durationMinutes());
                transfers+=leg.transferCount();walkingTotal+=leg.explicitWalkingDistanceMeters();
                if(leg.durationMinutes().compareTo(transitCritical)>=0)reasons.add("대중교통 이동 시간이 매우 긴 구간이에요.");
                else if(leg.durationMinutes().compareTo(transitWarn)>=0)reasons.add("대중교통 이동 시간이 긴 편이에요.");
                if(leg.transferCount()>=transferWarn)reasons.add("환승 횟수가 많은 편이에요.");
                evaluated.add(new IndexedLeg(index,leg,severity,leg.durationMinutes(),null));
            }else if(carEvidence(leg)){
                severity=carSeverity(leg.straightDistanceMeters());straightTotal+=leg.straightDistanceMeters();
                if(HIGH.equals(severity))reasons.add("직선거리가 매우 긴 구간이에요. 실제 도로 거리는 더 길 수 있어요.");
                else if(CAUTION.equals(severity))reasons.add("직선거리가 긴 편이에요. 실제 도로 거리는 더 길 수 있어요.");
                evaluated.add(new IndexedLeg(index,leg,severity,null,leg.straightDistanceMeters()));
            }
            legs.add(copy(leg,severity,reasons));
        }
        String severity=evaluated.stream().map(IndexedLeg::severity).reduce(NORMAL,RouteSanityEvaluator::maxSeverity);
        var dayReasons=new ArrayList<String>();
        if(transitTotal.compareTo(dayCritical)>=0){severity=HIGH;dayReasons.add("하루의 확인된 대중교통 이동 시간이 매우 긴 편이에요.");}
        else if(transitTotal.compareTo(dayWarn)>=0){severity=maxSeverity(severity,CAUTION);dayReasons.add("하루의 확인된 대중교통 이동 시간이 긴 편이에요.");}
        long highCount=evaluated.stream().filter(value->HIGH.equals(value.severity())).count();
        long cautionCount=evaluated.stream().filter(value->CAUTION.equals(value.severity())).count();
        if(highCount>0)dayReasons.add("확인 가능한 이동 구간 중 긴 이동이 "+highCount+"개 있어요.");
        else if(cautionCount>0)dayReasons.add("확인 가능한 이동 구간 중 살펴볼 이동이 "+cautionCount+"개 있어요.");
        var longest=evaluated.stream().max(Comparator.comparing(IndexedLeg::weight)).map(this::longest).orElse(null);
        var actions=new LinkedHashSet<String>();
        evaluated.stream().filter(value->!NORMAL.equals(value.severity())).sorted(Comparator.comparingInt(IndexedLeg::index))
                .forEach(value->actions.add(actionFor(value.leg().toSlotType())));
        actions.remove(null);
        if(actions.isEmpty()&&!NORMAL.equals(severity)&&longest!=null)actions.add(actionFor(longest.toSlotType()));
        actions.remove(null);if(!NORMAL.equals(severity)&&!NOT_EVALUATED.equals(severity))actions.add("KEEP_ITINERARY");
        int count=evaluated.size();String state=count==0?NOT_EVALUATED:count==source.size()?"EVALUATED":"PARTIAL";
        int coverage=source.isEmpty()?0:(int)Math.round(count*100.0/source.size());
        var summary=new TripPlannerView.RouteSanity(state,count==0?NOT_EVALUATED:severity,coverage,count,source.size(),longest,
                transitTotal.signum()==0?null:transitTotal,straightTotal==0?null:straightTotal,transfers,walkingTotal,
                List.copyOf(dayReasons),List.copyOf(actions));
        return new Result(List.copyOf(legs),summary);
    }

    private String transitSeverity(TripPlannerView.Leg leg){
        if(leg.durationMinutes().compareTo(transitCritical)>=0)return HIGH;
        if(leg.durationMinutes().compareTo(transitWarn)>=0||leg.transferCount()>=transferWarn)return CAUTION;
        return NORMAL;
    }
    private String carSeverity(long meters){return meters>=carCriticalMeters?HIGH:meters>=carWarnMeters?CAUTION:NORMAL;}
    private boolean transitEvidence(TripPlannerView.Leg leg){return "PUBLIC_TRANSIT".equals(leg.mode())&&"CURRENT_DATA".equals(leg.dataAvailability())&&leg.durationMinutes()!=null;}
    private boolean carEvidence(TripPlannerView.Leg leg){return "CAR".equals(leg.mode())&&"STRAIGHT_LINE_REFERENCE".equals(leg.dataAvailability())&&leg.straightDistanceMeters()!=null;}
    private TripPlannerView.Leg copy(TripPlannerView.Leg leg,String severity,List<String> reasons){return new TripPlannerView.Leg(leg.fromSlotType(),leg.toSlotType(),leg.mode(),leg.durationMinutes(),leg.transferCount(),leg.explicitWalkingDistanceMeters(),leg.unaccountedDistanceMeters(),leg.straightDistanceMeters(),leg.dataAvailability(),leg.kakaoMapLandingUrl(),severity,reasons);}
    private TripPlannerView.LongestLeg longest(IndexedLeg value){var leg=value.leg();return new TripPlannerView.LongestLeg(value.index(),leg.fromSlotType(),leg.toSlotType(),leg.mode(),leg.durationMinutes(),leg.straightDistanceMeters(),value.severity());}
    private static String actionFor(String slot){if(slot==null)return null;if(List.of("BREAKFAST","LUNCH","DINNER","POST_MEAL_DESSERT","POST_LUNCH_DESSERT","POST_DINNER_DESSERT").contains(slot))return "NEAR_RESTAURANT";if("STAY".equals(slot))return "NEAR_STAY";if(List.of("DAY_FOCUS","MORNING_ACTIVITY","AFTERNOON_ACTIVITY").contains(slot))return "NEAR_ATTRACTION";return null;}
    private static String maxSeverity(String left,String right){return rank(right)>rank(left)?right:left;}
    private static int rank(String value){return HIGH.equals(value)?2:CAUTION.equals(value)?1:0;}
    private static boolean nonPositive(BigDecimal value){return value==null||value.signum()<=0;}
    private static long kilometersToMeters(BigDecimal value){return value.multiply(BigDecimal.valueOf(1000)).setScale(0,RoundingMode.HALF_UP).longValueExact();}
    record Result(List<TripPlannerView.Leg> legs,TripPlannerView.RouteSanity summary) { }
    private record IndexedLeg(int index,TripPlannerView.Leg leg,String severity,BigDecimal duration,Long distance){BigDecimal weight(){return duration!=null?duration:BigDecimal.valueOf(distance).divide(BigDecimal.valueOf(1000));}}
}
