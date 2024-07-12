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
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { AddParameterDialogModel } from "../../../../../../../shared/schedule/model/add-parameter-dialog-model";
import { XSchema } from "../../../../common/data/xschema";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Tool } from "../../../../../../../shared/util/tool";
import { ComponentTool } from "../../../../common/util/component-tool";
import { DynamicValueModel, ValueTypes } from "../../../../vsobjects/model/dynamic-value-model";
import { dynamicDates } from "../dynamic-date";
import { FeatureFlagsService, FeatureFlagValue } from "../../../../../../../shared/feature-flags/feature-flags.service";
import { ComboMode } from "../../../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { FormulaEditorDialogModel } from "../../../../widget/formula-editor/formula-editor-dialog-model";
import { HttpClient } from "@angular/common/http";
import { StringWrapper } from "../../../data/model/datasources/database/string-wrapper";

const DATE_PATTERN = /^(?:(?!0000)[0-9]{4}(-?)(?:(?:0?[1-9]|1[0-2])\1(?:0?[1-9]|1[0-9]|2[0-8])|(?:0?[13-9]|1[0-2])\1(?:29|30)|(?:0?[13578]|1[02])\1(?:31))|(?:[0-9]{2}(?:0[48]|[2468][048]|[13579][26])|(?:0[48]|[2468][048]|[13579][26])00)(-?)0?2\2(?:29))$/;
const TIME_PATTERN = /^([01]?[0-9]|2[0-3]):[0-5]?[0-9]:[0-5]?[0-9]$/;
const DATE_TIME_PATTERN = /^(?:(?!0000)[0-9]{4}(-?)(?:(?:0?[1-9]|1[0-2])\1(?:0?[1-9]|1[0-9]|2[0-8])|(?:0?[13-9]|1[0-2])\1(?:29|30)|(?:0?[13578]|1[02])\1(?:31))|(?:[0-9]{2}(?:0[48]|[2468][048]|[13579][26])|(?:0[48]|[2468][048]|[13579][26])00)(-?)0?2\2(?:29))([T|\s+])([01]?[0-9]|2[0-3]):[0-5]?[0-9]:[0-5]?[0-9]$/;

@Component({
   selector: "add-parameter-dialog",
   templateUrl: "add-parameter-dialog.component.html",
   styleUrls: ["add-parameter-dialog.component.scss"]
})
export class AddParameterDialog implements OnInit {
   @Input() index: number;
   @Input() parameters: AddParameterDialogModel[];
   @Input() parameterNames: string[];
   @Input() supportDynamic: boolean;
   @Output() onCommit: EventEmitter<AddParameterDialogModel[]>
      = new EventEmitter<AddParameterDialogModel[]>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   public form: UntypedFormGroup = null;
   public XSchema = XSchema;
   dataTypeList = XSchema.scheduledTaskDataTypeList;
   model: AddParameterDialogModel;
   title: string;
   currentType: string;
   readonly FeatureFlagValue = FeatureFlagValue;

   constructor(private modalService: NgbModal,
               private http: HttpClient)
   {
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
         this.convertTimeInstantArray();
         this.title = "_#(js:Edit Parameter)";
      }

