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

import { IdentityModel } from "../../security-table-view/identity-model";
import { PropertyModel } from "../../property-table-view/property-model";
import {IdentityId} from "../identity-id";

export interface EditGroupPaneModel extends EditIdentityPaneModel {
}

export interface EditRolePaneModel extends EditIdentityPaneModel {
   defaultRole: boolean;
   isSysAdmin: boolean;
   isOrgAdmin: boolean;
   description?: string;
   enterprise?: boolean;
}

export interface EditOrganizationPaneModel extends EditIdentityPaneModel {
   id: string;
   properties: PropertyModel[];
   locale?: string;
   localesList: string[];
   currentUserName: string;
}

export interface EditUserPaneModel extends EditIdentityPaneModel {
   status: boolean;
   alias?: string;
   email?: string;
   locale?: string;
   password?: string;
   currentUser: boolean;
   localesList: string[];
}

export interface EditIdentityPaneModel {
   name: string;
   organization: string;
   root: boolean;
   identityNames: IdentityId[];
   members: IdentityModel[]; //groups and users
   roles: IdentityId[];
   permittedIdentities: IdentityModel[];
   editable: boolean;
   oldName?: string;
   theme?: string;
}
