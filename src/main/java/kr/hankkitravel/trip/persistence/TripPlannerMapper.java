package kr.hankkitravel.trip.persistence;

import java.util.List;
import kr.hankkitravel.trip.model.TripPlannerRows;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TripPlannerMapper {
    String CONTEXT="SELECT t.id AS tripId,t.public_id AS tripPublicId,t.guest_id AS guestId,t.profile_id AS profileId,"+
            "t.region_key AS regionKey,t.start_date AS startDate,t.end_date AS endDate,d.id AS dayId,"+
            "d.day_number AS dayNumber,d.travel_date AS travelDate FROM trips t JOIN trip_days d ON d.trip_id=t.id "+
            "WHERE t.guest_id=#{guestId} AND t.public_id=#{tripPublicId} AND d.day_number=#{dayNumber}";
    @Select(CONTEXT)
    TripPlannerRows.Context context(@Param("guestId")long guestId,@Param("tripPublicId")String tripPublicId,@Param("dayNumber")int dayNumber);
    @Select(CONTEXT+" FOR UPDATE")
    TripPlannerRows.Context lockContext(@Param("guestId")long guestId,@Param("tripPublicId")String tripPublicId,@Param("dayNumber")int dayNumber);
    @Select("""
        SELECT id,public_id AS publicId,trip_day_id AS tripDayId,slot_type AS slotType,provider,
               content_id AS contentId,content_type AS contentType
        FROM trip_day_place_anchors WHERE trip_day_id=#{dayId}
        ORDER BY CASE slot_type WHEN 'DAY_FOCUS' THEN 0 WHEN 'MORNING_ACTIVITY' THEN 1 WHEN 'AFTERNOON_ACTIVITY' THEN 2 WHEN 'POST_MEAL_DESSERT' THEN 3 ELSE 4 END
        """)
    List<TripPlannerRows.Reference> placeReferences(long dayId);
    @Select("""
        SELECT a.id,NULL AS publicId,s.trip_day_id AS tripDayId,s.meal_type AS slotType,a.provider,
               a.content_id AS contentId,a.content_type AS contentType
        FROM trip_meal_slots s JOIN meal_anchors a ON a.trip_meal_slot_id=s.id
        WHERE s.trip_day_id=#{dayId}
        ORDER BY CASE s.meal_type WHEN 'BREAKFAST' THEN 1 WHEN 'LUNCH' THEN 2 ELSE 3 END
        """)
    List<TripPlannerRows.Reference> mealReferences(long dayId);
    @Insert("""
        INSERT INTO trip_day_place_anchors(public_id,trip_day_id,slot_type,provider,content_id,content_type)
        VALUES(#{publicId},#{tripDayId},#{slotType},'KTO',#{contentId},#{contentType})
        ON DUPLICATE KEY UPDATE content_id=#{contentId},content_type=#{contentType}
        """)
    int upsert(TripPlannerRows.Reference reference);
    @Select("""
        SELECT id,public_id AS publicId,trip_day_id AS tripDayId,slot_type AS slotType,provider,
               content_id AS contentId,content_type AS contentType
        FROM trip_day_place_anchors WHERE trip_day_id=#{dayId} AND slot_type=#{slotType}
        """)
    TripPlannerRows.Reference reference(@Param("dayId")long dayId,@Param("slotType")String slotType);
    @Delete("DELETE FROM trip_day_place_anchors WHERE trip_day_id=#{dayId} AND slot_type=#{slotType}")
    int clear(@Param("dayId")long dayId,@Param("slotType")String slotType);
}
