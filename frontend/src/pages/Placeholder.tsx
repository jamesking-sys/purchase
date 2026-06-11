import { useLocation } from 'react-router-dom';
import { Empty, Typography } from '@douyinfe/semi-ui';
import PageHeader from '../layout/PageHeader';
import { domainTitleOf, findNavItem } from '../layout/navConfig';

const { Text } = Typography;

/**
 * 业务页占位：U3 提供可达占位并对齐原型骨架（面包屑 + 渐变横幅 + 卡片），具体业务由 U14 前端集成填充。
 * 主色随当前域由 AppLayout 注入（CSS 变量 --stage），故各占位页已呈现对应阶段配色。
 */
export default function Placeholder({ title }: { title: string }) {
  const { pathname } = useLocation();
  const item = findNavItem(pathname);
  const domainTitle = item ? domainTitleOf(item.domain) : '功能';

  return (
    <>
      <PageHeader domainTitle={domainTitle} title={title} />
      <div className="pms-card">
        <Empty
          title="待 U14 前端集成实现"
          description={
            <Text type="tertiary">本页为 U3 外壳提供的路由占位，业务功能将在 U14 阶段按原型填充。</Text>
          }
          style={{ padding: '24px 0' }}
        />
      </div>
    </>
  );
}
