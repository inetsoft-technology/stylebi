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
import { MatDialog } from "@angular/material/dialog";
import { Observable } from "rxjs";
import { CanComponentDeactivate } from "../../../../../../shared/util/guard/can-component-deactivate";
import { MessageDialog, MessageDialogType } from "../../../common/util/message-dialog";

export abstract class ProviderDetailPage implements CanComponentDeactivate {
   private changed: boolean;

   protected constructor(private dialog: MatDialog) {
   }

   public canDeactivate(): Observable<boolean> | Promise<boolean> | boolean {
      return !this.changed || this.dialog.open(MessageDialog, {
         data: {
            title: "_#(js:Confirm)",
            content: "_#(js:em.security.provider.changed)",
            type: MessageDialogType.CONFIRMATION
         }
      }).afterClosed().toPromise().then(result => result == true);
   }

   public onChanged(changed: boolean) {
      this.changed = changed;
   }
}
