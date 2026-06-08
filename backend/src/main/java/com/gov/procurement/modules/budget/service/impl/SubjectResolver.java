package com.gov.procurement.modules.budget.service.impl;

import com.gov.procurement.modules.budget.domain.BudgetSubject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 科目映射解析器：导入开始时一次性预载未删科目，建编码索引与全路径索引，避免逐行查库（详设 §5.3）。
 * 匹配优先级：科目编码非空用 byCode；否则用 byPath（全路径名「L1 / L2 / L3」精确匹配）。
 */
final class SubjectResolver {

    private static final String SEP = " / ";

    private final Map<String, BudgetSubject> byCode = new HashMap<>();
    private final Map<String, BudgetSubject> byPath = new HashMap<>();

    private SubjectResolver() {
    }

    static SubjectResolver from(List<BudgetSubject> all) {
        Map<Long, BudgetSubject> byId = new HashMap<>();
        for (BudgetSubject s : all) {
            byId.put(s.getId(), s);
        }
        SubjectResolver resolver = new SubjectResolver();
        for (BudgetSubject s : all) {
            if (s.getCode() != null && !s.getCode().isBlank()) {
                resolver.byCode.put(s.getCode().trim(), s);
            }
            resolver.byPath.put(fullPath(s, byId), s);
        }
        return resolver;
    }

    BudgetSubject resolve(String code, String path) {
        if (code != null && !code.isBlank()) {
            return byCode.get(code.trim());
        }
        if (path != null && !path.isBlank()) {
            return byPath.get(normalizePath(path));
        }
        return null;
    }

    private static String fullPath(BudgetSubject subject, Map<Long, BudgetSubject> byId) {
        LinkedList<String> names = new LinkedList<>();
        BudgetSubject cur = subject;
        while (cur != null) {
            names.addFirst(cur.getName() == null ? "" : cur.getName().trim());
            cur = (cur.getParentId() == null) ? null : byId.get(cur.getParentId());
        }
        return String.join(SEP, names);
    }

    private static String normalizePath(String path) {
        List<String> parts = new ArrayList<>();
        for (String p : path.split("/")) {
            String t = p.trim();
            if (!t.isEmpty()) {
                parts.add(t);
            }
        }
        return String.join(SEP, parts);
    }
}
