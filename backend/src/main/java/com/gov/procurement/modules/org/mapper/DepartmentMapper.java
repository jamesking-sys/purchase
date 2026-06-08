package com.gov.procurement.modules.org.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gov.procurement.modules.org.domain.Department;

/**
 * department Mapper。CRUD 由 MyBatis-Plus 条件构造器完成，逻辑删除自动过滤 is_deleted=0。
 */
public interface DepartmentMapper extends BaseMapper<Department> {
}
