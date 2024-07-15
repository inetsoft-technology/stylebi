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
   OnInit,
   Output,
   SimpleChanges
} from "@angular/core";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { CustomPeriodPaneModel } from "../../model/custom-period-pane-model";
import { DatePeriodModel } from "../../model/date-period-model";
import { DynamicValueModel, ValueTypes } from "../../model/dynamic-value-model";
import { DateComparisonService } from "../../util/date-comparison.service";

@Component({
   selector: "date-comparison-custom-periods",
   templateUrl: "./date-comparison-custom-periods.component.html",
   styleUrls: ["./date-comparison-custom-periods.component.scss"]
})
export class DateComparisonCustomPeriodsComponent implements OnInit, OnChanges {
   @Input() customPeriodPaneModel: CustomPeriodPaneModel;
   @Input() variableValues: string[] = [];
   @Input() disable: boolean = false;
   @Input() columnTreeRoot: TreeNodeModel = null;
   @Input() functionTreeRoot: TreeNodeModel = null;
   @Input() operatorTreeRoot: TreeNodeModel = null;
   @Input() scriptDefinitions: any = null;
   @Output() validChange = new EventEmitter<boolean>();

   constructor(private dateComparisonService: DateComparisonService) {
   }

   ngOnInit(): void {
      this.initCustomPeriodModel();
      this.validChange.emit(this.isValidCustomPeriod());
   }

   ngOnChanges(): void {
      this.validChange.emit(this.isValidCustomPeriod());
   }

   initCustomPeriodModel() {
      if(this.customPeriodPaneModel.datePeriods.length != 0) {
         this.sortCustomPeriods();
      }
      else {
         for(let i = this.customPeriodPaneModel.datePeriods.length; i < 2; i++) {
            this.customPeriodPaneModel.datePeriods.push(this.createDatePeriod());
         }
      }
   }

   private createDatePeriod(): DatePeriodModel {
      return {
         start: this.createDefaultValueModel(),
         end: this.createDefaultValueModel()
      };
   }

   private createDefaultValueModel(): DynamicValueModel {
      return {
         value: null,
         type: ValueTypes.VALUE
      };
   }

   addDatePeriod(): void {
      this.customPeriodPaneModel.datePeriods.push(this.createDatePeriod());
      this.validChange.emit(false);
   }

   delDatePeriod(index: number): void {
      this.customPeriodPaneModel.datePeriods.splice(index, 1);
      this.validChange.emit(this.isValidCustomPeriod());
   }

   get isRemoveDisable(): boolean {
      return this.customPeriodPaneModel.datePeriods.length <= 2 || this.disable;
   }

   sortCustomPeriods(): void {
      this.customPeriodPaneModel.datePeriods.sort((a, b) => {
         return new Date(a.start.value).getTime() - new Date(b.start.value).getTime();
      });
   }

   isValidCustomPeriod(): boolean {
      let dynamicValue: DynamicValueModel[] = [];

      this.customPeriodPaneModel.datePeriods.forEach(datePeriod => {
         dynamicValue.push(datePeriod.start);
         dynamicValue.push(datePeriod.end);
      });

      return !dynamicValue.some(value => !this.dateComparisonService.isValidDate(value.value));
   }

   onValueChange(): void {
      this.validChange.emit(this.isValidCustomPeriod());
   }
}
