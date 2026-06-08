-- =====================================================================
-- U1 数据库基线 · 采购项目管理系统
-- 来源：docs/design/db/procurement-db.md（PostgreSQL）
-- 约定：主键 IDENTITY；金额 NUMERIC(18,2)；数量 NUMERIC(18,3)；时间 TIMESTAMPTZ。
-- 软删除：主数据用 is_deleted(0/1)，对齐 MyBatis-Plus 逻辑删除配置；
--        "未删唯一"用部分唯一索引 WHERE is_deleted = 0。
-- 业务单据不软删，用 status 含作废。Flowable ACT_* 表由引擎自管，不在此。
-- =====================================================================

-- updated_at 自动刷新触发器函数
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN NEW.updated_at = now(); RETURN NEW; END; $$ LANGUAGE plpgsql;

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ============ BC1 组织与权限 ============
CREATE TABLE department (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name        VARCHAR(128) NOT NULL,
  code        VARCHAR(64)  NOT NULL,
  is_deleted  SMALLINT     NOT NULL DEFAULT 0,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_department_code ON department(code) WHERE is_deleted = 0;
CREATE TRIGGER trg_department_updated BEFORE UPDATE ON department FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE role (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name        VARCHAR(64) NOT NULL,
  code        VARCHAR(64) NOT NULL,
  is_deleted  SMALLINT    NOT NULL DEFAULT 0,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_role_code ON role(code) WHERE is_deleted = 0;
CREATE TRIGGER trg_role_updated BEFORE UPDATE ON role FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE project_group (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name          VARCHAR(128) NOT NULL,
  code          VARCHAR(64)  NOT NULL,
  department_id BIGINT       NOT NULL REFERENCES department(id),
  is_deleted    SMALLINT     NOT NULL DEFAULT 0,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_pg_code ON project_group(code) WHERE is_deleted = 0;
CREATE INDEX idx_pg_dept ON project_group(department_id);
CREATE TRIGGER trg_pg_updated BEFORE UPDATE ON project_group FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE sys_user (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name          VARCHAR(64)  NOT NULL,
  account       VARCHAR(64)  NOT NULL,
  password_hash VARCHAR(128) NOT NULL,
  department_id BIGINT       NOT NULL REFERENCES department(id),
  is_deleted    SMALLINT     NOT NULL DEFAULT 0,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_user_account ON sys_user(account) WHERE is_deleted = 0;
CREATE INDEX idx_user_dept ON sys_user(department_id);
CREATE TRIGGER trg_user_updated BEFORE UPDATE ON sys_user FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE user_role (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id    BIGINT NOT NULL REFERENCES sys_user(id),
  role_id    BIGINT NOT NULL REFERENCES role(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_user_role ON user_role(user_id, role_id);
CREATE INDEX idx_ur_role ON user_role(role_id);

-- ============ BC2 预算与科目 ============
CREATE TABLE budget_subject (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  parent_id   BIGINT REFERENCES budget_subject(id),
  name        VARCHAR(128) NOT NULL,
  code        VARCHAR(64)  NOT NULL,
  level       SMALLINT     NOT NULL DEFAULT 1,
  is_leaf     BOOLEAN      NOT NULL DEFAULT true,
  is_deleted  SMALLINT     NOT NULL DEFAULT 0,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_subject_code ON budget_subject(code) WHERE is_deleted = 0;
CREATE INDEX idx_subject_parent ON budget_subject(parent_id);
CREATE INDEX idx_subject_name_trgm ON budget_subject USING gin (name gin_trgm_ops);
CREATE TRIGGER trg_subject_updated BEFORE UPDATE ON budget_subject FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE budget (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  project_group_id BIGINT       NOT NULL REFERENCES project_group(id),
  name             VARCHAR(128) NOT NULL,
  source_doc_path  VARCHAR(512),
  version          INT          NOT NULL DEFAULT 1,
  status           VARCHAR(32)  NOT NULL DEFAULT 'draft'
                   CHECK (status IN ('draft','submitted','approved','rejected')),
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_budget_pg ON budget(project_group_id);
CREATE INDEX idx_budget_status ON budget(status);
CREATE TRIGGER trg_budget_updated BEFORE UPDATE ON budget FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE budget_item (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  budget_id   BIGINT NOT NULL REFERENCES budget(id) ON DELETE RESTRICT,
  subject_id  BIGINT NOT NULL REFERENCES budget_subject(id),
  amount      NUMERIC(18,2) NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_bi ON budget_item(budget_id, subject_id);
CREATE INDEX idx_bi_budget ON budget_item(budget_id);
CREATE INDEX idx_bi_subject ON budget_item(subject_id);
CREATE TRIGGER trg_bi_updated BEFORE UPDATE ON budget_item FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============ BC3 审批（Flowable 业务投影） ============
CREATE TABLE approval (
  id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  biz_type            VARCHAR(32) NOT NULL,
  biz_id              BIGINT      NOT NULL,
  flow_code           VARCHAR(32),
  process_instance_id VARCHAR(64),
  current_node        VARCHAR(32) NOT NULL,
  status              VARCHAR(32) NOT NULL DEFAULT 'draft'
                      CHECK (status IN ('draft','pending_purchase_mgr','pending_dept_mgr','approved','rejected')),
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_approval_biz ON approval(biz_type, biz_id);
CREATE INDEX idx_approval_status ON approval(status);
CREATE INDEX idx_approval_pi ON approval(process_instance_id);
CREATE TRIGGER trg_approval_updated BEFORE UPDATE ON approval FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE approval_record (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  approval_id BIGINT      NOT NULL REFERENCES approval(id) ON DELETE RESTRICT,
  node_seq    SMALLINT    NOT NULL,
  node        VARCHAR(32) NOT NULL,
  task_id     VARCHAR(64),
  approver_id BIGINT      NOT NULL REFERENCES sys_user(id),
  action      VARCHAR(16) NOT NULL CHECK (action IN ('approve','reject')),
  opinion     VARCHAR(512),
  acted_at    TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ar_approval ON approval_record(approval_id);

-- ============ BC5 库存与资产（先于引用它的入库/出库/盘点明细建表） ============
CREATE TABLE stock_item (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  material_name    VARCHAR(128) NOT NULL,
  project_group_id BIGINT       NOT NULL REFERENCES project_group(id),
  department_id    BIGINT       NOT NULL REFERENCES department(id),
  quantity         NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (quantity >= 0),
  asset_no         VARCHAR(64),
  original_value   NUMERIC(18,2),
  life_status      VARCHAR(32),
  is_deleted       SMALLINT     NOT NULL DEFAULT 0,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_stock ON stock_item(material_name, project_group_id) WHERE is_deleted = 0;
CREATE INDEX idx_stock_pg ON stock_item(project_group_id);
CREATE TRIGGER trg_stock_updated BEFORE UPDATE ON stock_item FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE stock_txn (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  stock_item_id BIGINT        NOT NULL,  -- 逻辑外键
  type          VARCHAR(16)   NOT NULL CHECK (type IN ('inbound','outbound','gain','loss')),
  qty_change    NUMERIC(18,3) NOT NULL,
  ref_type      VARCHAR(32)   NOT NULL,
  ref_id        BIGINT        NOT NULL,
  created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_txn_stock ON stock_txn(stock_item_id, created_at);
CREATE INDEX idx_txn_ref ON stock_txn(ref_type, ref_id);

-- ============ BC4 采购与入库 ============
CREATE TABLE purchase_order (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  budget_id        BIGINT      NOT NULL REFERENCES budget(id),
  project_group_id BIGINT      NOT NULL REFERENCES project_group(id),
  supplier_name    VARCHAR(128),
  contract_no      VARCHAR(64),
  status           VARCHAR(32) NOT NULL DEFAULT 'executing'
                   CHECK (status IN ('executing','inbounded','void')),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_po_budget ON purchase_order(budget_id);
CREATE INDEX idx_po_pg ON purchase_order(project_group_id);
CREATE INDEX idx_po_status ON purchase_order(status);
CREATE TRIGGER trg_po_updated BEFORE UPDATE ON purchase_order FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE purchase_item (
  id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  purchase_order_id BIGINT NOT NULL REFERENCES purchase_order(id) ON DELETE RESTRICT,
  subject_id        BIGINT NOT NULL REFERENCES budget_subject(id),
  material_name     VARCHAR(128) NOT NULL,
  qty               NUMERIC(18,3) NOT NULL,
  received_qty      NUMERIC(18,3) NOT NULL DEFAULT 0,
  amount            NUMERIC(18,2) NOT NULL,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_received CHECK (received_qty >= 0 AND received_qty <= qty)
);
CREATE INDEX idx_pi_po ON purchase_item(purchase_order_id);
CREATE INDEX idx_pi_subject ON purchase_item(subject_id);
CREATE TRIGGER trg_pi_updated BEFORE UPDATE ON purchase_item FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE delivery_note (
  id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  purchase_order_id BIGINT NOT NULL REFERENCES purchase_order(id) ON DELETE RESTRICT,
  file_path         VARCHAR(512) NOT NULL,
  uploaded_by       BIGINT NOT NULL REFERENCES sys_user(id),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_dn_po ON delivery_note(purchase_order_id);

CREATE TABLE inbound_order (
  id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  purchase_order_id BIGINT NOT NULL REFERENCES purchase_order(id) ON DELETE RESTRICT,
  received_by       BIGINT NOT NULL REFERENCES sys_user(id),
  inbound_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_io_po ON inbound_order(purchase_order_id);

CREATE TABLE inbound_item (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  inbound_order_id BIGINT NOT NULL REFERENCES inbound_order(id) ON DELETE RESTRICT,
  purchase_item_id BIGINT NOT NULL REFERENCES purchase_item(id),
  stock_item_id    BIGINT NOT NULL REFERENCES stock_item(id),
  received_qty     NUMERIC(18,3) NOT NULL,
  project_group_id BIGINT NOT NULL REFERENCES project_group(id),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ii_inbound ON inbound_item(inbound_order_id);
CREATE INDEX idx_ii_pi ON inbound_item(purchase_item_id);
CREATE INDEX idx_ii_stock ON inbound_item(stock_item_id);

-- ============ BC5 盘点 ============
CREATE TABLE stocktake (
  id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  scope_project_group_id BIGINT      NOT NULL REFERENCES project_group(id),
  status                 VARCHAR(32) NOT NULL DEFAULT 'counting'
                         CHECK (status IN ('counting','confirmed')),
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_st_pg ON stocktake(scope_project_group_id);
CREATE INDEX idx_st_status ON stocktake(status);
CREATE TRIGGER trg_st_updated BEFORE UPDATE ON stocktake FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE stocktake_item (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  stocktake_id  BIGINT NOT NULL REFERENCES stocktake(id) ON DELETE RESTRICT,
  stock_item_id BIGINT NOT NULL REFERENCES stock_item(id),
  book_qty      NUMERIC(18,3) NOT NULL,
  actual_qty    NUMERIC(18,3) NOT NULL,
  diff          NUMERIC(18,3) NOT NULL,
  diff_type     VARCHAR(16) NOT NULL CHECK (diff_type IN ('gain','loss','none')),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_sti_stocktake ON stocktake_item(stocktake_id);
CREATE INDEX idx_sti_stock ON stocktake_item(stock_item_id);

-- ============ BC6 领用与出库 ============
CREATE TABLE requisition (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  project_group_id BIGINT      NOT NULL REFERENCES project_group(id),
  applicant_id     BIGINT      NOT NULL REFERENCES sys_user(id),
  purpose          VARCHAR(512),
  status           VARCHAR(32) NOT NULL DEFAULT 'pending_warehouse'
                   CHECK (status IN ('pending_warehouse','outbound','rejected')),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_req_pg ON requisition(project_group_id);
CREATE INDEX idx_req_applicant ON requisition(applicant_id);
CREATE INDEX idx_req_status ON requisition(status);
CREATE TRIGGER trg_req_updated BEFORE UPDATE ON requisition FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE requisition_item (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  requisition_id BIGINT NOT NULL REFERENCES requisition(id) ON DELETE RESTRICT,
  stock_item_id  BIGINT NOT NULL REFERENCES stock_item(id),
  qty            NUMERIC(18,3) NOT NULL,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ri_req ON requisition_item(requisition_id);
CREATE INDEX idx_ri_stock ON requisition_item(stock_item_id);

CREATE TABLE outbound_order (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  requisition_id BIGINT NOT NULL REFERENCES requisition(id),
  approver_id    BIGINT NOT NULL REFERENCES sys_user(id),
  outbound_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_ob_req ON outbound_order(requisition_id);

CREATE TABLE outbound_item (
  id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  outbound_order_id BIGINT NOT NULL REFERENCES outbound_order(id) ON DELETE RESTRICT,
  stock_item_id     BIGINT NOT NULL REFERENCES stock_item(id),
  qty               NUMERIC(18,3) NOT NULL,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_oi_ob ON outbound_item(outbound_order_id);
CREATE INDEX idx_oi_stock ON outbound_item(stock_item_id);
