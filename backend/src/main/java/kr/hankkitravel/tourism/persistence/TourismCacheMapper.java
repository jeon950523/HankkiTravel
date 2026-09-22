package kr.hankkitravel.tourism.persistence;

import java.util.List;
import kr.hankkitravel.tourism.model.TourismCachePlace;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TourismCacheMapper {
    @Insert("""
            INSERT INTO tourism_places (content_id, content_type_id, title, addr1, addr2, tel, zipcode,
                l_dong_regn_cd, l_dong_signgu_cd, longitude, latitude, first_image, first_image2,
                cpyrht_div_cd, lcls_systm1, lcls_systm2, lcls_systm3, source_created_raw,
                source_modified_raw, active)
            VALUES (#{contentId}, #{contentTypeId}, #{title}, #{address}, #{addressDetail}, #{tel}, #{zipcode},
                #{lDongRegnCd}, #{lDongSignguCd}, #{longitude}, #{latitude}, #{firstImage}, #{firstImage2},
                #{cpyrhtDivCd}, #{lclsSystm1}, #{lclsSystm2}, #{lclsSystm3}, #{sourceCreatedRaw},
                #{sourceModifiedRaw}, #{active})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TourismCachePlace place);

    @Update("""
            UPDATE tourism_places SET content_type_id = #{contentTypeId}, title = #{title}, addr1 = #{address},
                addr2 = #{addressDetail}, tel = #{tel}, zipcode = #{zipcode}, l_dong_regn_cd = #{lDongRegnCd},
                l_dong_signgu_cd = #{lDongSignguCd}, longitude = #{longitude}, latitude = #{latitude},
                first_image = #{firstImage}, first_image2 = #{firstImage2}, cpyrht_div_cd = #{cpyrhtDivCd},
                lcls_systm1 = #{lclsSystm1}, lcls_systm2 = #{lclsSystm2}, lcls_systm3 = #{lclsSystm3},
                source_created_raw = #{sourceCreatedRaw}, source_modified_raw = #{sourceModifiedRaw}, active = TRUE
            WHERE content_id = #{contentId}
            """)
    int update(TourismCachePlace place);

    @Update("UPDATE tourism_places SET active = FALSE WHERE id = #{id} AND active = TRUE")
    int deactivate(long id);

    @Select("""
            SELECT id, content_id AS contentId, content_type_id AS contentTypeId, title, addr1 AS address,
                addr2 AS addressDetail, tel, zipcode, l_dong_regn_cd AS lDongRegnCd,
                l_dong_signgu_cd AS lDongSignguCd, longitude, latitude, first_image AS firstImage,
                first_image2 AS firstImage2, cpyrht_div_cd AS cpyrhtDivCd, lcls_systm1, lcls_systm2,
                lcls_systm3, source_created_raw AS sourceCreatedRaw, source_modified_raw AS sourceModifiedRaw,
                active
            FROM tourism_places WHERE content_id = #{contentId}
            """)
    TourismCachePlace findByContentId(String contentId);

    @Select("""
            SELECT id, content_id AS contentId, content_type_id AS contentTypeId, title, addr1 AS address,
                addr2 AS addressDetail, tel, zipcode, l_dong_regn_cd AS lDongRegnCd,
                l_dong_signgu_cd AS lDongSignguCd, longitude, latitude, first_image AS firstImage,
                first_image2 AS firstImage2, cpyrht_div_cd AS cpyrhtDivCd, lcls_systm1, lcls_systm2,
                lcls_systm3, source_created_raw AS sourceCreatedRaw, source_modified_raw AS sourceModifiedRaw,
                active
            FROM tourism_places
            WHERE active = TRUE AND l_dong_regn_cd = #{lDongRegnCd}
              AND l_dong_signgu_cd = #{lDongSignguCd} AND content_type_id = #{contentTypeId}
            """)
    List<TourismCachePlace> findActiveByScope(@Param("lDongRegnCd") String lDongRegnCd,
            @Param("lDongSignguCd") String lDongSignguCd, @Param("contentTypeId") String contentTypeId);
}
