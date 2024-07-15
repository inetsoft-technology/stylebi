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
import { Injectable } from "@angular/core";
import {
   ActivatedRouteSnapshot,
   CanDeactivate,
   RouterStateSnapshot
} from "@angular/router";
import { map } from "rxjs/operators";
import { UsersSettingsPageComponent } from "./users-settings-page.component";
import { MatDialog } from "@angular/material/dialog";
import { Observable, of } from "rxjs";
import { MessageDialog, MessageDialogType } from "../../../../common/util/message-dialog";

@Injectable()
export class UsersSettingsSaveGuard implements CanDeactivate<UsersSettingsPageComponent> {
   constructor(private dialog: MatDialog) {
   }

   canDeactivate(component: UsersSettingsPageComponent, currentRoute: ActivatedRouteSnapshot,
                 currentState: RouterStateSnapshot, nextState?: RouterStateSnapshot): Observable<boolean>
   {
      if(component && component.pageChanged) {
         const ref = this.dialog.open(MessageDialog, {
            data: {
               title: "_#(js:em.settings.userSettingsChanged)",
               content: "_#(js:em.settings.userSettings.confirm)",
               type: MessageDialogType.CONFIRMATION
            }
         });

         return ref.afterClosed().pipe(
            map(result => !!result)
         );
      }

      return of(true);
   }

}
