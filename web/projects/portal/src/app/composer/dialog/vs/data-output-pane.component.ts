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
import { Component, Input, OnInit } from "@angular/core";
import { ComboMode } from "../../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { DataOutputPaneModel } from "../../data/vs/data-output-pane-model";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { OutputColumnModel } from "../../data/vs/output-column-model";
import { AggregateFormula } from "../../../binding/util/aggregate-formula";
import { AssetUtil } from "../../../binding/util/asset-util";
import { XSchema } from "../../../common/data/xschema";
import { OutputCubeModel } from "../../data/vs/output-cube-model";
import { Tool } from "../../../../../../shared/util/tool";
import { HttpClient, HttpHeaders, HttpParams } from "@angular/common/http";

const TABLE_COLUMNS_URI: string = "../vs/dataOutput/table/columns";
const CUBE_COLUMNS_URI: string = "../vs/dataOutput/cube/columns";

@Component({
   selector: "data-output-pane",
   templateUrl: "data-output-pane.component.html",
})
export class DataOutputPane implements OnInit {
   @Input() model: DataOutputPaneModel;
   @Input() runtimeId: string;
   @Input() variableValues: string[];
   headers: HttpHeaders;
   columns: OutputColumnModel[] = [];
   columnValues: any[] = [];
   withColumnValues: any[] = [];
   aggregates: any[] = this.getDefaultAggregates();
   twoColumns: boolean = false;
   hasN: boolean = false;
   sqlProvider: boolean = true;
   tableType: string = "";
   currentLabel: string = "";

   constructor(private http: HttpClient) {
      this.headers = new HttpHeaders({
         "Content-Type": "application/json"
      });
   }

   ngOnInit(): void {
      this.fixInitialValues();

      if(this.model.table) {
         this.tableType = "cube";

         for(let node of this.model.targetTree.children) {
            if(node.data == this.model.table) {
               this.tableType = "table";
               break;
            }
         }

         this.setLabelFromData(this.model.table);

         this.updateColumns();
      }
   }

   // null values get stored as "null" string on server
   fixInitialValues(): void {
      if(this.model.table == "null") {
         this.model.table = null;
         this.model.column = null;
         this.model.aggregate = null;
         this.model.with = null;
      }
      else if(this.model.column == "null") {
         this.model.column = null;
         this.model.aggregate = null;
         this.model.with = null;
      }
      else if(this.model.with == "null") {
         this.model.with = null;
      }
   }

   // When a new table is selected, update the columns
   selectTarget(targetNode: TreeNodeModel): void {
      this.model.table = targetNode.data;
      this.tableType = targetNode.type;
      this.updateColumns();
   }

   setLabelFromData(data: string, parentNode?: TreeNodeModel) {
      if(parentNode == null) {
         parentNode = this.model.targetTree;
      }

      this.currentLabel = null;

      for(let node of parentNode.children) {
         if(node.data === data) {
            this.currentLabel = node.label;
            return;
         }
         else if(!node.leaf) {
            this.setLabelFromData(data, node);
         }
      }
   }

   // When a new column is selected, update the aggregates
   selectColumn(columnValue: string): void {
      this.model.column = columnValue;
      let index = this.getIndex(columnValue);

      // If it is a value column, update the columnType/column
      if(index != -1) {
         if(this.tableType == "table") {
            this.model.columnType = this.columns[index].type;
         }
         else if(this.tableType == "cube") {
            this.model.column = (<OutputCubeModel> this.columns[index]).data;
         }

         // select the default formula when changing the column
         if(this.tableType !== "cube" &&
            this.model.aggregate.charAt(0) !== "$" &&
            this.model.aggregate.charAt(0) !== "=")
         {
            const colType = this.columns[index].type;

            if(this.columns[index].defaultFormula){
               this.model.aggregate = this.columns[index].defaultFormula;
            }
            else {
               const formula = AggregateFormula.getDefaultFormula(colType);
               this.model.aggregate = formula.formulaName;
            }
         }
      }

      this.updateAggregates();
   }

   // When an aggregate is selected, check if two columns are supported
   selectAgg(aggregate: string): void {
      this.model.aggregate = aggregate;

      // Two columns are not supported for cube tables
      if(this.tableType != "cube" && aggregate != null) {
         if(Tool.isDynamic(aggregate)) {
            this.twoColumns = this.hasN = true;
         }
         else {
            let formula: AggregateFormula = AggregateFormula.getFormula(aggregate);
            this.twoColumns = formula && formula.twoColumns;
            this.hasN = formula && formula.hasN;
         }
      }
      else {
         this.twoColumns = false;
         this.hasN = false;
      }

      this.updateColumn2();
   }

