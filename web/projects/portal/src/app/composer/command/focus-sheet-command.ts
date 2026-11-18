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

/**
 * Hint that an agent just edited the sheet runtime `runtimeId`, so a Composer whose Follow Focus
 * checkbox is ticked may switch to that sheet's tab. Never opens a tab; the Composer decides
 * whether to act. See `FollowFocusService.isFollowTabsEnabled` and
 * `ComposerMainComponent.initComposerClient`.
 */
export interface FocusSheetCommand {
   type: "focusSheet";
   runtimeId: string;
}
