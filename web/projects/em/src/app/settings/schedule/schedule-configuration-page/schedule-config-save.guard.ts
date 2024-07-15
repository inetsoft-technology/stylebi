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
import {
   ActivatedRouteSnapshot,
   CanDeactivate,
   RouterStateSnapshot
} from "@angular/router";
import { ScheduleConfigurationPageComponent } from "./schedule-configuration-page.component";
import { Injectable } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { Observable, of } from "rxjs";
import { Tool } from "../../../../../../shared/util/tool";
import { MessageDialog, MessageDialogType } from "../../../common/util/message-dialog";
import { map } from "rxjs/operators";

@Injectable()
export class ScheduleConfigSaveGuard implements CanDeactivate<ScheduleConfigurationPageComponent> {
   constructor(private dialog: MatDialog) {
   }

   canDeactivate(component: ScheduleConfigurationPageComponent, currentRoute: ActivatedRouteSnapshot,
                 currentState: RouterStateSnapshot, nextState?: RouterStateSnapshot): Observable<boolean> {
      if(component.model && component.resetModel && !Tool.isEquals(component.model, component.resetModel)) {
         const ref = this.dialog.open(MessageDialog, {
            data: {
               title: "_#(js:em.settings.schedule.settingsChanged)",
               content: "_#(js:em.settings.schedule.settings.confirm)",
               type: MessageDialogType.CONFIRMATION
            }
         });

         return ref.afterClosed().pipe(
             map(result => result ? true : false)
         );
      }
      return of(true);
   }
}