      this.initForm();
      this.currentType = this.model.type;
   }

   initForm() {
      this.form = new UntypedFormGroup({
         "name": new UntypedFormControl(this.model.name, [Validators.required,
            FormValidators.variableSpecialCharacters]),
         "type": new UntypedFormControl(this.model.type),
         "value": new UntypedFormControl(this.model.value.value, [Validators.required]),
         "array": new UntypedFormControl(this.model.array)
      });

      //Clear the value when switching between incompatible data types, otherwise you may be able to
      //save an invalid value
      this.form.controls["type"].valueChanges.subscribe(
         (value) => {
            if(!this.currentType) {
               this.currentType = value;
            }
            else if(!this.model.array && (value === XSchema.BOOLEAN || XSchema.isDateType(value)
               || (!XSchema.isNumericType(this.currentType) && XSchema.isNumericType(value)
                  && (isNaN(this.model.value.value) || this.currentType === XSchema.BOOLEAN))))
            {
               this.model.value.value = this.model.value.type == ValueTypes.EXPRESSION ? "=" : "";
               this.form.controls["value"].setValue(this.model.value.value);
               this.currentType = value;
            }

            this.model.value.dataType = value;
            this.model.type = value;
         });

      this.form.controls["array"].valueChanges.subscribe(
         (array) => {
            this.model.array = array;

            if(!array && this.model.array) {
               let value = typeof this.model.value.value == "string" ?
                  this.model.value.value.split(",")[0] : this.model.value;
               this.model.value.value = this.isValidDataTypeValue(value.toString(), this.model.type) ? value : "";
               this.form.controls["value"].setValue(this.model.value.value);
            }
            else if(array && typeof this.model.value.value !== "string") {
               this.model.value.value = this.model.value.value?.toString();
               this.form.controls["value"].setValue(this.model.value.value);
            }

            this.convertTimeInstantArray();
         });
   }

   convertTimeInstantArray(): void {
      if(!!this.model && this.model.array && this.model.type === "timeInstant") {
         this.model.value.value = this.model.value.value?.replace(/T/gm, " ");
      }
   }

   enterSubmit(): boolean {
      return true;
   }

   private checkArrayValues(): boolean {
      if(this.model.array) {
         let values = typeof this.model.value.value === "string" ?
            this.model.value.value?.split(",") : [this.model.value.value];

         for(let i = 0; i < values.length; i++) {
            if(!this.isValidDataTypeValue(values[i], this.model.type)) {
               ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                  "Please make sure your values are correct for the selected data type.");
               return false;
            }
         }
      }

      return true;
   }

   validValue(value: string, dataType: string, isArray: boolean = false) {
      if(!isArray) {
         return this.isValidDataTypeValue(value, dataType);
      }

      let vals: string[] = value.split(",");

      for(let i = 0; i < vals.length; i++) {
         if(dataType == XSchema.INTEGER && (parseInt(vals[i], 10) > 2147483647 ||
            parseInt(vals[i], 10) < -2147483648) || !this.isValidDataTypeValue(vals[i], dataType))
         {
            return false;
         }
      }

      return true;
   }

   isValidDataTypeValue(value: any, dataType: string): boolean {
      let pattern;

      switch(dataType) {
         case XSchema.BYTE:
         case XSchema.SHORT:
         case XSchema.LONG:
         case XSchema.INTEGER:
            let intValue = parseInt(value.trim(), 10);
            pattern = /^(-|\d)\d*$/;

            return pattern.test(value.trim()) && !isNaN(intValue)
               && String(value.trim()).indexOf(".") < 0;
         case XSchema.FLOAT:
         case XSchema.DOUBLE:
            let doubleValue = parseFloat(value.trim());
            pattern = /^(-|\d)\d*\.?\d*(e\d+)?$/i;

            return pattern.test(value.trim()) && !isNaN(doubleValue);
         case XSchema.BOOLEAN:
            pattern = /^(true)|(false)$/i;

            return pattern.test(value.trim());
         case XSchema.DATE:
            return DATE_PATTERN.test(value.trim());
         case XSchema.TIME_INSTANT:
            return DATE_TIME_PATTERN.test(value.trim());
         case XSchema.TIME:
            return TIME_PATTERN.test(value.trim());
         default:
            return true;
         }
   }

   private fixTimeValue() {
      const val = this.model.value.value;
      const type = this.model.type;

      let timeInsPattern = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}\w*$/;
      let timePattern = /^\d{2}:\d{2}\w*$/;

      if(type == XSchema.TIME_INSTANT && timeInsPattern.test(val.trim()) ||
         type == XSchema.TIME && timePattern.test(val.trim()))
      {
         this.model.value.value = val + ":00";
      }
   }

   changeValue(value: any) {
      this.model.value = value;
      this.form.controls["value"].setValue(this.model.value.value);
   }

   updateDynamicValue() {
      this.form.controls["value"].setValue(this.model.value.value);

      if(this.model.value.type == ValueTypes.EXPRESSION) {
         this.form.controls["array"].disable();
      }
      else {
         this.form.controls["array"].enable();
      }
   }

   ok(): void {
      this.fixTimeValue();

      if(!this.checkArrayValues()) {
         return;
      }

      let copyIndex: number = -1;

      if(!this.parameters) {
         this.parameters = [];
      }

      for(let i = 0; i < this.parameters.length; i++) {
         const param: AddParameterDialogModel = this.parameters[i];

         if(this.model.name === param.name && i != this.index) {
            copyIndex = i;
         }
      }

      if(this.index > -1 && copyIndex < 0) {
         this.parameters[this.index] = this.model;
         this.onCommit.emit(this.parameters);
      }
      else {
         if(copyIndex > -1) {
            const name: string = this.model.name;
            const msg: string = "Replace the existing parameter: " + name + "?";
            ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", msg)
               .then((buttonClicked: string) => {
                  if(buttonClicked === "ok") {
                     this.parameters[copyIndex] = Tool.clone(this.model);

                     if(this.index >= 0) {
                        this.parameters.splice(this.index, 1);
                     }

                     this.onCommit.emit(this.parameters);
                  }
               });
         }
         else {
            this.parameters.push(Tool.clone(this.model));
            this.onCommit.emit(this.parameters);
         }
      }
   }

   close(): void {
      this.onCancel.emit("cancel");
   }

   verifyDynamicValue(): boolean {
      return Tool.isEmpty(this.getErrorMessage());
   }

   getErrorMessage(): string {
      let value: DynamicValueModel = this.model.value;
      let errorMessage: string = "";

      if(value.type == ValueTypes.EXPRESSION) {
         return errorMessage;
      }

      if(value.value == null || value.value.toString() == "") {
         errorMessage = value.dataType == XSchema.BOOLEAN ?
            "_#(js:em.common.param.boolean)" : "_#(js:parameter.value.emptyValid)";

         return errorMessage;
      }

      if(XSchema.isNumericType(value.dataType) &&
         !this.validValue(value.value.toString(), value.dataType, this.model.array))
      {
         errorMessage = "_#(js:em.common.param.numberInvalid)";
      }
      else if(value.dataType == XSchema.INTEGER &&
         !this.validValue(value.value.toString(), value.dataType, this.model.array))
      {
         errorMessage = "_#(js:em.common.param.number.outNegativeRange)";
      }
      else if(value.dataType == XSchema.BOOLEAN &&
         !this.validValue(value.value.toString(), value.dataType, this.model.array))
      {
         errorMessage = "_#(js:em.common.param.boolean)";
      }
      else if(value.dataType == XSchema.DATE &&
         !this.validValue(value.value.toString(), value.dataType, this.model.array))
      {
         errorMessage = "_#(js:em.schedule.condition.dateRequired)";
      }
      else if(value.dataType == XSchema.TIME &&
         !this.validValue(value.value.toString(), value.dataType, this.model.array))
      {
         errorMessage = "_#(js:em.schedule.condition.parameter.timeRequired)";
      }
      else if(value.dataType == XSchema.TIME_INSTANT &&
         !this.validValue(value.value.toString(), value.dataType, this.model.array))
      {
         errorMessage = "_#(js:em.schedule.condition.parameter.timeInstantRequired)";
      }

      return errorMessage;
   }

   getDynamicDates() {
      return {
         label: "_#(js:Data)",
         children: [dynamicDates]
      };
   }

   isSupportDynamic(): boolean {
      return this.supportDynamic;
   }

   getScriptTester(): (_?: FormulaEditorDialogModel) => Promise<boolean> {
      return (formulaModel?: FormulaEditorDialogModel) => {
         if(!formulaModel || !formulaModel.expression) {
            return Promise.resolve(true);
         }
         else {
            let script = new StringWrapper(formulaModel.expression);

            return this.http.post("../api/portal/schedule/parameters/formula/test-script", script).toPromise().then((result: any) => {
               let promise: Promise<boolean> = Promise.resolve(true);

               if(!!result?.body) {
                  let msg = "_#(js:composer.vs.testScript)" + "\n" + result?.body;

                  promise = ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", msg,
                     {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
                     .then(option => {
                        if(option == "yes") {
                           return true;
                        }

                        return false;
                     });
               }

               return promise;
            });
         }
      };
   }
}
