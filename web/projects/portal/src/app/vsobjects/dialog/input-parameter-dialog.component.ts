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
import { ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators, ValidatorFn, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbDateParserFormatter, NgbDateStruct, NgbDropdown, NgbDropdownToggle, NgbDropdownMenu, NgbInputDatepicker } from "@ng-bootstrap/ng-bootstrap";
import { AssetEntry } from "../../../../../shared/data/asset-entry";
import { DataRef } from "../../common/data/data-ref";
import { XSchema } from "../../common/data/xschema";
import { DateTypeFormatter } from "../../../../../shared/util/date-type-formatter";
import { FormValidators } from "../../../../../shared/util/form-validators";
import { InputParameterDialogModel } from "../model/input-parameter-dialog-model";
import { Tool } from "../../../../../shared/util/tool";
import { debounceTime, distinctUntilChanged } from "rxjs/operators";
import { InputTrimDirective } from "../../widget/directive/input-trim.directive";
import { EnterSubmitDirective } from "../../widget/directive/enter-submit.directive";
import { NgIf, NgSwitch, NgClass, NgFor, NgSwitchCase } from "@angular/common";
import { ModalHeaderComponent } from "../../widget/modal-header/modal-header.component";
import { CustomSelectOption, CustomSelectComponent } from "../../widget/custom-select/custom-select.component";

import { NumberStepperComponent } from "../../widget/number-stepper/number-stepper.component";
@Component({
    selector: "input-parameter-dialog",
    templateUrl: "input-parameter-dialog.component.html",
    styleUrls: ["./input-parameter-dialog.component.scss"],
    imports: [ModalHeaderComponent, NgIf, EnterSubmitDirective, FormsModule, NgSwitch, ReactiveFormsModule, NgbDropdown, InputTrimDirective, NgbDropdownToggle, NgbDropdownMenu, NgClass, NgFor, NgSwitchCase, NgbInputDatepicker, CustomSelectComponent, NumberStepperComponent]
})
export class InputParameterDialog implements OnInit {
   @Input() fields: DataRef[];
   @Input() grayedOutFields: DataRef[];
   @Input() selectEdit: boolean = true;
   @Input() viewsheetParameters: string[] = null;
   @Output() onCommit: EventEmitter<InputParameterDialogModel>
      = new EventEmitter<InputParameterDialogModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   public form: UntypedFormGroup = null;
   public XSchema = XSchema;
   dataTypeList = XSchema.standardDataTypeList;
   formValid = () => this.form && this.form.valid;

   date: NgbDateStruct = null;
   time: string = null;

   private _model: InputParameterDialogModel;

   @Input()
   set model(value: InputParameterDialogModel) {
      this._model = Tool.clone(value);

      if(this._model && this._model.valueSource === "field") {
         this.model.type = XSchema.STRING;
      }

      if(value && value.type === XSchema.DATE) {
         this.date = this.ngbDateParserFormatter.parse(value.value);
         this.time = null;
      }
      else if(value && value.type === XSchema.TIME) {
         this.time = value.value;
         this.date = null;
      }
      else if(value && value.type === XSchema.TIME_INSTANT) {
         const formattedDate = DateTypeFormatter.transformValue(
            value.value, DateTypeFormatter.ISO_8601_TIME_INSTANT_FORMAT,
            DateTypeFormatter.ISO_8601_DATE_FORMAT);
         this.date = this.ngbDateParserFormatter.parse(formattedDate);
         this.time = DateTypeFormatter.transformValue(
            value.value, DateTypeFormatter.ISO_8601_TIME_INSTANT_FORMAT,
            DateTypeFormatter.ISO_8601_TIME_FORMAT);
      }

      if(this._model && this.form) {
         // Bug #75652 (fixed): capture the intended value before syncing the "type" control -
         // setting "type" triggers changeType(), which for numeric/STRING/CHARACTER types
         // blanks alphaNumericValue (cascading into model.value) before this method gets a
         // chance to write the real value below. Without capturing it first, the final
         // `this.alphaNumericValue = this.model.value` re-reads the already-blanked value.
         const modelValue = this._model.value;
         this.form.controls["name"].setValue(this.model.name);
         this.form.controls["type"].setValue(this.model.type);
         this.dateValue = this.date;
         this.timeValue = this.time;
         this.alphaNumericValue = modelValue;
      }
   }

   get model(): InputParameterDialogModel {
      return this._model;
   }

   get fieldValueSelectOptions(): CustomSelectOption<string>[] {
      const options: CustomSelectOption<string>[] = [];

      if(this.model?.value === "") {
         options.push({
            label: "",
            value: ""
         });
      }

      return options.concat((this.fields ?? []).map((field) => ({
         label: field.name,
         value: field.name,
         title: field.description,
         disabled: this.isGrayedOut(field.name)
      })));
   }

   get dataTypeSelectOptions(): CustomSelectOption<string>[] {
      return this.dataTypeList.map((type) => ({
         label: type.label,
         value: type.data
      }));
   }

