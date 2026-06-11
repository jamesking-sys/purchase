package com.gov.procurement.modules.approval.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gov.procurement.common.BizException;
import com.gov.procurement.common.ErrorCode;
import com.gov.procurement.modules.approval.domain.Approval;
import com.gov.procurement.modules.approval.domain.ApprovalRecord;
import com.gov.procurement.modules.approval.dto.HistoryItemVO;
import com.gov.procurement.modules.approval.dto.RejectReq;
import com.gov.procurement.modules.approval.dto.SubmitApprovalReq;
import com.gov.procurement.modules.approval.dto.TodoBizInfo;
import com.gov.procurement.modules.approval.dto.TodoItemVO;
import com.gov.procurement.modules.approval.mapper.ApprovalMapper;
import com.gov.procurement.modules.approval.mapper.ApprovalRecordMapper;
import com.gov.procurement.modules.approval.service.ApprovalService;
import com.gov.procurement.modules.budget.domain.Budget;
import com.gov.procurement.modules.budget.mapper.BudgetMapper;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.gov.procurement.modules.approval.constant.ApprovalConst.ACTION_APPROVE;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.ACTION_REJECT;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.BIZ_BUDGET;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.BUDGET_DRAFT;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.BUDGET_SUBMITTED;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.FLOW_BUDGET;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.NODE_DEPT_MGR;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.NODE_PURCHASE_MGR;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.SEQ_DEPT_MGR;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.SEQ_PURCHASE_MGR;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.ST_DRAFT;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.ST_PENDING_DEPT_MGR;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.ST_PENDING_PURCHASE_MGR;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.TASK_KEY_PREFIX;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.VAR_ACTION;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.VAR_APPROVAL_ID;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.VAR_APPROVER_ID;
import static com.gov.procurement.modules.approval.constant.ApprovalConst.VAR_BIZ_ID;

/**
 * 通用审批服务实现。提交/通过/驳回均在单一 Spring 事务内推进引擎并写业务投影，保证「引擎 ↔ 投影」原子一致。
 * 通过路径的投影写入集中在 {@code ApprovalProjectionListener}（complete 监听器，同事务）；驳回路径在本类内完成。
 */
