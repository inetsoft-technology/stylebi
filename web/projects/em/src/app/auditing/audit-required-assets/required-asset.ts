/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { IdentityId } from "../../settings/security/users/identity-id";
import { AssetModel } from "../audit-dependent-assets/asset-model";
import { AuditRecordList, AuditRecordParameters } from "../audit-table-view/audit-record";

export interface RequiredAssetParameters extends AuditRecordParameters {
   users: string[];
   assets: AssetModel[];
}

export interface RequiredAsset {
   dependentType: string;
   dependentName: string;
   dependentUser: string;
   targetType: string;
   targetName: string;
   targetUser: string;
   description: string;
}

export interface RequiredAssetList extends AuditRecordList<RequiredAsset> {
}
