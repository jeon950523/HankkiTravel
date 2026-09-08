package kr.hankkitravel.profile.persistence;

import kr.hankkitravel.profile.model.FamilyMember;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FamilyMemberMapper {
    @Insert("""
            INSERT INTO family_members (profile_id, nickname, sort_order)
            VALUES (#{profileId}, #{nickname}, #{sortOrder})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FamilyMember entity);

    @Select("""
            SELECT id, profile_id AS profileId, nickname, sort_order AS sortOrder, created_at AS createdAt, updated_at AS updatedAt
            FROM family_members WHERE id = #{id}
            """)
    FamilyMember findById(long id);
}
