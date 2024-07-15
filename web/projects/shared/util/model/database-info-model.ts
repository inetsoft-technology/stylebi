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
export interface DatabaseInfoModel {
   productName?: string;
   poolProperties?: { [name: string]: string };
   customUrl?: string;
   customEditMode?: boolean;
}

export interface CustomDatabaseInfoModel extends DatabaseInfoModel {
   driverClass?: string;
   jdbcUrl?: string;
   testQuery?: string;
}

export interface AccessDatabaseInfoModel extends DatabaseInfoModel {
   dataSourceName?: string;
   testQuery?: string;
}

export interface ODBCDatabaseInfoModel extends DatabaseInfoModel {
   dataSourceName?: string;
   testQuery?: string;
}

export interface OracleDatabaseInfoModel extends DatabaseInfoModel {
   sid?: string;
}

export interface SQLServerDatabaseInfoModel extends DatabaseNameInfoModel {
   instanceName?: string;
}

export interface DatabaseNameInfoModel extends DatabaseInfoModel {
   databaseName?: string;
   properties?:string;
}

export interface InformixDatabaseInfoModel extends DatabaseNameInfoModel {
   serverName?: string;
   databaseLocale?: string;
}

export interface PostgreSQLDatabaseInfoModel extends DatabaseNameInfoModel {
}

export interface SybaseDatabaseInfoModel extends DatabaseNameInfoModel {
}

export interface DB2DatabaseInfoModel extends DatabaseNameInfoModel {
}

export interface MySQLDatabaseInfo extends DatabaseNameInfoModel {
}
