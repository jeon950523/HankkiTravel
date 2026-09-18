package kr.hankkitravel.trip.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import kr.hankkitravel.profile.application.FamilyProfileApplicationService;
import kr.hankkitravel.profile.application.FamilyProfileSnapshot;
import kr.hankkitravel.recommendation.application.AreaDemandSignalProvider;
import kr.hankkitravel.shared.geo.Coordinates;
import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.tourism.application.TourismRealtimeGateway;
import kr.hankkitravel.tourism.model.TourismContentType;
import kr.hankkitravel.tourism.model.TourismLivePlace;
import kr.hankkitravel.tourism.model.TourismPlace;
import kr.hankkitravel.tourism.model.TourismRegion;
import kr.hankkitravel.transit.application.TransitRouteFinder;
import kr.hankkitravel.transit.model.TransitRoute;
import kr.hankkitravel.trip.model.TripPlannerRows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Request-scoped route-aware ranking. Live tourism and transit evidence are never persisted. */
@Service
final class RouteAwareAttractionRecommendationService {
    private static final String ATTRIBUTION="출처: ⓒ한국관광공사";
    private final TripPlaceScheduleService schedules;private final FamilyProfileApplicationService profiles;
    private final TourismRealtimeGateway tourism;private final TransitRouteFinder transit;private final AreaDemandSignalProvider demand;
    private final DayRecommendationOriginService contexts;
    private final int listSize;private final int detailLimit;private final int transitLimit;
    private final int nearbyMaxTransitMinutes;private final long nearbyMaxDistanceMeters;private final int signatureMaxTransitMinutes;
    private final RouteAwareAttractionRanker ranker;

    RouteAwareAttractionRecommendationService(TripPlaceScheduleService schedules,FamilyProfileApplicationService profiles,
            TourismRealtimeGateway tourism,TransitRouteFinder transit,AreaDemandSignalProvider demand,DayRecommendationOriginService contexts,
            @Value("${hankki.planner.list-size:15}")int listSize,
            @Value("${hankki.planner.detail-limit:6}")int detailLimit,
            @Value("${hankki.planner.attraction.transit-call-limit:6}")int transitLimit,
            @Value("${hankki.planner.attraction.nearby.max-transit-minutes:90}")int nearbyMaxTransitMinutes,
            @Value("${hankki.planner.attraction.nearby.max-distance-km:25}")double nearbyMaxDistanceKm,
            @Value("${hankki.planner.attraction.signature.max-transit-minutes:150}")int signatureMaxTransitMinutes,
            @Value("${hankki.planner.attraction.weights.nearby.route:50}")int nearbyRoute,
            @Value("${hankki.planner.attraction.weights.nearby.focus:20}")int nearbyFocus,
            @Value("${hankki.planner.attraction.weights.nearby.mobility:15}")int nearbyMobility,
            @Value("${hankki.planner.attraction.weights.nearby.relevance:15}")int nearbyRelevance,
            @Value("${hankki.planner.attraction.weights.signature.route:20}")int signatureRoute,
            @Value("${hankki.planner.attraction.weights.signature.focus:15}")int signatureFocus,
            @Value("${hankki.planner.attraction.weights.signature.relevance:40}")int signatureRelevance,
            @Value("${hankki.planner.attraction.weights.signature.demand:25}")int signatureDemand){
        if(listSize<1||listSize>30||detailLimit<1||detailLimit>listSize||transitLimit<1||nearbyMaxTransitMinutes<1
                ||nearbyMaxDistanceKm<=0||signatureMaxTransitMinutes<nearbyMaxTransitMinutes)throw new IllegalArgumentException("관광지 추천 설정을 확인하세요.");
        this.schedules=schedules;this.profiles=profiles;this.tourism=tourism;this.transit=transit;this.demand=demand;this.contexts=contexts;
        this.listSize=listSize;this.detailLimit=detailLimit;this.transitLimit=transitLimit;
        this.nearbyMaxTransitMinutes=nearbyMaxTransitMinutes;this.nearbyMaxDistanceMeters=Math.round(nearbyMaxDistanceKm*1000);
        this.signatureMaxTransitMinutes=signatureMaxTransitMinutes;
        this.ranker=new RouteAwareAttractionRanker(new RouteAwareAttractionRanker.Weights(nearbyRoute,nearbyFocus,nearbyMobility,nearbyRelevance,0),
                new RouteAwareAttractionRanker.Weights(signatureRoute,signatureFocus,0,signatureRelevance,signatureDemand));
    }

