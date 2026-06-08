package com.gov.procurement.modules.org.service;

import com.gov.procurement.modules.org.dto.RoleVO;
import com.gov.procurement.modules.org.dto.SysUserVO;
import com.gov.procurement.modules.org.dto.UserCreateReq;
import com.gov.procurement.modules.org.dto.UserUpdateReq;

import java.util.List;

/**
 * 用户服务：用户 CRUD（口令 BCrypt，委托 U2 编码器）、用户↔角色全量覆盖分配，以及只读角色字典。
 */
public interface UserService {

    List<SysUserVO> list(Long departmentId);

    SysUserVO get(Long id);

    Long create(UserCreateReq req);

    void update(Long id, UserUpdateReq req);

    void remove(Long id);

    List<RoleVO> getUserRoles(Long id);

    List<RoleVO> assignRoles(Long id, List<Long> roleIds);

    /** 内置角色字典（只读，供分配 UI）。 */
    List<RoleVO> listRoles();
}
