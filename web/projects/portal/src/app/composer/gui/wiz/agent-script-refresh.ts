/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
import { EditorContext } from "./editor-context";
import { RefreshAgentScriptCommand } from "../../../vsobjects/command/refresh-agent-script-command";

/**
 * Whether `command` -- a push naming the script/expression an agent just wrote -- names the
 * EXACT location `context` is editing. `assembly`/`name` are compared null/undefined-tolerantly
 * (a kind with no third component sends neither), mirroring `FollowFocusService`'s own
 * `sameEditorContext` comparison.
 */
export function matchesAgentScriptRefresh(command: RefreshAgentScriptCommand,
                                           context: EditorContext | null | undefined): boolean
{
   if(!command || !context || command.kind !== context.kind) {
      return false;
   }

   return (command.assembly || null) === (context.assembly || null) &&
      (command.name || null) === (context.name || null);
}

/**
 * Whether it is safe to apply an agent's pushed text onto a dialog's bound field without
 * clobbering an unsaved human edit.
 *
 * Safe exactly when the field's current text still equals the text last known to match the
 * server (set when the dialog opened, or the last time a push was actually applied) -- i.e. the
 * human has not typed anything since. Once the two diverge, the human is mid-editing and this
 * returns `false` so the caller can surface the incoming text as a "stale, click to reload"
 * affordance instead of silently overwriting what they typed (bug #80447 symptom 1's own
 * refutation addendum: the existing `RefreshBindingTreeCommand`/`SetViewsheetInfoCommand`
 * precedents only ever replace read-only state, so neither of their guards -- tab-focus -- applies
 * here; this is the guard built specifically for EDITABLE dialog state).
 */
export function shouldApplyAgentScriptPush(currentText: string, lastKnownText: string): boolean {
   return (currentText ?? "") === (lastKnownText ?? "");
}
