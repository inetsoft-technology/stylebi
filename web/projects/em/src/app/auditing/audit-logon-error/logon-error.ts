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
import { AuditRecordList, AuditRecordParameters } from "../audit-table-view/audit-record";
import {IdentityId} from "../../settings/security/users/identity-id";

export interface LogonErrorParameters extends AuditRecordParameters {
   users: string[];
   hosts: string[];
   organizations: string[];
   systemAdministrator: boolean;
}

export interface LogonError {
   server: string;
   userName: string;
   userHost: string;
   logonTime: number;
   errorMessage: string;
   organizationId: string;
}

export interface LogonErrorList extends AuditRecordList<LogonError> {
}
