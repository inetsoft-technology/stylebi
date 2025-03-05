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
import { DatabaseInfoModel } from "./database-info-model";
import { NetworkLocationModel } from "./network-location-model";
import { AuthenticationDetailsModel } from "./authentication-details-model";
import { DatasourceDatabaseType } from "./datasource-database-type";

export interface DatabaseDefinitionModel {
   name: string;
   // string referring to DatabaseType enum name
   type: DatasourceDatabaseType;
   info: DatabaseInfoModel;
   network: NetworkLocationModel;
   authentication: AuthenticationDetailsModel;
   deletable: boolean;
   description?: string;
   unasgn?: boolean;
   ansiJoin: boolean;
   transactionIsolation: number;
   tableNameOption: number;
   defaultDatabase: string;
   changeDefaultDB: boolean;
   cloudError?: string;
}