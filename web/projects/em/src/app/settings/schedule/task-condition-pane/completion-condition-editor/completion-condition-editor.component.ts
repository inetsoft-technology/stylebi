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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";
import { CompletionConditionModel } from "../../../../../../../shared/schedule/model/completion-condition-model";
import { ScheduleTaskNamesService } from "../../../../../../../shared/schedule/schedule-task-names.service";
import { NameLabelTuple } from "../../../../../../../shared/util/name-label-tuple";
import { TaskConditionChanges } from "../task-condition-pane.component";

@Component({
   selector: "em-completion-condition-editor",
   templateUrl: "./completion-condition-editor.component.html",
   styleUrls: ["./completion-condition-editor.component.scss"]
})
export class CompletionConditionEditorComponent implements OnInit {
   @Input() originalTaskName: string = null;
   @Output() modelChanged = new EventEmitter<TaskConditionChanges>();

   @Input()
   get condition(): CompletionConditionModel {
      return this._condition;
   }

   set condition(val: CompletionConditionModel) {
      this._condition = Object.assign({}, val);
      this.form.get("task").setValue(this._condition.taskName);
   }

   form: UntypedFormGroup;
   private _condition: CompletionConditionModel;
   allTasks: NameLabelTuple[] = [];

   constructor(fb: UntypedFormBuilder, private scheduleTaskNamesService: ScheduleTaskNamesService) {
      this.form = fb.group({
         task: ["", [Validators.required]]
      });
   }

   ngOnInit() {
      this.scheduleTaskNamesService.getAllTasks().subscribe((allTasks) => {
         this.allTasks = allTasks;

         if(!!!this.condition?.taskName && this.allTasks) {
            let firstTask = this.allTasks.find(task => task?.name != this.originalTaskName);

            if(firstTask) {
               this.condition.taskName = firstTask.name;
               this.form.setValue({ task: firstTask.name });
               this.fireModelChanged();
            }
         }
      });

      if(!this.form.valid) {
         //propogate an initial invalid state caused by resource visibility
         this.fireModelChanged();
      }
   }

   fireModelChanged() {
      this.condition.taskName = this.form.get("task").value;
      this.modelChanged.emit({
         valid: this.form.valid,
         model: this.condition
      });
   }

   get loadingTasks(): boolean {
      return this.scheduleTaskNamesService.isLoading;
   }
}
