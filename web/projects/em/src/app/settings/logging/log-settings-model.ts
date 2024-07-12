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
import { LogLevelDTO } from "./LogLevelDTO";

export interface FileLogSettingsModel {
   file: string;
   maxLogSize: number;
   count: number;
}

export interface FluentdLogSettingsModel {
   port: number;
   host: string;
   connectTimeout: number;
   securityEnabled: boolean;
   sharedKey: string;
   userAuthenticationEnabled: boolean;
   orgAdminAccess: boolean;
   username: string;
   password: string;
   tlsEnabled: boolean;
   caCertificateFile: string;
   logViewUrl: string;
   auditViewUrl: string;
}

export interface LogSettingsModel {
   provider: string;
   fileSettings: FileLogSettingsModel;
   fluentdSettings: FluentdLogSettingsModel;
   outputToStd: boolean;
   detailLevel: string;
   logLevels: LogLevelDTO[];
}