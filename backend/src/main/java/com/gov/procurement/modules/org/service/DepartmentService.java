package com.gov.procurement.modules.org.service;

import com.gov.procurement.modules.org.dto.DepartmentReq;
import com.gov.procurement.modules.org.dto.DepartmentVO;

import java.util.List;

/**
 * 部门服务：CRUD 与删除前置 RESTRICT 校验。
 */
public interface DepartmentService {

    List<DepartmentVO> list();

    DepartmentVO get(Long id);

    Long create(DepartmentReq req);

    void update(Long id, DepartmentReq req);

    void remove(Long id);
}