   // When the column type is selected, update the column value and aggregates
   selectColType(combo: ComboMode) {
      this.model.columnType = XSchema.STRING;

      if(combo == ComboMode.VARIABLE) {
         if(this.variableValues && this.variableValues.length > 0 &&
            !Tool.isVar(this.model.column))
         {
            this.model.column = this.variableValues[0];
         }

         this.updateAggregates();
      }
      else if(combo == ComboMode.EXPRESSION) {
         this.updateAggregates();
      }
      else {
         if(this.columnValues && this.columnValues.length > 0) {
            this.model.column = this.columnValues[0].value;

            if(this.tableType == "table") {
               this.model.columnType = this.columns[0].type;
            }
         }

         this.updateAggregates();
      }
   }

   // When aggregate type is selected, update the aggregates
   selectAggType(combo: ComboMode) {
      if(combo == ComboMode.VALUE && !Tool.isDynamic(this.model.aggregate) ||
         combo == ComboMode.VARIABLE && Tool.isVar(this.model.aggregate) ||
         combo == ComboMode.EXPRESSION && Tool.isExpr(this.model.aggregate))
      {
         return;
      }

      if(combo == ComboMode.VARIABLE || combo == ComboMode.EXPRESSION) {
         this.aggregates = this.getDefaultAggregates();

         if(combo == ComboMode.VARIABLE) {
            if(this.variableValues && this.variableValues.length > 0 &&
               !Tool.isVar(this.model.aggregate))
            {
               this.model.aggregate = this.variableValues[0];
            }
         }

         //cube tables do not support two columns
         if(this.tableType != "cube") {
            this.twoColumns = this.hasN = true;
         }
      }
      else {
         this.updateAggregates();
      }
   }

   // Update the column list
   updateColumns(): void {
      this.sqlProvider = true;
      this.model.columnType = XSchema.STRING;

      if(this.model.table && this.model.table.length > 0) {
         let params = new HttpParams()
            .set("table", this.model.table)
            .set("runtimeId", this.runtimeId);

         if(this.tableType == "physical-table" || this.tableType == "table" ||
            this.tableType == "logical" || this.tableType == "query")
         {
            this.http.get<OutputColumnModel[]>(TABLE_COLUMNS_URI, {params}).subscribe(
                  (data: OutputColumnModel[]) => {
                     this.columns = data;

                     if(this.columns && this.columns.length > 0) {
                        this.columnValues = [];
                        this.withColumnValues = [];

                        for(let column of this.columns) {
                           const columnValue = {value: column.name, type: column.type,
                              label: column.label || column.name, tooltip: column.tooltip};

                           this.columnValues.push(columnValue);

                           if(!column.aggregateCalcField) {
                              this.withColumnValues.push(columnValue);
                           }
                        }

                        // check is there is a current column and if it should be preserved
                        if(this.model.column) {
                           if(this.model.column.charAt(0) != "$" &&
                              this.model.column.charAt(0) != "=")
                           {
                              let index = this.getIndex(this.model.column);

                              if(index == -1) {
                                 this.model.column = this.columnValues[0].value;
                                 this.model.columnType = this.columns[0].type;
                              }
                              else {
                                 this.model.columnType = this.columns[index].type;
                              }
                           }
                        }
                        else {
                           this.model.column = this.columnValues[0].value;
                           this.model.columnType = this.columns[0].type;
                        }

                        this.updateAggregates();
                     }
                     else {
                        this.columnValues = [];
                        this.model.column = null;
                        this.model.aggregate = null;
                        this.model.with = null;
                     }
                  },
                  (err) => {
                     this.model.column = null;
                     this.columns = [];
                     this.columnValues = [];
                     this.model.aggregate = null;
                     this.model.with = null;
                  }
               );
         }
         else if(this.tableType == "cube") {
            params = params.set("columnType", "measures");
            this.http.get<OutputCubeModel[]>(CUBE_COLUMNS_URI, {params}).subscribe(
                  (data: OutputCubeModel[]) => {
                     this.columns = data;

                     if(this.columns && this.columns.length > 0) {
                        this.columnValues = [];

                        for(let column of this.columns) {
                           this.columnValues.push({value: (<OutputCubeModel> column).data,
                              label: column.name, tooltip: column.tooltip});
                        }

                        // check is there is a current column and if it should be preserved
                        if(this.model.column) {
                           if(this.model.column.charAt(0) != "$" &&
                              this.model.column.charAt(0) != "=")
                           {
                              let found = false;

                              for(let column of data) {
                                 if(this.model.column == column.data) {
                                    found = true;
                                    break;
                                 }
                              }

                              if(!found) {
                                 this.model.column = data[0].data;
                              }
                           }
                        }
                        else {
                           this.model.column = data[0].data;
                        }

                        // If the cube is not an sql provider then aggregate/two columns
                        // are not supported
                        if(!data[0].sqlProvider) {
                           this.sqlProvider = false;
                           this.model.aggregate = null;
                           this.model.with = null;
                        }
                        else {
                           this.updateAggregates();
                        }
                     }
                     else {
                        this.columnValues = [];
                        this.model.column = null;
                        this.model.aggregate = null;
                        this.model.with = null;
                     }
                  },
                  (err) => {
                     this.model.column = null;
                     this.columns = [];
                     this.columnValues = [];
                     this.model.aggregate = null;
                     this.model.with = null;
                  }
               );
         }
      }
      else {
         this.model.table = null;
         this.model.column = null;
         this.model.aggregate = null;
         this.model.with = null;
         this.columns = [];
         this.columnValues = [];
      }
   }

