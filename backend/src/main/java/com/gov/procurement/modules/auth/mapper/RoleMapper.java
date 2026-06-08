package com.gov.procurement.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gov.procurement.modules.auth.domain.Role;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * role Mapper：含「按用户加载未删除角色 code」的只读查询，供 Sa-Token StpInterface 角色解析使用。
 */
public interface RoleMapper extends BaseMapper<Role> {

    /**
     * 查询指定用户拥有的、未删除角色的 code 列表（user_role JOIN role，仅 is_deleted=0）。
     *
     * @param userId 用户 id
     * @return 角色 code 列表；无角色时返回空列表
     */
    @Select("SELECT r.code FROM user_role ur JOIN role r ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId} AND r.is_deleted = 0")
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}
