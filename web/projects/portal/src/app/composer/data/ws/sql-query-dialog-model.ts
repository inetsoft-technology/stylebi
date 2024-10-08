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
import {
   AdvancedSqlQueryModel
} from "../../../portal/data/model/datasources/database/query/advanced-sql-query-model";
import { BasicSqlQueryModel } from "./basic-sql-query-model";

export class SqlQueryDialogModel {
   mashUpData: boolean;
   closeDialog?: boolean;
   name: string;
   runtimeId: string;
   dataSources: string[];
   dataSource: string;
   variableNames: string[];
   physicalTablesEnabled: boolean;
   freeFormSqlEnabled: boolean;
   advancedEdit: boolean;
   simpleModel?: BasicSqlQueryModel;
   advancedModel?: AdvancedSqlQueryModel;
   supportsFullOuterJoin: boolean[];
}
