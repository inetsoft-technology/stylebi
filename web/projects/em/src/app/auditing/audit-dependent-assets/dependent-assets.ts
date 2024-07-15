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
import { AuditRecordList, AuditRecordParameters } from "../audit-table-view/audit-record";
import { AssetModel } from "./asset-model";

export interface DependentAssetParameters extends AuditRecordParameters {
   users: string[];
   assets: AssetModel[];
}

export interface DependentAsset {
   targetType: string;
   targetName: string;
   targetUser: string;
   dependentType: string;
   dependentName: string;
   dependentUser: string;
   description: string;
   organizationId: string;
}

export interface DependentAssetList extends AuditRecordList<DependentAsset> {
}
