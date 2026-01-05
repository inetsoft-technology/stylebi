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
import { Tool } from "../../../../../../shared/util/tool";
import { MessageDialog, MessageDialogType } from "../../../common/util/message-dialog";
import { ScheduleTaskEditorPageComponent } from "./schedule-task-editor-page.component";

export const scheduleSaveGuard: CanDeactivateFn<ScheduleTaskEditorPageComponent> = (component: ScheduleTaskEditorPageComponent, currentRoute: ActivatedRouteSnapshot, currentState: RouterStateSnapshot, nextState: RouterStateSnapshot): Observable<boolean> => {
   const dialog = inject(MatDialog);

   if(component.originalModel && component.model &&
      (!Tool.isEquals(component.originalModel, component.model) || component.form.value["taskName"] !== component.model.label))
   {
      const ref = dialog.open(MessageDialog, {
         data: {
            title: "_#(js:em.scheduler.taskchanged)", //make general keystring for both em and portal
            content: "_#(js:em.scheduler.unsave.confirm)",
            type: MessageDialogType.CONFIRMATION
         }
      });

      return ref.afterClosed().pipe(map((value) => !!value));
   }

   return of(true);
};