   get booleanValueSelectOptions(): CustomSelectOption<boolean>[] {
      return [
         {
            label: "_#(True)",
            value: true
         },
         {
            label: "_#(False)",
            value: false
         }
      ];
   }

   constructor(private ngbDateParserFormatter: NgbDateParserFormatter,
               private changeDetectionRef: ChangeDetectorRef)
   {
   }

   ngOnInit() {
      const hasFields = !!this.fields && this.fields.length > 0;

      if(!this.selectEdit) {
         this.model = {
            name: "",
            value: "",
            valueSource: hasFields ? "field" : "constant",
            type: XSchema.STRING
         };
      }
      else if(!hasFields && this.model.valueSource === "field") {
         this.model.valueSource = "constant";
      }

      this.initForm();

      this.form.controls["name"].valueChanges.subscribe((name: string) => {
         this.model.name = name;
      });

      this.form.controls["type"].valueChanges.subscribe(
         (type: string) => {
            this.model.type = type;
            this.changeType();
            this.changeDetectionRef.detectChanges();
         }
      );

      this.form.controls["alphaNumericValue"].valueChanges.subscribe((value) => {
         this.updateValue(value);
      });

      this.form.controls["dateValue"].valueChanges.subscribe((dateValue) => {
         this.updateValue(dateValue);
      });

      this.form.controls["timeValue"].valueChanges
         .pipe(debounceTime(1000), distinctUntilChanged())
         .subscribe((value => {
            this.updateTime(value);
            this.updateDateTime();
         }));
   }

   changeType() {
      if(XSchema.isNumericType(this.model.type) ||
         this.model.type == XSchema.STRING || this.model.type == XSchema.CHARACTER)
      {
         this.alphaNumericValue = "";
      }
      else if(this.model.type === XSchema.DATE) {
         this.dateValue = this.date;
      }
      else if(this.model.type === XSchema.TIME) {
         this.timeValue = this.time;
      }
      else if(this.model.type === XSchema.TIME_INSTANT) {
         this.dateValue = this.date;
         this.timeValue = this.time;
      }
      else if(this.model.type === XSchema.BOOLEAN) {
         this.alphaNumericValue = true;
      }
      else {
         this.alphaNumericValue = "";
      }

      this.changeValidators(this.model.type);
   }

   updateValue(value: any) {
      if(XSchema.isNumericType(this.model.type) || this.model.type == XSchema.STRING
         || this.model.type == XSchema.CHARACTER)
      {
         this.model.value = value;
      }
      else if(this.model.type === XSchema.DATE) {
         this.updateDate(value);
      }
      else if(this.model.type === XSchema.TIME) {
         this.updateTime(value);
      }
      else if(this.model.type === XSchema.TIME_INSTANT) {
         this.updateDate(value);
      }
      else {
         this.model.value = value;
      }

      this.changeValidators(this.model.type);
   }

   changeValidators(type: string) {
      const doublePattern = /-?[0-9]+(\.[0-9]+)?(,-?[0-9]+(\.[0-9]+)?)*/;
      const intPattern = /^[-+]?[0-9,]+(,^[-+]?[0-9,]+)*$/;

      if(type == XSchema.STRING || this.model.type == XSchema.CHARACTER) {
         this.updateValidators([Validators.required, FormValidators.notWhiteSpace], [], []);
      }
      else if(type == XSchema.INTEGER) {
         this.updateValidators([Validators.required, FormValidators.notWhiteSpace,
            FormValidators.integerInRange(true), Validators.pattern(intPattern)], [], []);
      }
      else if(type == XSchema.SHORT) {
         this.updateValidators([Validators.required, FormValidators.notWhiteSpace,
            FormValidators.shortInRange(), Validators.pattern(intPattern)], [], []);
      }
      else if(type == XSchema.BYTE) {
         this.updateValidators([Validators.required, FormValidators.notWhiteSpace,
            FormValidators.byteInRange(), Validators.pattern(intPattern)], [], []);
      }
      else if(type == XSchema.LONG) {
         this.updateValidators([Validators.required, FormValidators.notWhiteSpace,
            FormValidators.longInRange(), Validators.pattern(intPattern)], [], []);
      }
      else if(type == XSchema.FLOAT || type == XSchema.DOUBLE) {
         this.updateValidators([Validators.required, FormValidators.notWhiteSpace,
            Validators.pattern(doublePattern)], [], []);
      }
      else if(type === XSchema.DATE) {
         this.updateValidators([],
            [Validators.required, FormValidators.notWhiteSpace], []);
      }
      else if(type === XSchema.TIME) {
         this.updateValidators([], [], [Validators.required]);
      }
      else if(type === XSchema.TIME_INSTANT) {
         this.updateValidators([],
            [Validators.required, FormValidators.notWhiteSpace], [Validators.required]);
      }
      else if(type == XSchema.BOOLEAN) {
         this.updateValidators([], [], []);
      }
   }

