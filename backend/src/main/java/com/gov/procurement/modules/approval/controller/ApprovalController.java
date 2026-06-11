package com.gov.procurement.modules.approval.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gov.procurement.common.Result;
import com.gov.procurement.modules.approval.dto.HistoryItemVO;
import com.gov.procurement.modules.approval.dto.RejectReq;
import com.gov.procurement.modules.approval.dto.SubmitApprovalReq;
import com.gov.procurement.modules.approval.dto.TodoItemVO;
import com.gov.procurement.modules.approval.service.ApprovalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 通用审批接口（U7）：提交 / 待办 / 通过 / 驳回 / 流转历史。统一前缀 /api/approvals，均需登录。
 * 提交限编制人；通过/驳回限采购主管或部门主管（粗粒度），细粒度节点-角色比对在服务层。
 */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private static final String ROLE_PURCHASE_MGR = "purchase_mgr";
    private static final String ROLE_DEPT_MGR = "dept_mgr";

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @SaCheckRole("editor")
    @PostMapping
    public Result<Long> submit(@Valid @RequestBody SubmitApprovalReq req) {
        return Result.ok(approvalService.submit(req));
    }

    @SaCheckRole(value = {ROLE_PURCHASE_MGR, ROLE_DEPT_MGR}, mode = SaMode.OR)
    @GetMapping("/todo")
    public Result<Page<TodoItemVO>> todo(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return Result.ok(approvalService.todo(page, size));
    }

    @SaCheckRole(value = {ROLE_PURCHASE_MGR, ROLE_DEPT_MGR}, mode = SaMode.OR)
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        approvalService.approve(id);
        return Result.ok();
    }

    @SaCheckRole(value = {ROLE_PURCHASE_MGR, ROLE_DEPT_MGR}, mode = SaMode.OR)
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @Valid @RequestBody RejectReq req) {
        approvalService.reject(id, req);
        return Result.ok();
    }

    @SaCheckLogin
    @GetMapping("/{id}/history")
    public Result<List<HistoryItemVO>> history(@PathVariable Long id) {
        return Result.ok(approvalService.history(id));
    }
}
