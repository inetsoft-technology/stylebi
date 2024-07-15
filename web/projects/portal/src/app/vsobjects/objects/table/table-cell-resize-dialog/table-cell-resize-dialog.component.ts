/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { Subscription } from "rxjs";

export interface TableCellResizeDialogResult {
   width: number;
   height: number;
}

@Component({
   selector: "table-cell-resize-dialog",
   templateUrl: "./table-cell-resize-dialog.component.html",
   styleUrls: ["./table-cell-resize-dialog.component.scss"]
})
export class TableCellResizeDialogComponent implements OnInit {
   @Input() width: number;
   @Input() height: number;
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
      this.onCommit.emit(<TableCellResizeDialogResult>{
         width: this.width,
         height: this.height
      });
   }
}
