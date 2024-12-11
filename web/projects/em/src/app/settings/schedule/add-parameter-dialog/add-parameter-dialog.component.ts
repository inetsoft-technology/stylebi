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
import {Component, Inject, OnInit} from "@angular/core";
import {UntypedFormControl, UntypedFormGroup, Validators} from "@angular/forms";
import {MAT_DIALOG_DATA, MatDialog, MatDialogConfig, MatDialogRef} from "@angular/material/dialog";
import {XSchema} from "../../../../../../portal/src/app/common/data/xschema";
import {AddParameterDialogModel} from "../../../../../../shared/schedule/model/add-parameter-dialog-model";
import {FormValidators} from "../../../../../../shared/util/form-validators";
import {Tool} from "../../../../../../shared/util/tool";
import {ValueTypes} from "../../../../../../portal/src/app/vsobjects/model/dynamic-value-model";
import {FlatTreeControl} from "@angular/cdk/tree";
import {ScriptTreeFlatNode, ScriptTreeNode} from "../../../widget/script-tree-node";
import {ScriptTreeDataSource} from "../../../widget/script-tree-data-source";
import {DynamicDate} from "../../../../../../portal/src/app/portal/schedule/schedule-task-editor/dynamic-date";
import {
  FormulaEditorDialogModel
} from "../../../../../../portal/src/app/widget/formula-editor/formula-editor-dialog-model";
import {HttpClient} from "@angular/common/http";
import {MessageDialog, MessageDialogType} from "../../../common/util/message-dialog";
import {TestTaskParameterExpressionRequest} from "../model/test-task-parameter-expression-request";

@Component({
   selector: "em-add-parameter-dialog",
   templateUrl: "./add-parameter-dialog.component.html",
   styleUrls: ["./add-parameter-dialog.component.scss"],
})
export class AddParameterDialogComponent implements OnInit {
   index: number;
   cshid: string = "EMAddParameter";
   parameters: AddParameterDialogModel[];
   parameterNames: string[];
   parameterType: string;
   public form: UntypedFormGroup = null;
   public XSchema = XSchema;
   dataTypeList = XSchema.scheduledTaskDataTypeList;
   model: AddParameterDialogModel;
   modelName: string;
   title: string;
   currentType: string;
   confirmMessage: string = "";
   treeControl: FlatTreeControl<ScriptTreeFlatNode>;
   treeDataSource: ScriptTreeDataSource = new ScriptTreeDataSource();
   supportDynamic: boolean;

   constructor(private dialogRef: MatDialogRef<AddParameterDialogComponent>,
               private http: HttpClient,
               private dialog: MatDialog,
               @Inject(MAT_DIALOG_DATA) public data: any)
   {
      this.index = data.index;
      this.cshid = !!data.cshid ? data.cshid : this.cshid;
      this.parameters = data.parameters;
      this.parameterNames = data.parameterNames;
      this.parameterType = data.parameterType;
      this.treeDataSource.data = this.treeDataSource.transform({nodes: [this.createDynamicDates()]}, 0);
      this.supportDynamic = data.supportDynamic;
   }

   ngOnInit() {
      if(this.index == -1) {
         this.model = {
            name: "",
            value: {
               value: "",
               type: ValueTypes.VALUE,
               dataType: XSchema.STRING
            },
            array: false,
            type: XSchema.STRING
         };

         this.title = "_#(js:Add Parameter)";
      }
      else {
         this.model = Tool.clone(this.parameters[this.index]);
         this.modelName = this.model.name;
         this.convertTimeInstantArray();
         this.title = "_#(js:Edit Parameter)";
      }

      this.initForm();
   }

