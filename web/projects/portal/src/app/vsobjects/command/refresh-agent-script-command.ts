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
import { ViewsheetCommand } from "../../common/viewsheet-client/index";

/**
 * Command telling the client that an agent just wrote new text to a script or calc-field
 * expression. `kind`/`assembly`/`name` mirror `EditorContext`'s own fields, so a dialog matches
 * this command against its own `editorContext` to decide whether it is the one being addressed --
 * see `agent-script-refresh.ts` for that matching logic and the clobber guard built on it.
 */
export interface RefreshAgentScriptCommand extends ViewsheetCommand {
   kind: string;
   assembly?: string;
   name?: string;
   text: string;
}
