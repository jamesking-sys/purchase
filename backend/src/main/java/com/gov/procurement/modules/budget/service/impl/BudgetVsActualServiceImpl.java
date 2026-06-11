package com.gov.procurement.modules.budget.service.impl;

import com.gov.procurement.common.BizException;
import com.gov.procurement.common.ErrorCode;
import com.gov.procurement.modules.budget.domain.Budget;
import com.gov.procurement.modules.budget.domain.BudgetSubject;
import com.gov.procurement.modules.budget.dto.BudgetVsActualVO;
import com.gov.procurement.modules.budget.dto.BudgetVsActualVO.RowVO;
import com.gov.procurement.modules.budget.dto.SubjectAmountRow;
import com.gov.procurement.modules.budget.mapper.BudgetMapper;
import com.gov.procurement.modules.budget.mapper.BudgetSubjectMapper;
import com.gov.procurement.modules.budget.mapper.BudgetVsActualMapper;
import com.gov.procurement.modules.budget.service.BudgetVsActualService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 预算 vs 实际读模型实现（U13）。两段聚合 SQL（预算侧 / 实际侧）+ 内存以预算科目为基准左连接合并；
 * 金额统一两位小数（HALF_UP），超支仅置标识不拦截。{@code @Transactional(readOnly=true)} 保证零副作用。
 */
@Service
public class BudgetVsActualServiceImpl implements BudgetVsActualService {

    private static final int MONEY_SCALE = 2;

    private final BudgetMapper budgetMapper;
    private final BudgetVsActualMapper vsActualMapper;
    private final BudgetSubjectMapper budgetSubjectMapper;

    public BudgetVsActualServiceImpl(BudgetMapper budgetMapper, BudgetVsActualMapper vsActualMapper,
                                     BudgetSubjectMapper budgetSubjectMapper) {
        this.budgetMapper = budgetMapper;
        this.vsActualMapper = vsActualMapper;
        this.budgetSubjectMapper = budgetSubjectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public BudgetVsActualVO getVsActual(Long budgetId) {
        Budget budget = budgetMapper.selectById(budgetId);
        if (budget == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "预算不存在");
        }

        // 预算侧为基准（TreeMap 按 subjectId 升序稳定输出）；实际侧无对应科目则 actual=0（AC-3）。
        Map<Long, BigDecimal> budgetedMap = new TreeMap<>(toMap(vsActualMapper.aggregateBudgetBySubject(budgetId)));
        Map<Long, BigDecimal> actualMap = toMap(vsActualMapper.aggregateActualBySubject(budgetId));
        Map<Long, BudgetSubject> subjects = loadSubjects(budgetedMap.keySet());

        List<RowVO> rows = new ArrayList<>(budgetedMap.size());
        BigDecimal totalBudgeted = BigDecimal.ZERO;
        BigDecimal totalActual = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> e : budgetedMap.entrySet()) {
            Long subjectId = e.getKey();
            BigDecimal budgeted = scale(e.getValue());
            BigDecimal actual = scale(actualMap.getOrDefault(subjectId, BigDecimal.ZERO));
            BigDecimal remaining = budgeted.subtract(actual);
            BudgetSubject s = subjects.get(subjectId);
            rows.add(new RowVO(subjectId,
                    s != null ? s.getName() : null,
                    s != null ? s.getCode() : null,
                    budgeted, actual, remaining, remaining.signum() < 0));
            totalBudgeted = totalBudgeted.add(budgeted);
            totalActual = totalActual.add(actual);
        }

        return new BudgetVsActualVO(budget.getId(), budget.getName(), rows,
                scale(totalBudgeted), scale(totalActual), scale(totalBudgeted.subtract(totalActual)));
    }

    private Map<Long, BigDecimal> toMap(List<SubjectAmountRow> rows) {
        return rows.stream().collect(Collectors.toMap(SubjectAmountRow::getSubjectId, SubjectAmountRow::getAmount));
    }

    private Map<Long, BudgetSubject> loadSubjects(java.util.Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return budgetSubjectMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(BudgetSubject::getId, Function.identity()));
    }

    private BigDecimal scale(BigDecimal v) {
        return v.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
