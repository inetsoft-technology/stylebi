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
import { AssetType } from "../../../../../../shared/data/asset-type";

export interface WorksheetBrowserInfo {
   name: string;
   path: string;
   type: AssetType;
   scope: number;
   description: string;
   id?: string;
   createdBy: string;
   createdDate: number;
   createdDateLabel: string;
   modifiedDate: number;
   dateFormat: string;
   modifiedDateLabel: string;
   editable: boolean;
   deletable: boolean;
   materialized: boolean;
   canMaterialize: boolean;
   parentPath?: string;
   parentFolderCount?: number;
   hasSubFolder?: number;
   workSheetType?: number;
}
