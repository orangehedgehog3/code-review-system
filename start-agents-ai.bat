@echo off

rem ============================================================
rem  AI 路径启动脚本：使用 DeepSeek 大模型完成代码审查
rem  使用前请先在下方填入你的 DeepSeek API Key（留空则自动回退规则引擎）
rem ============================================================
set MODE=ai
set DSK=

call "%~dp0start-agents.bat"
