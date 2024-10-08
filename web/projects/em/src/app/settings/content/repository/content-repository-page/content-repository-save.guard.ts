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
import { MatDialog } from "@angular/material/dialog";
import { Observable, of } from "rxjs";
import { map } from "rxjs/operators";
import { MessageDialog, MessageDialogType } from "../../../../common/util/message-dialog";
import { ContentRepositoryPageComponent } from "./content-repository-page.component";

@Injectable()
export class ContentRepositorySaveGuard implements CanDeactivate<ContentRepositoryPageComponent> {
   constructor(private dialog: MatDialog) {
   }

   canDeactivate(component: ContentRepositoryPageComponent, currentRoute: ActivatedRouteSnapshot,
                 currentState: RouterStateSnapshot, nextState?: RouterStateSnapshot): Observable<boolean>
   {
      if(component.unsavedChanges) {
         const ref = this.dialog.open(MessageDialog, {
            data: {
               title: "_#(js:em.settings.repositorySettingsChanged)",
               content: "_#(js:em.settings.repositorySettings.confirm)",
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
