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
import { UntypedFormControl, UntypedFormGroup, Validators, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { Subscription } from "rxjs";

import { EnterSubmitDirective } from "../../../../widget/directive/enter-submit.directive";
import { ModalHeaderComponent } from "../../../../widget/modal-header/modal-header.component";

import { NumberStepperComponent } from "../../../../widget/number-stepper/number-stepper.component";
export interface TableCellResizeDialogResult {
   width: number;
   height: number;
   // absent when the dialog showed no box
   followsDensity?: boolean;
}

@Component({
    selector: "table-cell-resize-dialog",
    templateUrl: "./table-cell-resize-dialog.component.html",
    styleUrls: ["./table-cell-resize-dialog.component.scss"],
    imports: [ModalHeaderComponent, EnterSubmitDirective, FormsModule, ReactiveFormsModule, NumberStepperComponent]
})
export class TableCellResizeDialogComponent implements OnInit {
   @Input() width: number;
   @Input() height: number;
   // the cell height's follow-the-default-density box; null hides it
   @Input() followsDensity: boolean | null = null;
   @Output() onCommit: EventEmitter<TableCellResizeDialogResult> = new EventEmitter<TableCellResizeDialogResult>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   public form: UntypedFormGroup;
   subscriptions: Subscription = new Subscription();

   ngOnInit(): void {
      this.initForm();
   }

   initForm(): void {
      this.form = new UntypedFormGroup({
         cellWidth: new UntypedFormControl(this.width,
            [Validators.min(0), Validators.required, FormValidators.isInteger()]),
         cellHeight: new UntypedFormControl(this.height,
            [Validators.min(0), Validators.required, FormValidators.isInteger()])
      });

      this.subscriptions.add(this.form.get("cellWidth").valueChanges.subscribe((value) => {
         this.width = value;
      }));

      this.subscriptions.add(this.form.get("cellHeight").valueChanges.subscribe((value) => {
         this.height = value;
      }));

      if(this.followsDensity != null) {
         this.form.addControl("followsDensity", new UntypedFormControl(this.followsDensity));
         this.followsDensityChanged(this.followsDensity);
         this.subscriptions.add(this.form.get("followsDensity").valueChanges
            .subscribe((value) => this.followsDensityChanged(value)));
      }
   }

   private followsDensityChanged(follows: boolean): void {
      this.followsDensity = follows;

      // a following height is the density's, so it keeps the shown value but can't be edited
      if(follows) {
         this.cellHeight.disable({ emitEvent: false });
      }
      else {
         this.cellHeight.enable({ emitEvent: false });
      }
   }

   public get cellHeight() {
      return this.form.get("cellHeight");
   }

   public get cellWidth() {
      return this.form.get("cellWidth");
   }

   cancel() {
      this.onCancel.emit("cancel");
   }

   ok() {
      const result: TableCellResizeDialogResult = {
         width: this.width,
         height: this.height
      };

      if(this.followsDensity != null) {
         result.followsDensity = this.followsDensity;
      }

      this.onCommit.emit(result);
   }
}
