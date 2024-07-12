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
import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from "@angular/core";
import { ScheduleTaskDialogModel } from "../../../../../../shared/schedule/model/schedule-task-dialog-model";
import { UntypedFormControl, UntypedFormGroup, ValidationErrors, Validators } from "@angular/forms";
import { NotificationsComponent } from "../../../widget/notifications/notifications.component";

const SAVE_TASK_MESSAGE = "_#(js:em.schedule.task.saveSuccess)";

@Component({
   selector: "schedule-task-dialog",
   templateUrl: "schedule-task-dialog.component.html",
   styleUrls: ["./schedule-task-dialog.component.scss"]
})
export class ScheduleTaskDialog implements OnInit {
   @Input() model: ScheduleTaskDialogModel;
   @ViewChild("notifications") notifications: NotificationsComponent;
   @Output() onCommit: EventEmitter<ScheduleTaskDialogModel> =
      new EventEmitter<ScheduleTaskDialogModel>();
   showLoading: boolean = false;
   form: UntypedFormGroup;

   ngOnInit(): void {
      this.form = new UntypedFormGroup({
         "name": new UntypedFormControl(this.model.label, [Validators.required,
            this.invalidTaskName])
      });
   }

   public updateLoading(load: boolean): void {
      this.showLoading = load;
   }

   public enterSubmit(): boolean {
      return true;
   }

   close(): void {
      this.onCommit.emit(this.model);
   }

   private invalidTaskName(control: UntypedFormControl): ValidationErrors {
      if(!!control && !/^[A-Za-z0-9$ &@+_:.\-[\]]+$/.test(control.value)) {
         return {invalidTaskName: true};
      }

      return null;
   }

   updateOldTaskName(taskName: string): void {
      let oldName = this.model.name;

      if(oldName == null || oldName.indexOf(":") == -1) {
         return;
      }

      let idx = oldName.indexOf(":");
      let user = oldName.substring(0, idx);
      this.model.name = user + ":" + taskName;
   }

   saveSuccess() {
      this.notifications.success(SAVE_TASK_MESSAGE);
   }
}
