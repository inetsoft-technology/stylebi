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
export interface DatabaseAuthenticationProviderModel {
   driver: string;
   url: string;
   requiresLogin: boolean;
   useCredential: boolean;
   secretId: string;
   user: string;
   password: string;
   hashAlgorithm: string;
   userQuery: string;
   groupListQuery: string;
   userListQuery: string;
   groupUsersQuery: string;
   roleListQuery: string;
   organizationListQuery: string;
   organizationNameQuery: string;
   organizationMembersQuery: string,
   organizationRolesQuery: string,
   userRolesQuery: string;
   userRoleListQuery: string;
   appendSalt: boolean;
   userEmailsQuery: string;
   sysAdminRoles: string;
   orgAdminRoles: string;
}