   initForm() {
      let dupeFunction = FormValidators.duplicateName(() => this.parameters && this.index > -1 ?
         this.parameters.map(p => p.name).filter(name => name != this.modelName) : []);

      this.form = new UntypedFormGroup({
         "name": new UntypedFormControl(this.model.name, [Validators.required,
            FormValidators.variableSpecialCharacters, dupeFunction]),
         "type": new UntypedFormControl(this.model.type === "_#(js:Array)" ? this.XSchema.STRING : this.model.type),
         "array": new UntypedFormControl(this.model.array)
      });

      if(this.isSupportDynamic()) {
         this.form.addControl("dynamicValue", new UntypedFormGroup({}));
      }
      else {
         this.form.addControl("value", new UntypedFormControl(this.model.value.value, [Validators.required]));
      }

      this.currentType = this.model.type;
      this.setValueValidator();

      this.form.controls["name"].valueChanges.subscribe((value) => {
         this.model.name = value;
         this.confirmMessage = this.confirmMessage ? "" : this.confirmMessage;
      });

      this.form.controls["type"].valueChanges.subscribe(
         (type) => {
            this.model.type = type;
            this.setValueValidator();

            if(!this.currentType) {
               this.currentType = type;
            }
            else if(!this.model.array && (type === XSchema.BOOLEAN || XSchema.isDateType(type)
               || (!XSchema.isNumericType(this.currentType) && XSchema.isNumericType(type)
               && (isNaN(this.model.value.value) || this.currentType === XSchema.BOOLEAN))))
            {
               this.model.value.value = this.model.value.type == ValueTypes.EXPRESSION ? "=" :
                  type === XSchema.BOOLEAN ? false : "";
               this.form.controls["value"]?.setValue(this.model.value.value);
               this.currentType = type;
            }
            else if(!this.isSupportDynamic()) {
               this.form.controls["value"].updateValueAndValidity();
            }

            this.model.value.dataType = type;
            this.model.type = type;
         });

      this.form.controls["value"]?.valueChanges.subscribe((value) => {
         this.model.value.value = value;
         this.convertTimeInstantArray();
      });

      this.form.controls["array"].valueChanges.subscribe(
         (array) => {
            this.model.array = array;
            this.setValueValidator();

            if(!array && this.model.value.type == ValueTypes.VALUE) {
               let value = typeof this.model.value.value == "string" && this.model.value.dataType != XSchema.STRING ?
                  this.model.value.value.split(",")[0] : this.model.value.value;

               setTimeout(() => {
                  this.model.value.value = this.valueControl.valid ? value : "";

                  if(!this.isSupportDynamic()) {
                     this.form.controls["value"].setValue(this.model.value.value);
                  }
               });
            }
            else if(typeof this.model.value.value !== "string") {
               this.model.value.value = this.model.value.value == null ? "" : this.model.value.value + "";
            }
         });

      this.updateArrayStatus();
   }

   get valueControl() {
      return this.isSupportDynamic() ? this.form.controls["dynamicValue"] : this.form.controls["value"];
   }

   convertTimeInstantArray(): void {
      if(!!this.model && this.model.array && this.model.type === "timeInstant" &&
         this.model.value.type == ValueTypes.VALUE)
      {
         this.model.value.value = this.model.value.value.replace(/T/gm, " ");
      }
   }

   convertToArray() {
      if(this.form.controls["array"] && !this.form.controls["array"].value) {
         this.fixTimeValue();
      }
   }

   valueModelChange(): void {
      this.updateArrayStatus();
   }

   private updateArrayStatus() {
      if(this.model.value.type == ValueTypes.EXPRESSION && this.model.value.value != "") {
         if(!this.form.controls["array"].disabled) {
            this.form.controls["array"].disable();
         }
      }
      else if(this.form.controls["array"].disabled) {
         this.form.controls["array"].enable();
      }
   }

   private fixTimeValue() {
      if(this.model.value.type != ValueTypes.VALUE) {
         return;
      }

      const val = this.model.value.value;
      const type = this.model.type;

      let timeInsPattern = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}\w*$/;
      let timePattern = /^\d{2}:\d{2}\w*$/;

      if(type == XSchema.TIME_INSTANT && timeInsPattern.test(val.trim()) ||
         type == XSchema.TIME && timePattern.test(val.trim()))
      {
         this.model.value.value = val + ":00";
      }

