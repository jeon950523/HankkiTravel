package kr.hankkitravel.profile.persistence;

import kr.hankkitravel.profile.model.FamilyProfile;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FamilyProfileMapper {
    @Insert("""
            INSERT INTO family_profiles (owner_user_id, owner_guest_id, name, transport_mode, parking_preference,
                walking_burden_preference, transfer_preference, stairs_avoidance)
            VALUES (#{ownerUserId}, #{ownerGuestId}, #{name}, #{transportMode}, #{parkingPreference},
                #{walkingBurdenPreference}, #{transferPreference}, #{stairsAvoidance})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FamilyProfile entity);

    @Select("""
            SELECT id, owner_user_id AS ownerUserId, owner_guest_id AS ownerGuestId, name,
                transport_mode AS transportMode, parking_preference AS parkingPreference,
                walking_burden_preference AS walkingBurdenPreference, transfer_preference AS transferPreference,
                stairs_avoidance AS stairsAvoidance, created_at AS createdAt, updated_at AS updatedAt
            FROM family_profiles WHERE id = #{id}
            """)
    FamilyProfile findById(long id);

    @Select("""
            SELECT id, owner_user_id AS ownerUserId, owner_guest_id AS ownerGuestId, name,
                transport_mode AS transportMode, parking_preference AS parkingPreference,
                walking_burden_preference AS walkingBurdenPreference, transfer_preference AS transferPreference,
                stairs_avoidance AS stairsAvoidance, created_at AS createdAt, updated_at AS updatedAt
            FROM family_profiles WHERE owner_guest_id = #{guestId} ORDER BY updated_at DESC, id DESC
            """)
    java.util.List<FamilyProfile> findByOwnerGuestId(long guestId);

    @Select("""
            SELECT id, owner_user_id AS ownerUserId, owner_guest_id AS ownerGuestId, name,
                transport_mode AS transportMode, parking_preference AS parkingPreference,
                walking_burden_preference AS walkingBurdenPreference, transfer_preference AS transferPreference,
                stairs_avoidance AS stairsAvoidance, created_at AS createdAt, updated_at AS updatedAt
            FROM family_profiles WHERE id = #{profileId} AND owner_guest_id = #{guestId}
            """)
    FamilyProfile findByIdAndOwnerGuestId(long profileId, long guestId);

    @Update("""
            UPDATE family_profiles
            SET name = #{name}, transport_mode = #{transportMode}, parking_preference = #{parkingPreference},
                walking_burden_preference = #{walkingBurdenPreference}, transfer_preference = #{transferPreference},
                stairs_avoidance = #{stairsAvoidance}
            WHERE id = #{id} AND owner_guest_id = #{ownerGuestId}
            """)
    int update(FamilyProfile entity);
}
