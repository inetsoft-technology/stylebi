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
    Component, Input, Output, EventEmitter, OnInit, ViewChild, ElementRef, AfterViewInit,
    ChangeDetectorRef
} from "@angular/core";
import {
    UntypedFormControl, UntypedFormGroup, Validators, ValidationErrors,
    AbstractControl, ValidatorFn
} from "@angular/forms";
import { DrillParameterModel } from "../../../../../../../model/datasources/database/physical-model/logical-model/drill-parameter-model";
import { ValidatorMessageInfo } from "../../../../../../../../../widget/dialog/input-name-dialog/input-name-dialog.component";
import { XSchema } from "../../../../../../../../../common/data/xschema";
import { FormulaEditorService } from "../../../../../../../../../widget/formula-editor/formula-editor.service";
import { Tool } from "../../../../../../../../../../../../shared/util/tool";
import { FormValidators } from "../../../../../../../../../../../../shared/util/form-validators";

enum SourceType {
   FIELD,
   CONSTANT
}

@Component({
   selector: "parameter-dialog",
   templateUrl: "parameter-dialog.component.html",
   styleUrls: ["parameter-dialog.component.scss"]
})
export class ParameterDialog implements OnInit, AfterViewInit {
   @Input() index: number = -1;
   @Input() parameters: DrillParameterModel[] = [];
   @Input() variables: string[] = [];
   @Input() fields: string[] = ["this.column"];
   @Output() onCommit: EventEmitter<DrillParameterModel[]>
      = new EventEmitter<DrillParameterModel[]>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild("inputFocus") inputFocus: ElementRef;
   XSchema = XSchema;
   form: UntypedFormGroup;
   SourceType = SourceType;
   model: DrillParameterModel;
   source: SourceType;
   validatorMessages: ValidatorMessageInfo[] = [
      {validatorName: "required", message: "_#(js:data.logicalmodel.drillParameterNameRequired)"},
      {validatorName: "duplicate", message: "_#(js:data.logicalmodel.drillParameterNameDuplicate)"},
      {validatorName: "nameSpecialCharacters", message:"_#(js:common.autodrill.parameter.name.specialCharError)"},
      {validatorName: "doesNotStartWithNumberOrLetter", message:"_#(js:common.autodrill.parameter.name.startCharDigitError)"}
   ];
   types: {label: string, data: string}[] = FormulaEditorService.returnTypes;
   field: string = "this.column";
   variable: string = "";
   private typeInitialized = false;

   constructor(private changeDetectionRef: ChangeDetectorRef)
   {
   }

   ngOnInit() {
      if(this.index == -1) {
         this.model = {
            name: "",
            field: this.field,
            type: null
         };
      }
      else {
         this.model = Tool.clone(this.parameters[this.index]);
      }

      this.source = this.model.type ? SourceType.CONSTANT : SourceType.FIELD;
      this.variable = this.model.name ? this.model.name : "";
      this.initForm();

      this.form.controls["type"].valueChanges.subscribe((type: string) => {
         setTimeout(() => {
            let validators: ValidatorFn[] = [Validators.required];

            switch(type) {
            case XSchema.STRING:
               validators.push(FormValidators.notWhiteSpace);
               break;
            case XSchema.CHAR:
               validators.push(FormValidators.notWhiteSpace);
               validators.push(Validators.maxLength(1));
               break;
            case XSchema.BYTE:
               validators.push(FormValidators.byteInRange());
               break;
            case XSchema.SHORT:
               validators.push(FormValidators.shortInRange());
               break;
            case XSchema.INTEGER:
               validators.push(FormValidators.integerInRange());
               break;
            case XSchema.LONG:
               validators.push(FormValidators.longInRange());
               break;
            case XSchema.FLOAT:
            case XSchema.DOUBLE:
               validators.push(FormValidators.isFloatNumber());
               break;
            default:
               break;
            }

            if(this.typeInitialized) {
               this.model.field = null;
               this.valueControl.setValue(type === XSchema.BOOLEAN ? false : null);
            }
            else {
               this.typeInitialized = true;
            }

            this.updateValidators(validators);
         });
      });
   }

   ngAfterViewInit(): void {
      this.inputFocus.nativeElement.focus();
   }

   private updateValidators(
      valueValidator: ValidatorFn[])
   {
      this.valueControl.setValidators(valueValidator);
      this.valueControl.updateValueAndValidity();
      this.changeDetectionRef.detectChanges();
   }

   /**
    * Initialize the form.
    */
   initForm() {
      this.form = new UntypedFormGroup({
         "name": new UntypedFormControl(this.model.name, [
            Validators.required,
            this.uniqueName,
            FormValidators.nameSpecialCharacters,
            FormValidators.doesNotStartWithNumberOrLetter
         ]),
         "value": new UntypedFormControl(this.model.field, [Validators.required]),
         "type": new UntypedFormControl(this.model.type)
      });
   }

   /**
    * Get the name form control
    * @returns {AbstractControl|null} the form control
    */
   get nameControl(): AbstractControl {
      return this.form.get("name");
   }

