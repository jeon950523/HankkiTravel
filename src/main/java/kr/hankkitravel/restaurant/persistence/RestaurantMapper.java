package kr.hankkitravel.restaurant.persistence;

import kr.hankkitravel.restaurant.model.Restaurant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RestaurantMapper {
    @Insert("""
            INSERT INTO restaurants (tourism_place_id)
            VALUES (#{tourismPlaceId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Restaurant entity);

    @Select("""
            SELECT id, tourism_place_id AS tourismPlaceId, created_at AS createdAt, updated_at AS updatedAt
            FROM restaurants WHERE id = #{id}
            """)
    Restaurant findById(long id);
}
