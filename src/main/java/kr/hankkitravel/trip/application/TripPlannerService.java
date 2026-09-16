package kr.hankkitravel.trip.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import kr.hankkitravel.profile.application.FamilyProfileApplicationService;
import kr.hankkitravel.shared.geo.Coordinates;
import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.tourism.application.TourismRealtimeGateway;
import kr.hankkitravel.tourism.model.*;
import kr.hankkitravel.transit.application.TransitRouteFinder;
import kr.hankkitravel.transit.model.TransitRoute;
import kr.hankkitravel.trip.model.TripPlannerRows;
import kr.hankkitravel.trip.model.TripProblem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Live composition only. All network calls happen outside database transactions. */
@Service
public class TripPlannerService {
    static final String ATTRIBUTION="출처: ⓒ한국관광공사";
    private final TripPlaceScheduleService schedules; private final FamilyProfileApplicationService profiles;
    private final TourismRealtimeGateway tourism; private final TransitRouteFinder transit;
    private final RouteAwareAttractionRecommendationService routeAwareAttractions;
    private final int listSize; private final int detailLimit;
    private final int dayModerateMinutes; private final int dayHighMinutes;
    public TripPlannerService(TripPlaceScheduleService schedules,FamilyProfileApplicationService profiles,
            TourismRealtimeGateway tourism,TransitRouteFinder transit,RouteAwareAttractionRecommendationService routeAwareAttractions,
            @Value("${hankki.planner.list-size:15}")int listSize,@Value("${hankki.planner.detail-limit:6}")int detailLimit,
            @Value("${hankki.planner.attraction.day-burden.moderate-transit-minutes:90}")int dayModerateMinutes,
            @Value("${hankki.planner.attraction.day-burden.high-transit-minutes:180}")int dayHighMinutes){
        if(listSize<1||listSize>30||detailLimit<1||detailLimit>listSize||dayModerateMinutes<1||dayHighMinutes<=dayModerateMinutes)throw new IllegalArgumentException("플래너 호출 제한 설정이 올바르지 않습니다.");
        this.schedules=schedules;this.profiles=profiles;this.tourism=tourism;this.transit=transit;this.routeAwareAttractions=routeAwareAttractions;this.listSize=listSize;this.detailLimit=detailLimit;
        this.dayModerateMinutes=dayModerateMinutes;this.dayHighMinutes=dayHighMinutes;
    }
    public TripPlannerView.Recommendations recommendActivities(String guest,String trip,int day,TripPlannerView.SlotType slot){
        if(slot==null||!slot.activity()||slot==TripPlannerView.SlotType.DAY_FOCUS)throw TripProblem.invalid("PLACE_SLOT_TYPE_INVALID");
        return routeAwareAttractions.recommend(guest,trip,day,slot);
    }
    public TripPlannerView.Recommendations recommendFocus(String guest,String trip,int day){
        return recommend(guest,trip,day,TripPlannerView.SlotType.DAY_FOCUS,TourismContentType.ATTRACTION,null);
    }
    public TripPlannerView.Recommendations searchFocus(String guest,String trip,int day,String keyword){
        if(keyword==null||keyword.isBlank())throw TripProblem.invalid("FOCUS_SEARCH_KEYWORD_REQUIRED");
        return recommend(guest,trip,day,TripPlannerView.SlotType.DAY_FOCUS,TourismContentType.ATTRACTION,keyword.trim());
    }
    public TripPlannerView.Recommendations recommendStay(String guest,String trip,int day){
        var c=schedules.context(guest,trip,day); if(c.lastDay())throw TripProblem.invalid("STAY_NOT_REQUIRED");
        return recommend(guest,trip,day,TripPlannerView.SlotType.STAY,TourismContentType.LODGING,null);
    }
    private TripPlannerView.Recommendations recommend(String guest,String trip,int day,TripPlannerView.SlotType slot,TourismContentType type){
        return recommend(guest,trip,day,slot,type,null);
    }
    private TripPlannerView.Recommendations recommend(String guest,String trip,int day,TripPlannerView.SlotType slot,TourismContentType type,String keyword){
        long started=System.nanoTime();var refs=schedules.references(guest,trip,day);profiles.owned(guest,refs.context().profileId());
        Set<String> selected=new HashSet<>();refs.places().forEach(r->selected.add(r.getContentId()));
        var listed=new ArrayList<TourismPlace>();int listCalls=0;
        for(var region:regions(refs.context().regionKey())){var page=keyword==null?tourism.places(region,type,0,listSize):tourism.searchPlaces(region,type,keyword,0,listSize);listCalls++;listed.addAll(page.items());}
        var mealContext=mealAnchor(refs.meals());Coordinates meal=mealContext.coordinates();int details=0;var candidates=new ArrayList<TripPlannerView.Candidate>();
        for(var place:listed.stream().filter(p->type.code().equals(p.contentTypeId())).filter(p->inRegion(p,refs.context().regionKey()))
                .filter(p->p.coordinates()!=null&&!selected.contains(p.contentId())).sorted(Comparator.comparing(TourismPlace::contentId,TripPlannerService::stableIdCompare)).toList()){
            if(details>=detailLimit)break;details++;
            try{var live=tourism.place(place.contentId());if(!valid(live,type,refs.context().regionKey()))continue;
                var reasons=new ArrayList<String>();if(meal!=null&&live.coordinates()!=null)reasons.add("선택한 식사 장소 기준 직선거리 "+distanceLabel(meal,live.coordinates()));
                if(reasons.isEmpty())reasons.add("현재 TourAPI에서 지역과 장소 유형을 확인했습니다.");
                candidates.add(new TripPlannerView.Candidate(live.contentId(),live.contentType(),live.title(),areaLabel(live.address()),live.firstImage(),live.address(),
                        live.coordinates(),"TOUR_API_LIVE",List.copyOf(reasons),List.of("운영시간과 휴무일은 방문 전에 확인해 주세요."),ATTRIBUTION));
            }catch(IntegrationException ignored){ }
        }
        candidates.sort(Comparator.comparingDouble((TripPlannerView.Candidate c)->meal==null||c.coordinates()==null?Double.MAX_VALUE:distanceKm(meal,c.coordinates()))
                .thenComparing(TripPlannerView.Candidate::contentId,TripPlannerService::stableIdCompare));
        return new TripPlannerView.Recommendations(slot.name(),List.copyOf(candidates),new TripPlannerView.CallSummary(listCalls,details+mealContext.calls(),0,elapsed(started)),
                candidates.isEmpty()?"CURRENT_DATA_PARTIALLY_UNAVAILABLE":"CURRENT_DATA",ATTRIBUTION);
    }
    public TripPlannerView.Reference select(String guest,String trip,int day,TripPlannerView.SlotType slot,String contentId){
        var c=schedules.context(guest,trip,day);if(slot==null)throw TripProblem.invalid("PLACE_SLOT_TYPE_INVALID");
        if(slot==TripPlannerView.SlotType.STAY&&c.lastDay())throw TripProblem.invalid("STAY_NOT_REQUIRED");
        if(contentId==null||!contentId.matches("[0-9]{1,20}"))throw TripProblem.invalid("PLACE_ANCHOR_INVALID");
        var expected=slot.activity()?TourismContentType.ATTRACTION:TourismContentType.LODGING;var live=tourism.place(contentId);
        if(!contentId.equals(live.contentId())||!valid(live,expected,c.regionKey()))throw TripProblem.invalid("PLACE_ANCHOR_INVALID");
        return schedules.select(guest,trip,day,slot,new TripPlaceScheduleService.ValidatedPlace(contentId,live.contentType()));
    }
    public void clear(String guest,String trip,int day,TripPlannerView.SlotType slot){if(slot==null)throw TripProblem.invalid("PLACE_SLOT_TYPE_INVALID");schedules.clear(guest,trip,day,slot);}
    public TripPlannerView.Planner planner(String guest,String trip,int day){
        long started=System.nanoTime();var refs=schedules.references(guest,trip,day);var profile=profiles.owned(guest,refs.context().profileId());
        var all=new ArrayList<TripPlannerRows.Reference>();all.addAll(refs.meals());all.addAll(refs.places());all.sort(Comparator.comparingInt(r->order(r.getSlotType())));
        var items=new ArrayList<TripPlannerView.Item>();int detailCalls=0;Set<String> rendered=new HashSet<>();
        for(var ref:all){if(!rendered.add(ref.getContentId()))continue;detailCalls++;try{var live=tourism.place(ref.getContentId());
            boolean identity=ref.getContentId().equals(live.contentId())&&ref.getContentType().equals(live.contentType())&&inRegion(live,refs.context().regionKey());
            if(!identity){items.add(unavailable(ref));continue;}
            items.add(new TripPlannerView.Item(ref.getSlotType(),ref.getProvider(),ref.getContentId(),ref.getContentType(),live.title(),live.address(),
                    live.firstImage(),live.coordinates(),ATTRIBUTION,"CURRENT_DATA"));
        }catch(IntegrationException|IllegalArgumentException e){items.add(unavailable(ref));}}
        var legs=new ArrayList<TripPlannerView.Leg>();int transitCalls=0;
        for(int i=1;i<items.size();i++){
            var from=items.get(i-1);var to=items.get(i);if(!"PUBLIC_TRANSIT".equals(profile.transportMode())||from.coordinates()==null||to.coordinates()==null){legs.add(unavailableLeg(from,to));continue;}
            transitCalls++;try{var result=transit.findRoutes(from.coordinates(),to.coordinates());var route=result.routes().stream()
                    .min(Comparator.comparing(TransitRoute::totalTimeMinutes).thenComparingInt(TransitRoute::transferCount)
                            .thenComparingLong(TransitRoute::explicitWalkingDistanceMeters)).orElse(null);
                legs.add(route==null?unavailableLeg(from,to):new TripPlannerView.Leg(from.slotType(),to.slotType(),"PUBLIC_TRANSIT",
                        route.totalTimeMinutes(),route.transferCount(),route.explicitWalkingDistanceMeters(),route.unaccountedDistanceMeters(),"CURRENT_DATA"));
            }catch(IntegrationException e){legs.add(unavailableLeg(from,to));}
        }
        return new TripPlannerView.Planner(refs.context().tripPublicId(),day,refs.context().travelDate(),List.copyOf(items),List.copyOf(legs),
                new TripPlannerView.CallSummary(0,detailCalls,transitCalls,elapsed(started)),dayBurden(items,legs,profile.transportMode()));
    }
    private TripPlannerView.DayBurden dayBurden(List<TripPlannerView.Item> items,List<TripPlannerView.Leg> legs,String mode){
        if(!"PUBLIC_TRANSIT".equals(mode))return new TripPlannerView.DayBurden("NOT_EVALUATED","NOT_EVALUATED",items.size(),null,0,0,
                "자동차 이동시간은 제공하지 않아요. 거리와 공개 주차정보를 참고해 주세요.");
        var current=legs.stream().filter(leg->"CURRENT_DATA".equals(leg.dataAvailability())).toList();
        if(current.isEmpty())return new TripPlannerView.DayBurden("NOT_EVALUATED","NOT_EVALUATED",items.size(),null,0,0,null);
        BigDecimal minutes=current.stream().map(TripPlannerView.Leg::durationMinutes).reduce(BigDecimal.ZERO,BigDecimal::add);
        long walking=current.stream().mapToLong(TripPlannerView.Leg::explicitWalkingDistanceMeters).sum();int transfers=current.stream().mapToInt(TripPlannerView.Leg::transferCount).sum();
        String level=minutes.compareTo(BigDecimal.valueOf(dayHighMinutes))>0?"HIGH":minutes.compareTo(BigDecimal.valueOf(dayModerateMinutes))>0?"MODERATE":"LOW";
        String caution="HIGH".equals(level)?"오늘 이동이 많은 편이에요. 가까운 코스를 우선하면 부담을 줄일 수 있어요.":null;
        return new TripPlannerView.DayBurden(current.size()==legs.size()?"EVALUATED":"PARTIAL",level,items.size(),minutes,walking,transfers,caution);
    }
    private AnchorContext mealAnchor(List<TripPlannerRows.Reference> meals){int calls=0;for(var r:meals)try{calls++;var live=tourism.place(r.getContentId());if(live.coordinates()!=null)return new AnchorContext(live.coordinates(),calls);}catch(RuntimeException ignored){}return new AnchorContext(null,calls);}
    private TripPlannerView.Item unavailable(TripPlannerRows.Reference r){return new TripPlannerView.Item(r.getSlotType(),r.getProvider(),r.getContentId(),r.getContentType(),null,null,null,null,ATTRIBUTION,"CURRENT_DATA_UNAVAILABLE");}
    private TripPlannerView.Leg unavailableLeg(TripPlannerView.Item a,TripPlannerView.Item b){return new TripPlannerView.Leg(a.slotType(),b.slotType(),"PUBLIC_TRANSIT",null,0,0,0,"UNAVAILABLE");}
    private boolean valid(TourismLivePlace p,TourismContentType t,String region){return p!=null&&t.code().equals(p.contentType())&&inRegion(p,region);}
    private static boolean inRegion(TourismLivePlace p,String region){return regionMatches(p.regionCode(),p.districtCode(),region);}
    private static boolean inRegion(TourismPlace p,String region){return regionMatches(p.lDongRegnCd(),p.lDongSignguCd(),region);}
    private static boolean regionMatches(String r,String d,String region){if(r==null||r.isBlank())return false;return "JEJU".equals(region)?"50".equals(r):"47".equals(r)&&"130".equals(d);}
    private List<TourismRegion> regions(String region){return "JEJU".equals(region)?List.of(TourismRegion.JEJU_CITY,TourismRegion.SEOGWIPO):List.of(TourismRegion.GYEONGJU);}
    private int order(String s){return switch(s){case"DAY_FOCUS"->0;case"BREAKFAST"->1;case"MORNING_ACTIVITY"->2;case"LUNCH"->3;case"AFTERNOON_ACTIVITY"->4;case"DINNER"->5;case"STAY"->6;default->99;};}
    private String areaLabel(String address){if(address==null||address.isBlank())return null;var parts=address.trim().split("\\s+");return String.join(" ",java.util.Arrays.copyOf(parts,Math.min(parts.length,3)));}
    private static int stableIdCompare(String a,String b){try{return new java.math.BigInteger(a).compareTo(new java.math.BigInteger(b));}catch(Exception e){return a.compareTo(b);}}
    private static double distanceKm(Coordinates a,Coordinates b){double lat1=Math.toRadians(a.latitude().doubleValue()),lat2=Math.toRadians(b.latitude().doubleValue());double dlat=lat2-lat1,dlon=Math.toRadians(b.longitude().doubleValue()-a.longitude().doubleValue());double h=Math.sin(dlat/2)*Math.sin(dlat/2)+Math.cos(lat1)*Math.cos(lat2)*Math.sin(dlon/2)*Math.sin(dlon/2);return 6371*2*Math.atan2(Math.sqrt(h),Math.sqrt(1-h));}
    private String distanceLabel(Coordinates a,Coordinates b){return String.format(Locale.ROOT,"%.1fkm",distanceKm(a,b));}
    private long elapsed(long started){return Duration.ofNanos(System.nanoTime()-started).toMillis();}
    private record AnchorContext(Coordinates coordinates,int calls){}
}
