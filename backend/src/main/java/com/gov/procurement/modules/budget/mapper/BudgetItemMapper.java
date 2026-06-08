package com.gov.procurement.modules.budget.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gov.procurement.modules.budget.domain.BudgetItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * budget_item Mapper（批量写明细）。
 */
public interface BudgetItemMapper extends BaseMapper<BudgetItem> {

    /**
     * 单条多行 INSERT 批量写入预算明细。
     *
     * @param items 明细列表（budgetId/subjectId/amount）
     * @return 写入行数
     */
    @Insert("<script>INSERT INTO budget_item(budget_id, subject_id, amount) VALUES "
            + "<foreach collection='items' item='it' separator=','>"
            + "(#{it.budgetId}, #{it.subjectId}, #{it.amount})"
            + "</foreach></script>")
    int insertBatch(@Param("items") List<BudgetItem> items);
}
