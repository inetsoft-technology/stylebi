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
import { inject } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { ActivatedRouteSnapshot, CanDeactivateFn, RouterStateSnapshot } from "@angular/router";
import { Observable, of } from "rxjs";
import { map } from "rxjs/operators";
import { MessageDialog, MessageDialogType } from "../../../common/util/message-dialog";
import { PresentationOrgSettingsViewComponent } from "./presentation-org-settings-view.component";

export const presentationOrgSaveGuard: CanDeactivateFn<PresentationOrgSettingsViewComponent> = (component: PresentationOrgSettingsViewComponent, currentRoute: ActivatedRouteSnapshot, currentState: RouterStateSnapshot, nextState: RouterStateSnapshot): Observable<boolean> => {
   const dialog = inject(MatDialog);
   const nestedComponent = component.settingsView;

   if(nestedComponent.saveModel.constructor === Object && Object.keys(nestedComponent.saveModel).length > 0) {
      const ref = dialog.open(MessageDialog, {
         data: {
            title: "_#(js:em.settings.presentationChanged)",
            content: "_#(js:em.settings.presentation.confirm)",
            type: MessageDialogType.CONFIRMATION
         }
      });

      return ref.afterClosed().pipe(
         map(result => !!result)
      );
   }

   return of(true);
};