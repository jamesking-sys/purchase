/** 后端统一响应包装（与 com.gov.procurement.common.Result 一致）。 */
export interface Result<T> {
  code: number;
  message: string;
  data: T;
}

/** 当前用户（对齐 U2 GET /api/auth/me）。 */
export interface MeResp {
  userId: number;
  account: string;
  name: string;
  departmentId: number;
  roles: string[];
}

/** 登录响应（对齐 U2 POST /api/auth/login）。 */
export interface LoginResp {
  token: string;
  userId: number;
}
