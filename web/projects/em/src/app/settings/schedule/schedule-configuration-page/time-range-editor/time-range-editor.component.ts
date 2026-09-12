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
   Component,
   HostListener,
   Inject,
   OnInit,
   ViewEncapsulation
} from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup, Validators, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogContent, MatDialogActions } from "@angular/material/dialog";
import { TimeRange } from "../../../../../../../shared/schedule/model/time-condition-model";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../../shared/util/tool";
import { COPY_PASTE_CONTEXT_SCHEDULE } from "../../../security/resource-permission/copy-paste-context";
import { ResourcePermissionModel } from "../../../security/resource-permission/resource-permission-model";
import { MatButton } from "@angular/material/button";
import { ResourcePermissionComponent } from "../../../security/resource-permission/resource-permission.component";
import { MatCheckbox } from "@angular/material/checkbox";

import { MatInput } from "@angular/material/input";
import { MatFormField, MatLabel, MatError } from "@angular/material/form-field";
import { MatCard, MatCardContent } from "@angular/material/card";
import { MatTabGroup, MatTab } from "@angular/material/tabs";
import { ErrorStateMatcher } from "@angular/material/core";
import { ModalHeaderComponent } from "../../../../common/util/modal-header/modal-header.component";
import { EmErrorStateMatcher } from "../../../../common/util/error/em-error-state-matcher";
import { NgIf } from "@angular/common";

export interface TimeRangeData {
   range: TimeRange;
   ranges: TimeRange[];
}

@Component({
    selector: "em-time-range-editor",
    templateUrl: "./time-range-editor.component.html",
    styleUrls: ["./time-range-editor.component.scss"],
    encapsulation: ViewEncapsulation.None,
    // This dialog is opened via MatDialog, which uses the root injector and does
    // not inherit the schedule route's EmErrorStateMatcher provider. Provide it
    // here so required-field errors display eagerly (Bug #75338).
    providers: [{ provide: ErrorStateMatcher, useClass: EmErrorStateMatcher }],
    imports: [NgIf, ModalHeaderComponent, MatDialogContent, FormsModule, ReactiveFormsModule, MatTabGroup, MatTab, MatCard, MatCardContent, MatFormField, MatLabel, MatInput, MatError, MatCheckbox, ResourcePermissionComponent, MatDialogActions, MatButton]
})
export class TimeRangeEditorComponent implements OnInit {
   form: UntypedFormGroup;
   permissions: ResourcePermissionModel;
   protected readonly copyPasteContext = COPY_PASTE_CONTEXT_SCHEDULE;

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
