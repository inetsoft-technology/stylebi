#!/usr/bin/env bash
set -eu

# Under HTTP MCP + OAuth, the MCP client owns the StyleBI token; there is no
# local credentials file to inspect. Login is a browser OAuth flow that the
# /stylebi-viz-chat:login command starts on demand.
MSG="[stylebi-viz-chat] StyleBI is reached over HTTP MCP with OAuth handled by your MCP client. To create or modify StyleBI visualizations, use the viz-chat skill. If the viz-chat tools are unavailable, the server needs authentication: run /stylebi-viz-chat:login to start the OAuth flow (it opens a browser and waits until you are authenticated)."

# Claude Code: context via stderr
printf '%s\n' "$MSG" >&2

# Claude Code / Codex: context via JSON stdout
if command -v jq >/dev/null 2>&1; then
  jq -n --arg ctx "$MSG" '{"hookSpecificOutput":{"hookEventName":"SessionStart","additionalContext":$ctx}}'
else
  printf '{"hookSpecificOutput":{"hookEventName":"SessionStart","additionalContext":"%s"}}\n' \
    "$(printf '%s' "$MSG" | sed 's/\\/\\\\/g; s/"/\\"/g')"
fi
