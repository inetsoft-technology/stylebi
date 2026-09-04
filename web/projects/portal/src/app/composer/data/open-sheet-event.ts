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
import { SheetType } from "./sheet";

export interface OpenSheetEvent {
   type: SheetType;
   assetId: string;
   meta?: boolean;
   // runtime id of the viewsheet a worksheet is being opened from (e.g. the
   // base worksheet link in the composer's bottom status bar), so the new
   // worksheet's sandbox can be linked back to the viewsheet's sandbox.
   vsId?: string;
}
