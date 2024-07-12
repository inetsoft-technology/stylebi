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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { TaskConditionPaneModel } from "../../../../../../../shared/schedule/model/task-condition-pane-model";
import {
   TimeConditionModel,
   TimeConditionType, TimeRange
} from "../../../../../../../shared/schedule/model/time-condition-model";
import { TimeZoneModel } from "../../../../../../../shared/schedule/model/time-zone-model";
import { DateTimeService } from "../date-time.service";
import { TaskConditionChanges } from "../task-condition-pane.component";

@Component({
   selector: "em-time-condition-editor",
   templateUrl: "./time-condition-editor.component.html",
   styleUrls: ["./time-condition-editor.component.scss"],
   providers: [ DateTimeService ]
})
export class TimeConditionEditorComponent implements OnInit {
   @Input() model: TaskConditionPaneModel;
   @Input() condition: TimeConditionModel;
   @Input() timeZone: string;
   @Input() timeZoneOptions: TimeZoneModel[];
   @Input() taskDefaultTime: boolean;
   @Input() timeRanges: TimeRange[] = [];
   @Input() startTimeEnabled = true;
   @Input() timeRangeEnabled = true;
   @Input() showMeridian: boolean;
   @Output() modelChanged = new EventEmitter<TaskConditionChanges>();
   readonly TimeConditionType = TimeConditionType;

   constructor() {
   }

   ngOnInit() {
   }

   onModelChanged(change: TaskConditionChanges) {
      this.condition = <TimeConditionModel> change.model;
      this.modelChanged.emit(change);
   }
}