    TripPlannerView.Recommendations recommend(String guest,String trip,int day,TripPlannerView.SlotType slot){
        long started=System.nanoTime();var refs=schedules.references(guest,trip,day);var profile=profiles.owned(guest,refs.context().profileId());
        var calls=new CallCounter();var context=movementContext(refs,slot,calls);var listed=list(refs.context().regionKey());
        Set<String> selected=new HashSet<>();refs.places().forEach(value->selected.add(value.getContentId()));
        var liveCandidates=new ArrayList<LiveCandidate>();
        for(var place:listed.places().stream().filter(value->TourismContentType.ATTRACTION.code().equals(value.contentTypeId()))
                .filter(value->inRegion(value,refs.context().regionKey())).filter(value->value.coordinates()!=null&&!selected.contains(value.contentId()))
                .sorted(Comparator.comparing(TourismPlace::contentId,RouteAwareAttractionRecommendationService::stableIdCompare)).toList()){
            if(liveCandidates.size()>=detailLimit)break;calls.details++;
            try{var live=tourism.place(place.contentId());if(valid(live,refs.context().regionKey())&&live.coordinates()!=null)liveCandidates.add(new LiveCandidate(place,live));}
            catch(IntegrationException ignored){}
        }
        var evaluated=evaluate(liveCandidates,context,profile,refs.context().travelDate(),calls);
        var nearby=ranker.rank(evaluated.stream().filter(value->nearbyAllowed(value,profile)).map(EvaluatedCandidate::input).toList(),RouteAwareAttractionRanker.Perspective.NEARBY_COURSE)
                .stream().map(value->view(value,liveCandidates,"NEARBY_COURSE")).toList();
        var signature=ranker.rank(evaluated.stream().map(value->signatureInput(value.input())).toList(),RouteAwareAttractionRanker.Perspective.SIGNATURE_COURSE)
                .stream().map(value->view(value,liveCandidates,"SIGNATURE_COURSE")).toList();
        String nearbyStatus=context.hasAny()?"READY":"MOVEMENT_CONTEXT_REQUIRED";
        var perspectives=List.of(new TripPlannerView.PerspectiveResult("NEARBY_COURSE",nearbyStatus,
                        context.hasAny()?"현재 일정에서 이동 부담이 적은 장소를 우선했어요.":"이동 비교를 위해 중심 장소나 식사 장소를 먼저 선택해 주세요.",nearby),
                new TripPlannerView.PerspectiveResult("SIGNATURE_COURSE","READY","조금 더 이동하더라도 공식 지역 대표성을 함께 고려했어요.",signature));
        String availability=calls.transitFailures>0?"TRANSIT_PARTIALLY_UNAVAILABLE":nearby.isEmpty()&&signature.isEmpty()?"CURRENT_DATA_PARTIALLY_UNAVAILABLE":"CURRENT_DATA";
        return new TripPlannerView.Recommendations(slot.name(),nearby,new TripPlannerView.CallSummary(listed.calls(),calls.details,calls.transit,elapsed(started)),
                availability,ATTRIBUTION,perspectives,context.hasAny()?"READY":"MOVEMENT_CONTEXT_REQUIRED");
    }

