package com.gov.procurement.modules.org.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gov.procurement.common.BizException;
import com.gov.procurement.common.ErrorCode;
import com.gov.procurement.modules.auth.domain.SysUser;
import com.gov.procurement.modules.auth.mapper.SysUserMapper;
import com.gov.procurement.modules.org.domain.Department;
import com.gov.procurement.modules.org.domain.ProjectGroup;
import com.gov.procurement.modules.org.dto.DepartmentReq;
import com.gov.procurement.modules.org.dto.DepartmentVO;
import com.gov.procurement.modules.org.mapper.DepartmentMapper;
import com.gov.procurement.modules.org.mapper.ProjectGroupMapper;
import com.gov.procurement.modules.org.service.DepartmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 部门服务实现。软删（逻辑删除）+ 未删唯一（应用层 count 校验，DB 部分唯一索引兜底）+ 删除前置子级检查。
 */
@Service
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final ProjectGroupMapper projectGroupMapper;
    private final SysUserMapper sysUserMapper;

    public DepartmentServiceImpl(DepartmentMapper departmentMapper, ProjectGroupMapper projectGroupMapper,
                                 SysUserMapper sysUserMapper) {
        this.departmentMapper = departmentMapper;
        this.projectGroupMapper = projectGroupMapper;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public List<DepartmentVO> list() {
        return departmentMapper.selectList(new LambdaQueryWrapper<Department>().orderByAsc(Department::getId))
                .stream()
                .map(d -> new DepartmentVO(d.getId(), d.getName(), d.getCode()))
                .toList();
    }

    @Override
    public DepartmentVO get(Long id) {
        Department d = requireExisting(id);
        return new DepartmentVO(d.getId(), d.getName(), d.getCode());
    }

    @Override
    @Transactional
    public Long create(DepartmentReq req) {
        ensureCodeUnique(req.code(), null);
        Department d = new Department();
        d.setName(req.name());
        d.setCode(req.code());
        departmentMapper.insert(d);
        return d.getId();
    }

    @Override
    @Transactional
    public void update(Long id, DepartmentReq req) {
        Department d = requireExisting(id);
        ensureCodeUnique(req.code(), id);
        d.setName(req.name());
        d.setCode(req.code());
        departmentMapper.updateById(d);
    }

    @Override
    @Transactional
    public void remove(Long id) {
        requireExisting(id);
        long projectGroups = projectGroupMapper.selectCount(
                new LambdaQueryWrapper<ProjectGroup>().eq(ProjectGroup::getDepartmentId, id));
        long users = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getDepartmentId, id));
        if (projectGroups > 0 || users > 0) {
            throw new BizException(ErrorCode.DELETE_RESTRICTED, "部门下存在项目组或用户，不可删除");
        }
        departmentMapper.deleteById(id);
    }

    private Department requireExisting(Long id) {
        Department d = departmentMapper.selectById(id);
        if (d == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "部门不存在");
        }
        return d;
    }

    private void ensureCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<Department> w = new LambdaQueryWrapper<Department>().eq(Department::getCode, code);
        if (excludeId != null) {
            w.ne(Department::getId, excludeId);
        }
        if (departmentMapper.selectCount(w) > 0) {
            throw new BizException(ErrorCode.CODE_DUPLICATE, "部门编码已存在");
        }
    }
}
