package kr.hankkitravel.trip.persistence;

import java.util.List;
import kr.hankkitravel.trip.model.TripRows;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TripScheduleMapper {
    String COLUMNS = "id, public_id AS publicId, guest_id AS guestId, profile_id AS profileId, "
            + "region_key AS regionKey, start_date AS startDate, end_date AS endDate, created_at AS createdAt";
    @Insert("""
        INSERT INTO trips (public_id, guest_id, profile_id, region_key, start_date, end_date, status)
        VALUES (#{publicId}, #{guestId}, #{profileId}, #{regionKey}, #{startDate}, #{endDate}, 'DRAFT')
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertTrip(TripRows.Schedule trip);
    @Insert("INSERT INTO trip_days (trip_id, day_number, travel_date) VALUES (#{tripId}, #{dayNumber}, #{travelDate})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertDay(TripRows.Day day);
    @Insert("INSERT INTO trip_meal_slots (public_id, trip_day_id, meal_type) VALUES (#{publicId}, #{tripDayId}, #{mealType})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSlot(TripRows.Slot slot);
    @Select("SELECT " + COLUMNS + " FROM trips WHERE public_id = #{publicId} AND guest_id = #{guestId}")
    TripRows.Schedule findOwned(@Param("guestId") long guestId, @Param("publicId") String publicId);
    @Select("SELECT " + COLUMNS + " FROM trips WHERE public_id = #{publicId} AND guest_id = #{guestId} FOR UPDATE")
    TripRows.Schedule lockOwned(@Param("guestId") long guestId, @Param("publicId") String publicId);
    @Select("""
        SELECT t.id, t.public_id AS publicId, t.profile_id AS profileId, t.region_key AS regionKey,
               t.start_date AS startDate, t.end_date AS endDate, t.created_at AS createdAt,
               COUNT(s.id) AS mealSlotCount, COUNT(a.id) AS selectedAnchorCount
        FROM trips t LEFT JOIN trip_days d ON d.trip_id=t.id
        LEFT JOIN trip_meal_slots s ON s.trip_day_id=d.id
        LEFT JOIN meal_anchors a ON a.trip_meal_slot_id=s.id
        WHERE t.guest_id=#{guestId} AND t.public_id IS NOT NULL
        GROUP BY t.id, t.public_id, t.profile_id, t.region_key, t.start_date, t.end_date, t.created_at
        ORDER BY t.created_at DESC, t.id DESC
        """)
    List<TripRows.Schedule> list(long guestId);
    @Select("SELECT id, trip_id AS tripId, day_number AS dayNumber, travel_date AS travelDate FROM trip_days WHERE trip_id=#{tripId} ORDER BY day_number")
    List<TripRows.Day> days(long tripId);
    @Select("""
        SELECT s.id, s.public_id AS publicId, s.trip_day_id AS tripDayId, s.meal_type AS mealType,
               a.provider, a.content_id AS contentId, a.content_type AS contentType
        FROM trip_meal_slots s JOIN trip_days d ON d.id=s.trip_day_id
        LEFT JOIN meal_anchors a ON a.trip_meal_slot_id=s.id
        WHERE d.trip_id=#{tripId}
        ORDER BY d.day_number, CASE s.meal_type WHEN 'BREAKFAST' THEN 1 WHEN 'LUNCH' THEN 2 ELSE 3 END
        """)
    List<TripRows.Slot> slots(long tripId);
    @Select("""
        SELECT s.id, s.public_id AS publicId, s.trip_day_id AS tripDayId, s.meal_type AS mealType
        FROM trip_meal_slots s JOIN trip_days d ON d.id=s.trip_day_id
        WHERE d.trip_id=#{tripId} AND s.public_id=#{slotPublicId}
        """)
    TripRows.Slot slot(@Param("tripId") long tripId, @Param("slotPublicId") String slotPublicId);
    @Insert("""
        INSERT INTO meal_anchors (trip_meal_slot_id, provider, content_id, content_type)
        VALUES (#{slotId}, 'KTO', #{contentId}, #{contentType})
        ON DUPLICATE KEY UPDATE content_id=#{contentId}, content_type=#{contentType}
        """)
    int upsertAnchor(@Param("slotId") long slotId, @Param("contentId") String contentId, @Param("contentType") String contentType);
    @Delete("DELETE FROM meal_anchors WHERE trip_meal_slot_id=#{slotId}")
    int deleteAnchor(long slotId);
    @Delete("DELETE FROM trips WHERE id=#{tripId} AND guest_id=#{guestId}")
    int deleteTrip(@Param("tripId") long tripId, @Param("guestId") long guestId);
}