   updateAggregates(): void {
      if(this.isAggregateCalcField()) {
         this.model.aggregate = "none";
      }
      else if(this.model.column != null && this.sqlProvider) {
         let formula: AggregateFormula = null;
         let index = -1;

         if(this.tableType == "cube") {
            for(let i = 0; i < this.columns.length; i++) {
               if(this.model.column == (<OutputCubeModel> this.columns[i]).data) {
                  index = i;
                  break;
               }
            }
         }
         else {
            index = this.getIndex(this.model.column);
         }

         // If the column is a value column, get colType aggregates
         if(index != -1) {
            let colType = this.columns[index].type;

            if(colType != "cubeNode") {
               this.aggregates = this.getAggregates(colType);
               formula = AggregateFormula.getDefaultFormula(colType);
            }
            else {
               this.aggregates = this.getCubeAggregates();
            }
         }
         else {
            this.aggregates = this.getDefaultAggregates();
         }

         // If there is an aggregate, try to preserve it
         if(!this.model.aggregate ||
            (this.model.aggregate.charAt(0) != "$" && this.model.aggregate.charAt(0) != "=" &&
             !this.aggregates.some(f => f.value == this.model.aggregate)))
         {
            if(formula) {
               this.model.aggregate = formula.formulaName;
            }
            else {
               this.model.aggregate = this.aggregates[0].formulaName;
            }
         }
      }
      else {
         this.model.aggregate = null;
      }

      this.selectAgg(this.model.aggregate ? this.model.aggregate : this.aggregates[0].formulaName);
   }

   updateColumn2(): void {
      if(this.twoColumns) {
         if(this.model.with) {
            if(this.model.with.charAt(0) != "$" &&
               this.model.with.charAt(0) != "=" &&
               this.getIndex(this.model.with) < 0)
            {
               this.model.with = this.withColumnValues[0].label;
            }
         }
         else {
            this.model.with = this.withColumnValues[0].label;
         }
      }
      else {
         this.model.with = null;
      }
   }

   //Get aggregates according to col type
   getAggregates(type: string): any[] {
      let formulas: AggregateFormula[] = AssetUtil.getFormulas(type);
      let result: any[] = [];

      for(let formula of formulas) {
         result.push({
            label: formula.label,
            value: formula.formulaName
         });
      }

      return result;
   }

   // get default aggregates
   getDefaultAggregates(): any[] {
      return this.getAggregates(XSchema.STRING);
   }

   // get aggregates for cube columns
   getCubeAggregates(): string[] {
      return this.getAggregates("cube");
   }

   columnDisabled(): boolean {
      return !this.model.table || this.model.table.length == 0;
   }

   aggregateDisabled(): boolean {
      return this.model.column == null || !this.sqlProvider || this.isAggregateCalcField();
   }

   withDisabled(): boolean {
      return !this.twoColumns;
   }

   nDisabled(): boolean {
      return !this.hasN;
   }

   getIndex(value: string): number {
      for(let i = 0; i < this.columnValues.length; i++) {
         if(this.columnValues[i].label == value) {
            return i;
         }
      }
      return -1;
   }

   private isAggregateCalcField(): boolean {
      const idx = this.getIndex(this.model.column);
      return idx >= 0 && this.columns[idx].aggregateCalcField;
   }

   getNPLabel(): string {
      return AggregateFormula.getNPLabel(this.model.aggregate);
   }

   isNValid(): boolean {
      return Tool.isDynamic(this.model.num) || parseInt(this.model.num, 10) > 0
         ? true : false;
   }

   npValueChange(str: string) {
      if(Tool.isDynamic(str)) {
         this.model.num = str;
      }
      else {
         const val = parseInt(str, 10);
         this.model.num = val < 1 || isNaN(val) ? "1" : val + "";
      }
   }
}
