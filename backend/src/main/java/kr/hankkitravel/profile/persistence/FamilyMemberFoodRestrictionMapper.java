package kr.hankkitravel.profile.persistence;

import java.util.List;
import kr.hankkitravel.profile.model.FamilyMemberFoodRestriction;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FamilyMemberFoodRestrictionMapper {
    @Insert("""
        INSERT INTO family_member_food_restrictions
            (family_member_id, restriction_type, normalized_value, display_value)
        VALUES (#{familyMemberId}, #{restrictionType}, #{normalizedValue}, #{displayValue})
        """)
    int insert(FamilyMemberFoodRestriction entity);

    @Select("""
        SELECT family_member_id AS familyMemberId, restriction_type AS restrictionType,
               normalized_value AS normalizedValue, display_value AS displayValue
        FROM family_member_food_restrictions
        WHERE family_member_id = #{familyMemberId}
        ORDER BY restriction_type, id
        """)
    List<FamilyMemberFoodRestriction> findByFamilyMemberId(long familyMemberId);
}
