package kr.hankkitravel.trip.application;

import java.util.*;
import kr.hankkitravel.identity.application.GuestApplicationService;
import kr.hankkitravel.trip.model.*;
import kr.hankkitravel.trip.persistence.TripPlannerMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripPlaceScheduleService {
    private final TripPlannerMapper mapper; private final GuestApplicationService guests;
    public TripPlaceScheduleService(TripPlannerMapper mapper,GuestApplicationService guests){this.mapper=mapper;this.guests=guests;}
    @Transactional(readOnly=true)
    public DayContext context(String guest,String trip,int day){return convert(owned(guest,trip,day,false));}
    @Transactional(readOnly=true)
    public DayReferences references(String guest,String trip,int day){var c=owned(guest,trip,day,false);return refs(c);}
    @Transactional
    public TripPlannerView.Reference select(String guest,String trip,int day,TripPlannerView.SlotType type,ValidatedPlace place){
        var c=owned(guest,trip,day,true); requireStay(c,type); requireDessertMeal(c,type,place);
        var row=new TripPlannerRows.Reference(); row.setPublicId(UUID.randomUUID().toString());row.setTripDayId(c.getDayId());
        row.setSlotType(type.name());row.setContentId(place.contentId());row.setContentType(place.contentType());mapper.upsert(row);
        var saved=mapper.reference(c.getDayId(),type.name());
        return new TripPlannerView.Reference(saved.getPublicId(),type.name(),saved.getProvider(),saved.getContentId(),saved.getContentType());
    }
    @Transactional
    public void clear(String guest,String trip,int day,TripPlannerView.SlotType type){var c=owned(guest,trip,day,true);mapper.clear(c.getDayId(),type.name());}
    private DayReferences refs(TripPlannerRows.Context c){return new DayReferences(convert(c),List.copyOf(mapper.mealReferences(c.getDayId())),List.copyOf(mapper.placeReferences(c.getDayId())),List.copyOf(mapper.mealSlotTypes(c.getDayId())));}
    private TripPlannerRows.Context owned(String guest,String trip,int day,boolean lock){
        if(day<1||day>4)throw TripProblem.missing("TRIP_DAY_NOT_FOUND");
        try{if(!UUID.fromString(trip).toString().equalsIgnoreCase(trip))throw new IllegalArgumentException();}catch(RuntimeException e){throw TripProblem.missing("TRIP_NOT_FOUND");}
        long owner=guests.requirePublicId(guest).getId(); var c=lock?mapper.lockContext(owner,trip,day):mapper.context(owner,trip,day);
        if(c==null)throw TripProblem.missing("TRIP_DAY_NOT_FOUND");return c;
    }
    private void requireStay(TripPlannerRows.Context c,TripPlannerView.SlotType type){if(type==TripPlannerView.SlotType.STAY&&!c.getTravelDate().isBefore(c.getEndDate()))throw TripProblem.invalid("STAY_NOT_REQUIRED");}
    private void requireDessertMeal(TripPlannerRows.Context c,TripPlannerView.SlotType type,ValidatedPlace place){
        String mealType=dessertMealType(type);
        if(mealType==null)return;
        boolean selectedMeal=mapper.mealReferences(c.getDayId()).stream().anyMatch(value->
                (mealType.equals(value.getSlotType())||"ANY".equals(mealType))&&value.getContentId()!=null);
        if(!selectedMeal)throw TripProblem.invalid("POST_MEAL_DESSERT_MEAL_REQUIRED");
        boolean duplicate=mapper.mealReferences(c.getDayId()).stream().anyMatch(value->place.contentId().equals(value.getContentId()))
                ||mapper.placeReferences(c.getDayId()).stream().anyMatch(value->place.contentId().equals(value.getContentId()));
        if(duplicate)throw TripProblem.invalid("POST_MEAL_DESSERT_DUPLICATE");
    }
    private String dessertMealType(TripPlannerView.SlotType type){return switch(type){
        case POST_LUNCH_DESSERT->"LUNCH";case POST_DINNER_DESSERT->"DINNER";case POST_MEAL_DESSERT->"ANY";default->null;};}
    private DayContext convert(TripPlannerRows.Context c){return new DayContext(c.getTripId(),c.getDayId(),c.getTripPublicId(),c.getProfileId(),c.getRegionKey(),c.getStartDate(),c.getEndDate(),c.getDayNumber(),c.getTravelDate());}
    public record DayContext(long tripId,long dayId,String tripPublicId,long profileId,String regionKey,java.time.LocalDate startDate,
            java.time.LocalDate endDate,int dayNumber,java.time.LocalDate travelDate){public boolean lastDay(){return !travelDate.isBefore(endDate);}}
    public record DayReferences(DayContext context,List<TripPlannerRows.Reference> meals,List<TripPlannerRows.Reference> places,List<String> requiredMealTypes){
        public DayReferences(DayContext context,List<TripPlannerRows.Reference> meals,List<TripPlannerRows.Reference> places){
            this(context,meals,places,meals.stream().map(TripPlannerRows.Reference::getSlotType).toList());
        }
    }
    public record ValidatedPlace(String contentId,String contentType){}
}
