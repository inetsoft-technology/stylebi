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
import {Component, EventEmitter, Input, Output} from "@angular/core";
import {NgbModal, NgbModalOptions} from "@ng-bootstrap/ng-bootstrap";
import {isString} from "lodash";
import {DateTypeFormatter} from "../../../../../../shared/util/date-type-formatter";
import {ComponentTool} from "../../../common/util/component-tool";
import {ParameterPageModel} from "../parameter-page-model";
import {
   ChoiceParameterModel,
   ListParameterModel,
   OptionParameterModel,
   RepletParameterModel
} from "../replet-parameter-model";
import {Tool} from "../../../../../../shared/util/tool";
import {DateTimeValueDialog} from "../date-time-value-dialog.component";

@Component({
   selector: "parameter-page",
   templateUrl: "parameter-page.component.html",
   styleUrls: ["./parameter-page.component.scss"]
})
export class ParameterPage {
   @Input() isDialog = false;
   @Input()
   get pageModel(): ParameterPageModel {
      return this._pageModel;
   }

   set pageModel(pageModel: ParameterPageModel) {
      this._pageModel = pageModel;
   }

   @Output() onSubmit: EventEmitter<RepletParameterModel[]> = new EventEmitter<RepletParameterModel[]>();
   private _pageModel: ParameterPageModel;
   readonly TIME_INSTANT_FORMAT: string = "YYYY-MM-DD HH:mm:ss";
   readonly DATE_FORMAT: string = "YYYY-MM-DD";
   readonly TIME_FORMAT: string = "HH:mm:ss";

   constructor(public modalService: NgbModal) {
   }

   get sidePaneVisible(): boolean {
      return !!this.pageModel && (!!this.pageModel.reportDesc || !!this.pageModel.footerText);
   }

   get formWidthClasses(): string {
      return this.sidePaneVisible ? "col-lg-9 col-md-10" : "col-12";
   }

   get fieldWidthClasses(): string {
      return this.sidePaneVisible ?
         "col-xl-6 col-lg-8 col-md-10 col-sm-12" :
         "col-xl-4 col-lg-6 col-md-8 col-sm-12";
   }

   getDateFormat(param: RepletParameterModel): string {
      switch(param.type) {
      case "DateParameter":
         return this.DATE_FORMAT;
      case "TimeParameter":
         return this.TIME_FORMAT;
      case "DateTimeParameter":
         return this.TIME_INSTANT_FORMAT;
      default:
         return null;
      }
   }

   initRadioDefaultValue(pageModel: ParameterPageModel): void {
      for(let param of pageModel.params) {
         if(param.type == "RadioParameter") {
            param.value = param.value == null ?
               (<ChoiceParameterModel> param).choicesValue[0] : param.value;
         }
      }
   }

   openDateTimeValue(param: RepletParameterModel): void {
      let modalOptions: NgbModalOptions = {
         backdrop: "static",
         size: "sm"
      };

      const dialog = ComponentTool.showDialog(
         this.modalService,
         DateTimeValueDialog,
         (dateTime: string) => {
            if(param.multi && !!param.value) {
               param.value += "," + dateTime;
            }
            else {
               param.value = dateTime;
            }
         },
         modalOptions,
         (value) => {
            if(value == "clear") {
               param.value = "";
            }
         });

      dialog.promptTime = param.type === "TimeParameter" || param.type === "DateTimeParameter";
      dialog.format = DateTypeFormatter.fixFormatToMoment(this.getDateFormat(param));
      dialog.date = param.value;
      dialog.promptDate = param.type !== "TimeParameter";
   }

   changeValue(value: any, param: RepletParameterModel) {
      param.value = value;
      param.changed = true;
   }

