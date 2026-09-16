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
            String sourceAttribution,String perspective,int overallScore,int evidenceCoverage,RouteBurden routeBurden,
            Long distanceMeters,TransitSummary transitSummary,List<String> familyMobilityEvidence,
            List<String> reasons,List<String> cautions) {
        public Candidate {
            fitReasons=List.copyOf(fitReasons);checkBeforeVisit=List.copyOf(checkBeforeVisit);
            familyMobilityEvidence=List.copyOf(familyMobilityEvidence);reasons=List.copyOf(reasons);cautions=List.copyOf(cautions);
        }
        public Candidate(String contentId,String contentType,String title,String areaLabel,String imageUrl,String address,
                Coordinates coordinates,String informationEvidence,List<String> fitReasons,List<String> checkBeforeVisit,
                String sourceAttribution){this(contentId,contentType,title,areaLabel,imageUrl,address,coordinates,informationEvidence,
                    fitReasons,checkBeforeVisit,sourceAttribution,null,0,0,null,null,null,List.of(),fitReasons,checkBeforeVisit);}
    }
    public record Recommendations(String slotType,List<Candidate> candidates,CallSummary callSummary,String dataAvailability,
            String sourceAttribution,List<PerspectiveResult> perspectives,String movementContext) {
        public Recommendations { candidates=List.copyOf(candidates);perspectives=List.copyOf(perspectives); }
        public Recommendations(String slotType,List<Candidate> candidates,CallSummary callSummary,String dataAvailability,
                String sourceAttribution){this(slotType,candidates,callSummary,dataAvailability,sourceAttribution,List.of(),null);}
    }
    public record PerspectiveResult(String perspective,String status,String message,List<Candidate> candidates) {
        public PerspectiveResult { candidates=List.copyOf(candidates); }
    }
    public record RouteBurden(String state,String level,int score) { }
    public record TransitSummary(String state,BigDecimal durationMinutes,int transferCount,
            long explicitWalkingDistanceMeters) { }
    public record CallSummary(int tourListCalls,int tourDetailCalls,int transitCalls,long elapsedMillis) { }
    public record Planner(String tripPublicId,int dayNumber,LocalDate travelDate,List<Item> items,List<Leg> legs,
            CallSummary callSummary,DayBurden dayBurden) {
        public Planner(String tripPublicId,int dayNumber,LocalDate travelDate,List<Item> items,List<Leg> legs,
                CallSummary callSummary){this(tripPublicId,dayNumber,travelDate,items,legs,callSummary,null);}
    }
    public record DayBurden(String state,String level,int selectedPlaceCount,BigDecimal transitMinutes,
            long explicitWalkingDistanceMeters,int transferCount,String caution) { }
    public record Item(String slotType,String provider,String contentId,String contentType,String title,String address,
            String imageUrl,Coordinates coordinates,String sourceAttribution,String dataAvailability) { }
    public record Leg(String fromSlotType,String toSlotType,String mode,BigDecimal durationMinutes,int transferCount,
            long explicitWalkingDistanceMeters,long unaccountedDistanceMeters,String dataAvailability) { }
}
