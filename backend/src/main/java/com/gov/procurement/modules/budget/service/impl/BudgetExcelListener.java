package com.gov.procurement.modules.budget.service.impl;

import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.event.AnalysisEventListener;
import com.gov.procurement.modules.budget.domain.BudgetItem;
import com.gov.procurement.modules.budget.domain.BudgetSubject;
import com.gov.procurement.modules.budget.dto.BudgetRow;
import com.gov.procurement.modules.budget.dto.ErrorRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 预算明细流式监听器（FastExcel）。逐行校验（§5.2），错误累加进 errorRows **不中断**（AC-8），合法行进 validItems。
 * 列头缺失在 {@link #invokeHeadMap} 检出（致命，整批 42201）。
 */
class BudgetExcelListener extends AnalysisEventListener<BudgetRow> {

    private static final List<String> REQUIRED_HEADERS = List.of("科目路径", "科目编码", "金额");
    private static final int MAX_AMOUNT_SCALE = 2;

    private final SubjectResolver resolver;
    private final List<ErrorRow> errorRows = new ArrayList<>();
    private final List<BudgetItem> validItems = new ArrayList<>();
    private final Set<Long> seenSubjectIds = new HashSet<>();
    private final List<String> missingHeaders = new ArrayList<>();
    private boolean headerValid = true;
    private boolean hasNonLeafError = false;

    BudgetExcelListener(SubjectResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
        Set<String> headers = new HashSet<>();
        for (String h : headMap.values()) {
            if (h != null) {
                headers.add(h.trim());
            }
        }
        for (String required : REQUIRED_HEADERS) {
            if (!headers.contains(required)) {
                headerValid = false;
                missingHeaders.add(required);
            }
        }
    }

    @Override
    public void invoke(BudgetRow row, AnalysisContext context) {
        if (!headerValid) {
            return;
        }
        int rowNo = context.readRowHolder().getRowIndex();
        String path = trimToNull(row.getSubjectPath());
        String code = trimToNull(row.getSubjectCode());
        String amountRaw = row.getAmount();
        String locate = code != null ? code : path;

        if (path == null && code == null) {
            addError(rowNo, locate, amountRaw, "科目路径或编码至少填一项");
            return;
        }
        BigDecimal amount = parseAmount(amountRaw);
        if (amount == null) {
            addError(rowNo, locate, amountRaw, "金额必填且须为正数");
            return;
        }
        BudgetSubject subject = resolver.resolve(code, path);
        if (subject == null) {
            addError(rowNo, locate, amountRaw, "科目不存在");
            return;
        }
        if (!Boolean.TRUE.equals(subject.getIsLeaf())) {
            hasNonLeafError = true;
            addError(rowNo, locate, amountRaw, "金额只能录在叶子级科目");
            return;
        }
        if (!seenSubjectIds.add(subject.getId())) {
            addError(rowNo, locate, amountRaw, "科目重复");
            return;
        }
        validItems.add(new BudgetItem(null, subject.getId(), amount));
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // 解析完成；Service 据 headerValid / errorRows 决定落库或返回错误清单
    }

    boolean isHeaderValid() {
        return headerValid;
    }

    List<String> getMissingHeaders() {
        return missingHeaders;
    }

    boolean hasNonLeafError() {
        return hasNonLeafError;
    }

    List<ErrorRow> getErrorRows() {
        return errorRows;
    }

    List<BudgetItem> getValidItems() {
        return validItems;
    }

    private void addError(int rowNo, String locate, String amountRaw, String reason) {
        errorRows.add(new ErrorRow(rowNo, locate, amountRaw, reason));
    }

    private BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            BigDecimal bd = new BigDecimal(raw.trim());
            if (bd.signum() <= 0) {
                return null;
            }
            if (bd.stripTrailingZeros().scale() > MAX_AMOUNT_SCALE) {
                return null;
            }
            return bd.setScale(MAX_AMOUNT_SCALE, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
