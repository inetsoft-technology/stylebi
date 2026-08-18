/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

/**
 * Names the script location a pane-scoped agent pairing session is bound to.
 * Sent as part of the mint request payload so the server can scope the
 * resulting session to a single script/formula location rather than the
 * whole sheet. The server validates and consumes this field at mint time
 * (see SheetPairingService.validateEditorContext).
 */
export interface EditorContext {
   kind: string;
   assembly?: string;
   name?: string;
   table?: string;
}
