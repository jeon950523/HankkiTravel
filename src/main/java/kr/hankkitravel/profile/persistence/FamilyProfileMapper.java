package kr.hankkitravel.profile.persistence;

import kr.hankkitravel.profile.model.FamilyProfile;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FamilyProfileMapper {
    @Insert("""
            INSERT INTO family_profiles (owner_user_id, owner_guest_id, name)
            VALUES (#{ownerUserId}, #{ownerGuestId}, #{name})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FamilyProfile entity);

    @Select("""
            SELECT id, owner_user_id AS ownerUserId, owner_guest_id AS ownerGuestId, name, created_at AS createdAt, updated_at AS updatedAt
            FROM family_profiles WHERE id = #{id}
            """)
    FamilyProfile findById(long id);
}
