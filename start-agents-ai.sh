#!/usr/bin/env bash
# ============================================================
#  AI 路径启动脚本：使用 DeepSeek 大模型完成代码审查
#  使用前请先在下方填入你的 DeepSeek API Key（留空则自动回退规则引擎）
# ============================================================
export MODE=ai
export DSK=""

exec "$(dirname "$0")/start-agents.sh"
