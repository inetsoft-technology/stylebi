---
description: Save the current sheet's edits to the StyleBI asset repository. Usage - /stylebi-composer-chat:save [name]
---

A Composer session may have a viewsheet connected, a worksheet connected, or both at once. Check
`status` (or what you already know from this conversation) to see which, and save each connected
sheet with its own tool — they have different save semantics.

**Viewsheet — `save_viewsheet`:**

1. Call `save_viewsheet` with no arguments. There is no save-as here: the viewsheet must already
   have a name. If the tool errors that it's unsaved, tell the user to name it once in the
   Composer, then retry — do not invent a name argument for it, the tool doesn't take one.
2. Print the returned confirmation verbatim.

**Worksheet — `save_worksheet`:**

1. **Backstop check — tidy the canvas if it was never done.** `auto_layout` should normally
   already have been called right after a multi-table build finished (see the `composer-chat`
   skill's worksheet workflow) — don't wait for a save request to do it in the first place. But if
   this session added multiple tables/joins since connecting and `auto_layout` was *not* already
   called, call it now before saving. New assemblies land wherever the server drops them, and
   skipping this leaves the canvas cluttered for whoever opens it next. Skip it for a small,
   single-table tweak to an already-organized worksheet.
2. **Determine whether this worksheet has ever been saved.** `save_worksheet`'s `name` argument is
   required the first time an untitled worksheet is saved (optional afterwards — omitting it saves
   in place, providing it triggers Save As). A worksheet that has never been saved typically shows
   a `runtimeId` like `Untitled-N-M` from `connect_sheet` — if you saw that (or otherwise have no
   indication this worksheet already has a saved name/path), treat it as untitled.
3. If untitled: use `$ARGUMENTS` as the name if the user supplied one; otherwise ask the user what
   to name it (and optionally which scope — `global` for the shared repository, `user` for their
   private folder) before proceeding. Do not call `save_worksheet` without a name for an untitled
   worksheet — it will error.
4. Call the MCP tool `save_worksheet`, passing `name` (and `scope` if given) when saving an
   untitled worksheet for the first time. For an already-saved worksheet, omit `name` unless the
   user explicitly wants a Save As under a new name.
5. Print the returned confirmation verbatim.

If only one sheet type is connected, do only that half. Remind the user the edits are now
persisted and will survive a browser reload.
