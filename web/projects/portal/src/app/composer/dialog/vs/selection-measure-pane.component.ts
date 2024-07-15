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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { AggregateFormula } from "../../../binding/util/aggregate-formula";
import { DataRefType } from "../../../common/data/data-ref-type";
import { XSchema } from "../../../common/data/xschema";
import { OutputColumnRefModel } from "../../../vsobjects/model/output-column-ref-model";
import { ComboMode } from "../../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { SelectionMeasurePaneModel } from "../../data/vs/selection-measure-pane-model";
import { HttpClient, HttpHeaders, HttpParams } from "@angular/common/http";
import { VSUtil } from "../../../vsobjects/util/vs-util";

const COLUMNS_URI: string = "../vs/dataOutput/selection/columns";

@Component({
   selector: "selection-measure-pane",
   templateUrl: "selection-measure-pane.component.html",
   styleUrls: ["selection-measure-pane.component.scss"]
})
export class SelectionMeasurePane implements OnInit {
   @Input() model: SelectionMeasurePaneModel;
   @Input() runtimeId: string;
   @Input() variableValues: string[];
   @Input() grayedOutValues: string[] = [];
   @Output() measuresChange: EventEmitter<OutputColumnRefModel[]> = new EventEmitter<OutputColumnRefModel[]>();
   cubeString: string = "__inetsoft_cube_";
   headers: HttpHeaders;
   tableNames: string[] = [];
   localMeasure: string;
   localFormula: string;
   measures: OutputColumnRefModel[] = [];
   measureLabels: any[] = [];
   measureTooltips: string[] = [];
   aggregatesNonnumeric: string[] = [AggregateFormula.COUNT_ALL.label, AggregateFormula.MAX.label,
                                            AggregateFormula.MIN.label];
   aggregatesNumeric: string[] = [AggregateFormula.COUNT_ALL.label, AggregateFormula.SUM.label,
                                           AggregateFormula.MAX.label, AggregateFormula.MIN.label];
   aggregateLabels: string[] = this.aggregatesNumeric;

   constructor(private http: HttpClient) {
      this.headers = new HttpHeaders({
         "Content-Type": "application/json"
      });
   }

   ngOnInit() {
      if(this.model.formula &&
         !this.model.formula.startsWith("$") && !this.model.formula.startsWith("="))
      {
         this.localFormula = AggregateFormula.getFormula(this.model.formula).label;
      }
      else {
         this.localFormula = this.model.formula;
      }

      if(this.model.measure &&
         (this.model.measure.startsWith("$") || this.model.measure.startsWith("=")))
      {
         this.localMeasure = this.model.measure;
      }
   }

   @Input()
   set tables(tables: string[]) {
      const tablesChanged = tables.length !== this.tableNames.length ||
         tables.find((t) => this.tableNames.indexOf(t) == -1) != null;
      this.tableNames = tables;

      if(this.tableNames.length === 0) {
         this.measureLabels = [];
         this.measureTooltips = [];
         this.aggregateLabels = this.aggregatesNumeric;
         this.updateAggregate();

         if(this.localMeasure &&
            !this.localMeasure.startsWith("$") && !this.localMeasure.startsWith("="))
         {
            this.localMeasure = null;
            this.model.measure = null;
         }
      }
      else if(tablesChanged) {
         this.getMeasures();
      }
   }

   getMeasures(): void {
      if(this.tableNames.length > 0) {
         let params = new HttpParams()
            .set("runtimeId", this.runtimeId);

         for(const tableName of this.tableNames) {
            params = params.append("table", VSUtil.getTableName(tableName));
         }

         this.measureLabels = [];
         this.measureTooltips = [];
         this.measures = [];

         this.http.get<OutputColumnRefModel[]>(COLUMNS_URI, {params}).subscribe(
               (data: OutputColumnRefModel[]) => {
                  this.measures = data;
                  this.measuresChange.emit(this.measures);
                  this.localMeasure = null;

                  for(let measure of this.measures) {
                     this.measureLabels.push({value: measure.name, label: measure.view,
                           tooltip: measure.description});

                     if(this.model.measure == measure.name) {
                        this.localMeasure = measure.name;
                        this.aggregateLabels = XSchema.isNumericType(measure.dataType) ?
                           this.aggregatesNumeric : this.aggregatesNonnumeric;
                        this.updateAggregate();
                     }
                  }

                  if(this.model.measure &&
                     (this.model.measure.startsWith("$") || this.model.measure.startsWith("=")))
                  {
                     this.localMeasure = this.model.measure;
                     this.aggregateLabels = this.aggregatesNumeric;
                  }
                  else if(!this.localMeasure && this.measures.length > 0) {
                     this.localMeasure = this.measures[0].name;
                     this.model.measure = this.measures[0].name;
                     this.aggregateLabels = XSchema.isNumericType(this.measures[0].dataType) ?
                        this.aggregatesNumeric : this.aggregatesNonnumeric;
                     this.updateAggregate();
                  }
               },
               (err) => {
                  //TODO show error message?
               }
            );
      }
   }

   updateAggregate(): void {
      if(!this.localFormula || (this.localFormula && !this.localFormula.startsWith("$") &&
                                !this.localFormula.startsWith("=") &&
                                this.aggregateLabels.indexOf(this.localFormula) == -1))
      {
         this.localFormula = this.aggregateLabels[0];
         this.model.formula = AggregateFormula.getFormula(this.localFormula).formulaName;
      }
   }

   selectMeasure(val: any): void {
      const measure: string = val.label ? val.value : val;
      this.localMeasure = measure;

      if(measure && (measure.startsWith("$") || measure.startsWith("="))) {
         this.model.measure = measure;
      }
      else {
         let index = this.getIndex(measure);

         if(index != -1) {
            this.model.measure = this.measures[index].name;
            this.aggregateLabels = XSchema.isNumericType(this.measures[index].dataType) ?
               this.aggregatesNumeric : this.aggregatesNonnumeric;
            this.updateAggregate();
         }
      }
   }

   selectMeasureType(type: ComboMode): void {
      this.aggregateLabels = this.aggregatesNumeric;

      if(type == ComboMode.VALUE) {
         if(this.measureLabels.length > 0) {
            this.localMeasure = this.measureLabels[0].value;
            this.model.measure = this.measures[0].name;
         }
         else {
            this.model.measure = null;
            this.localMeasure = null;
         }
      }
   }

   selectAggregate(formula: string): void {
      if(formula.startsWith("$") || formula.startsWith("=")) {
         this.model.formula = formula;
      }
      else {
         this.model.formula = AggregateFormula.getFormula(formula).formulaName;
      }
   }

   isAggregateDisabled(): boolean {
      let index = this.getIndex(this.localMeasure);

      if(index >= 0) {
         let measure = this.measures[index];

         if(measure && measure.refType == DataRefType.AGG_CALC) {
            return true;
         }
      }

      return this.tableNames.find((t) => t.indexOf(this.cubeString) != -1) != null ||
         this.isNoneSelected();
   }

   isNoneSelected(): boolean {
      return this.measureLabels && this.getIndex(this.localMeasure) == 0;
   }

   getIndex(value: string) {
      for(let i = 0; i < this.measureLabels.length; i++) {
         if(this.measureLabels[i].value == value) {
            return i;
         }
      }

      return -1;
   }
}
