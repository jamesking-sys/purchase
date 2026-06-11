package com.gov.procurement.modules.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gov.procurement.modules.approval.domain.ApprovalRecord;
import com.gov.procurement.modules.approval.dto.HistoryItemVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * approval_record Mapper：基础 CRUD + 流转历史查询（关联处理人姓名，按处理时间升序）。
 */
public interface ApprovalRecordMapper extends BaseMapper<ApprovalRecord> {

    /**
     * 查询指定审批单的全部流转记录（含多轮重提），按 acted_at 升序、同刻按 id 升序。
     *
     * @param approvalId 审批单 id
     * @return 历史记录列表；无记录返回空列表
     */
    @Select("SELECT ar.node_seq, ar.node, u.name AS approver_name, ar.action, ar.opinion, ar.acted_at "
            + "FROM approval_record ar "
            + "JOIN sys_user u ON ar.approver_id = u.id "
            + "WHERE ar.approval_id = #{approvalId} "
            + "ORDER BY ar.acted_at ASC, ar.id ASC")
    List<HistoryItemVO> selectHistory(@Param("approvalId") Long approvalId);
}
