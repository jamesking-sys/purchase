package com.gov.procurement.modules.purchase.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gov.procurement.common.BizException;
import com.gov.procurement.common.ErrorCode;
import com.gov.procurement.modules.budget.domain.Budget;
import com.gov.procurement.modules.budget.domain.BudgetSubject;
import com.gov.procurement.modules.budget.mapper.BudgetMapper;
import com.gov.procurement.modules.budget.mapper.BudgetSubjectMapper;
import com.gov.procurement.modules.org.mapper.ProjectGroupMapper;
import com.gov.procurement.modules.purchase.domain.PurchaseItem;
import com.gov.procurement.modules.purchase.domain.PurchaseOrder;
import com.gov.procurement.modules.purchase.dto.CreatePurchaseOrderReq;
import com.gov.procurement.modules.purchase.dto.ItemReq;
import com.gov.procurement.modules.purchase.dto.PurchaseItemVO;
import com.gov.procurement.modules.purchase.dto.PurchaseOrderDetailVO;
import com.gov.procurement.modules.purchase.dto.PurchaseOrderVO;
import com.gov.procurement.modules.purchase.mapper.PurchaseItemMapper;
import com.gov.procurement.modules.purchase.mapper.PurchaseOrderMapper;
import com.gov.procurement.modules.purchase.service.DeliveryNoteService;
import com.gov.procurement.modules.purchase.service.PurchaseOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.gov.procurement.modules.purchase.constant.PurchaseConst.BUDGET_APPROVED;
import static com.gov.procurement.modules.purchase.constant.PurchaseConst.ST_EXECUTING;

/**
 * 采购单服务实现。创建在单事务内写主单 + 明细；只读校验来源预算（approved）与明细科目（已有叶子级），不写 M2。
 */
@Service
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderServiceImpl.class);

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseItemMapper purchaseItemMapper;
    private final BudgetMapper budgetMapper;
    private final BudgetSubjectMapper budgetSubjectMapper;
    private final ProjectGroupMapper projectGroupMapper;
    private final DeliveryNoteService deliveryNoteService;

    public PurchaseOrderServiceImpl(PurchaseOrderMapper purchaseOrderMapper, PurchaseItemMapper purchaseItemMapper,
                                    BudgetMapper budgetMapper, BudgetSubjectMapper budgetSubjectMapper,
                                    ProjectGroupMapper projectGroupMapper, DeliveryNoteService deliveryNoteService) {
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.purchaseItemMapper = purchaseItemMapper;
        this.budgetMapper = budgetMapper;
        this.budgetSubjectMapper = budgetSubjectMapper;
        this.projectGroupMapper = projectGroupMapper;
        this.deliveryNoteService = deliveryNoteService;
    }

    @Override
    @Transactional
    public PurchaseOrderVO createOrder(CreatePurchaseOrderReq req) {
        Budget budget = budgetMapper.selectById(req.budgetId());
        if (budget == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "来源预算不存在");
        }
        if (!BUDGET_APPROVED.equals(budget.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "预算非已通过状态，不可执行采购");
        }
        if (projectGroupMapper.selectById(req.projectGroupId()) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "项目组不存在");
        }
        validateSubjects(req.items());

        PurchaseOrder order = new PurchaseOrder();
        order.setBudgetId(req.budgetId());
        order.setProjectGroupId(req.projectGroupId());
        order.setSupplierName(req.supplierName());
        order.setContractNo(req.contractNo());
        order.setStatus(ST_EXECUTING);
        purchaseOrderMapper.insert(order);

        List<PurchaseItem> items = req.items().stream().map(i -> {
            PurchaseItem item = new PurchaseItem();
            item.setPurchaseOrderId(order.getId());
            item.setSubjectId(i.subjectId());
            item.setMaterialName(i.materialName());
            item.setQty(i.qty());
            item.setAmount(i.amount());
            return item;
        }).toList();
        purchaseItemMapper.insertBatch(items);

        log.info("创建采购单 orderId={}, budgetId={}, items={}", order.getId(), req.budgetId(), items.size());
        return new PurchaseOrderVO(order.getId(), order.getBudgetId(), order.getProjectGroupId(),
                order.getSupplierName(), order.getContractNo(), order.getStatus(), order.getCreatedAt(),
                loadItems(order.getId()));
    }

    @Override
    public PurchaseOrderDetailVO getDetail(Long orderId) {
        PurchaseOrder order = purchaseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "采购单不存在");
        }
        return new PurchaseOrderDetailVO(order.getId(), order.getBudgetId(), order.getProjectGroupId(),
                order.getSupplierName(), order.getContractNo(), order.getStatus(), order.getCreatedAt(),
                loadItems(orderId), deliveryNoteService.listByOrder(orderId));
    }

    @Override
    public Page<PurchaseOrderVO> listOrders(String status, Long projectGroupId, int pageNum, int size) {
        int current = pageNum < 1 ? 1 : pageNum;
        int limit = (size < 1 || size > MAX_PAGE_SIZE) ? DEFAULT_PAGE_SIZE : size;

        LambdaQueryWrapper<PurchaseOrder> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(PurchaseOrder::getStatus, status);
        }
        if (projectGroupId != null) {
            wrapper.eq(PurchaseOrder::getProjectGroupId, projectGroupId);
        }
        wrapper.orderByDesc(PurchaseOrder::getId);

        Page<PurchaseOrder> result = purchaseOrderMapper.selectPage(new Page<>(current, limit), wrapper);
        List<PurchaseOrderVO> records = result.getRecords().stream()
                .map(o -> new PurchaseOrderVO(o.getId(), o.getBudgetId(), o.getProjectGroupId(),
                        o.getSupplierName(), o.getContractNo(), o.getStatus(), o.getCreatedAt(), null))
                .toList();
        Page<PurchaseOrderVO> page = new Page<>(current, limit, result.getTotal());
        page.setRecords(records);
        return page;
    }

    private void validateSubjects(List<ItemReq> items) {
        List<Long> ids = items.stream().map(ItemReq::subjectId).distinct().toList();
        // MyBatis-Plus 逻辑删除自动追加 is_deleted=0，故仅返回未删科目。
        Map<Long, BudgetSubject> byId = budgetSubjectMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(BudgetSubject::getId, Function.identity()));
        for (Long id : ids) {
            BudgetSubject subject = byId.get(id);
            if (subject == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "采购明细科目不存在：" + id);
            }
            if (!Boolean.TRUE.equals(subject.getIsLeaf())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "采购明细科目非叶子级：" + id);
            }
        }
    }

    private List<PurchaseItemVO> loadItems(Long orderId) {
        return purchaseItemMapper.selectList(new LambdaQueryWrapper<PurchaseItem>()
                        .eq(PurchaseItem::getPurchaseOrderId, orderId)
                        .orderByAsc(PurchaseItem::getId)).stream()
                .map(it -> new PurchaseItemVO(it.getId(), it.getSubjectId(), it.getMaterialName(),
                        it.getQty(), it.getAmount(), it.getReceivedQty()))
                .toList();
    }
}
