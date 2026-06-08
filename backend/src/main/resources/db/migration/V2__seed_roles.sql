-- =====================================================================
-- U1 基线种子：6 个内置角色（与 docs/design/db/procurement-db.md §5 一致）
-- 默认管理员账号在 U2（认证/权限）实现时由 ApplicationRunner 用 BCrypt 创建，
-- 避免在迁移里硬编码无法校验的口令哈希。
-- =====================================================================
INSERT INTO role (name, code) VALUES
  ('编制人',   'editor'),
  ('采购主管', 'purchase_mgr'),
  ('部门主管', 'dept_mgr'),
  ('仓管员',   'warehouse'),
  ('领用人',   'requester'),
  ('管理员',   'admin');
