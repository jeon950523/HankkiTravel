package kr.hankkitravel.trip.persistence;

import kr.hankkitravel.trip.model.Trip;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TripMapper {
    @Insert("""
            INSERT INTO trips (profile_id, status)
            VALUES (#{profileId}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Trip entity);

    @Select("""
            SELECT id, profile_id AS profileId, status, created_at AS createdAt, updated_at AS updatedAt
            FROM trips WHERE id = #{id}
            """)
    Trip findById(long id);
}
