package com.stms.risk.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface ShareholderGroupMapper {

    @Select("SELECT group_id FROM shareholder_group WHERE shareholder_id = #{shareholderId}")
    String findGroupIdByShareholderId(@Param("shareholderId") String shareholderId);

    @Select("SELECT shareholder_id FROM shareholder_group WHERE group_id = #{groupId}")
    List<String> findShareholderIdsByGroupId(@Param("groupId") String groupId);
}