@Service
public class ApprovalServiceImpl implements ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalServiceImpl.class);

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ApprovalMapper approvalMapper;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final BudgetMapper budgetMapper;
    private final RuntimeService runtimeService;
    private final TaskService taskService;

    public ApprovalServiceImpl(ApprovalMapper approvalMapper, ApprovalRecordMapper approvalRecordMapper,
                               BudgetMapper budgetMapper, RuntimeService runtimeService, TaskService taskService) {
        this.approvalMapper = approvalMapper;
        this.approvalRecordMapper = approvalRecordMapper;
        this.budgetMapper = budgetMapper;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
    }

    @Override
    @Transactional
    public Long submit(SubmitApprovalReq req) {
        if (!BIZ_BUDGET.equals(req.bizType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "暂仅支持预算审批（bizType=budget）");
        }
        Budget budget = budgetMapper.selectById(req.bizId());
        if (budget == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "预算不存在");
        }
        if (!BUDGET_DRAFT.equals(budget.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "预算非草稿态，不可提交审批");
        }

        // 复用同一 approval 行（按 biz_type+biz_id），多轮重提共享 approval_id 以累积历史（详设 §5.2 / TBD-4）。
        Approval approval = findLatestByBiz(BIZ_BUDGET, req.bizId());
        if (approval == null) {
            approval = new Approval();
            approval.setBizType(BIZ_BUDGET);
            approval.setBizId(req.bizId());
            approval.setFlowCode(FLOW_BUDGET);
            approval.setCurrentNode(NODE_PURCHASE_MGR);
            approval.setStatus(ST_PENDING_PURCHASE_MGR);
            approvalMapper.insert(approval);
        }

        Map<String, Object> vars = new HashMap<>(4);
        vars.put(VAR_APPROVAL_ID, approval.getId());
        vars.put(VAR_BIZ_ID, req.bizId());
        ProcessInstance pi = runtimeService.startProcessInstanceByKey(FLOW_BUDGET, vars);

        // 启动后回填新流程实例并重置投影到节点一（重提场景覆盖旧值）。
        approvalMapper.update(null, new LambdaUpdateWrapper<Approval>()
                .eq(Approval::getId, approval.getId())
                .set(Approval::getFlowCode, FLOW_BUDGET)
                .set(Approval::getCurrentNode, NODE_PURCHASE_MGR)
                .set(Approval::getStatus, ST_PENDING_PURCHASE_MGR)
                .set(Approval::getProcessInstanceId, pi.getId()));

        budget.setStatus(BUDGET_SUBMITTED);
        budgetMapper.updateById(budget);

        log.info("提交审批 approvalId={}, bizId={}, pi={}", approval.getId(), req.bizId(), pi.getId());
        return approval.getId();
    }

    @Override
    @Transactional
    public void approve(Long approvalId) {
        long userId = StpUtil.getLoginIdAsLong();
        Approval approval = loadPendingApproval(approvalId);
        Task task = activeTask(approval);
        String node = nodeFromKey(task.getTaskDefinitionKey());
        ensureNodeRole(node);

        Map<String, Object> vars = new HashMap<>(2);
        vars.put(VAR_ACTION, ACTION_APPROVE);
        vars.put(VAR_APPROVER_ID, userId);
        // 推进引擎；complete 监听器在同事务写记录、翻转投影、终审同步预算。
        taskService.complete(task.getId(), vars);

        log.info("审批通过 approvalId={}, node={}, userId={}", approvalId, node, userId);
    }

    @Override
    @Transactional
    public void reject(Long approvalId, RejectReq req) {
        long userId = StpUtil.getLoginIdAsLong();
        String opinion = req == null ? null : req.opinion();
        if (opinion == null || opinion.isBlank()) {
            throw new BizException(ErrorCode.REJECT_OPINION_REQUIRED);
        }
        Approval approval = loadPendingApproval(approvalId);
        Task task = activeTask(approval);
        String node = nodeFromKey(task.getTaskDefinitionKey());
        ensureNodeRole(node);

        ApprovalRecord record = new ApprovalRecord();
        record.setApprovalId(approvalId);
        record.setNodeSeq(seqOf(node));
        record.setNode(node);
        record.setTaskId(task.getId());
        record.setApproverId(userId);
        record.setAction(ACTION_REJECT);
        record.setOpinion(opinion);
        record.setActedAt(OffsetDateTime.now());
        approvalRecordMapper.insert(record);

        // 驳回 = 结束实例（决策 D-3，不走同实例回退），退回编制态以便修改重提。
        runtimeService.deleteProcessInstance(approval.getProcessInstanceId(), "rejected by user " + userId);

        approvalMapper.update(null, new LambdaUpdateWrapper<Approval>()
                .eq(Approval::getId, approvalId)
                .set(Approval::getStatus, ST_DRAFT)
                .set(Approval::getCurrentNode, node)
                .set(Approval::getProcessInstanceId, null));

        Budget budget = budgetMapper.selectById(approval.getBizId());
        if (budget == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "预算不存在");
        }
        budget.setStatus(BUDGET_DRAFT);
        budgetMapper.updateById(budget);

        log.info("审批驳回 approvalId={}, node={}, userId={}", approvalId, node, userId);
    }

    @Override
    public Page<TodoItemVO> todo(int pageNum, int size) {
        int current = pageNum < 1 ? 1 : pageNum;
        int limit = (size < 1 || size > MAX_PAGE_SIZE) ? DEFAULT_PAGE_SIZE : size;

        // 仅保留审批相关角色作为 candidateGroup，避免把其它角色误传给引擎查询。
        List<String> groups = StpUtil.getRoleList().stream()
                .filter(r -> NODE_PURCHASE_MGR.equals(r) || NODE_DEPT_MGR.equals(r))
                .toList();
        Page<TodoItemVO> page = new Page<>(current, limit);
        if (groups.isEmpty()) {
            page.setTotal(0);
            page.setRecords(Collections.emptyList());
            return page;
        }

        TaskQuery query = taskService.createTaskQuery()
                .taskCandidateGroupIn(groups)
                .active()
                .orderByTaskCreateTime().desc();
        long total = query.count();
        int offset = (current - 1) * limit;
        List<Task> tasks = query.listPage(offset, limit);

        List<TodoItemVO> records = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            records.add(toTodoVO(task));
        }
        page.setTotal(total);
        page.setRecords(records);
        return page;
    }

    @Override
    public List<HistoryItemVO> history(Long approvalId) {
        if (approvalMapper.selectById(approvalId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "审批单不存在");
        }
        return approvalRecordMapper.selectHistory(approvalId);
    }

    // ---- 私有辅助 ----

    private Approval findLatestByBiz(String bizType, Long bizId) {
        List<Approval> list = approvalMapper.selectList(new LambdaQueryWrapper<Approval>()
                .eq(Approval::getBizType, bizType)
                .eq(Approval::getBizId, bizId)
                .orderByDesc(Approval::getId));
        return list.isEmpty() ? null : list.get(0);
    }

    private Approval loadPendingApproval(Long approvalId) {
        Approval approval = approvalMapper.selectById(approvalId);
        if (approval == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "审批单不存在");
        }
        if (!ST_PENDING_PURCHASE_MGR.equals(approval.getStatus())
                && !ST_PENDING_DEPT_MGR.equals(approval.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "审批单状态不可处理（已结束或非待审）");
        }
        return approval;
    }

    private Task activeTask(Approval approval) {
        Task task = taskService.createTaskQuery()
                .processInstanceId(approval.getProcessInstanceId())
                .active()
                .singleResult();
        if (task == null) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "审批任务已处理或不存在");
        }
        return task;
    }

    private void ensureNodeRole(String node) {
        if (!StpUtil.getRoleList().contains(node)) {
            throw new BizException(ErrorCode.NO_PERMISSION, "当前角色与审批节点不符");
        }
    }

    private TodoItemVO toTodoVO(Task task) {
        TodoBizInfo info = approvalMapper.selectTodoBizInfo(task.getProcessInstanceId());
        String node = nodeFromKey(task.getTaskDefinitionKey());
        OffsetDateTime createdAt = task.getCreateTime() == null ? null
                : task.getCreateTime().toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        if (info == null) {
            return new TodoItemVO(null, task.getId(), null, null, node, null, null, createdAt);
        }
        return new TodoItemVO(info.getApprovalId(), task.getId(), info.getBizType(), info.getBizId(),
                node, info.getBudgetName(), info.getProjectGroupName(), createdAt);
    }

    private static String nodeFromKey(String taskDefinitionKey) {
        if (taskDefinitionKey != null && taskDefinitionKey.startsWith(TASK_KEY_PREFIX)) {
            return taskDefinitionKey.substring(TASK_KEY_PREFIX.length());
        }
        return taskDefinitionKey;
    }

    private static int seqOf(String node) {
        return NODE_DEPT_MGR.equals(node) ? SEQ_DEPT_MGR : SEQ_PURCHASE_MGR;
    }
}
