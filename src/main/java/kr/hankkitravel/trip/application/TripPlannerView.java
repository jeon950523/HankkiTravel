package kr.hankkitravel.trip.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import kr.hankkitravel.shared.geo.Coordinates;

public final class TripPlannerView {
    private TripPlannerView() { }
    public enum SlotType { DAY_FOCUS, MORNING_ACTIVITY, AFTERNOON_ACTIVITY, STAY;
        public boolean activity(){return this!=STAY;}
    }
    public record Reference(String publicId,String slotType,String provider,String contentId,String contentType) { }
    public record Candidate(String contentId,String contentType,String title,String areaLabel,String imageUrl,String address,
            Coordinates coordinates,String informationEvidence,List<String> fitReasons,List<String> checkBeforeVisit,
            String sourceAttribution) { }
    public record Recommendations(String slotType,List<Candidate> candidates,CallSummary callSummary,String dataAvailability,
            String sourceAttribution) { }
    public record CallSummary(int tourListCalls,int tourDetailCalls,int transitCalls,long elapsedMillis) { }
    public record Planner(String tripPublicId,int dayNumber,LocalDate travelDate,List<Item> items,List<Leg> legs,CallSummary callSummary) { }
    public record Item(String slotType,String provider,String contentId,String contentType,String title,String address,
            String imageUrl,Coordinates coordinates,String sourceAttribution,String dataAvailability) { }
    public record Leg(String fromSlotType,String toSlotType,String mode,BigDecimal durationMinutes,int transferCount,
            long explicitWalkingDistanceMeters,long unaccountedDistanceMeters,String dataAvailability) { }
}
