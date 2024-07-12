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
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, TemplateRef, ViewChild } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { DataRef } from "../../../common/data/data-ref";
import { FormulaType } from "../../../common/data/formula-type";
import { ComponentTool } from "../../../common/util/component-tool";
import { Subscription } from "rxjs";

@Component({
   selector: "create-calc-dialog",
   templateUrl: "create-calc-dialog.component.html",
})
export class CreateCalcDialog implements OnInit, OnDestroy {
   @Input() name: string;
   @Input() calcType: string;
   @Input() formulaType: string;
   @Input() expression: string;
   @Input() vsId: string;
   @Input() availableFields: DataRef[];
   @Input() columns: DataRef[] = [];
   @Input() aggregates: DataRef[] = [];
   @Input() calcFieldsGroup: string[] = [];
   @Input() sqlMergeable: boolean = true;

   @Output() onCommit = new EventEmitter<any>();
   @Output() onCancel = new EventEmitter<string>();
   @Output() fieldChanged = new EventEmitter<string>();
   @Output() aggregateModify = new EventEmitter<any>();
   @ViewChild("formulaEditorDialog") formulaEditorDialog: TemplateRef<any>;

   form: UntypedFormGroup;
   subscriptions: Subscription = new Subscription();

   constructor(private modalService: NgbModal) {
   }

   ngOnInit(): void {
      this.initForm();
   }

   ngOnDestroy(): void {
      if(!!this.subscriptions) {
         this.subscriptions.unsubscribe();
         this.subscriptions = null;
      }
   }

   initForm(): void {
      this.form = new UntypedFormGroup({
         name: new UntypedFormControl(this.name, [
            Validators.required,
            FormValidators.calcSpecialCharacters,
         ]),
         calcType: new UntypedFormControl(this.calcType, [
            Validators.required
         ])
      });

      this.subscriptions.add(this.form.get("name").valueChanges.subscribe((value) => {
         this.name = value;
      }));

      this.subscriptions.add(this.form.get("calcType").valueChanges.subscribe((value) => {
         this.calcType = value;
      }));
   }

   get aggregateOnly(): boolean {
      return this.calcType == "aggregate";
   }

   get dataType(): string {
      return this.calcType == "aggregate" ? "double" : "string";
   }

   showCreateMeasureDialog(): void {
      if((this.calcFieldsGroup && this.calcFieldsGroup.indexOf(this.name) > -1)
         || (this.columns && this.columns.find(column => column.name === this.name)))
      {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", "_#(js:Duplicate Name)!");
         return;
      }

      const options: NgbModalOptions = {
         windowClass: "formula-dialog",
         backdrop: "static"
      };
      this.formulaType = this.sqlMergeable && !this.aggregateOnly ? FormulaType.SQL : FormulaType.SCRIPT;
      this.modalService.open(this.formulaEditorDialog, options).result.then(
         (result: any) => {
            this.onCommit.emit({
               expression: result.expression,
               formulaType: result.formulaType,
               formulaName: result.formulaName,
               dataType: result.dataType,
               calcType: this.calcType
            });
         },
         (reject) => {});
   }

   cancelChanges(): void {
      this.onCancel.emit("cancel");
   }
}
