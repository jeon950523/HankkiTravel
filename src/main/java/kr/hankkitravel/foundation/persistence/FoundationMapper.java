package kr.hankkitravel.foundation.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FoundationMapper {
    @Select("SELECT foundation_version FROM foundation_metadata WHERE id = 1")
    String findVersion();
}
