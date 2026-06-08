package com.gov.procurement.modules.budget.service;

import com.gov.procurement.modules.budget.dto.CompareResult;
import com.gov.procurement.modules.budget.dto.ConfirmAddReq;
import com.gov.procurement.modules.budget.dto.SubjectHit;
import com.gov.procurement.modules.budget.dto.SubjectTreeNode;

import java.util.List;

/**
 * 预算科目树服务：树查询（含金额汇总）、模糊搜索、新增子级、比对、确认新增、删除。
 */
public interface BudgetSubjectService {

    /**
     * 科目树查询。
     *
     * @param budgetId 限定汇总金额的预算（可空=汇总全部）
     * @param lazy     true 时仅返回单层（children 为空）
     * @param parentId lazy 时取该父的单层子节点（空=根层）
     */
    List<SubjectTreeNode> tree(Long budgetId, boolean lazy, Long parentId);

    /** 名称 / 编码模糊搜索，返回命中及其祖先路径。 */
    List<SubjectHit> search(String keyword, Integer limit);

    /** 新增子级（parentId 为空=新建根科目）。返回新科目 id。 */
    Long addChild(Long parentId, String name, String code);

    /** 比对一组科目路径，标记已存在 / 缺失。 */
    List<CompareResult> compare(List<List<String>> paths);

    /** 确认新增缺失项，自上而下补建缺失各级，返回各路径末级 id。 */
    List<Long> confirmAdd(List<ConfirmAddReq.ConfirmAddItem> items);

    /** 删除科目（有子级或被预算引用则拒绝）。 */
    void delete(Long id);
}
