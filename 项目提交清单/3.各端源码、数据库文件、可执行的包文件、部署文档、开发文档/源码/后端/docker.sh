#!/usr/bin/env bash
# 兼容旧入口：转调 docker compose（勿再 docker run 散装 PG/EMQX）
set -euo pipefail
cd "$(dirname "$0")"
exec docker compose "$@"
