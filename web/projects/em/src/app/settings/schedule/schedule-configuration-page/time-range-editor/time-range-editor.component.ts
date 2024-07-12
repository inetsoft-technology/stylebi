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
import {
   Component,
   HostListener,
   Inject,
   OnInit,
   ViewEncapsulation
} from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { TimeRange } from "../../../../../../../shared/schedule/model/time-condition-model";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../../shared/util/tool";
import { ResourcePermissionModel } from "../../../security/resource-permission/resource-permission-model";

export interface TimeRangeData {
   range: TimeRange;
   ranges: TimeRange[];
}

@Component({
   selector: "em-time-range-editor",
   templateUrl: "./time-range-editor.component.html",
   styleUrls: ["./time-range-editor.component.scss"],
   encapsulation: ViewEncapsulation.None
})
export class TimeRangeEditorComponent implements OnInit {
   form: UntypedFormGroup;
   permissions: ResourcePermissionModel;

   constructor(private dialogRef: MatDialogRef<TimeRangeEditorComponent>,
               @Inject(MAT_DIALOG_DATA) data: TimeRangeData,
               fb: UntypedFormBuilder)
   {
      const names = data.ranges
         .filter(r => r.name !== data.range.name)
         .map(r => r.name);
      this.form = fb.group({
         name: [data.range.name, [Validators.required, FormValidators.duplicateName(() => names)]],
         startTime: [data.range.startTime, [Validators.required]],
         endTime: [data.range.endTime, [Validators.required]],
         defaultRange: [data.range.defaultRange]
      });
      this.permissions = data.range.permissions ? Tool.clone(data.range.permissions) : null;
   }

   ngOnInit() {
   }

   commit(): void {
      this.dialogRef.close(Object.assign({ permissions: this.permissions }, this.form.value));
   }

   @HostListener("window:keyup.esc", [])
   onKeyUp() {
      this.cancel();
   }

   cancel(): void {
      this.dialogRef.close();
   }
}