      if(type == XSchema.TIME_INSTANT) {
         this.model.value.value = val.replace("T", " ");
      }
   }

   getValueValidator() {
      let validators: any[] = [Validators.required];
      const isArray = this.model.array;
      const type = this.model.type;

      if(this.model.value.type != ValueTypes.VALUE) {
         return validators;
      }

      if(type === XSchema.INTEGER) {
         validators = validators.concat([FormValidators.integerInRange(isArray),
            FormValidators.isInteger(isArray)]);
      }
      else if(type === XSchema.DOUBLE) {
         validators = validators.concat([FormValidators.isFloatNumber(isArray)]);
      }
      else if(type === XSchema.DATE && isArray) {
         validators = validators.concat([FormValidators.isDate(isArray)]);
      }
      else if(type === XSchema.TIME && isArray) {
         validators = validators.concat([FormValidators.isTime(isArray)]);
      }
      else if(type === XSchema.TIME_INSTANT && isArray) {
         validators = validators.concat([FormValidators.isDateTime(isArray)]);
      }
      else if(type === XSchema.BOOLEAN && isArray) {
         validators = validators.concat([FormValidators.isBoolean(isArray)]);
      }

      return validators;
   }

   private setValueValidator() {
      if(this.isSupportDynamic()) {
         return;
      }

      this.form.controls["value"].setValidators(this.getValueValidator());
   }

   getDateFormat(): string | null {
      if(this.model.type == XSchema.TIME_INSTANT) {
         return "YYYY-MM-DD HH:mm:ss";
      }

      return null;
   }

   ok(): void {
      this.fixTimeValue();

      if(this.form.invalid) {
         return;
      }

      if(this.parameterType) {
         this.model.parameterType = this.parameterType;
      }

      if(this.index > -1) {
         this.parameters[this.index] = this.model;
         this.dialogRef.close(this.parameters);
      }
      else {
         let copyIndex: number = -1;

         if(!this.parameters) {
            this.parameters = [];
         }

         for(let i = 0; i < this.parameters.length; i++) {
            const param: AddParameterDialogModel = this.parameters[i];

            if(this.model.name === param.name) {
               copyIndex = i;
            }
         }

         if(copyIndex > -1 && !this.confirmMessage) { // if it's a duplicate and there was no confirmation message before
            const name: string = this.model.name;
            this.confirmMessage = "_#(js:em.actionParam.replaceConfirm): " + name;
         }
         else {
            if(this.confirmMessage) { // if there is a duplicate and a confirmation message, we need to change parameter's value
               this.parameters[copyIndex] = this.model;
            }
            else {
               this.parameters.push(Tool.clone(this.model));
            }

            this.parameters.sort((p1: AddParameterDialogModel, p2: AddParameterDialogModel) => p1.name.localeCompare(p2.name));
            this.dialogRef.close(this.parameters);
         }
      }
   }

   isSupportDynamic(): boolean {
      return this.supportDynamic;
   }

   createDynamicDates() {
      let dynamicChildNodes = [
         this.createDynamicNode(DynamicDate.BEGINNING_OF_THIS_YEAR, [], true),
         this.createDynamicNode(DynamicDate.BEGINNING_OF_THIS_QUARTER, [], true),
         this.createDynamicNode(DynamicDate.BEGINNING_OF_THIS_MONTH, [], true),
         this.createDynamicNode(DynamicDate.BEGINNING_OF_THIS_WEEK, [], true),
         this.createDynamicNode(DynamicDate.END_OF_THIS_YEAR, [], true),
         this.createDynamicNode(DynamicDate.END_OF_THIS_QUARTER, [], true),
         this.createDynamicNode(DynamicDate.END_OF_THIS_MONTH, [], true),
         this.createDynamicNode(DynamicDate.END_OF_THIS_WEEK, [], true),
         this.createDynamicNode(DynamicDate.NOW, [], true),
         this.createDynamicNode(DynamicDate.THIS_QUARTER, [], true),
         this.createDynamicNode(DynamicDate.TODAY, [], true),
         this.createDynamicNode(DynamicDate.LAST_YEAR, [], true),
         this.createDynamicNode(DynamicDate.LAST_QUARTER, [], true),
         this.createDynamicNode(DynamicDate.LAST_MONTH, [], true),
         this.createDynamicNode(DynamicDate.LAST_WEEK, [], true),
         this.createDynamicNode(DynamicDate.LAST_DAY, [], true),
         this.createDynamicNode(DynamicDate.LAST_HOUR, [], true),
         this.createDynamicNode(DynamicDate.LAST_MINUTE, [], true),
         this.createDynamicNode(DynamicDate.NEXT_YEAR, [], true),
         this.createDynamicNode(DynamicDate.NEXT_QUARTER, [], true),
         this.createDynamicNode(DynamicDate.NEXT_MONTH, [], true),
         this.createDynamicNode(DynamicDate.NEXT_WEEK, [], true),
         this.createDynamicNode(DynamicDate.NEXT_DAY, [], true),
         this.createDynamicNode(DynamicDate.NEXT_HOUR, [], true),
         this.createDynamicNode(DynamicDate.NEXT_MINUTE, [], true)
      ];

      return this.createDynamicNode("_#(js:Dynamic Dates)", dynamicChildNodes, false);
   }

   createDynamicNode(label: string, children: ScriptTreeNode[], level: boolean) {
      let data =!level ? {data: label} :
         {data: label, parentName: DynamicDate.DYNAMIC_DATES, parentLabel: DynamicDate.DYNAMIC_DATES};
      return new ScriptTreeNode(children, label, data, !level ? "folder-icon" :  "worksheet-icon", level);
   }

   getScriptTester(): (_?: FormulaEditorDialogModel) => Promise<boolean> {
      return (formulaModel?: FormulaEditorDialogModel) => {
         if(!formulaModel || !formulaModel.expression) {
            return Promise.resolve(true);
         }
         else {
            let script: TestTaskParameterExpressionRequest = {
               expression: formulaModel.expression
            };

            return this.http.post("../api/em/schedule/parameters/formula/test-script", script).toPromise().then((result: any) => {
               let promise: Promise<boolean> = Promise.resolve(true);

               if(!!result) {
                  let msg = "_#(js:composer.vs.testScript)" + "\n" + result;

                  promise = this.dialog.open(MessageDialog, <MatDialogConfig>{
                     data: {
                        title: "_#(js:Confirm)",
                        content: msg,
                        type: MessageDialogType.CONFIRMATION
                     }
                  }).afterClosed().toPromise();
               }

               return promise;
            });
         }
      };
   }
}
