package kr.hankkitravel.identity.persistence;

import kr.hankkitravel.identity.model.Guest;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GuestMapper {
    @Insert("""
            INSERT INTO guests (public_id)
            VALUES (#{publicId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Guest entity);

    @Select("""
            SELECT id, public_id AS publicId, created_at AS createdAt, updated_at AS updatedAt
            FROM guests WHERE id = #{id}
            """)
    Guest findById(long id);

    @Select("""
            SELECT id, public_id AS publicId, created_at AS createdAt, updated_at AS updatedAt
            FROM guests WHERE public_id = #{publicId}
            """)
    Guest findByPublicId(String publicId);
}
