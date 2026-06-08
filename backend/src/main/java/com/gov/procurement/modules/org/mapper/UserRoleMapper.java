package com.gov.procurement.modules.org.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gov.procurement.modules.auth.domain.Role;
import com.gov.procurement.modules.org.domain.UserRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * user_role Mapper。关系表（不软删）：基础增删 + 按用户加载其未删角色（含 id/code/name）。
 */
public interface UserRoleMapper extends BaseMapper<UserRole> {

    /**
     * 查询某用户拥有的未删角色（含 id/code/name），按 id 排序。
     *
     * @param userId 用户 id
     * @return 角色列表
     */
    @Select("SELECT r.* FROM user_role ur JOIN role r ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId} AND r.is_deleted = 0 ORDER BY r.id")
    List<Role> selectRolesByUserId(@Param("userId") Long userId);
}
