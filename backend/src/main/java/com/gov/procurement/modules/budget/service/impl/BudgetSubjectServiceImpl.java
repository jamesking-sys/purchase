package com.gov.procurement.modules.budget.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gov.procurement.common.BizException;
import com.gov.procurement.common.ErrorCode;
import com.gov.procurement.modules.budget.domain.BudgetSubject;
import com.gov.procurement.modules.budget.dto.CompareResult;
import com.gov.procurement.modules.budget.dto.ConfirmAddReq;
import com.gov.procurement.modules.budget.dto.SubjectHit;
import com.gov.procurement.modules.budget.dto.SubjectTreeNode;
import com.gov.procurement.modules.budget.mapper.BudgetSubjectMapper;
import com.gov.procurement.modules.budget.service.BudgetSubjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 预算科目树服务实现。树/金额「查询时算不落库」；写操作维护 level 与 is_leaf 不变量（同一事务）。
 */
@Service
public class BudgetSubjectServiceImpl implements BudgetSubjectService {

    private static final int DEFAULT_SEARCH_LIMIT = 50;
    private static final String ROOT_KEY = "_root_";

    private final BudgetSubjectMapper subjectMapper;

    public BudgetSubjectServiceImpl(BudgetSubjectMapper subjectMapper) {
        this.subjectMapper = subjectMapper;
    }

    @Override
    public List<SubjectTreeNode> tree(Long budgetId, boolean lazy, Long parentId) {
        List<BudgetSubject> all = subjectMapper.selectList(
                new LambdaQueryWrapper<BudgetSubject>()
                        .orderByAsc(BudgetSubject::getLevel)
                        .orderByAsc(BudgetSubject::getCode));
        Map<Long, BigDecimal> leafAmounts = loadLeafAmounts(budgetId);

        Map<Long, SubjectTreeNode> nodeById = new HashMap<>();
        for (BudgetSubject s : all) {
            nodeById.put(s.getId(), toNode(s));
        }
        List<SubjectTreeNode> roots = new ArrayList<>();
        for (BudgetSubject s : all) {
            SubjectTreeNode node = nodeById.get(s.getId());
            if (s.getParentId() == null) {
                roots.add(node);
            } else {
                SubjectTreeNode parent = nodeById.get(s.getParentId());
                if (parent != null) {
                    parent.getChildren().add(node);
                }
            }
        }
        for (SubjectTreeNode root : roots) {
            computeAmount(root, leafAmounts);
        }

        if (!lazy) {
            return roots;
        }
        // 懒加载：仅返回请求层级（root 层或指定父的子层），剥离 children
        List<SubjectTreeNode> layer;
        if (parentId == null) {
            layer = roots;
        } else {
            SubjectTreeNode p = nodeById.get(parentId);
            layer = (p == null) ? new ArrayList<>() : new ArrayList<>(p.getChildren());
        }
        for (SubjectTreeNode node : layer) {
            node.setChildren(new ArrayList<>());
        }
        return layer;
    }

