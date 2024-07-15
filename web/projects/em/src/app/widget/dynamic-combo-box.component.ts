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
import {
   Component,
   ElementRef,
   EventEmitter,
   Input,
   OnInit,
   Output,
   ViewChild
} from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { ComboMode, ValueMode } from "../../../../portal/src/app/widget/dynamic-combo-box/dynamic-combo-box-model";
import { MatDialog } from "@angular/material/dialog";
import { FormulaEditorDialogComponent } from "./formula-editor-dialog.component";
import { ScriptTreeDataSource } from "./script-tree-data-source";
import { FormulaEditorDialogModel } from "../../../../portal/src/app/widget/formula-editor/formula-editor-dialog-model";
import { MatAutocompleteTrigger } from "@angular/material/autocomplete";
import { MatMenuTrigger } from "@angular/material/menu";
import { XSchema } from "../../../../portal/src/app/common/data/xschema";
import { Tool } from "../../../../shared/util/tool";
import { FormValidators } from "../../../../shared/util/form-validators";

@Component({
   selector: "em-dynamic-combo-box",
   templateUrl: "./dynamic-combo-box.component.html",
   styleUrls: ["./dynamic-combo-box.component.scss"]
})
export class DynamicComboBoxComponent implements OnInit {
   public ComboMode = ComboMode;
   public ValueMode = ValueMode;
   _value: any;
   @Input() type: ComboMode = ComboMode.VALUE;
   @Input() mode: ValueMode = ValueMode.TEXT;

   @Input() form: UntypedFormGroup;
   @Input() values: any[];
   @Input() origValue: string = null; // original value if switch to expr
   @Input() isDate: boolean;
   @Input() columnTree: ScriptTreeDataSource;
   @Input() expressionEnable: boolean = true;
   @Input() format: string;
   @Input() task: boolean = false;
   @Input() expressionSubmitCallback: (_?: FormulaEditorDialogModel) => Promise<boolean> =
      () => Promise.resolve(true);
   @Output() typeChange: EventEmitter<ComboMode> = new EventEmitter<ComboMode>(true);
   @Output() valueChange: EventEmitter<any> = new EventEmitter<any>();
   @ViewChild(MatAutocompleteTrigger) autocompleteTrigger: MatAutocompleteTrigger;
   @ViewChild("menuTrigger") dateMenuTrigger: MatMenuTrigger;
   @ViewChild("menuPanel") dateMenu: ElementRef;
   protected readonly XSchema = XSchema;
   selected: Date = new Date();
   time: string;
   _dataType: string;
   _array: boolean;

   @Input() set array(isArray: boolean) {
      this._array = isArray;

      if(this.form && this.form.controls["value"]) {
         this.setValueValidator();
      }
   }

   get array() {
      return this._array;
   }

   @Input() set value(val: any) {
      this.updateType(val);
      this.updateValue(val);
   }

   get value(): any {
      return this._value;
   }

   @Input() set dataType(type: string) {
      this._dataType = type;

      if(this.form && this.form.controls["value"]) {
         this.setValueValidator();
      }
   }

   get dataType() {
      return this._dataType;
   }

   constructor(private dialog: MatDialog) {
   }

   ngOnInit() {
      this.initForm();
      this.updateType(this.value);

      if(this.type == ComboMode.VALUE) {
         this.origValue = this._value;
      }
      else {
         // type set by value should be reflected in caller which may rely on its accuracy
         this.typeChange.emit(this.type);

         if(this.isValuesDefinedAndNotEmpty()) {
            this.origValue = this.values[0]?.value || this.values[0];
         }
      }

      if(this.form && this.form.controls["value"]) {
         this.setValueValidator();
      }
   }

   initForm(): void {
      if(!this.form) {
         this.form = new UntypedFormGroup({
            "value": new UntypedFormControl(this.value, [Validators.required]),
         });
      }
      else {
         this.form.addControl("value", new UntypedFormControl(this.value, [Validators.required]));
      }

      this.form.get("value").valueChanges.subscribe((val) => {
         this.valueChange.emit(val);
      });
   }

   selectType(event: MouseEvent, type: ComboMode) {
      if(this.type != type) {
         if(this.type == ComboMode.VALUE) {
            this.origValue = this._value;
         }

         this.type = type;
         this.typeChange.emit(type);
         this.setValueValidator();

         if(type == ComboMode.VALUE) {
            this.updateValue(this.origValue);
         }
         else {
            this.updateValue("");
         }

         if(type == ComboMode.EXPRESSION) {
            setTimeout(() => this.showFormulaEditor(), 0);
         }
      }
   }

   private updateValue(val: any) {
      if(this.type == ComboMode.EXPRESSION) {
         val = val && val.charAt(0) == "=" ? val : "=" + val;
      }

      this._value = val;
      this.valueChange.emit(val);

      if(this.form && this.form.get("value") && val != this.form.get("value").value) {
         this.form.get("value").setValue(val);
      }
   }

