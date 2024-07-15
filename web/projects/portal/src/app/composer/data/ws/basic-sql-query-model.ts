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
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import {
   ConditionItemModel
} from "../../../portal/data/model/datasources/database/vpm/condition/condition-item-model";
import { JoinItem } from "./join-item";
import { SQLQueryDialogColumn } from "./sql-dialog-column";

export class BasicSqlQueryModel {
   tables: {[key: string]: AssetEntry};
   columns: SQLQueryDialogColumn[];
   maxColumnCount: number;
   joins: JoinItem[];
   conditionList: ConditionItemModel[];
   sqlString: string;
   sqlParseResult: string;
   sqlEdited: boolean;
   freeFormSqlEnabled: boolean;
}
