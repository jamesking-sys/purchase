package com.gov.procurement.modules.budget.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gov.procurement.modules.budget.domain.BudgetSubject;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * budget_subject Mapper。基础 CRUD（逻辑删除自动过滤）+ pg_trgm 模糊搜索 + 预算引用计数 + 叶子金额聚合。
 */
public interface BudgetSubjectMapper extends BaseMapper<BudgetSubject> {

    /**
     * 名称 / 编码模糊搜索（pg_trgm 加速的 ILIKE，仅未删）。
     *
     * @param kw    已包裹 %的关键词（如 %耗材%）
     * @param limit 返回上限
     * @return 命中科目列表，按 level、code 排序
     */
    @Select("SELECT * FROM budget_subject WHERE is_deleted = 0 "
            + "AND (name ILIKE #{kw} OR code ILIKE #{kw}) ORDER BY level, code LIMIT #{limit}")
    List<BudgetSubject> search(@Param("kw") String kw, @Param("limit") int limit);

    /**
     * 统计某科目被 budget_item 引用的条数（>0 即「已挂预算」，用于删除/降级前置校验）。
     *
     * @param subjectId 科目 id
     * @return 引用条数
     */
    @Select("SELECT count(*) FROM budget_item WHERE subject_id = #{subjectId}")
    long countBudgetItemBySubject(@Param("subjectId") Long subjectId);

    /**
     * 按科目聚合叶子预算金额（可按预算过滤；缺省汇总全部预算）。
     *
     * @param budgetId 预算 id，可空
     * @return 行集合，每行含列 subject_id、amount（SUM）
     */
    @Select("<script>SELECT subject_id, SUM(amount) AS amount FROM budget_item "
            + "<if test='budgetId != null'> WHERE budget_id = #{budgetId} </if> "
            + "GROUP BY subject_id</script>")
    List<Map<String, Object>> selectLeafAmounts(@Param("budgetId") Long budgetId);
}
