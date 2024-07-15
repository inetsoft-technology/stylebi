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
import { Component, OnInit, Input, Output, EventEmitter } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { LayoutOptionDialogModel } from "../../data/vs/layout-option-dialog-model";
import { ViewsheetClientService } from "../../../common/viewsheet-client";

/**
 * Grouping Options
 */
export enum Placement {
   HERE = 0,
   SELECTION = 1,
   TAB = 2
}

@Component({
   selector: "layout-option-dialog",
   templateUrl: "layout-option-dialog.component.html"
})
export class LayoutOptionDialog implements OnInit {
   @Input() model: LayoutOptionDialogModel;
   controller: string = "composer/vs/layout-option-dialog-model/";
   form: UntypedFormGroup;
   Placement = Placement;
   @Output() onCommit: EventEmitter<LayoutOptionDialogModel> =
      new EventEmitter<LayoutOptionDialogModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   formValid = () => this.model && this.form && this.form.valid;

   constructor(private viewsheetClient: ViewsheetClientService) {
   }

   initForm(): void {
      this.form = new UntypedFormGroup({
         selectedValue: new UntypedFormControl(this.model.selectedValue, [Validators.required])
      });
   }

   ngOnInit(): void {
      if(!this.model.selectedValue) {
         this.model.selectedValue = Placement.HERE;
      }

      this.initForm();
   }

   cancelChanges(): void {
      this.onCancel.emit("cancel");
   }

   saveChanges(): void {
      if(this.model.selectedValue != Placement.HERE && this.viewsheetClient) {
         this.viewsheetClient.sendEvent("/events/" + this.controller, this.model);
         this.onCommit.emit(this.model);
      }
      else {
         this.onCommit.emit(this.model);
      }
   }

   optionClicked(placement: Placement) {
      this.model.selectedValue = placement;
   }
}
