package kr.hankkitravel.restaurant.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RestaurantSpecializationMapper {
    /** The tourism event filters content type; INSERT IGNORE preserves the required 1:1 relation. */
    @Insert("INSERT IGNORE INTO restaurants (tourism_place_id) VALUES (#{tourismPlaceId})")
    int ensure(@Param("tourismPlaceId") long tourismPlaceId);

    @Delete("DELETE FROM restaurants WHERE tourism_place_id = #{tourismPlaceId}")
    int deleteByTourismPlaceId(@Param("tourismPlaceId") long tourismPlaceId);
}
