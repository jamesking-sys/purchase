package com.gov.procurement.modules.org.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gov.procurement.common.BizException;
import com.gov.procurement.common.ErrorCode;
import com.gov.procurement.modules.org.domain.Department;
import com.gov.procurement.modules.org.domain.ProjectGroup;
import com.gov.procurement.modules.org.dto.ProjectGroupReq;
import com.gov.procurement.modules.org.dto.ProjectGroupVO;
import com.gov.procurement.modules.org.mapper.DepartmentMapper;
import com.gov.procurement.modules.org.mapper.ProjectGroupMapper;
import com.gov.procurement.modules.org.service.ProjectGroupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 项目组服务实现。创建 / 更新校验所属部门存在；删除前置「被业务单据引用」检查（RESTRICT）。
 */
@Service
public class ProjectGroupServiceImpl implements ProjectGroupService {

    private final ProjectGroupMapper projectGroupMapper;
    private final DepartmentMapper departmentMapper;

    public ProjectGroupServiceImpl(ProjectGroupMapper projectGroupMapper, DepartmentMapper departmentMapper) {
        this.projectGroupMapper = projectGroupMapper;
        this.departmentMapper = departmentMapper;
    }

    @Override
    public List<ProjectGroupVO> list(Long departmentId) {
        LambdaQueryWrapper<ProjectGroup> w = new LambdaQueryWrapper<ProjectGroup>().orderByAsc(ProjectGroup::getId);
        if (departmentId != null) {
            w.eq(ProjectGroup::getDepartmentId, departmentId);
        }
        List<ProjectGroup> groups = projectGroupMapper.selectList(w);
        Map<Long, String> deptNames = loadDepartmentNames(groups.stream().map(ProjectGroup::getDepartmentId).toList());
        return groups.stream()
                .map(pg -> toVO(pg, deptNames.get(pg.getDepartmentId())))
                .toList();
    }

    @Override
    public ProjectGroupVO get(Long id) {
        ProjectGroup pg = requireExisting(id);
        Department dept = departmentMapper.selectById(pg.getDepartmentId());
        return toVO(pg, dept == null ? null : dept.getName());
    }

    @Override
    @Transactional
    public Long create(ProjectGroupReq req) {
        requireDepartment(req.departmentId());
        ensureCodeUnique(req.code(), null);
        ProjectGroup pg = new ProjectGroup();
        pg.setName(req.name());
        pg.setCode(req.code());
        pg.setDepartmentId(req.departmentId());
        projectGroupMapper.insert(pg);
        return pg.getId();
    }

    @Override
    @Transactional
    public void update(Long id, ProjectGroupReq req) {
        ProjectGroup pg = requireExisting(id);
        requireDepartment(req.departmentId());
        ensureCodeUnique(req.code(), id);
        pg.setName(req.name());
        pg.setCode(req.code());
        pg.setDepartmentId(req.departmentId());
        projectGroupMapper.updateById(pg);
    }

    @Override
    @Transactional
    public void remove(Long id) {
        requireExisting(id);
        if (projectGroupMapper.countBusinessReferences(id) > 0) {
            throw new BizException(ErrorCode.DELETE_RESTRICTED, "项目组已被业务单据引用，不可删除");
        }
        projectGroupMapper.deleteById(id);
    }

    private ProjectGroup requireExisting(Long id) {
        ProjectGroup pg = projectGroupMapper.selectById(id);
        if (pg == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "项目组不存在");
        }
        return pg;
    }

    private void requireDepartment(Long departmentId) {
        if (departmentMapper.selectById(departmentId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "所属部门不存在");
        }
    }

    private void ensureCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<ProjectGroup> w = new LambdaQueryWrapper<ProjectGroup>().eq(ProjectGroup::getCode, code);
        if (excludeId != null) {
            w.ne(ProjectGroup::getId, excludeId);
        }
        if (projectGroupMapper.selectCount(w) > 0) {
            throw new BizException(ErrorCode.CODE_DUPLICATE, "项目组编码已存在");
        }
    }

    private Map<Long, String> loadDepartmentNames(List<Long> departmentIds) {
        List<Long> distinct = departmentIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return departmentMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));
    }

    private ProjectGroupVO toVO(ProjectGroup pg, String departmentName) {
        return new ProjectGroupVO(pg.getId(), pg.getName(), pg.getCode(), pg.getDepartmentId(), departmentName);
    }
}
