package com.gov.procurement.modules.approval.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gov.procurement.modules.approval.dto.HistoryItemVO;
import com.gov.procurement.modules.approval.dto.RejectReq;
import com.gov.procurement.modules.approval.dto.SubmitApprovalReq;
import com.gov.procurement.modules.approval.dto.TodoItemVO;

import java.util.List;

/**
 * 通用审批服务（U7）：提交启动流程、待办查询、通过、驳回、流转历史。
 * 审批权威流转在 Flowable 引擎，approval/approval_record 为业务投影；写投影与引擎推进同事务。
 */
public interface ApprovalService {

    /**
     * 提交审批：校验预算处于草稿态后启动 budget_approval 流程实例，写投影并置预算为 submitted。
     *
     * @param req 提交请求（bizType=budget、bizId）
     * @return 审批单 id
     */
    Long submit(SubmitApprovalReq req);

    /**
     * 通过当前节点：节点-角色校验后 complete 任务，由 TaskListener 同事务写记录、翻转投影（终审同步预算）。
     *
     * @param approvalId 审批单 id
     */
    void approve(Long approvalId);

    /**
     * 驳回：意见必填，写驳回记录后结束流程实例并退回编制态（approval/budget 同步置 draft）。
     *
     * @param approvalId 审批单 id
     * @param req        驳回请求（opinion 必填）
     */
    void reject(Long approvalId, RejectReq req);

    /**
     * 待办查询：仅返回 candidateGroup 命中登录用户角色、且未完成的任务（分页）。
     *
     * @param pageNum 页码（从 1 起）
     * @param size    每页大小
     * @return 待办分页
     */
    Page<TodoItemVO> todo(int pageNum, int size);

    /**
     * 流转历史：按处理时间升序返回审批单的全部记录（含多轮重提）。
     *
     * @param approvalId 审批单 id
     * @return 历史记录列表
     */
    List<HistoryItemVO> history(Long approvalId);
}
