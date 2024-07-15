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
import { Component, OnChanges, Input, Output, EventEmitter, SimpleChanges } from "@angular/core";
import { OperationModel } from "../../../../../../data/model/datasources/database/vpm/condition/clause/operation-model";
import { Observable } from "rxjs";
import { XSchema } from "../../../../../../../common/data/xschema";
import { DateTypeFormatter } from "../../../../../../../../../../shared/util/date-type-formatter";
import { ClauseOperationSymbols } from "../../../../../../data/model/datasources/database/vpm/condition/clause/clause-operation-symbols";

@Component({
   selector: "vpm-value-editor",
   templateUrl: "vpm-value-editor.component.html",
   styleUrls: ["vpm-value-editor.component.scss"]
})
export class VPMValueEditorComponent implements OnChanges {
   @Input() value: string;
   @Input() values: any[] = [];
   @Input() operation: OperationModel;
   @Input() type: string = XSchema.STRING;
   @Input() enableBrowseData: boolean = true;
   @Input() dataFunction: () => Observable<string[]>;
   @Output() valueChange: EventEmitter<string> = new EventEmitter<string>();
   @Output() valueChanges: EventEmitter<string[]> = new EventEmitter<string[]>();
   dataList: string[] = [];
   XSchema = XSchema;
   error: boolean = false;
   loadingDataList: boolean;
   readonly TIME_INSTANT_FORMAT: string = "[{ts ']YYYY-MM-DD HH:mm:ss['}]";
   readonly TIME_FORMAT: string = "[{t ']HH:mm:ss['}]";
   readonly DATE_FORMAT: string = "[{d ']YYYY-MM-DD['}]";

   ngOnChanges(changes: SimpleChanges) {
      if(changes["operation"] || changes["type"]) {
         if(this.value == null && XSchema.isDateType(this.type)) {
            this.emitDefaultDateValue();
         }

         if(this.type === XSchema.BOOLEAN &&
            (this.value == null || typeof this.value != "boolean" && this.value != "false" && this.value != "true"))
         {
            this.emitDefaultBooleanValue();
         }
      }
   }

   get isOneOf(): boolean {
      return !!this.operation && this.operation.symbol == ClauseOperationSymbols.IN;
   }

   /**
    * Called when user clicks browse data button. Get the browse data list.
    */
   browseData(): void {
      let observable: Observable<string[]> = this.dataFunction();

      if(observable != null) {
         this.loadingDataList = true;

         observable.subscribe((dataList) => {
            this.dataList = dataList;
            this.error = false;
            this.loadingDataList = false;
         },
         (error: Response) => {
            this.error = true;
            this.loadingDataList = false;
         });
      }
   }

   /**
    * Called when user selects a value from browse data list. Update and emit the new value.
    * @param data the data selected
    */
   selectData(data: string): void {
      this.value = data;
      this.valueChange.emit(this.value);
   }

   selectValues(event: any): void {
      if(!this.values) {
         this.values = [];
      }

      // remove field items if value is selected
      this.values = this.values.filter(s => s.classType == null);

      if(event.target.checked) {
         this.values.push(event.target.value);
      }
      else {
         this.values = this.values.filter(s => s != event.target.value);
      }

      this.valueChanges.emit(this.values);
   }

   isSelected(item: any): boolean {
      return this.values && this.values.filter(v => v == item).length > 0;
   }

   /**
    * Called when the current value type is date and the current value is null. Create a new date
    * with the current date and set the value.
    */
   private emitDefaultDateValue(): void {
      let value: string;

      switch(this.type) {
      case XSchema.DATE:
         value = DateTypeFormatter.currentTimeInstantInFormat(this.DATE_FORMAT);
         break;
      case XSchema.TIME:
         value = DateTypeFormatter.transformValue(
            "00:00:00", DateTypeFormatter.ISO_8601_TIME_FORMAT, this.TIME_FORMAT);
         break;
      case XSchema.TIME_INSTANT:
         value = DateTypeFormatter.currentTimeInstantInFormat(this.TIME_INSTANT_FORMAT);
         break;
      default:
         // no-op
      }

      if(value != undefined) {
         // change value on changes causes expression changed error on parent components, wrap in
         // timeout so change detection updates correctly
         setTimeout(() => this.valueChange.emit(value));
      }
   }

   /**
    * Called when the current value type is boolean and the current value is null. Set the current
    * value to false.
    */
   private emitDefaultBooleanValue(): void {
      // change value on changes causes expression changed error on parent components, wrap in
      // timeout so change detection updates correctly
      setTimeout(() => this.valueChange.emit("false"));
   }
}