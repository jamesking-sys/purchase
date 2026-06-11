import type { ReactNode } from 'react';

/**
 * 页面头：面包屑 chip（域）+ 渐变横幅（标题）。主色经父级 CSS 变量 `--stage` 继承（由 AppLayout 按当前域注入）。
 * 业务页（U14）复用本组件即可与原型一致。
 */
export default function PageHeader({
  domainTitle,
  title,
  pill = 'P0',
  extra,
}: {
  domainTitle: string;
  title: string;
  pill?: string;
  extra?: ReactNode;
}) {
  return (
    <>
      <div className="pms-crumb">
        <span className="pms-chip">{domainTitle}</span>
        {title}
      </div>
      <h2 className="pms-banner">
        {title}
        {pill ? <span className="pms-pill">{pill}</span> : null}
        {extra}
      </h2>
    </>
  );
}
