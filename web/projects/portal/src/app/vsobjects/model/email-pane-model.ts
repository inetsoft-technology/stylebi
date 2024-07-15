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
import { IdentityId } from "../../../../../em/src/app/settings/security/users/identity-id";
import { EmailAddrDialogModel } from "../../widget/email-dialog/email-addr-dialog-model";

export interface EmailPaneModel {
   toAddress: string;
   ccAddress: string;
   bccAddress?: string;
   fromAddress: string;
   fromAddressEnabled: boolean;
   subject: string;
   message: string;
   userDialogEnabled: boolean;
   emailAddrDialogModel: EmailAddrDialogModel;
   users?: IdentityId[]; // all available userName
   groups?: string[];
   emailGroups?: IdentityId[];
}