    @Override
    public List<SubjectHit> search(String keyword, Integer limit) {
        if (keyword == null || keyword.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "搜索关键词不能为空");
        }
        int lim = (limit == null || limit <= 0) ? DEFAULT_SEARCH_LIMIT : limit;
        List<BudgetSubject> hits = subjectMapper.search("%" + keyword.trim() + "%", lim);
        if (hits.isEmpty()) {
            return List.of();
        }
        Map<Long, BudgetSubject> byId = new HashMap<>();
        for (BudgetSubject s : subjectMapper.selectList(new LambdaQueryWrapper<>())) {
            byId.put(s.getId(), s);
        }
        return hits.stream().map(h -> toHit(h, byId)).toList();
    }

    @Override
    @Transactional
    public Long addChild(Long parentId, String name, String code) {
        if (existsByCode(code)) {
            throw new BizException(ErrorCode.CODE_DUPLICATE, "科目编码已存在");
        }
        int level;
        BudgetSubject parent = null;
        if (parentId != null) {
            parent = subjectMapper.selectById(parentId);
            if (parent == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "父科目不存在");
            }
            if (Boolean.TRUE.equals(parent.getIsLeaf()) && subjectMapper.countBudgetItemBySubject(parentId) > 0) {
                throw new BizException(ErrorCode.DELETE_RESTRICTED, "该叶子科目已挂预算金额，不能再新增子级（金额只挂叶子）");
            }
            level = (parent.getLevel() == null ? 1 : parent.getLevel()) + 1;
        } else {
            level = 1;
        }

        BudgetSubject child = new BudgetSubject();
        child.setParentId(parentId);
        child.setName(name);
        child.setCode(code);
        child.setLevel(level);
        child.setIsLeaf(true);
        subjectMapper.insert(child);

        if (parent != null && Boolean.TRUE.equals(parent.getIsLeaf())) {
            parent.setIsLeaf(false);
            subjectMapper.updateById(parent);
        }
        return child.getId();
    }

    @Override
    public List<CompareResult> compare(List<List<String>> paths) {
        if (paths == null || paths.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "比对路径不能为空");
        }
        Map<String, BudgetSubject> index = new HashMap<>();
        for (BudgetSubject s : subjectMapper.selectList(new LambdaQueryWrapper<>())) {
            index.put(childKey(s.getParentId(), s.getName()), s);
        }
        List<CompareResult> results = new ArrayList<>();
        for (List<String> path : paths) {
            Long parentId = null;
            Long lastId = null;
            boolean matched = true;
            for (String name : path) {
                BudgetSubject node = index.get(childKey(parentId, name));
                if (node == null) {
                    matched = false;
                    break;
                }
                parentId = node.getId();
                lastId = node.getId();
            }
            results.add(matched
                    ? new CompareResult(path, CompareResult.Status.EXISTS, lastId, null)
                    : new CompareResult(path, CompareResult.Status.MISSING, null, suggestCode(path)));
        }
        return results;
    }

    @Override
    @Transactional
    public List<Long> confirmAdd(List<ConfirmAddReq.ConfirmAddItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "确认新增项不能为空");
        }
        List<Long> created = new ArrayList<>();
        for (ConfirmAddReq.ConfirmAddItem item : items) {
            List<String> path = item.path();
            List<String> codes = item.codes();
            Long parentId = null;
            Long lastId = null;
            for (int i = 0; i < path.size(); i++) {
                String name = path.get(i);
                BudgetSubject existing = findChildByName(parentId, name);
                if (existing != null) {
                    parentId = existing.getId();
                    lastId = existing.getId();
                    continue;
                }
                String code = (codes != null && i < codes.size() && codes.get(i) != null && !codes.get(i).isBlank())
                        ? codes.get(i)
                        : suggestCode(path.subList(0, i + 1));
                Long newId = addChild(parentId, name, code);
                parentId = newId;
                lastId = newId;
            }
            created.add(lastId);
        }
        return created;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        BudgetSubject subject = subjectMapper.selectById(id);
        if (subject == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "科目不存在");
        }
        long children = subjectMapper.selectCount(
                new LambdaQueryWrapper<BudgetSubject>().eq(BudgetSubject::getParentId, id));
        if (children > 0) {
            throw new BizException(ErrorCode.DELETE_RESTRICTED, "存在子级科目，不可删除");
        }
        if (subjectMapper.countBudgetItemBySubject(id) > 0) {
            throw new BizException(ErrorCode.DELETE_RESTRICTED, "科目已被预算引用，不可删除");
        }
        subjectMapper.deleteById(id);

        if (subject.getParentId() != null) {
            long siblings = subjectMapper.selectCount(
                    new LambdaQueryWrapper<BudgetSubject>().eq(BudgetSubject::getParentId, subject.getParentId()));
            if (siblings == 0) {
                BudgetSubject parent = subjectMapper.selectById(subject.getParentId());
                if (parent != null) {
                    parent.setIsLeaf(true);
                    subjectMapper.updateById(parent);
                }
            }
        }
    }

    // ---- helpers ----

    private boolean existsByCode(String code) {
        return subjectMapper.selectCount(
                new LambdaQueryWrapper<BudgetSubject>().eq(BudgetSubject::getCode, code)) > 0;
    }

    private BudgetSubject findChildByName(Long parentId, String name) {
        LambdaQueryWrapper<BudgetSubject> w = new LambdaQueryWrapper<BudgetSubject>()
                .eq(BudgetSubject::getName, name);
        if (parentId == null) {
            w.isNull(BudgetSubject::getParentId);
        } else {
            w.eq(BudgetSubject::getParentId, parentId);
        }
        return subjectMapper.selectList(w).stream().findFirst().orElse(null);
    }

    private Map<Long, BigDecimal> loadLeafAmounts(Long budgetId) {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (Map<String, Object> row : subjectMapper.selectLeafAmounts(budgetId)) {
            Object sid = row.get("subject_id");
            Object amt = row.get("amount");
            if (sid != null) {
                map.put(((Number) sid).longValue(), amt == null ? BigDecimal.ZERO : (BigDecimal) amt);
            }
        }
        return map;
    }

    private BigDecimal computeAmount(SubjectTreeNode node, Map<Long, BigDecimal> leafAmounts) {
        if (Boolean.TRUE.equals(node.getIsLeaf()) || node.getChildren().isEmpty()) {
            BigDecimal amt = leafAmounts.getOrDefault(node.getId(), BigDecimal.ZERO);
            node.setAmount(amt);
            return amt;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (SubjectTreeNode child : node.getChildren()) {
            sum = sum.add(computeAmount(child, leafAmounts));
        }
        node.setAmount(sum);
        return sum;
    }

    private SubjectTreeNode toNode(BudgetSubject s) {
        SubjectTreeNode node = new SubjectTreeNode();
        node.setId(s.getId());
        node.setParentId(s.getParentId());
        node.setName(s.getName());
        node.setCode(s.getCode());
        node.setLevel(s.getLevel());
        node.setIsLeaf(s.getIsLeaf());
        return node;
    }

    private SubjectHit toHit(BudgetSubject hit, Map<Long, BudgetSubject> byId) {
        LinkedList<SubjectHit.Ancestor> path = new LinkedList<>();
        BudgetSubject cur = hit;
        while (cur != null) {
            path.addFirst(new SubjectHit.Ancestor(cur.getId(), cur.getName()));
            cur = (cur.getParentId() == null) ? null : byId.get(cur.getParentId());
        }
        return new SubjectHit(hit.getId(), hit.getName(), hit.getCode(), hit.getLevel(), hit.getIsLeaf(), path);
    }

    private String childKey(Long parentId, String name) {
        return (parentId == null ? ROOT_KEY : parentId.toString()) + ' ' + name;
    }

    /** 建议编码（TBD-U5-1 占位）：基于全路径前缀生成确定、唯一、ASCII 的占位码，前端可改后经 confirm-add 提交。 */
    private String suggestCode(List<String> pathPrefix) {
        int hash = String.join("/", pathPrefix).hashCode() & 0x7fffffff;
        return "AUTO_" + Integer.toHexString(hash);
    }
}