   /**
    * Get the value form control
    * @returns {AbstractControl|null} the form control
    */
   get valueControl(): AbstractControl {
      return this.form.get("value");
   }

   /**
    * Make sure that parameter name is unique.
    * @param control
    * @returns {{duplicate: boolean}}
    */
   private uniqueName = (control: UntypedFormControl): ValidationErrors => {
      for(let i = 0; i < this.parameters.length; i++) {
         if(this.parameters[i].name === control.value && i !== this.index) {
            return { duplicate: true };
         }
      }

      return null;
   };

   /**
    * When there are multiple errors on one input, only show the first one.
    * @param control
    * @returns {string}
    */
   getFirstErrorMessage(control: AbstractControl): string {
      const info: ValidatorMessageInfo = this.validatorMessages.find(
         (messageInfo: ValidatorMessageInfo) => control.getError(messageInfo.validatorName));
      return info ? info.message : null;
   }

   /**
    * Check if the value matches the data type.
    * @param value
    * @param dataType
    * @returns {any}
    */
   isValidDataTypeValue(value: any, dataType: string): boolean {
      let pattern;
      let pattern2;

      switch(dataType) {
         case XSchema.BYTE:
         case XSchema.SHORT:
         case XSchema.LONG:
         case XSchema.INTEGER:
            let intValue = parseInt(("" + value).trim(), 10);
            pattern = /^(-|\d)\d*$/;

            return pattern.test(("" + value).trim()) && !isNaN(intValue)
               && (("" + value).trim()).indexOf(".") < 0;
         case XSchema.FLOAT:
         case XSchema.DOUBLE:
            let doubleValue = parseFloat(("" + value).trim());
            pattern = /^(-|\d)\d*\.?\d*(e\d+)?$/i;

            return pattern.test(("" + value).trim()) && !isNaN(doubleValue);
         case XSchema.BOOLEAN:
            pattern = /^(true)|(false)$/i;

            return pattern.test(("" + value).trim());
         case XSchema.DATE:
            pattern = /^\d{4}-\d{2}-\d{2}\w*$/;

            return pattern.test(("" + value).trim());
         case XSchema.TIME_INSTANT:
            pattern = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\w*$/;
            pattern2 = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\w*$/;

            return pattern.test(("" + value).trim()) || pattern2.test(("" + value).trim());
         case XSchema.TIME:
            pattern = /^\d{2}:\d{2}:\d{2}\w*$/;

            return pattern.test(("" + value).trim());
         case XSchema.CHARACTER:
            return value && value.trim().length == 1;
         default:
            return true;
         }
   }

   /**
    * Fix time value if necessary.
    */
   private fixTimeValue() {
      const val = this.model.field;
      const type = this.model.type;

      let timeInsPattern = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}\w*$/;
      let timePattern = /^\d{2}:\d{2}\w*$/;

      if(type == XSchema.TIME_INSTANT && timeInsPattern.test(val.trim()) ||
         type == XSchema.TIME && timePattern.test(val.trim()))
      {
         this.model.field = val + ":00";
      }
   }

   /**
    * Change the type of parameter between field or constant.
    * @param value
    */
   updateParamType(value: SourceType): void {
      this.source = value;

      if(value == SourceType.FIELD) {
         this.model.type = null;
         this.model.field = this.field;
         this.valueControl.setValue(this.field);
         this.updateValidators([Validators.required]);
      }
      else {
         this.model.type = XSchema.STRING;
         this.model.field = null;
         this.valueControl.setValue(null);
      }
   }

   okDisabled(): boolean {
      return this.source == SourceType.FIELD ?
         this.nameControl.invalid : this.form.invalid ||
         !this.isValidDataTypeValue(this.model.field, this.model.type);
   }

   isInValidTypeRange(): boolean {
      return this.valueControl.getError("byteInRange") ||
         this.valueControl.getError("shortInRange") ||
         this.valueControl.getError("integerInRange") ||
         this.valueControl.getError("longInRange") ||
         this.valueControl.getError("isFloatNumber");
   }

   /**
    * Commit parameter changes.
    */
   ok(): void {
      this.model.name = this.nameControl.value;
      this.model.field = this.valueControl.value;
      this.fixTimeValue();

      if(this.index > -1) {
         this.parameters[this.index] = this.model;
         this.onCommit.emit(this.parameters);
      }
      else {
         this.parameters.push(Tool.clone(this.model));
         this.onCommit.emit(this.parameters);
      }
   }

   /**
    * Close dialog without saving.
    */
   close(): void {
      this.onCancel.emit("cancel");
   }

   changeType(value: any) {
      if(XSchema.TIME == value) {
         this.changeDetectionRef.detectChanges();
      }
   }

   changeValue(value: any) {
      if(!!!value) {
         return;
      }

      if(!Tool.isEquals(this.model.field, value)) {
         this.model.field = value;
         this.valueControl.setValue(value, {
            emitEvent: false
         });
      }
   }
}