    private List<EvaluatedCandidate> evaluate(List<LiveCandidate> candidates,MovementContext context,FamilyProfileSnapshot profile,
            java.time.LocalDate travelDate,CallCounter calls){
        var ordered=candidates.stream().sorted(Comparator.comparingDouble(value->straightDistance(context,value.live().coordinates()))).toList();
        var result=new ArrayList<EvaluatedCandidate>();Map<String,AreaDemandSignalProvider.Signal> demandByRegion=new LinkedHashMap<>();
        for(var candidate:ordered){
            var official=officialDemand(candidate.live(),travelDate,demandByRegion);
            Coordinates point=candidate.live().coordinates();Long distance=context.hasAny()?Math.round(straightDistance(context,point)*1000):null;
            Integer routeScore=null;Integer mobilityScore=null;TripPlannerView.TransitSummary summary=null;String state="NOT_EVALUATED";
            var reasons=new ArrayList<String>();var cautions=new ArrayList<String>();var mobility=new ArrayList<String>();
            if("PUBLIC_TRANSIT".equals(profile.transportMode())&&context.hasAny()){
                var route=transitEvidence(context,point,calls);summary=route.summary();state=route.state();
                if(route.duration()!=null){routeScore=routeScore(route.duration(),route.transfers(),route.walking());mobilityScore=mobilityScore(routeScore,route.transfers(),route.walking(),profile);
                    reasons.add("대중교통 약 "+route.duration().setScale(0,RoundingMode.HALF_UP)+"분 · 환승 "+route.transfers()+"회 · 명시 도보 "+route.walking()+"m");
                    mobility.add("실제 대중교통 경로의 시간·환승·명시 도보만 반영했습니다.");}
                else cautions.add("대중교통 경로를 확인하지 못해 이동 부담은 미평가했어요.");
            }else if("CAR".equals(profile.transportMode())&&context.hasAny()){
                routeScore=distanceScore(distance);mobilityScore=routeScore;state="REFERENCE";reasons.add("직선거리 약 "+distanceLabel(distance));
                mobility.add("자동차는 직선거리 기준 참고값입니다.");cautions.add("실제 도로 이동시간이 아닙니다. 자동차 이동시간은 제공하지 않아요.");
            }else cautions.add("이동 비교를 위해 중심 장소나 식사 장소가 더 필요해요.");
            int relevance=80+(candidate.listed().lclsSystm1()==null?0:10);
            Integer focus=context.focus()==null?null:distanceScore(Math.round(distanceKm(context.focus(),point)*1000));
            Integer demandScore=official.evaluated()?official.score():null;
            reasons.add("현재 TourAPI에서 지역과 관광지 유형을 확인했습니다.");
            if(official.evaluated())reasons.add(official.reason());else cautions.add("공식 지역 관광 수요는 현재 확인하지 못했어요.");
            cautions.add("운영시간과 휴무일은 방문 전에 확인해 주세요.");
            var burden=new TripPlannerView.RouteBurden(state,routeScore==null?"NOT_EVALUATED":burdenLevel(routeScore),routeScore==null?0:routeScore);
            var input=new RouteAwareAttractionRanker.Input(candidate.live().contentId(),routeScore,focus,mobilityScore,relevance,demandScore,burden,distance,summary,
                    List.copyOf(mobility),List.copyOf(reasons),List.copyOf(cautions));result.add(new EvaluatedCandidate(input,summary));
        }
        return result;
    }

