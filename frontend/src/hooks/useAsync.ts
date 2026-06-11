import { useCallback, useEffect, useRef, useState } from 'react';

/** 异步加载态。reload 重新执行 fn（用于错误重试 / 写操作后刷新）。 */
export interface AsyncState<T> {
  data: T | undefined;
  loading: boolean;
  error: Error | undefined;
  reload: () => void;
}

/**
 * 通用异步加载 Hook：挂载与 deps 变化时执行 fn，统一管理 loading/data/error，并暴露 reload。
 * 用 seq 守卫丢弃过期响应，避免快速重载时的竞态串扰。
 */
export function useAsync<T>(fn: () => Promise<T>, deps: unknown[] = []): AsyncState<T> {
  const [data, setData] = useState<T>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error>();
  const fnRef = useRef(fn);
  fnRef.current = fn;
  const seqRef = useRef(0);

  const reload = useCallback(() => {
    const seq = ++seqRef.current;
    setLoading(true);
    setError(undefined);
    fnRef.current()
      .then((d) => {
        if (seq === seqRef.current) {
          setData(d);
        }
      })
      .catch((e: unknown) => {
        if (seq === seqRef.current) {
          setError(e instanceof Error ? e : new Error(String(e)));
        }
      })
      .finally(() => {
        if (seq === seqRef.current) {
          setLoading(false);
        }
      });
  }, []);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(reload, deps);

  return { data, loading, error, reload };
}
