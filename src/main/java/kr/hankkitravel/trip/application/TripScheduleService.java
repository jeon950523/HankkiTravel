package kr.hankkitravel.trip.application;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import kr.hankkitravel.identity.application.GuestApplicationService;
import kr.hankkitravel.profile.application.FamilyProfileApplicationService;
import kr.hankkitravel.trip.model.*;
import kr.hankkitravel.trip.persistence.TripPlannerMapper;
import kr.hankkitravel.trip.persistence.TripScheduleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns only schedule/reference persistence. Network calls belong outside this service. */
@Service
public class TripScheduleService {
    private final TripScheduleMapper mapper;
    private final TripPlannerMapper plannerMapper;
    private final GuestApplicationService guests;
    private final FamilyProfileApplicationService profiles;
    public TripScheduleService(TripScheduleMapper mapper, TripPlannerMapper plannerMapper, GuestApplicationService guests, FamilyProfileApplicationService profiles) {
        this.mapper=mapper; this.plannerMapper=plannerMapper; this.guests=guests; this.profiles=profiles;
    }
    @Transactional
    public TripView create(String guest, TripPlan plan) {
        long owner=guestId(guest);
        profiles.owned(guest, plan.profileId());
        var trip=new TripRows.Schedule();
        trip.setPublicId(UUID.randomUUID().toString()); trip.setGuestId(owner); trip.setProfileId(plan.profileId());
        trip.setRegionKey(plan.regionKey()); trip.setStartDate(plan.startDate()); trip.setEndDate(plan.endDate());
        mapper.insertTrip(trip);
        for (var config:plan.days()) {
            var day=new TripRows.Day(); day.setTripId(trip.getId()); day.setDayNumber(config.dayNumber());
            day.setTravelDate(plan.startDate().plusDays(config.dayNumber()-1)); mapper.insertDay(day);
            for (var meal:config.mealTypes()) {
                var slot=new TripRows.Slot(); slot.setPublicId(UUID.randomUUID().toString());
                slot.setTripDayId(day.getId()); slot.setMealType(meal.name()); mapper.insertSlot(slot);
            }
        }
        return view(trip);
    }
    @Transactional(readOnly=true)
    public List<TripView.Summary> list(String guest) {
        return mapper.list(guestId(guest)).stream().map(t -> new TripView.Summary(t.getPublicId(),t.getRegionKey(),
                t.getStartDate(),t.getEndDate(),duration(t),t.getProfileId(),t.getMealSlotCount(),t.getSelectedAnchorCount(),t.getCreatedAt())).toList();
    }
    @Transactional(readOnly=true)
    public TripView detail(String guest,String trip) { return view(owned(guest,trip,false)); }
    @Transactional
    public void delete(String guest,String trip) {
        var owned=owned(guest,trip,true); mapper.deleteTrip(owned.getId(),owned.getGuestId());
    }
    @Transactional(readOnly=true)
    public SlotContext context(String guest,String trip,String slot) {
        var owned=owned(guest,trip,false); var found=slot(owned.getId(),slot);
        var day=mapper.days(owned.getId()).stream().filter(d -> d.getId().equals(found.getTripDayId())).findFirst().orElseThrow();
        return new SlotContext(owned.getProfileId(),owned.getRegionKey(),day.getTravelDate(),found.getMealType(),day.getDayNumber());
    }
    @Transactional
    public TripView.Anchor select(String guest,String trip,String slot,ValidatedAnchor anchor) {
        var owned=owned(guest,trip,true); var found=slot(owned.getId(),slot);
        mapper.upsertAnchor(found.getId(),anchor.contentId(),anchor.contentType());
        return new TripView.Anchor("KTO",anchor.contentId(),anchor.contentType());
    }
    @Transactional
    public void clear(String guest,String trip,String slot) {
        var owned=owned(guest,trip,true); var found=slot(owned.getId(),slot);
        if ("LUNCH".equals(found.getMealType())) plannerMapper.clear(found.getTripDayId(),TripPlannerView.SlotType.POST_LUNCH_DESSERT.name());
        if ("DINNER".equals(found.getMealType())) plannerMapper.clear(found.getTripDayId(),TripPlannerView.SlotType.POST_DINNER_DESSERT.name());
        mapper.deleteAnchor(found.getId());
    }
    private TripRows.Schedule owned(String guest,String trip,boolean lock) {
        uuid(trip,"TRIP_NOT_FOUND"); long owner=guestId(guest);
        var found=lock?mapper.lockOwned(owner,trip):mapper.findOwned(owner,trip);
        if(found==null) throw TripProblem.missing("TRIP_NOT_FOUND"); return found;
    }
    private TripRows.Slot slot(long trip,String slot) {
        uuid(slot,"MEAL_SLOT_NOT_FOUND"); var found=mapper.slot(trip,slot);
        if(found==null) throw TripProblem.missing("MEAL_SLOT_NOT_FOUND"); return found;
    }
    private long guestId(String guest) { return guests.requirePublicId(guest).getId(); }
    private static void uuid(String value,String error) {
        try { if(!UUID.fromString(value).toString().equalsIgnoreCase(value)) throw new IllegalArgumentException(); }
        catch(RuntimeException e) { throw TripProblem.missing(error); }
    }
    private TripView view(TripRows.Schedule trip) {
        var slots=mapper.slots(trip.getId());
        var days=mapper.days(trip.getId()).stream().map(d -> new TripView.Day(d.getDayNumber(),d.getTravelDate(),
                slots.stream().filter(s -> s.getTripDayId().equals(d.getId())).map(s -> new TripView.Slot(s.getPublicId(),s.getMealType(),
                        s.getContentId()==null?null:new TripView.Anchor(s.getProvider(),s.getContentId(),s.getContentType()))).toList())).toList();
        return new TripView(trip.getPublicId(),trip.getProfileId(),trip.getRegionKey(),trip.getStartDate(),trip.getEndDate(),duration(trip),days);
    }
    private int duration(TripRows.Schedule trip) { return (int)ChronoUnit.DAYS.between(trip.getStartDate(),trip.getEndDate())+1; }
    public record SlotContext(long profileId,String regionKey,java.time.LocalDate travelDate,String mealType,int dayNumber) { }
    public record ValidatedAnchor(String contentId,String contentType) { }
}