   private updateType(val: any): void {
      let _type: ComboMode = ComboMode.VALUE;

      if(!val || !(typeof val === "string")) {
         this.type = _type;
         return;
      }
      else if(val.charAt(0) == "=") {
         _type = ComboMode.EXPRESSION;
      }

      if(this.type != _type) {
         this.type = _type;
         this.typeChange.emit(this.type);
      }
   }

   isValuesDefinedAndNotEmpty(): boolean {
      return !!this.values && this.values.length > 0;
   }


   showFormulaEditor(): void {
      let dialogRef = this.dialog.open(FormulaEditorDialogComponent, {
         data: {
            expression: this.displayValue,
            columnTreeRoot: Tool.clone(this.columnTree),
            submitCallback: this.expressionSubmitCallback,
            task: this.task
         },
         maxWidth: "1000px",
         width: "90vw",
         disableClose: true,
         autoFocus: false
      });

      dialogRef.afterClosed().subscribe((result: FormulaEditorDialogModel) => {
         if(!!result) {
            this.updateValue(result.expression);
         }
      });
   }

   get displayValue(): string {
      return this.value?.charAt(0) == "=" ? this.value.substring(1) : this.value;
   }

   onValueChange(val: string): void {
      this.value = val;
      this.form.get("value").setValue(val);

      if(this.dataType === XSchema.DATE) {
         this.closeDateTimePicker();
      }
   }

   onDateMenuOpened() {
      const menuPanel: Element = document.querySelector(".mat-mdc-menu-panel");
      (menuPanel as any).removeAllListeners();
   }

   selectedOption(val): boolean {
      return JSON.stringify(val) == JSON.stringify(this.form.get("value").value);
   }

   closeAutoComplete(evt: MouseEvent): void {
      evt.stopPropagation();

      if(this.autocompleteTrigger) {
         this.autocompleteTrigger.closePanel();
      }
   }

   closeDateTimePicker(): void {
      this.dateMenuTrigger.closeMenu();
   }

   get hasError(): boolean {
      return !Tool.isEmpty(this.getErrorMessage());
   }

   getPromptString() {
      let promptString: string = "_#(js:Value)";

      if(this.array || this.type != ComboMode.VALUE) {
         return promptString;
      }

      if(this.dataType == XSchema.DATE) {
         promptString = "yyyy-mm-dd";
      }
      else if(this.dataType == XSchema.TIME) {
         promptString = "hh:mm:ss";
      }
      else if(this.dataType == XSchema.TIME_INSTANT) {
         promptString = "yyyy-mm-dd hh:mm:ss";
      }

      return promptString;
   }

   getErrorMessage(): string {
      if(!this.form.controls["value"].errors) {
         return "";
      }

      let errorMessage: string = "_#(js:parameter.value.emptyValid)";

      if(this.form.controls["value"].errors["required"] && this.dataType != XSchema.BOOLEAN) {
         return errorMessage;
      }

      if(this.mode == ValueMode.NUMBER && (this.form.controls["value"].errors["isInteger"] ||
         this.form.controls["value"].errors["isFloatNumber"]))
      {
         errorMessage = "_#(js:em.common.param.numberInvalid)";
      }
      else if(this.mode == ValueMode.NUMBER && this.form.controls["value"].errors["integerInRange"]) {
         errorMessage = "_#(js:em.common.param.number.outNegativeRange)";
      }
      else if(this.dataType == XSchema.BOOLEAN) {
         errorMessage = "_#(js:em.common.param.boolean)";
      }
      else if(this.dataType == XSchema.DATE && this.form.controls["value"].errors["isDate"]) {
         errorMessage = "_#(js:em.schedule.condition.dateRequired)";
      }
      else if(this.dataType == XSchema.TIME && this.form.controls["value"].errors["isTime"]) {
         errorMessage = "_#(js:em.schedule.condition.parameter.timeRequired)";
      }
      else if(this.dataType == XSchema.TIME_INSTANT && this.form.controls["value"].errors["isDateTime"]) {
         errorMessage = "_#(js:em.schedule.condition.parameter.timeInstantRequired)";
      }

      return errorMessage;
   }

   getValueValidator() {
      let validators: any[] = [Validators.required];

      if(this.type != ComboMode.VALUE) {
         return validators;
      }

      if(this.dataType === XSchema.INTEGER) {
         validators = validators.concat([FormValidators.integerInRange(this.array),
            FormValidators.isInteger(this.array)]);
      }
      else if(this.dataType === XSchema.DOUBLE) {
         validators = validators.concat([FormValidators.isFloatNumber(this.array)]);
      }
      else if(this.dataType === XSchema.DATE) {
         validators = validators.concat([FormValidators.isDate(this.array)]);
      }
      else if(this.dataType === XSchema.TIME) {
         validators = validators.concat([FormValidators.isTime(this.array)]);
      }
      else if(this.dataType === XSchema.TIME_INSTANT) {
         validators = validators.concat([FormValidators.isDateTime(this.array)]);
      }
      else if(this.dataType === XSchema.BOOLEAN) {
         validators = validators.concat([FormValidators.isBoolean(this.array)]);
      }

      return validators;
   }

   private setValueValidator() {
      this.form.controls["value"].setValidators(this.getValueValidator());
      this.form.controls["value"].updateValueAndValidity();
   }
}