   private updateValidators(
      alphaNumericValidator: ValidatorFn[],
      dateValueValidator: ValidatorFn[],
      timeValueValidator: ValidatorFn[])
   {
      this.form.controls["alphaNumericValue"].setValidators(alphaNumericValidator);
      this.form.controls["dateValue"].setValidators(dateValueValidator);
      this.form.controls["timeValue"].setValidators(timeValueValidator);
   }

   initForm() {
      this.form = new UntypedFormGroup({
         "name": new UntypedFormControl(this.model.name, [Validators.required,
            FormValidators.containsSpecialCharsForName,
            FormValidators.doesNotStartWithNumber]),
         "alphaNumericValue": new UntypedFormControl(this.model.value),
         "dateValue": new UntypedFormControl(this.date),
         "timeValue": new UntypedFormControl(this.time),
         "type": new UntypedFormControl(this.model.type)
      });
   }

   updateDate(dateValue: NgbDateStruct): void {
      this.date = dateValue;
      this.updateDateTime();
   }

   updateTime(timeString: string): void {
      this.time = DateTypeFormatter.transformValue(
         timeString, DateTypeFormatter.ISO_8601_TIME_FORMAT,
         DateTypeFormatter.ISO_8601_TIME_FORMAT);
      this.updateDateTime();
   }

   updateDateTime(): void {
      if(this.model.type === XSchema.DATE) {
         this.model.value = this.ngbDateParserFormatter.format(this.date);
      }
      else if(this.model.type === XSchema.TIME) {
         this.model.value = this.time;
      }
      else if(this.model.type === XSchema.TIME_INSTANT) {
         const date = !!this.date ? this.ngbDateParserFormatter.format(this.date) : "1970-01-01";
         const time = this.time ? this.time : "00:00:00";
         this.model.value = `${date} ${time}`;
      }
   }

   set dateValue(date: any) {
      this.form.get("dateValue").setValue(date);
   }

   set timeValue(time: any) {
      this.form.get("timeValue").setValue(time);
   }

   changeValueSource(val: any) {
      if(val == "constant") {
         this.model.valueSource = "constant";
         this.model.value = "";
         this.changeType();
      }
      else {
         this.model.valueSource = "field";
         this.model.type = XSchema.STRING;
         this.changeType();

         // Bug #75652 (fixed): changeType() above blanks alphaNumericValue (cascading into
         // model.value) for the new STRING type, so the field-derived value must be set
         // afterward - setting it beforehand would just get silently discarded by that cascade.
         this.alphaNumericValue = !!this.fields && this.fields.length > 0 ?
            this.fields[0].attribute : "";
      }
   }

   set alphaNumericValue(value: any) {
      this.form.get("alphaNumericValue").setValue(value);
   }

   isInvalid(): boolean {
      if(!this.form) {
         return true;
      }

      switch(this.model.type) {
         case XSchema.DATE:
            return this.form.controls["dateValue"].invalid || this.invalidDate();
         case XSchema.TIME:
            return this.form.controls["timeValue"].invalid;
         case XSchema.TIME_INSTANT:
            return this.form.controls["dateValue"].invalid || this.invalidDate() ||
               this.form.controls["timeValue"].invalid;
         case XSchema.BOOLEAN:
            return this.form.controls["alphaNumericValue"].invalid;
         default:
            return this.form.controls["alphaNumericValue"].invalid || !this.model.value;
      }
   }

   isFormInvalid(): boolean {
      return this.isInvalid() || this.form && this.form.controls["name"].invalid;
   }

   isGrayedOut(field: string): boolean {
      let grayedOutFields: any[] = this.grayedOutFields;
      let refName: string = field != null ? field.replace(":", ".") : null;

      if(refName && grayedOutFields) {
         for(let i = 0; grayedOutFields && i < grayedOutFields.length; i++) {
            if(grayedOutFields[i].name == refName) {
               return true;
            }
         }
      }

      return false;
   }

   changeName(name: any) {
      this.form.controls["name"].setValue(name);
   }

   invalidDate(): boolean {
      const date = new Date(this.model?.value)
      return isNaN(date.getTime());
   }

   hasViewsheetParameters(): boolean {
      return this.viewsheetParameters && this.viewsheetParameters.length > 0;
   }

   ok(): void {
      if(this._model.valueSource === "field") {
         if(this._model.value === "" && this.fields.length) {
            this.model.value = this.fields[0].name;
         }
         else {
            const idx = this.fields.findIndex(field => field.name == this._model.value);

            // Bug #75653 (fixed): guard against an empty `fields` array - previously this
            // unconditionally dereferenced fields[0] whenever no match was found, crashing on
            // a stale field-sourced model with zero available fields.
            if(idx < 0 && this.fields.length) {
               this.model.value = this.fields[0].name;
            }
         }
      }

      if(this.model.type == XSchema.CHARACTER && this.model.value) {
         this.model.value = (this.model.value + "").charAt(0);
      }

      this.onCommit.emit(this.model);
   }

   close(): void {
      this.onCancel.emit("cancel");
   }
}
