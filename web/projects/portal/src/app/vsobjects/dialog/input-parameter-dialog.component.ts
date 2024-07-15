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
import { UntypedFormControl, UntypedFormGroup, Validators, ValidatorFn } from "@angular/forms";
import { NgbDateParserFormatter, NgbDateStruct } from "@ng-bootstrap/ng-bootstrap";
import { DataRef } from "../../common/data/data-ref";
import { XSchema } from "../../common/data/xschema";
import { DateTypeFormatter } from "../../../../../shared/util/date-type-formatter";
import { FormValidators } from "../../../../../shared/util/form-validators";
import { InputParameterDialogModel } from "../model/input-parameter-dialog-model";
import { Tool } from "../../../../../shared/util/tool";
import { debounceTime, distinctUntilChanged } from "rxjs/operators";

@Component({
   selector: "input-parameter-dialog",
   templateUrl: "input-parameter-dialog.component.html",
   styleUrls: ["./input-parameter-dialog.component.scss"]
})
export class InputParameterDialog implements OnInit {
   @Input() fields: DataRef[];
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
         this.form.controls["name"].setValue(this.model.name);
         this.form.controls["type"].setValue(this.model.type);
         this.dateValue = this.date;
         this.timeValue = this.time;
         this.alphaNumericValue = this.model.value;
      }
   }

   get model(): InputParameterDialogModel {
      return this._model;
   }

   constructor(private ngbDateParserFormatter: NgbDateParserFormatter,
               private changeDetectionRef: ChangeDetectorRef)
   {
   }

   ngOnInit() {
      if(!this.selectEdit) {
         this.model = {
            name: "",
            value: "",
            valueSource: "field",
            type: XSchema.STRING
         };
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
      }
      else {
         this.model.valueSource = "field";
         this.model.value = !!this.fields && this.fields.length > 0 ?
            this.fields[0].attribute : "";
         this.model.type = XSchema.STRING;
      }

      this.changeType();
   }

   set alphaNumericValue(value: any) {
      this.form.get("alphaNumericValue").setValue(value);
   }

   isInvalid(): boolean {
      let invalid: boolean;

      if(this.model.type == XSchema.DATE) {
         invalid = this.form && this.form.controls["dateValue"].invalid;
      }
      else if(this.model.type == XSchema.TIME) {
         invalid = this.form && this.form.controls["timeValue"].invalid;
      }
      else if(this.model.type == XSchema.TIME_INSTANT) {
         invalid = this.form && (this.form.controls["dateValue"].invalid ||
            this.form.controls["timeValue"].invalid);
      }
      else {
         invalid = this.form && this.form.controls["alphaNumericValue"].invalid;
      }

      return invalid || !this.model.value;
   }

   isFormInvalid(): boolean {
      return this.isInvalid() || this.form && this.form.controls["name"].invalid;
   }

   changeName(name: any) {
      this.form.controls["name"].setValue(name);
   }

   ok(): void {
      if(this._model.valueSource === "field") {
         if(this._model.value === "" && this.fields.length) {
            this.model.value = this.fields[0].name;
         }
         else {
            const idx = this.fields.findIndex(field => field.name == this._model.value);

            if(idx < 0) {
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
