package com.gov.procurement.modules.approval.listener;

import com.gov.procurement.modules.approval.domain.Approval;
import com.gov.procurement.modules.approval.domain.ApprovalRecord;
import com.gov.procurement.modules.approval.mapper.ApprovalMapper;
import com.gov.procurement.modules.approval.mapper.ApprovalRecordMapper;
import com.gov.procurement.modules.budget.domain.Budget;
import com.gov.procurement.modules.budget.mapper.BudgetMapper;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

import static com.gov.procurement.modules.approval.constant.ApprovalConst.ACTION_APPROVE;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.BUDGET_APPROVED;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.NODE_DEPT_MGR;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.NODE_PURCHASE_MGR;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.SEQ_DEPT_MGR;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.SEQ_PURCHASE_MGR;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.ST_APPROVED;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.ST_PENDING_DEPT_MGR;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.TASK_KEY_DEPT_MGR;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.TASK_KEY_PURCHASE_MGR;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.VAR_APPROVAL_ID;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.VAR_APPROVER_ID;

/**
 * 审批通过路径的投影一致性单点（complete TaskListener）。
 *
 * <p>由 BPMN 两个 UserTask 的 {@code complete} 事件触发，运行在 {@code taskService.complete()} 调用栈内，
 * 与引擎推进共享同一 Spring 事务：写 approval_record + 翻转 approval 投影（终审同步 budget）与引擎表同提交/同回滚，
 * 杜绝「引擎走了、投影没写」。任何写库异常须冒泡以触发回滚（不得吞异常，详设 §5.3）。</p>
 *
 * <p>驳回不经 complete（走 deleteProcessInstance），故本监听器只处理通过路径。</p>
 */
@Component
public class ApprovalProjectionListener implements TaskListener {

    private static final Logger log = LoggerFactory.getLogger(ApprovalProjectionListener.class);

    private final ApprovalMapper approvalMapper;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final BudgetMapper budgetMapper;

    public ApprovalProjectionListener(ApprovalMapper approvalMapper, ApprovalRecordMapper approvalRecordMapper,
                                      BudgetMapper budgetMapper) {
        this.approvalMapper = approvalMapper;
        this.approvalRecordMapper = approvalRecordMapper;
        this.budgetMapper = budgetMapper;
    }

    @Override
    public void notify(DelegateTask delegateTask) {
        String taskKey = delegateTask.getTaskDefinitionKey();
        Long approvalId = asLong(delegateTask.getVariable(VAR_APPROVAL_ID));
        Long approverId = asLong(delegateTask.getVariable(VAR_APPROVER_ID));

        String node;
        int nodeSeq;
        String nextStatus;
        boolean terminal;
        if (TASK_KEY_PURCHASE_MGR.equals(taskKey)) {
            node = NODE_PURCHASE_MGR;
            nodeSeq = SEQ_PURCHASE_MGR;
            nextStatus = ST_PENDING_DEPT_MGR;
            terminal = false;
        } else if (TASK_KEY_DEPT_MGR.equals(taskKey)) {
            node = NODE_DEPT_MGR;
            nodeSeq = SEQ_DEPT_MGR;
            nextStatus = ST_APPROVED;
            terminal = true;
        } else {
            log.warn("未知审批节点，跳过投影 taskKey={}, approvalId={}", taskKey, approvalId);
            return;
        }

        ApprovalRecord record = new ApprovalRecord();
        record.setApprovalId(approvalId);
        record.setNodeSeq(nodeSeq);
        record.setNode(node);
        record.setTaskId(delegateTask.getId());
        record.setApproverId(approverId);
        record.setAction(ACTION_APPROVE);
        record.setActedAt(OffsetDateTime.now());
        approvalRecordMapper.insert(record);

        Approval approval = approvalMapper.selectById(approvalId);
        if (approval == null) {
            // 投影缺失属严重不一致；抛出以回滚本次 complete（不得吞异常）。
            throw new IllegalStateException("审批投影缺失 approvalId=" + approvalId);
        }
        approval.setStatus(nextStatus);
        // 通过节点一后流转到节点二、终审后停在节点二，current_node 均为 dept_mgr。
        approval.setCurrentNode(NODE_DEPT_MGR);
        approvalMapper.updateById(approval);

        if (terminal) {
            Budget budget = budgetMapper.selectById(approval.getBizId());
            if (budget == null) {
                throw new IllegalStateException("预算缺失 bizId=" + approval.getBizId());
            }
            budget.setStatus(BUDGET_APPROVED);
            budgetMapper.updateById(budget);
        }

        log.info("审批投影更新 approvalId={}, node={}, terminal={}", approvalId, node, terminal);
    }

    private static Long asLong(Object var) {
        return var == null ? null : ((Number) var).longValue();
    }
}
