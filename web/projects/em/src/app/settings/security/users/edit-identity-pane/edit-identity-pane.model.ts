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

/*
 * Copyright (c) 2018, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
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
