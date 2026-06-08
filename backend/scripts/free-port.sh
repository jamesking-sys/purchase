#!/usr/bin/env bash
# 释放被占用的端口（默认 8080）。正常停止时优雅停机已释放端口；本脚本是残留/孤儿进程的兜底手段。
# 用法： bash scripts/free-port.sh [PORT]
set -u
PORT="${1:-8080}"

if command -v powershell.exe >/dev/null 2>&1; then
  # Windows（git-bash）：复用 PowerShell 版，逻辑一致
  powershell.exe -ExecutionPolicy Bypass -File "$(dirname "$0")/free-port.ps1" -Port "$PORT"
elif command -v lsof >/dev/null 2>&1; then
  pids="$(lsof -ti tcp:"$PORT" 2>/dev/null || true)"
  if [ -z "$pids" ]; then
    echo "端口 $PORT 当前未被占用。"
    exit 0
  fi
  echo "$pids" | xargs -r kill -9
  echo "端口 $PORT 已释放。"
else
  echo "未找到 powershell.exe / lsof，无法自动释放端口 $PORT。" >&2
  exit 1
fi
