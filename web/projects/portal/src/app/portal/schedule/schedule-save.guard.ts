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
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable, of as observableOf, Subject } from "rxjs";
import { ScheduleConditionModel } from "../../../../../shared/schedule/model/schedule-condition-model";
import { ScheduleTaskDialogModel } from "../../../../../shared/schedule/model/schedule-task-dialog-model";
import { TimeConditionModel } from "../../../../../shared/schedule/model/time-condition-model";
import { Tool } from "../../../../../shared/util/tool";
import { ScheduleTaskEditorComponent } from "./schedule-task-editor/schedule-task-editor.component";
import { ComponentTool } from "../../common/util/component-tool";

@Injectable()
export class ScheduleSaveGuard implements CanDeactivate<ScheduleTaskEditorComponent> {
   constructor(private modalService: NgbModal) {
   }

   canDeactivate(component: ScheduleTaskEditorComponent, currentRoute: ActivatedRouteSnapshot,
                 currentState: RouterStateSnapshot,
                 nextState?: RouterStateSnapshot): Observable<boolean>
   {
      let result: Observable<boolean>;
      this.clearZoneForRangeCondition(component.originalModel);
      this.clearZoneForRangeCondition(component.model);

      if(component.originalModel && component.model &&
         !Tool.isEquals(component.originalModel, component.model))
      {
         const subject = new Subject<boolean>();
         result = subject.asObservable();
         ComponentTool.showConfirmDialog(this.modalService, "_#(js:portal.schedule.tastchanged)",
            "_#(js:portal.schedule.unsave.confirm)").then(
            (response) => {
               subject.next(response === "ok");
               subject.complete();
            },
            (error) => subject.error(error)
         );
      }
      else {
         result = observableOf(true);
      }

      return result;
   }

   clearZoneForRangeCondition(model: ScheduleTaskDialogModel) {
      if(model == null || model.taskConditionPaneModel == null ||
         model.taskConditionPaneModel.conditions == null)
      {
         return;
      }

      for(let i = 0; i < model.taskConditionPaneModel.conditions.length; i++) {
         let cond: ScheduleConditionModel = model.taskConditionPaneModel.conditions[i];

         if(cond.conditionType == "TimeCondition") {
            (<TimeConditionModel>cond).timeZone = null;
         }
      }
   }
}