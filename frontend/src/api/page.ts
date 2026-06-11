/** MyBatis-Plus IPage 序列化结构（前端仅用 records / total）。 */
export interface Page<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
}
