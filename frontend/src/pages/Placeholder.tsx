import { Empty, Typography } from '@douyinfe/semi-ui';

const { Title, Text } = Typography;

/**
 * 业务页占位：U3 仅提供可达的最小占位（标题 + 待实现提示），具体业务逻辑由 U14 前端集成填充。
 */
export default function Placeholder({ title }: { title: string }) {
  return (
    <div>
      <Title heading={4}>{title}</Title>
      <Empty
        style={{ marginTop: 48 }}
        title="待 U14 前端集成实现"
        description={<Text type="tertiary">本页为 U3 外壳提供的路由占位，业务功能将在 U14 阶段填充。</Text>}
      />
    </div>
  );
}
