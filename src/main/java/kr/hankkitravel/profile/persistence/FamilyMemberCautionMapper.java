package kr.hankkitravel.profile.persistence;

import java.util.List;
import kr.hankkitravel.profile.model.FamilyMemberCaution;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FamilyMemberCautionMapper {
    @Insert("""
            INSERT INTO family_member_cautions (family_member_id, caution)
            VALUES (#{familyMemberId}, #{caution})
            """)
    int insert(FamilyMemberCaution entity);

    @Select("""
            SELECT family_member_id AS familyMemberId, caution
            FROM family_member_cautions WHERE family_member_id = #{familyMemberId} ORDER BY caution
            """)
    List<FamilyMemberCaution> findByFamilyMemberId(long familyMemberId);
}
