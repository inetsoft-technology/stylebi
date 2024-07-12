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
   Component,
   EventEmitter,
   Input, OnInit,
   Output, SimpleChanges,
   TemplateRef, OnChanges,
   ViewChild
} from "@angular/core";
import { XSchema } from "../../common/data/xschema";
import { VariableListDialogModel } from "../../widget/dialog/variable-list-dialog/variable-list-dialog-model";
import { DialogService } from "../../widget/slide-out/dialog-service.service";
import { SlideOutOptions } from "../../widget/slide-out/slide-out-options";
import { ComboBoxEditorModel } from "../model/combo-box-editor-model";
import { SelectionListDialogModel } from "../model/selection-list-dialog-model";
import { HttpClient, HttpParams } from "@angular/common/http";
import { ComboboxGeneralPaneModel } from "../../composer/data/vs/combobox-general-pane-model";
import { ComboBoxDefaultValueListModel } from "../model/combo-box-queryList-model";

@Component({
   selector: "combo-box-editor",
   templateUrl: "combo-box-editor.component.html",
})
export class ComboBoxEditor implements OnInit, OnChanges {
   @ViewChild("selectionListDialog") selectionListDialog: TemplateRef<any>;
   @ViewChild("variableListDialog") variableListDialog: TemplateRef<any>;
   @Input() model: ComboBoxEditorModel;
   @Input() sortType: number;
   @Input() embeddedDataDown: boolean;
   @Input() general: ComboboxGeneralPaneModel;
   @Input() variableValues: string[];
   @Input() runtimeId: string;
   @Input() enableDataType: boolean = true;
   @Input() showCalendar: boolean = false;
   @Input() showApplySelection: boolean = true;
   @Output() isInputValid: EventEmitter<boolean> = new EventEmitter<boolean>();
   readonly dataTypeList = XSchema.standardDataTypeList;
   readonly DATE_PATTERN = /^([0-9]{4})-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])$/;
   readonly DATETIME_PATTERN = /^([0-9]{4})-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])(\s+)([01]?[0-9]|2[0-3]):[0-5]?[0-9]:[0-5]?[0-9]$/;
   readonly TIME_PATTERN = /^([01]?[0-9]|2[0-3]):[0-5]?[0-9]:[0-5]?[0-9]$/;
   valueList: ComboBoxDefaultValueListModel[];
   get datePrompt(): string {
      if(this.model.dataType != XSchema.TIME) {
         return this.isDate ? "yyyy-mm-dd" : "yyyy-MM-dd HH:mm:ss";
      }
      else if(this.model.dataType == XSchema.TIME) {
         return "HH:mm:ss";
      }

      return null;
   }

   get isDate(): boolean {
      return this.model.dataType == XSchema.DATE;
   }

   constructor(private modalService: DialogService, private http: HttpClient) {}

   ngOnInit(){
      this.model.noDefault = this.model.defaultValue === null || this.model.defaultValue === undefined;
      this.onDefaultvalueChanged(this.model.defaultValue);
   }

   ngOnChanges(changes: SimpleChanges){
      if(changes.sortType || changes.embeddedDataDown) {
         this.updatDefaultValues();
      }
   }

   showSelectionListDialog(): void {
      const options: SlideOutOptions = {
         popup: true,
         backdrop: "static"
      };

      this.modalService.open(this.selectionListDialog, options).result.then(
         (result: SelectionListDialogModel) => {
            this.model.selectionListDialogModel = result;
            this.updatDefaultValues();
         },
         () => {
         }
      );
   }

   private updatDefaultValues(): void {
      const queryLabel: string = this.model.selectionListDialogModel.selectionListEditorModel.column;
      const queyValue: string = this.model.selectionListDialogModel.selectionListEditorModel.value;
      const embedLabel: string[] =this.model.variableListDialogModel.labels;
      const embedValues: string[] =this.model.variableListDialogModel.values;

      if(this.general) {
         let params = new HttpParams()
             .set("runtimeId", this.runtimeId)
             .set("sortType", this.sortType)
             .set("embeddedDataDown", this.embeddedDataDown)
             .set("objectId", this.general.generalPropPaneModel.basicGeneralPaneModel.name);

         this.http.post<ComboBoxDefaultValueListModel[]>("../api/composer/vs/comboboxeditor/larbel",
            this.model, {params}).subscribe(value => {
               this.valueList = value;

            if((this.model.query && queryLabel != queyValue) ||
               (this.model.embedded && !(this.arraysAreEqual(embedValues,embedLabel))))
            {
               for(let i = 0; i < value.length; i++) {
                  this.valueList[i].formatValue = this.valueList[i].label + " | " + this.valueList[i].formatValue;
               }
            }

            if(!(this.model.defaultValue === null || this.model.defaultValue === undefined)) {
               for(let i = 0; i < value.length; i++) {
                  if(value[i].value === this.model.defaultValue) {
                     return;
                  }
               }

               this.model.defaultValue = value[0].value;
            }
         });
      }
   }

   private arraysAreEqual(arr1: any[], arr2: any[]): boolean {
      if(arr1.length !== arr2.length) {
         return false;
      }

      return arr1.every((element, index) => element === arr2[index]);
   }

   showVariableListDialog(): void {
      this.resetValid();
      const options: SlideOutOptions = {
         popup: true,
         size: "lg",
         backdrop: "static"
      };
      this.modalService.open(this.variableListDialog, options).result.then(
         (result: VariableListDialogModel) => {
            this.model.variableListDialogModel = result;
            this.updatDefaultValues();
         },
         () => {
         }
      );
   }

   updateType(event: string): void {
      this.model.dataType = event;
      this.model.variableListDialogModel.dataType = event;
      this.model.serverTZ = this.model.serverTZ ||
         event == XSchema.TIME || event == XSchema.TIME_INSTANT;
      this.model.maxDate = "";
      this.model.minDate = "";

      if(event !== XSchema.DATE && event !== XSchema.TIME && event !== XSchema.TIME_INSTANT) {
         this.model.calendar = false;
      }

      this.resetValid();
   }

   resetValid(): void {
      this.model.valid = true;
   }

   calendarEnabled(): boolean {
      return this.model && this.model.dataType &&
         (this.model.dataType == XSchema.DATE
         || this.model.dataType == XSchema.TIME
         || this.model.dataType == XSchema.TIME_INSTANT);
   }

   updateCalendar() {
      this.updatDefaultValues();
      if(this.model.embedded) {
         if(this.model.calendar) {
            this.model.query = false;
         }
         else {
            //If there are invalid range values, fix them before unsetting calendar

            if(!this.validateDateValue(this.model.minDate)) {
               this.model.minDate = "";
            }

            if(!this.validateDateValue(this.model.maxDate)) {
               this.model.maxDate = "";
            }

            if(!this.validateDateRange(this.model.minDate, this.model.maxDate)) {
               this.model.minDate = "";
               this.model.maxDate = "";
            }
         }

         this.onDateRangeChanged();
      }
      else if(this.model.calendar) {
         this.isInputValid.emit(true);
      }
   }


   /**
    * Disable calendar when using a query populated list
    */
   public toggleQuery() {
      this.updatDefaultValues();

      if(this.model.query) {
         this.model.calendar = false;
      }
   }

   onDateRangeChanged() {
      if(!this.model.calendar ||
         (this.validateDateValue(this.model.minDate) &&
         this.validateDateValue(this.model.maxDate) &&
         this.validateDateRange(this.model.minDate, this.model.maxDate)))
      {
         this.isInputValid.emit(true);
      }
      else {
         this.isInputValid.emit(false);
      }

      this.onDefaultvalueChanged(this.model.defaultValue);
   }

   onDefaultvalueChanged(defaultValue){
      if(this.model.calendar && (this.isdefaultValue(defaultValue) ||
         !this.validateDateValue(defaultValue)))
      {
         this.isInputValid.emit(false);
      }
      else {
         this.isInputValid.emit(true);
      }
   }

   get currentPattern(): RegExp {
      if(this.model.dataType != XSchema.TIME) {
         return this.isDate ? this.DATE_PATTERN :
             this.model.dataType == XSchema.TIME_INSTANT ? this.DATETIME_PATTERN : null;
      }
      else if(this.model.dataType == XSchema.TIME) {
         return this.TIME_PATTERN;
      }

      return null;
   }

   validateDateValue(date: string): boolean {
      if(date != null && date != "" && !date.startsWith("$") && !date.startsWith("=")) {
         let pattern = this.currentPattern;

         if(!!pattern) {
            return pattern.test(date);
         }
      }

      return true;
   }

   validateDateRange(minDate: string, maxDate: string): boolean {
      if(minDate != null && minDate != "" && !minDate.startsWith("$") && !minDate.startsWith("=") &&
         maxDate != null && maxDate != "" && !maxDate.startsWith("$") && !maxDate.startsWith("=") &&
         this.currentPattern != null)
      {
         const pattern = this.currentPattern;
         const minValues = minDate.match(pattern);
         const maxValues = maxDate.match(pattern);
         const maxSize = this.isDate ? 4 : 6;

         if(minValues.length == maxSize && maxValues.length == maxSize) {
            let i = 1;

            //Compare year, month, and day
            while (i < maxSize + 1) {
               if(minValues[i] > maxValues[i]) {
                  return false;
               }
               else if(minValues[i] == maxValues[i]) {
                  i ++;
               }
               else {
                  i = maxSize + 1;
               }
            }
         }
      }

      return true;
   }

   showDateRangeWarning(): boolean {
      return this.validateDateValue(this.model.minDate) &&
          this.validateDateValue(this.model.maxDate) &&
          !this.validateDateRange(this.model.minDate, this.model.maxDate);
   }

   showDateRanges(): boolean {
      return this.model.calendar  && this.model.dataType != XSchema.TIME;
   }

   changeNodefault(){
      if(this.model.noDefault) {
         this.model.defaultValue = null;
      }

      this.onDefaultvalueChanged(this.model.defaultValue);
   }

   isdefaultValue(defaultValue){
      if(this.model.dataType == XSchema.TIME_INSTANT || this.model.dataType == XSchema.DATE) {
         if(this.model.minDate && this.model.minDate > defaultValue) {
            return true;
         }
         else if(this.model.maxDate && this.model.maxDate < defaultValue) {
            return true;
         }
      }

      return false;
   }
}
