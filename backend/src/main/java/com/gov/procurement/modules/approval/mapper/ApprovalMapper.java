package com.gov.procurement.modules.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gov.procurement.modules.approval.domain.Approval;
import com.gov.procurement.modules.approval.dto.TodoBizInfo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * approval Mapper：基础 CRUD + 待办列表的业务信息补全（按流程实例反查预算/项目组名）。
 */
public interface ApprovalMapper extends BaseMapper<Approval> {

    /**
     * 按流程实例 id 反查审批单及其预算/项目组展示信息（仅预算审批，3 表关联，关联字段均有索引）。
     *
     * @param processInstanceId 流程实例 id
     * @return 待办业务信息；无匹配返回 null
     */
    @Select("SELECT a.id AS approval_id, a.biz_type, a.biz_id, b.name AS budget_name, "
            + "pg.name AS project_group_name "
            + "FROM approval a "
            + "JOIN budget b ON a.biz_id = b.id "
            + "JOIN project_group pg ON b.project_group_id = pg.id "
            + "WHERE a.process_instance_id = #{processInstanceId}")
    TodoBizInfo selectTodoBizInfo(@Param("processInstanceId") String processInstanceId);
}
