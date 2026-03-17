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
import { Injectable } from "@angular/core";
import {
   ActivatedRouteSnapshot,
   CanDeactivate,
   RouterStateSnapshot
} from "@angular/router";
import { catchError, map, switchMap } from "rxjs/operators";
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
      if(component && component.hasIncompleteNewUser) {
         const ref = this.dialog.open(MessageDialog, {
            data: {
               title: "_#(js:em.users.newUser.incompleteTitle)",
               content: "_#(js:em.users.newUser.incompleteContent)",
               type: MessageDialogType.CONFIRMATION
            }
         });

         return ref.afterClosed().pipe(
            switchMap(result => {
               if(result) {
                  return component.clearIncompleteNewUser(false).pipe(
                     map(() => true),
                     catchError(() => of(false))
                  );
               }

               return of(false);
            }),
            switchMap(canLeave => {
               if(canLeave && component.pageChanged) {
                  return this.confirmPageChanged();
               }

               return of(canLeave);
            })
         );
      }

      if(component && component.pageChanged) {
         return this.confirmPageChanged();
      }

      return of(true);
   }

   private confirmPageChanged(): Observable<boolean> {
      const ref = this.dialog.open(MessageDialog, {
         data: {
            title: "_#(js:em.settings.userSettingsChanged)",
            content: "_#(js:em.settings.userSettings.confirm)",
            type: MessageDialogType.CONFIRMATION
         }
      });

      return ref.afterClosed().pipe(map(result => !!result));
   }

}