   checkParamsValid(): boolean {
      let params = this.pageModel.params;

      if(!params || params.length == 0) {
         return true;
      }

      for(let param of params) {
         if(!param.multi && !this.checkValueValid(param, param.value)) {
            return false;
         }
         else if(param.multi) {
            let values = typeof param.value === "string" ?
               param.value.split(",") : [param.value];

            for(let i = 0; i < values.length; i++) {
               if(!this.checkValueValid(param, values[i])) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   private checkValueValid(param: RepletParameterModel, value: any): boolean {
      let valid = true;
      const type = param.type;

      if(!!value &&
         (type === "DateParameter" || type === "DateTimeParameter" || type === "TimeParameter"))
      {
         valid = !!DateTypeFormatter.toTimeInstant(value.trim(), this.getDateFormat(param));
      }

      if(!valid) {
         const name = !!param.alias ? param.alias : param.name;
         const errorMsg = "_#(js:viewer.wrongDateFmt.note4)" +
            "_*" + name + "," + this.getDateFormat(param);
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", errorMsg);
         return false;
      }

      return true;
   }

   ok(): void {
      if(!this.checkParamsValid()) {
         return;
      }

      let encodeParameter = this.encodeSimpleParameter();

      const params = encodeParameter.map((param => {
         let format = this.getDateFormat(param);

         if(!!param.value && !!format) {
            let paramValues: any[];

            if(param.multi) {
               paramValues = typeof param.value === "string" ?
                  param.value.split(",").filter(v => v) : [param.value];
            }
            else {
               paramValues = [param.value];
            }

            format = DateTypeFormatter.fixFormatToMoment(format);
            const transformedValues = [];

            for(let paramValue of paramValues) {
               if(format !== "YYYY-MM-DD HH:mm:ss") {
                  let transformedValue = DateTypeFormatter.transformValue(paramValue, format,
                     "YYYY-MM-DD HH:mm:ss");
                  // if the string doesn't match the format, instead of passing null,
                  // just pass the actual string to the server to try to parse it by java
                  transformedValue = transformedValue ? transformedValue : paramValue;
                  transformedValues.push(transformedValue);
               }
               else {
                  transformedValues.push(paramValue);
               }
            }

            let combinedValue: any;

            if(transformedValues.length > 1) {
               combinedValue = "^[" + transformedValues.join(",") + "]^";
            }
            else {
               combinedValue = transformedValues[0];
            }

            return Object.assign({}, param, {value: combinedValue});
         }

         return param;
      }));
      this.onSubmit.emit(params);
   }

   clear(): void {
      if(!this.pageModel || !this.pageModel.params) {
         return;
      }

      this.pageModel.params.forEach((param: RepletParameterModel) => {
         if(param.type == "OptionParameter") {
            let clearSelect: boolean[] = [];

             for(let selected of (<OptionParameterModel> param).selectedValues) {
                clearSelect.push(false);
             }

            (<OptionParameterModel> param).selectedValues = clearSelect;
         }
         else if(param.type == "ListParameter") {
            (<ListParameterModel> param).values = [];
         }
         else {
            param.value = null;
         }
      });
   }

   cancel(): void {
      this.onSubmit.emit(null);
   }

   isArray(val: any): boolean {
      return val != null && val instanceof Array;
   }

   isValidDecimal(param: RepletParameterModel): boolean {
      if(!param || !param.decimalType || param.type !== "SimpleParameter" || !param.value)
      {
         return true;
      }

      if(!param.multi) {
         return this.isValidJavaDouble(param.value);
      }

      let values: string[] = param.value.split(",");

      for(let val of values) {
         if(!this.isValidJavaDouble(val)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Test if the input string is a valid double value.
    *
    * @param value the specified input string
    * @return true if is, false otherwise
    */
   isValidJavaDouble(value: any): boolean {
      if(isString(value) && value.trim() == "" || value == "NULL_VALUE") {
         return true;
      }

      if(isNaN(value)) {
         return false;
      }

      const val = parseFloat(value);
      const str2 = val + "";
      return parseFloat(str2) == value;
   }

   canSubmit(): boolean {
      let params = this.pageModel.params;

      for(let i = 0; i < params.length; i++) {
         if(!this.isValidDecimal(params[i])) {
            return false;
         }

         if(params[i].required && (params[i].value == null || params[i].value === "")) {
            return false;
         }
      }

      return true;
   }

   private encodeSimpleParameter(): RepletParameterModel[] {
      let params = Tool.clone(this.pageModel.params);

      for(let i = 0; i < params.length; i++) {
         if(params[i].type === "SimpleParameter"
            && params[i].multi
            && this.isParameterArray(params[i].value))
         {
            params[i].value = "^[" + params[i].value + "]^";
         }
      }

      return params;
   }

   private isParameterArray(value: string): boolean {
      return !!value && value.indexOf(",") > -1;
   }
}
