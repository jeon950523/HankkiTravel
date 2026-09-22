package kr.hankkitravel.profile.persistence;

import kr.hankkitravel.profile.model.FamilyMember;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FamilyMemberMapper {
    @Insert("""
            INSERT INTO family_members (profile_id, nickname, sort_order, continuous_walking_minutes, stairs_preference)
            VALUES (#{profileId}, #{nickname}, #{sortOrder}, #{continuousWalkingMinutes}, #{stairsPreference})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FamilyMember entity);

    @Select("""
            SELECT id, profile_id AS profileId, nickname, sort_order AS sortOrder,
                continuous_walking_minutes AS continuousWalkingMinutes, stairs_preference AS stairsPreference,
                created_at AS createdAt, updated_at AS updatedAt
            FROM family_members WHERE id = #{id}
            """)
    FamilyMember findById(long id);

    @Select("""
            SELECT id, profile_id AS profileId, nickname, sort_order AS sortOrder,
                continuous_walking_minutes AS continuousWalkingMinutes, stairs_preference AS stairsPreference,
                created_at AS createdAt, updated_at AS updatedAt
            FROM family_members WHERE profile_id = #{profileId} ORDER BY sort_order, id
            """)
    java.util.List<FamilyMember> findByProfileId(long profileId);

    @Delete("DELETE FROM family_members WHERE profile_id = #{profileId}")
    int deleteByProfileId(long profileId);
}
