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
   EventEmitter,
   Input,
   OnChanges,
   Output,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { Observable } from "rxjs";
import { DateTypeFormatter } from "../../../../../shared/util/date-type-formatter";
import { BrowseDataItemModel, BrowseDataModel } from "../../common/data/browse-data-model";
import { ConditionOperation } from "../../common/data/condition/condition-operation";
import { DataRef } from "../../common/data/data-ref";
import { XSchema } from "../../common/data/xschema";
import { FixedDropdownDirective } from "../fixed-dropdown/fixed-dropdown.directive";

@Component({
   selector: "value-editor",
   templateUrl: "value-editor.component.html",
   styleUrls: ["value-editor.component.scss"]
})
export class ValueEditor implements OnChanges {
   @Input() field: DataRef;
   @Input() operation: ConditionOperation;
   @Input() dataFunction: () => Observable<BrowseDataModel>;
   @Input() type: string;
   @Input() value: any;
   @Input() values: any[] = [];
   @Input() isOneOf: boolean;
   @Input() enableBrowseData: boolean = true;
   @Output() openBrowse = new EventEmitter();
   @Output() valueChange = new EventEmitter<any>();
   @Output() valueChanges = new EventEmitter<any[]>();
   @Output() addValue = new EventEmitter<any>();
   @ViewChild(FixedDropdownDirective) fieldsDropdown: FixedDropdownDirective;

   public XSchema = XSchema;
   public ConditionOperation = ConditionOperation;
   readonly TIME_INSTANT_FORMAT: string = "[{ts ']YYYY-MM-DD HH:mm:ss['}]";
   readonly TIME_FORMAT: string = "[{t ']HH:mm:ss['}]";
   readonly DATE_FORMAT: string = "[{d ']YYYY-MM-DD['}]";
   readonly DATE_TRANSFORM_REGEX: RegExp = /^{ts '(.*)'}|{t '(.*)'}|{d '(.*)'}$/;
   dataList: BrowseDataItemModel[] = [];
   dataListTruncated = false;
   error: boolean = false;
   loadingDataList: boolean;

   ngOnChanges(changes: SimpleChanges) {
      if(changes["field"] && changes["field"].currentValue != null) {
         if(this.dataFunction == null) {
            this.dataList = null;
         }
         else if(!this.isOneOf || !changes["field"].previousValue ||
            changes["field"].currentValue.attribute != changes["field"].previousValue.attribute)
         {
            this.dataList = [];
            this.dataListTruncated = false;
         }
      }

      if(changes["field"] || changes["operation"] || changes["type"]) {
         if(this.value == null && XSchema.isDateType(this.type) &&
            this.operation != ConditionOperation.DATE_IN)
         {
            this.emitDefaultDateValue();
         }

         if(this.type === XSchema.BOOLEAN &&
           (this.value == null || typeof this.value != "boolean"))
         {
            this.emitDefaultBooleanValue();
         }
      }
   }

   browseData(): void {
      const observable = this.dataFunction();
      this.openBrowse.emit();

      if(observable != null) {
         this.loadingDataList = true;

         observable.subscribe((dataList) => {
            if(dataList) {
               this.dataList = this.getBrowseDataList(dataList);
               this.dataListTruncated = dataList.dataTruncated;
            }

            this.error = false;
            this.loadingDataList = false;
         },
         (error: Response) => {
            this.error = true;
            this.loadingDataList = false;
         });
      }
   }

   getBrowseDataList(model: BrowseDataModel): BrowseDataItemModel[] {
      if(!model || !model.values || model.values.length == 0) {
         return [];
      }

      let dataList: BrowseDataItemModel[] = [];

      for(let i = 0; i < model.values.length; i++) {
         let label = null;

         if(model.labels) {
            label = model.labels[i];
         }

         dataList.push({label: label, value: model.values[i]});
      }

      return dataList;
   }

   isBrowseEnabled() {
      return !!this.field && !this.field.fakeNone && this.enableBrowseData && this.dataList &&
         this.type != XSchema.BOOLEAN && this.operation != ConditionOperation.DATE_IN &&
         this.field != null && this.field.classType != "AggregateRef" &&
         this.field.classType != "BAggregateRefModel" && this.field.classType != "CalculateRef";
   }

   selectData(data: BrowseDataItemModel): void {
      this.value = data.value;
      this.valueChange.emit(this.value);
   }

   selectValues(event: any): void {
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
      return this.values
         .filter(v => v == item ||
            (XSchema.isDateType(this.type) && this.transformDate(v) == this.transformDate(item)))
         .length > 0;
   }

   private emitDefaultDateValue() {
      let value: string;

      switch(this.type) {
         case XSchema.DATE:
            value = DateTypeFormatter.currentTimeInstantInFormat(this.DATE_FORMAT);
            break;
         case XSchema.TIME:
         value = DateTypeFormatter.currentTimeInstantInFormat(this.TIME_FORMAT);
            break;
         case XSchema.TIME_INSTANT:
            value = DateTypeFormatter.currentTimeInstantInFormat(this.TIME_INSTANT_FORMAT);
            break;
         default:
            // no-op
      }

      if(value != undefined) {
         this.valueChange.emit(value);
      }
   }

   private emitDefaultBooleanValue() {
      this.valueChange.emit("false");
   }

   private transformDate(value: string): string {
      const match = this.DATE_TRANSFORM_REGEX.exec(value);

      if(match) {
         for(let i = 1; i < match.length; i++) {
            if(match[i]) {
               return match[i];
            }
         }
      }

      return value;
   }

   hideBrowse() {
      if(this.fieldsDropdown) {
         this.fieldsDropdown.close();
      }
   }
}
