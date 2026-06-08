package com.gov.procurement.modules.org.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gov.procurement.modules.org.domain.ProjectGroup;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * project_group Mapper。除基础 CRUD 外，提供「是否被业务单据引用」的聚合计数，用于删除前置 RESTRICT 校验。
 */
public interface ProjectGroupMapper extends BaseMapper<ProjectGroup> {

    /**
     * 统计项目组被各业务表以 project_group_id 引用的总条数（>0 即不可删，详设 §5.2 / TBD-1）。
     *
     * @param pgId 项目组 id
     * @return 业务引用总数
     */
    @Select("SELECT (SELECT count(*) FROM budget WHERE project_group_id = #{pgId}) "
            + "+ (SELECT count(*) FROM purchase_order WHERE project_group_id = #{pgId}) "
            + "+ (SELECT count(*) FROM stock_item WHERE project_group_id = #{pgId} AND is_deleted = 0) "
            + "+ (SELECT count(*) FROM inbound_item WHERE project_group_id = #{pgId}) "
            + "+ (SELECT count(*) FROM requisition WHERE project_group_id = #{pgId}) "
            + "+ (SELECT count(*) FROM stocktake WHERE scope_project_group_id = #{pgId})")
    long countBusinessReferences(@Param("pgId") Long pgId);
}
