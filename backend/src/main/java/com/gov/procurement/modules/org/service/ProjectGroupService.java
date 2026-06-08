package com.gov.procurement.modules.org.service;

import com.gov.procurement.modules.org.dto.ProjectGroupReq;
import com.gov.procurement.modules.org.dto.ProjectGroupVO;

import java.util.List;

/**
 * 项目组服务：CRUD（必挂存在部门）与删除前置「被业务引用」RESTRICT 校验。
 */
public interface ProjectGroupService {

    List<ProjectGroupVO> list(Long departmentId);

    ProjectGroupVO get(Long id);

    Long create(ProjectGroupReq req);

    void update(Long id, ProjectGroupReq req);

    void remove(Long id);
}
