/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { PhysicalTableType } from "./physical-table-type.enum";
import { AutoAliasJoinModel } from "./auto-alias-join-model";
import { JoinModel } from "./join-model";

export interface PhysicalTableModel {
   name: string;
   catalog: string;
   schema: string;
   qualifiedName: string;
   path: string;
   alias: string;
   sql: string;
   type: PhysicalTableType;
   joins: JoinModel[];
   autoAliases?: AutoAliasJoinModel[];
   autoAliasesEnabled?: boolean;
   aliasSource?: string;
   baseTable: boolean;
   outgoingAliasSource?: string;
}