package kr.hankkitravel.identity.persistence;

import kr.hankkitravel.identity.model.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {
    @Insert("""
            INSERT INTO users (status)
            VALUES (#{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User entity);

    @Select("""
            SELECT id, status, created_at AS createdAt, updated_at AS updatedAt
            FROM users WHERE id = #{id}
            """)
    User findById(long id);
}