    private TransitEvidence transitEvidence(MovementContext context,Coordinates candidate,CallCounter calls){
        BigDecimal duration=BigDecimal.ZERO;int transfers=0;long walking=0;int successful=0;int intended=0;
        var legs=new Coordinates[][]{{context.start(),candidate},{candidate,context.end()}};
        for(var leg:legs){if(leg[0]==null||leg[1]==null)continue;intended++;if(calls.transit>=transitLimit)continue;calls.transit++;
            try{var route=selectRoute(transit.findRoutes(leg[0],leg[1]));if(route!=null){successful++;duration=duration.add(route.totalTimeMinutes());transfers+=route.transferCount();walking+=route.explicitWalkingDistanceMeters();}}
            catch(IntegrationException exception){calls.transitFailures++;}}
        if(successful==0)return new TransitEvidence("NOT_EVALUATED",null,0,0,null);
        String state=successful==intended?"EVALUATED":"PARTIAL";return new TransitEvidence(state,duration,transfers,walking,new TripPlannerView.TransitSummary(state,duration,transfers,walking));
    }
    private boolean nearbyAllowed(EvaluatedCandidate value,FamilyProfileSnapshot profile){var input=value.input();
        if("PUBLIC_TRANSIT".equals(profile.transportMode())&&value.transit()!=null&&value.transit().durationMinutes()!=null)
            return value.transit().durationMinutes().compareTo(BigDecimal.valueOf(nearbyMaxTransitMinutes))<=0;
        return input.distanceMeters()==null||input.distanceMeters()<=nearbyMaxDistanceMeters;
    }
    private RouteAwareAttractionRanker.Input signatureInput(RouteAwareAttractionRanker.Input input){
        if(input.transit()==null||input.transit().durationMinutes()==null||input.transit().durationMinutes().compareTo(BigDecimal.valueOf(signatureMaxTransitMinutes))<=0)return input;
        var cautions=new ArrayList<>(input.cautions());cautions.add("대표 명소이지만 현재 일정에서는 이동 부담이 큰 편이에요.");
        return new RouteAwareAttractionRanker.Input(input.contentId(),Math.min(input.routeScore()==null?100:input.routeScore(),15),input.focusScore(),input.mobilityScore(),
                input.relevanceScore(),input.demandScore(),input.routeBurden(),input.distanceMeters(),input.transit(),input.mobilityEvidence(),input.reasons(),List.copyOf(cautions));
    }
    private TripPlannerView.Candidate view(RouteAwareAttractionRanker.Ranked ranked,List<LiveCandidate> candidates,String perspective){
        var live=candidates.stream().filter(value->value.live().contentId().equals(ranked.input().contentId())).findFirst().orElseThrow().live();var input=ranked.input();
        return new TripPlannerView.Candidate(live.contentId(),live.contentType(),live.title(),areaLabel(live.address()),live.firstImage(),live.address(),live.coordinates(),
                "TOUR_API_LIVE",input.reasons(),input.cautions(),ATTRIBUTION,perspective,ranked.overallScore(),ranked.evidenceCoverage(),input.routeBurden(),
                input.distanceMeters(),input.transit(),input.mobilityEvidence(),input.reasons(),input.cautions(),null);
    }
    private MovementContext movementContext(TripPlaceScheduleService.DayReferences refs,TripPlannerView.SlotType slot,CallCounter calls){
        var resolved=contexts.resolve(refs,slot.name());calls.details+=resolved.detailCalls();
        Coordinates end=nextCoordinate(refs,slot.name(),calls);
        return new MovementContext(resolved.origin(),end,resolved.currentDayFocus());
    }
    private Coordinates nextCoordinate(TripPlaceScheduleService.DayReferences refs,String target,CallCounter calls){
        var combined=new ArrayList<TripPlannerRows.Reference>();combined.addAll(refs.meals());combined.addAll(refs.places());
        return combined.stream().filter(value->DayRecommendationContextResolver.order(value.getSlotType())>DayRecommendationContextResolver.order(target))
                .sorted(Comparator.comparingInt(value->DayRecommendationContextResolver.order(value.getSlotType())))
                .map(value->hydrate(value,calls)).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }
    private Coordinates coordinate(List<TripPlannerRows.Reference> refs,String slot,CallCounter calls){return refs.stream().filter(value->slot.equals(value.getSlotType())).findFirst().map(value->hydrate(value,calls)).orElse(null);}
    private Coordinates firstCoordinate(List<TripPlannerRows.Reference> refs,List<String> slots,CallCounter calls){for(String slot:slots){var value=coordinate(refs,slot,calls);if(value!=null)return value;}return null;}
    private Coordinates hydrate(TripPlannerRows.Reference ref,CallCounter calls){try{calls.details++;return tourism.place(ref.getContentId()).coordinates();}catch(RuntimeException ignored){return null;}}
    private Listed list(String region){var places=new ArrayList<TourismPlace>();int calls=0;for(var value:regions(region)){var page=tourism.places(value,TourismContentType.ATTRACTION,0,listSize);calls++;places.addAll(page.items());}return new Listed(List.copyOf(places),calls);}
    private AreaDemandSignalProvider.Signal officialDemand(TourismLivePlace live,java.time.LocalDate date,Map<String,AreaDemandSignalProvider.Signal> cache){
        if(live.regionCode()==null||live.districtCode()==null)return AreaDemandSignalProvider.Signal.notEvaluated("",0,0);
        String district=live.districtCode().length()==3?live.regionCode()+live.districtCode():live.districtCode();
        String key=live.regionCode()+":"+district;if(cache.containsKey(key))return cache.get(key);
        try{var signal=demand.signal(new AreaDemandSignalProvider.RegionKey(live.regionCode(),district,areaLabel(live.address())),date);cache.put(key,signal);return signal;}
        catch(RuntimeException ignored){var signal=AreaDemandSignalProvider.Signal.notEvaluated("",0,0);cache.put(key,signal);return signal;}
    }
    private TransitRoute selectRoute(kr.hankkitravel.transit.model.TransitResult result){if(result==null||result.routes().isEmpty())return null;return result.routes().stream()
            .min(Comparator.comparing(TransitRoute::totalTimeMinutes).thenComparingInt(TransitRoute::transferCount).thenComparingLong(TransitRoute::explicitWalkingDistanceMeters)).orElse(null);}
    private int routeScore(BigDecimal minutes,int transfers,long walking){return clamp(100-minutes.setScale(0,RoundingMode.HALF_UP).intValue()-transfers*7-(int)(walking/200));}
    private int mobilityScore(int routeScore,int transfers,long walking,FamilyProfileSnapshot profile){int score=routeScore;if("AVOID".equals(profile.transferPreference()))score-=transfers*5;
        if("LOW".equals(profile.walkingBurdenPreference()))score-=(int)(walking/150);if(profile.stairsAvoidance())score-=3;return clamp(score);}
    private int distanceScore(long meters){return clamp(100-(int)Math.round(meters/1000d*3));}
    private int clamp(int value){return Math.max(0,Math.min(100,value));}
    private String burdenLevel(int score){return score>=70?"LOW":score>=40?"MODERATE":"HIGH";}
    private double straightDistance(MovementContext context,Coordinates point){double value=0;if(context.start()!=null)value+=distanceKm(context.start(),point);if(context.end()!=null)value+=distanceKm(point,context.end());return context.hasAny()?value:Double.POSITIVE_INFINITY;}
    private boolean valid(TourismLivePlace value,String region){return value!=null&&TourismContentType.ATTRACTION.code().equals(value.contentType())&&inRegion(value,region);}
    private static boolean inRegion(TourismLivePlace value,String region){return regionMatches(value.regionCode(),value.districtCode(),region);}
    private static boolean inRegion(TourismPlace value,String region){return regionMatches(value.lDongRegnCd(),value.lDongSignguCd(),region);}
    private static boolean regionMatches(String r,String d,String region){if(r==null||r.isBlank())return false;return "JEJU".equals(region)?"50".equals(r):"47".equals(r)&&"130".equals(d);}
    private List<TourismRegion> regions(String region){return "JEJU".equals(region)?List.of(TourismRegion.JEJU_CITY,TourismRegion.SEOGWIPO):List.of(TourismRegion.GYEONGJU);}
    private String areaLabel(String address){if(address==null||address.isBlank())return null;var parts=address.trim().split("\\s+");return String.join(" ",Arrays.copyOf(parts,Math.min(parts.length,3)));}
    private static int stableIdCompare(String a,String b){try{return new java.math.BigInteger(a).compareTo(new java.math.BigInteger(b));}catch(Exception e){return a.compareTo(b);}}
    private static double distanceKm(Coordinates a,Coordinates b){double lat1=Math.toRadians(a.latitude().doubleValue()),lat2=Math.toRadians(b.latitude().doubleValue());double dlat=lat2-lat1,dlon=Math.toRadians(b.longitude().doubleValue()-a.longitude().doubleValue());double h=Math.sin(dlat/2)*Math.sin(dlat/2)+Math.cos(lat1)*Math.cos(lat2)*Math.sin(dlon/2)*Math.sin(dlon/2);return 6371*2*Math.atan2(Math.sqrt(h),Math.sqrt(1-h));}
    private String distanceLabel(long meters){return meters<1000?meters+"m":String.format(Locale.ROOT,"%.1fkm",meters/1000d);}
    private long elapsed(long started){return Duration.ofNanos(System.nanoTime()-started).toMillis();}
    private record Listed(List<TourismPlace> places,int calls){}
    private record LiveCandidate(TourismPlace listed,TourismLivePlace live){}
    private record MovementContext(Coordinates start,Coordinates end,Coordinates focus){boolean hasAny(){return start!=null||end!=null;}}
    private record TransitEvidence(String state,BigDecimal duration,int transfers,long walking,TripPlannerView.TransitSummary summary){}
    private record EvaluatedCandidate(RouteAwareAttractionRanker.Input input,TripPlannerView.TransitSummary transit){}
    private static final class CallCounter {int details;int transit;int transitFailures;}
}
