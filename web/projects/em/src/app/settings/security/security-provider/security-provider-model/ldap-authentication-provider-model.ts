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
import { SecurityProviderType } from "./security-provider-type.enum";

export interface LdapAuthenticationProviderModel {
   ldapServer: SecurityProviderType;
   protocol: string;
   hostName: string;
   hostPort: number;
   rootDN: string;
   adminID: string;
   password: string;
   userFilter: string;
   userBase: string;
   userAttr: string;
   mailAttr: string;
   groupFilter: string;
   groupBase: string;
   groupAttr: string;
   roleFilter: string;
   roleBase: string;
   roleAttr: string;
   userRoleFilter: string;
   roleRoleFilter: string;
   groupRoleFilter: string;
   searchTree: boolean;
   sysAdminRoles: string[];
   startTls?: boolean;
}
