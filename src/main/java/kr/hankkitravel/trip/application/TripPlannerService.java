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
    private final List<Integer> stayRadiusLevels; private final int stayMinimumCandidates; private final int stayFarDistanceMeters;
    public TripPlannerService(TripPlaceScheduleService schedules,FamilyProfileApplicationService profiles,
            TourismRealtimeGateway tourism,TransitRouteFinder transit,RouteAwareAttractionRecommendationService routeAwareAttractions,
            @Value("${hankki.planner.list-size:15}")int listSize,@Value("${hankki.planner.detail-limit:6}")int detailLimit,
            @Value("${hankki.planner.stay.radius-levels-meters:3000,8000,15000}")String stayRadiusLevels,
            @Value("${hankki.planner.stay.minimum-candidates:3}")int stayMinimumCandidates,
            @Value("${hankki.planner.stay.far-distance-meters:20000}")int stayFarDistanceMeters,
            @Value("${hankki.planner.attraction.day-burden.moderate-transit-minutes:90}")int dayModerateMinutes,
            @Value("${hankki.planner.attraction.day-burden.high-transit-minutes:180}")int dayHighMinutes){
        if(listSize<1||listSize>30||detailLimit<1||detailLimit>listSize||stayMinimumCandidates<1||stayFarDistanceMeters<1
                ||dayModerateMinutes<1||dayHighMinutes<=dayModerateMinutes)throw new IllegalArgumentException("플래너 호출 제한 설정이 올바르지 않습니다.");
        this.schedules=schedules;this.profiles=profiles;this.tourism=tourism;this.transit=transit;this.routeAwareAttractions=routeAwareAttractions;this.listSize=listSize;this.detailLimit=detailLimit;
        this.stayRadiusLevels=parseRadii(stayRadiusLevels);this.stayMinimumCandidates=stayMinimumCandidates;this.stayFarDistanceMeters=stayFarDistanceMeters;
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
        return stayCandidates(guest,trip,day,null,Set.of());
    }
    public TripPlannerView.Recommendations otherPlaces(String guest,String trip,int day,TripPlannerView.SlotType slot,Set<String> excluded){
        if(slot==null||slot==TripPlannerView.SlotType.DAY_FOCUS||(!slot.activity()&&slot!=TripPlannerView.SlotType.STAY))
            throw TripProblem.invalid("PLACE_SLOT_TYPE_INVALID");
        if(slot==TripPlannerView.SlotType.STAY)return stayCandidates(guest,trip,day,null,safeIds(excluded));
        return recommend(guest,trip,day,slot,TourismContentType.ATTRACTION,null,safeIds(excluded));
    }
    public TripPlannerView.Recommendations searchPlaces(String guest,String trip,int day,TripPlannerView.SlotType slot,String keyword){
        if(keyword==null||keyword.isBlank())throw TripProblem.invalid("PLACE_SEARCH_KEYWORD_REQUIRED");
        if(slot==null||slot==TripPlannerView.SlotType.DAY_FOCUS||(!slot.activity()&&slot!=TripPlannerView.SlotType.STAY))
            throw TripProblem.invalid("PLACE_SLOT_TYPE_INVALID");
        if(slot==TripPlannerView.SlotType.STAY)return stayCandidates(guest,trip,day,keyword.trim(),Set.of());
        return recommend(guest,trip,day,slot,TourismContentType.ATTRACTION,keyword.trim(),Set.of());
    }
    public TripPlannerView.Recommendations otherRestaurants(String guest,String trip,int day,Set<String> excluded){
        return restaurantCandidates(guest,trip,day,null,safeIds(excluded));
    }
    public TripPlannerView.Recommendations searchRestaurants(String guest,String trip,int day,String keyword){
        if(keyword==null||keyword.isBlank())throw TripProblem.invalid("RESTAURANT_SEARCH_KEYWORD_REQUIRED");
        return restaurantCandidates(guest,trip,day,keyword.trim(),Set.of());
    }
    public TripPlannerView.Recommendations recommendDesserts(String guest,String trip,int day,String mealType){
        TripPlannerView.SlotType dessertSlot=dessertSlot(mealType);
        long started=System.nanoTime();var refs=schedules.references(guest,trip,day);
        var profile=profiles.owned(guest,refs.context().profileId());
        var meal=refs.meals().stream().filter(value->mealType.equals(value.getSlotType()))
                .findFirst().orElseThrow(()->TripProblem.invalid("POST_MEAL_DESSERT_MEAL_REQUIRED"));
        var mealLive=tourism.decisionData(meal.getContentId());
        if(mealLive.detail().contentId()==null||mealLive.detail().contentId().isBlank())throw TripProblem.invalid("POST_MEAL_DESSERT_MEAL_REQUIRED");
        Coordinates center=tourism.place(meal.getContentId()).coordinates();
        if(center==null)throw TripProblem.invalid("POST_MEAL_DESSERT_LOCATION_REQUIRED");
        var listed=new LinkedHashMap<String,TourismPlace>();int listCalls=0;
        try{tourism.restaurantsNear(center,2000,0,listSize).items().forEach(value->listed.putIfAbsent(value.contentId(),value));listCalls++;}
        catch(IntegrationException ignored){listCalls++;}
        if(listed.isEmpty())for(var region:regions(refs.context().regionKey())){try{tourism.places(region,TourismContentType.RESTAURANT,0,listSize).items()
                .forEach(value->listed.putIfAbsent(value.contentId(),value));}catch(IntegrationException ignored){}listCalls++;}
        int details=1;var candidates=new ArrayList<TripPlannerView.Candidate>();
        for(var place:listed.values().stream().filter(value->TourismContentType.RESTAURANT.code().equals(value.contentTypeId()))
                .filter(value->value.coordinates()!=null).sorted(Comparator.comparingDouble((TourismPlace value)->distanceKm(center,value.coordinates()))
                        .thenComparing(TourismPlace::contentId,TripPlannerService::stableIdCompare)).toList()){
            if(details>detailLimit)break;details++;
            try{var live=tourism.decisionData(place.contentId());
                if(!"CAFE_DESSERT".equals(live.restaurant().classification())||!passesStrictFoodRestrictions(profile,live))continue;
                String distance=distanceLabel(center,place.coordinates());
                candidates.add(new TripPlannerView.Candidate(place.contentId(),live.detail().contentType(),live.detail().title(),areaLabel(live.detail().address()),
                        live.detail().firstImage(),live.detail().address(),place.coordinates(),"TOUR_API_LIVE",List.of("선택한 식사 장소에서 직선거리 "+distance+"예요."),
                        List.of("알레르기 재료·교차조리·실제 메뉴는 방문 전 전화로 확인해 주세요.","영업시간과 휴무일은 방문 전에 확인해 주세요."),ATTRIBUTION));
            }catch(IntegrationException ignored) { }
        }
        String availability=candidates.isEmpty()?"CURRENT_DATA_PARTIALLY_UNAVAILABLE":"CURRENT_DATA";
        return new TripPlannerView.Recommendations(dessertSlot.name(),List.copyOf(candidates),
                new TripPlannerView.CallSummary(listCalls,details,0,elapsed(started)),availability,ATTRIBUTION);
    }
    private TripPlannerView.Recommendations recommend(String guest,String trip,int day,TripPlannerView.SlotType slot,TourismContentType type){
        return recommend(guest,trip,day,slot,type,null);
    }
    private TripPlannerView.Recommendations recommend(String guest,String trip,int day,TripPlannerView.SlotType slot,TourismContentType type,String keyword){
        return recommend(guest,trip,day,slot,type,keyword,Set.of());
    }
    private TripPlannerView.Recommendations recommend(String guest,String trip,int day,TripPlannerView.SlotType slot,TourismContentType type,String keyword,Set<String> excluded){
        long started=System.nanoTime();var refs=schedules.references(guest,trip,day);profiles.owned(guest,refs.context().profileId());
        Set<String> selected=new HashSet<>();refs.places().forEach(r->selected.add(r.getContentId()));
        selected.addAll(excluded);
        var listed=new ArrayList<TourismPlace>();int listCalls=0;
        for(var region:regions(refs.context().regionKey())){try{var page=keyword==null?tourism.places(region,type,0,listSize):tourism.searchPlaces(region,type,keyword,0,listSize);listed.addAll(page.items());}
            catch(IntegrationException ignored){}listCalls++;}
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

    private TripPlannerView.Recommendations restaurantCandidates(String guest,String trip,int day,String keyword,Set<String> excluded){
        long started=System.nanoTime();var refs=schedules.references(guest,trip,day);var profile=profiles.owned(guest,refs.context().profileId());
        var focus=anchorBySlot(refs,"DAY_FOCUS");Coordinates center=focus.coordinates();
        var listed=new LinkedHashMap<String,TourismPlace>();int listCalls=0;
        if(keyword==null&&center!=null){try{tourism.restaurantsNear(center,5000,0,listSize).items().forEach(value->listed.putIfAbsent(value.contentId(),value));}
            catch(IntegrationException ignored){}listCalls++;}
        if(keyword!=null||listed.isEmpty())for(var region:regions(refs.context().regionKey())){try{var items=keyword==null?tourism.places(region,TourismContentType.RESTAURANT,0,listSize).items()
                :tourism.searchPlaces(region,TourismContentType.RESTAURANT,keyword,0,listSize).items();items.forEach(value->listed.putIfAbsent(value.contentId(),value));}
            catch(IntegrationException ignored){}listCalls++;}
        Set<String> blocked=new HashSet<>(excluded);refs.meals().forEach(value->blocked.add(value.getContentId()));
        var candidates=new ArrayList<TripPlannerView.Candidate>();int details=0;
        for(var place:listed.values().stream().filter(value->TourismContentType.RESTAURANT.code().equals(value.contentTypeId()))
                .filter(value->value.contentId()!=null&&!blocked.contains(value.contentId())).filter(value->inRegion(value,refs.context().regionKey()))
                .sorted(Comparator.comparingDouble(value->center==null||value.coordinates()==null?Double.MAX_VALUE:distanceKm(center,value.coordinates())))
                .toList()){
            if(details>=detailLimit)break;details++;
            try{var live=tourism.decisionData(place.contentId());
                if(!List.of("MEAL","MIXED").contains(live.restaurant().classification())||!passesStrictFoodRestrictions(profile,live))continue;
                Coordinates coordinates=place.coordinates();long distance=distanceMeters(center,coordinates);
                var reasons=new ArrayList<String>();if(distance>=0)reasons.add("오늘의 중심 장소에서 직선거리 "+distanceLabel(center,coordinates)+"예요.");
                else reasons.add("현재 TourAPI에서 식당 유형과 지역을 확인했습니다.");
                candidates.add(candidate(live.detail().contentId(),live.detail().contentType(),live.detail().title(),live.detail().firstImage(),
                        live.detail().address(),coordinates,reasons,List.of("메뉴·알레르기 재료·교차조리는 방문 전 확인해 주세요."),distance));
            }catch(IntegrationException ignored){}
        }
        return new TripPlannerView.Recommendations("RESTAURANT",List.copyOf(candidates),
                new TripPlannerView.CallSummary(listCalls,details+focus.calls(),0,elapsed(started)),
                candidates.isEmpty()?"CURRENT_DATA_PARTIALLY_UNAVAILABLE":"CURRENT_DATA",ATTRIBUTION,List.of(),
                center==null?"REGION":"DAY_FOCUS");
    }

    private TripPlannerView.Recommendations stayCandidates(String guest,String trip,int day,String keyword,Set<String> excluded){
        long started=System.nanoTime();var refs=schedules.references(guest,trip,day);profiles.owned(guest,refs.context().profileId());
        if(refs.context().lastDay())throw TripProblem.invalid("STAY_NOT_REQUIRED");
        var anchor=lastRouteAnchor(refs);Coordinates center=anchor.coordinates();
        var nextFocus=anchorBySlot(schedules.references(guest,trip,day+1),"DAY_FOCUS");
        var unique=new LinkedHashMap<String,TourismPlace>();int listCalls=0;int radiusUsed=0;
        if(keyword!=null){for(var region:regions(refs.context().regionKey())){try{var page=tourism.searchPlaces(region,TourismContentType.LODGING,keyword,0,listSize);page.items().forEach(value->unique.putIfAbsent(value.contentId(),value));}
                catch(IntegrationException ignored){}listCalls++;}}
        else if(center!=null){for(int radius:stayRadiusLevels){try{var page=tourism.placesNear(center,TourismContentType.LODGING,radius,0,listSize);listCalls++;radiusUsed=radius;page.items().forEach(value->unique.putIfAbsent(value.contentId(),value));if(unique.size()>=stayMinimumCandidates)break;}
                catch(IntegrationException ignored){listCalls++;break;}}
            if(unique.size()<stayMinimumCandidates){radiusUsed=0;for(var region:regions(refs.context().regionKey())){try{var page=tourism.places(region,TourismContentType.LODGING,0,listSize);page.items().forEach(value->unique.putIfAbsent(value.contentId(),value));}
                    catch(IntegrationException ignored){}listCalls++;}}}
        else for(var region:regions(refs.context().regionKey())){try{var page=tourism.places(region,TourismContentType.LODGING,0,listSize);page.items().forEach(value->unique.putIfAbsent(value.contentId(),value));}
            catch(IntegrationException ignored){}listCalls++;}
        Set<String> blocked=new HashSet<>(excluded);refs.places().forEach(value->blocked.add(value.getContentId()));
        var hydrated=new ArrayList<StayCandidate>();int details=0;
        for(var place:unique.values()){
            if(details>=detailLimit||place.contentId()==null||blocked.contains(place.contentId())||!inRegion(place,refs.context().regionKey()))continue;
            details++;try{var live=tourism.place(place.contentId());if(!valid(live,TourismContentType.LODGING,refs.context().regionKey()))continue;
                long current=distanceMeters(center,live.coordinates());long next=distanceMeters(nextFocus.coordinates(),live.coordinates());
                hydrated.add(new StayCandidate(live,current,next));
            }catch(IntegrationException ignored){}
        }
        hydrated.sort(Comparator.comparingLong(StayCandidate::score).thenComparing(value->value.place().contentId(),TripPlannerService::stableIdCompare));
        var candidates=hydrated.stream().map(value->{var reasons=new ArrayList<String>();
            if(value.currentDistance()>=0)reasons.add(anchor.slotType()+"에서 직선거리 "+distanceLabelMeters(value.currentDistance())+"예요.");
            if(value.nextDistance()>=0)reasons.add("다음 날 중심 장소까지 직선거리 "+distanceLabelMeters(value.nextDistance())+"예요.");
            if(reasons.isEmpty())reasons.add("현재 TourAPI에서 숙박 유형과 지역을 확인했습니다.");
            var cautions=new ArrayList<String>();if(value.currentDistance()>stayFarDistanceMeters)cautions.add("현재 일정에서 거리가 먼 편이에요.");
            cautions.add("실시간 객실·가격·예약 가능 여부는 숙소에서 확인해 주세요.");
            return candidate(value.place().contentId(),value.place().contentType(),value.place().title(),value.place().firstImage(),
                    value.place().address(),value.place().coordinates(),reasons,cautions,value.currentDistance());}).toList();
        String context=(center==null?"REGION":anchor.slotType())+";radius="+radiusUsed+";candidateCount="+candidates.size();
        return new TripPlannerView.Recommendations(TripPlannerView.SlotType.STAY.name(),candidates,
                new TripPlannerView.CallSummary(listCalls,details+anchor.calls()+nextFocus.calls(),0,elapsed(started)),
                candidates.isEmpty()?"CURRENT_DATA_PARTIALLY_UNAVAILABLE":"CURRENT_DATA",ATTRIBUTION,List.of(),context);
    }
    public TripPlannerView.Reference select(String guest,String trip,int day,TripPlannerView.SlotType slot,String contentId){
        var c=schedules.context(guest,trip,day);if(slot==null)throw TripProblem.invalid("PLACE_SLOT_TYPE_INVALID");
        if(slot==TripPlannerView.SlotType.STAY&&c.lastDay())throw TripProblem.invalid("STAY_NOT_REQUIRED");
        if(contentId==null||!contentId.matches("[0-9]{1,20}"))throw TripProblem.invalid("PLACE_ANCHOR_INVALID");
        if(isDessert(slot)){
            var live=tourism.decisionData(contentId);
            if(!contentId.equals(live.detail().contentId())||!TourismContentType.RESTAURANT.code().equals(live.detail().contentType())
                    ||!inRegion(live.detail().regionCode(),live.detail().districtCode(),c.regionKey())||!"CAFE_DESSERT".equals(live.restaurant().classification())
                    ||!passesStrictFoodRestrictions(profiles.owned(guest,c.profileId()),live))
                throw TripProblem.invalid("POST_MEAL_DESSERT_INVALID");
            return schedules.select(guest,trip,day,slot,new TripPlaceScheduleService.ValidatedPlace(contentId,live.detail().contentType()));
        }
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
            var from=items.get(i-1);var to=items.get(i);
            if(from.coordinates()==null||to.coordinates()==null){legs.add(unavailableLeg(from,to,profile.transportMode()));continue;}
            long straightDistance=distanceMeters(from.coordinates(),to.coordinates());
            if("CAR".equals(profile.transportMode())){legs.add(new TripPlannerView.Leg(from.slotType(),to.slotType(),"CAR",null,0,0,0,
                    straightDistance,"STRAIGHT_LINE_REFERENCE",null));continue;}
            if(!"PUBLIC_TRANSIT".equals(profile.transportMode())){legs.add(unavailableLeg(from,to,profile.transportMode()));continue;}
            transitCalls++;try{var result=transit.findRoutes(from.coordinates(),to.coordinates());var route=result.routes().stream()
                    .min(Comparator.comparing(TransitRoute::totalTimeMinutes).thenComparingInt(TransitRoute::transferCount)
                            .thenComparingLong(TransitRoute::explicitWalkingDistanceMeters)).orElse(null);
                legs.add(route==null?unavailableLeg(from,to,"PUBLIC_TRANSIT"):new TripPlannerView.Leg(from.slotType(),to.slotType(),"PUBLIC_TRANSIT",
                        route.totalTimeMinutes(),route.transferCount(),route.explicitWalkingDistanceMeters(),route.unaccountedDistanceMeters(),
                        straightDistance,"CURRENT_DATA",route.kakaoMapLandingUrl()));
            }catch(IntegrationException e){legs.add(unavailableLeg(from,to,"PUBLIC_TRANSIT"));}
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
    private TripPlannerView.Leg unavailableLeg(TripPlannerView.Item a,TripPlannerView.Item b,String mode){return new TripPlannerView.Leg(a.slotType(),b.slotType(),mode,null,0,0,0,null,"UNAVAILABLE",null);}
    private boolean valid(TourismLivePlace p,TourismContentType t,String region){return p!=null&&t.code().equals(p.contentType())&&inRegion(p,region);}
    private static boolean inRegion(TourismLivePlace p,String region){return regionMatches(p.regionCode(),p.districtCode(),region);}
    private static boolean inRegion(TourismPlace p,String region){return regionMatches(p.lDongRegnCd(),p.lDongSignguCd(),region);}
    private static boolean regionMatches(String r,String d,String region){if(r==null||r.isBlank())return false;return "JEJU".equals(region)?"50".equals(r):"47".equals(r)&&"130".equals(d);}
    private static boolean inRegion(String r,String d,String region){return regionMatches(r,d,region);}
    private List<TourismRegion> regions(String region){return "JEJU".equals(region)?List.of(TourismRegion.JEJU_CITY,TourismRegion.SEOGWIPO):List.of(TourismRegion.GYEONGJU);}
    private int order(String s){return switch(s){case"DAY_FOCUS"->0;case"BREAKFAST"->1;case"MORNING_ACTIVITY"->2;case"LUNCH"->3;case"POST_LUNCH_DESSERT"->4;case"AFTERNOON_ACTIVITY"->5;case"DINNER"->6;case"POST_DINNER_DESSERT"->7;case"POST_MEAL_DESSERT"->8;case"STAY"->9;default->99;};}
    private String areaLabel(String address){if(address==null||address.isBlank())return null;var parts=address.trim().split("\\s+");return String.join(" ",java.util.Arrays.copyOf(parts,Math.min(parts.length,3)));}
    private static int stableIdCompare(String a,String b){try{return new java.math.BigInteger(a).compareTo(new java.math.BigInteger(b));}catch(Exception e){return a.compareTo(b);}}
    private static double distanceKm(Coordinates a,Coordinates b){double lat1=Math.toRadians(a.latitude().doubleValue()),lat2=Math.toRadians(b.latitude().doubleValue());double dlat=lat2-lat1,dlon=Math.toRadians(b.longitude().doubleValue()-a.longitude().doubleValue());double h=Math.sin(dlat/2)*Math.sin(dlat/2)+Math.cos(lat1)*Math.cos(lat2)*Math.sin(dlon/2)*Math.sin(dlon/2);return 6371*2*Math.atan2(Math.sqrt(h),Math.sqrt(1-h));}
    private String distanceLabel(Coordinates a,Coordinates b){return String.format(Locale.ROOT,"%.1fkm",distanceKm(a,b));}
    private static long distanceMeters(Coordinates a,Coordinates b){return a==null||b==null?-1:Math.round(distanceKm(a,b)*1000);}
    private String distanceLabelMeters(long meters){return meters<1000?meters+"m":String.format(Locale.ROOT,"%.1fkm",meters/1000.0);}
    private TripPlannerView.Candidate candidate(String contentId,String contentType,String title,String image,String address,
            Coordinates coordinates,List<String> reasons,List<String> cautions,long distanceMeters){
        return new TripPlannerView.Candidate(contentId,contentType,title,areaLabel(address),image,address,coordinates,"TOUR_API_LIVE",
                reasons,cautions,ATTRIBUTION,null,0,0,null,distanceMeters<0?null:distanceMeters,null,List.of(),reasons,cautions);
    }
    private AnchorContext anchorBySlot(TripPlaceScheduleService.DayReferences refs,String slot){
        var reference=refs.places().stream().filter(value->slot.equals(value.getSlotType())).findFirst().orElse(null);
        if(reference==null)return new AnchorContext(null,0);
        try{return new AnchorContext(tourism.place(reference.getContentId()).coordinates(),1);}catch(RuntimeException ignored){return new AnchorContext(null,1);}
    }
    private RouteAnchor lastRouteAnchor(TripPlaceScheduleService.DayReferences refs){
        var all=new ArrayList<TripPlannerRows.Reference>();all.addAll(refs.meals());all.addAll(refs.places().stream().filter(value->!"STAY".equals(value.getSlotType())).toList());
        all.sort(Comparator.comparingInt((TripPlannerRows.Reference value)->order(value.getSlotType())).reversed());int calls=0;
        for(var reference:all){calls++;try{var coordinates=tourism.place(reference.getContentId()).coordinates();if(coordinates!=null)return new RouteAnchor(coordinates,calls,reference.getSlotType());}catch(RuntimeException ignored){}}
        return new RouteAnchor(null,calls,"REGION");
    }
    private Set<String> safeIds(Set<String> values){if(values==null)return Set.of();return values.stream().filter(Objects::nonNull).filter(value->value.matches("[0-9]{1,20}")).collect(java.util.stream.Collectors.toUnmodifiableSet());}
    private List<Integer> parseRadii(String value){
        try{var parsed=Arrays.stream(value.split(",")).map(String::trim).map(Integer::parseInt).toList();
            if(parsed.isEmpty()||parsed.stream().anyMatch(radius->radius<100||radius>20000)||!parsed.equals(parsed.stream().sorted().toList()))throw new IllegalArgumentException();return parsed;
        }catch(RuntimeException e){throw new IllegalArgumentException("숙소 반경 단계 설정이 올바르지 않습니다.");}
    }
    private boolean passesStrictFoodRestrictions(kr.hankkitravel.profile.application.FamilyProfileSnapshot profile,TourismRealtimeGateway.DecisionData live){
        String target=(live.detail().title()+" "+live.detail().address()+" "+live.restaurant().menuCandidates().stream()
                .map(TourismMenuCandidate::rawMenuName).reduce("",(left,right)->left+" "+right)).toLowerCase(Locale.ROOT);
        return profile.members().stream().flatMap(member->java.util.stream.Stream.concat(member.allergenRestrictions().stream(),member.avoidedFoods().stream()))
                .filter(value->value!=null&&!value.isBlank()).noneMatch(value->target.contains(value.toLowerCase(Locale.ROOT)));
    }
    private TripPlannerView.SlotType dessertSlot(String mealType){if(mealType==null)throw TripProblem.invalid("POST_MEAL_DESSERT_MEAL_REQUIRED");return switch(mealType){
        case"LUNCH"->TripPlannerView.SlotType.POST_LUNCH_DESSERT;
        case"DINNER"->TripPlannerView.SlotType.POST_DINNER_DESSERT;
        default->throw TripProblem.invalid("POST_MEAL_DESSERT_MEAL_REQUIRED");};}
    private boolean isDessert(TripPlannerView.SlotType slot){return slot==TripPlannerView.SlotType.POST_MEAL_DESSERT
            ||slot==TripPlannerView.SlotType.POST_LUNCH_DESSERT||slot==TripPlannerView.SlotType.POST_DINNER_DESSERT;}
    private long elapsed(long started){return Duration.ofNanos(System.nanoTime()-started).toMillis();}
    private record AnchorContext(Coordinates coordinates,int calls){}
    private record RouteAnchor(Coordinates coordinates,int calls,String slotType){}
    private record StayCandidate(TourismLivePlace place,long currentDistance,long nextDistance){
        long score(){return (currentDistance<0?Long.MAX_VALUE/4:currentDistance)+(nextDistance<0?0:nextDistance);}
    }
}
