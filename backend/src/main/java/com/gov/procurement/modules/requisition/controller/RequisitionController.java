package com.gov.procurement.modules.requisition.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gov.procurement.common.Result;
import com.gov.procurement.modules.requisition.dto.ApproveOutboundVO;
import com.gov.procurement.modules.requisition.dto.CreateRequisitionCmd;
import com.gov.procurement.modules.requisition.dto.CreateRequisitionVO;
import com.gov.procurement.modules.requisition.dto.RejectReq;
import com.gov.procurement.modules.requisition.dto.RejectVO;
import com.gov.procurement.modules.requisition.dto.RequisitionDetailVO;
import com.gov.procurement.modules.requisition.dto.RequisitionTodoVO;
import com.gov.procurement.modules.requisition.dto.RequisitionVO;
import com.gov.procurement.modules.requisition.dto.StockOptionVO;
import com.gov.procurement.modules.requisition.service.RequisitionService;
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
 * 领用与出库接口（U11）：发起领用（requester）/ 待办、审批出库、驳回（warehouse）/ 查询（requester|warehouse）。
 */
@RestController
@RequestMapping("/api/requisitions")
public class RequisitionController {

    private static final String ROLE_REQUESTER = "requester";
    private static final String ROLE_WAREHOUSE = "warehouse";

    private final RequisitionService requisitionService;

    public RequisitionController(RequisitionService requisitionService) {
        this.requisitionService = requisitionService;
    }

    @SaCheckRole(ROLE_REQUESTER)
    @GetMapping("/stock-options")
    public Result<List<StockOptionVO>> stockOptions(@RequestParam Long projectGroupId) {
        return Result.ok(requisitionService.stockOptions(projectGroupId));
    }

    @SaCheckRole(ROLE_REQUESTER)
    @PostMapping
    public Result<CreateRequisitionVO> create(@Valid @RequestBody CreateRequisitionCmd cmd) {
        return Result.ok(requisitionService.create(cmd));
    }

    @SaCheckRole(ROLE_WAREHOUSE)
    @GetMapping("/todo")
    public Result<Page<RequisitionTodoVO>> todo(@RequestParam(required = false) Long projectGroupId,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return Result.ok(requisitionService.todo(projectGroupId, page, size));
    }

    @SaCheckRole(ROLE_WAREHOUSE)
    @PostMapping("/{id}/approve-outbound")
    public Result<ApproveOutboundVO> approveOutbound(@PathVariable Long id) {
        return Result.ok(requisitionService.approveOutbound(id));
    }

    @SaCheckRole(ROLE_WAREHOUSE)
    @PostMapping("/{id}/reject")
    public Result<RejectVO> reject(@PathVariable Long id, @Valid @RequestBody RejectReq req) {
        return Result.ok(requisitionService.reject(id, req));
    }

    @SaCheckRole(value = {ROLE_REQUESTER, ROLE_WAREHOUSE}, mode = SaMode.OR)
    @GetMapping
    public Result<Page<RequisitionVO>> list(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) Long projectGroupId,
                                            @RequestParam(required = false) Long applicantId,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(requisitionService.list(status, projectGroupId, applicantId, page, size));
    }

    @SaCheckRole(value = {ROLE_REQUESTER, ROLE_WAREHOUSE}, mode = SaMode.OR)
    @GetMapping("/{id}")
    public Result<RequisitionDetailVO> detail(@PathVariable Long id) {
        return Result.ok(requisitionService.getDetail(id));
    }
}
