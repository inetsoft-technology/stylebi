---
description: Start a new conversation thread on the active StyleBI deployment. Usage - /stylebi-viz-chat:new [datasource-id]
---

The user wants to start a new conversation thread.

1. If the user provided a datasource ID, treat it as the `datasourceId`. Otherwise omit the argument.
2. Call the MCP tool `new_thread` with `{ datasourceId: "<arg>" }` (or `{}` if no argument).
3. The tool returns `{ threadId, summary }`. Print `summary` verbatim.
4. If no `datasourceId` was given AND the user hasn't set one yet for this thread, suggest calling `list_datasources` to see available options, then `set_thread_datasource` to pick one before creating charts.
