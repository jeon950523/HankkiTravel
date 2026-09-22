package kr.hankkitravel.tourism.persistence;

import kr.hankkitravel.tourism.model.StoredTourismPlace;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StoredTourismPlaceMapper {
    @Insert("""
            INSERT INTO tourism_places (content_id, content_type_id, title, l_dong_regn_cd, l_dong_signgu_cd, longitude, latitude, source_modified_at, active)
            VALUES (#{contentId}, #{contentTypeId}, #{title}, #{lDongRegnCd}, #{lDongSignguCd}, #{longitude}, #{latitude}, #{sourceModifiedAt}, #{active})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(StoredTourismPlace entity);

    @Select("""
            SELECT id, content_id AS contentId, content_type_id AS contentTypeId, title, l_dong_regn_cd AS lDongRegnCd, l_dong_signgu_cd AS lDongSignguCd, longitude, latitude, source_modified_at AS sourceModifiedAt, active, created_at AS createdAt, updated_at AS updatedAt
            FROM tourism_places WHERE id = #{id}
            """)
    StoredTourismPlace findById(long id);
}
