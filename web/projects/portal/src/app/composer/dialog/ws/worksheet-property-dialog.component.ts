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
import { UntypedFormGroup } from "@angular/forms";
import { Tool } from "../../../../../../shared/util/tool";
import { ModelService } from "../../../widget/services/model.service";
import { WorksheetPropertyDialogModel } from "../../data/ws/worksheet-property-dialog-model";

@Component({
   selector: "worksheet-property-dialog",
   templateUrl: "worksheet-property-dialog.component.html",
})
export class WorksheetPropertyDialog implements OnInit {
   @Input() runtimeId: string;
   model: WorksheetPropertyDialogModel;
   private readonly controller: string = "../api/composer/ws/dialog/worksheet-property-dialog-model/";
   form: UntypedFormGroup;
   @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   formValid = () => this.form && this.form.valid;

   constructor(private modelService: ModelService) {
   }

   ngOnInit(): void {
      this.modelService.getModel(this.controller + Tool.byteEncode(this.runtimeId))
         .subscribe((data) => {
               this.model = <WorksheetPropertyDialogModel>data;
            }
         );

      this.form = new UntypedFormGroup({
         worksheetOptionPaneForm: new UntypedFormGroup({})
      });
   }

   cancelChanges(): void {
      this.onCancel.emit();
   }

   saveChanges(): void {
      this.onCommit.emit(this.model);
   }
}
