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
import { Component, Input, OnInit, Output, EventEmitter, ViewChild } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { BAggregateRef } from "../../../binding/data/b-aggregate-ref";
import { CalculateRef } from "../../../binding/data/calculate-ref";
import { ComponentTool } from "../../../common/util/component-tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { AggregateFormula } from "../../../binding/util/aggregate-formula";
import { AssetUtil } from "../../../binding/util/asset-util";
import { Tool } from "../../../../../../shared/util/tool";
import { SummaryAttrUtil } from "../../../binding/util/summary-attr-util";
import { DataRef } from "../../../common/data/data-ref";
import { VSWizardBindingTreeService } from "../../services/vs-wizard-binding-tree.service";
import { VSWizardItem } from "./wizard-item.component";
import { XSchema } from "../../../common/data/xschema";

@Component({
   selector: "wizard-aggregate-item",
   templateUrl: "./wizard-aggregate-item.component.html",
   styleUrls: ["./wizard-aggregate-item.component.scss", "./wizard-group-item.component.scss"]
})
export class VSWizardAggregateItem extends VSWizardItem<BAggregateRef> implements OnInit {
   @Input() showName: boolean;
   @Input() showMore: boolean;
   @Input() forceFormula: string = null;
   @Input() availableFields: DataRef[];
   @Input() grayedOutValues: string[];
   @Input() fixedFormulaMap: Map<string, string>;
   @Output() addItem: EventEmitter<number> = new EventEmitter<number>();
   @Output() onEditAggregate: EventEmitter<null> = new EventEmitter<null>();
   @Output() onEditSecondColumn: EventEmitter<number> = new EventEmitter<number>();
   @Output() onEditAggregateFormat: EventEmitter<null> = new EventEmitter<null>();
   formulaObjs: any[];
   availableValues: any[];

   constructor(protected modalService: NgbModal,
               protected clientService: ViewsheetClientService,
               protected treeService: VSWizardBindingTreeService)
   {
      super(modalService, clientService, treeService);
   }

   ngOnInit() {
      this.availableValues = this.getAvailableFields();
      let formulas: AggregateFormula[] = AssetUtil.getDefaultFormulas();
      this.formulaObjs = AggregateFormula.getFormulaObjs(formulas);
   }

   get formulaLabel(): string {
      let formula: AggregateFormula =
         AggregateFormula.getFormula(this.dataRef.formula);

      return formula ? formula.label : "";
   }

   getDataType(): string {
      return this.dataRef.dataType;
   }

   getFullName(): string {
      return this.dataRef.fullName;
   }

   addAggregate(): void {
     this.addItem.emit(this.index);
   }

   formulaChange(val: string): void {
      this.dataRef.formula = val;
      this.availableValues = this.getAvailableFields();
      this.fixSecondaryColumn();

      if(this.isWithFormula() && this.dataRef.secondaryColumnValue == null) {
         let defaultSecondColumn = this.getDefaultSecondColumn();

         if(!!defaultSecondColumn) {
            this.changeSecondColumnValue(defaultSecondColumn);
         }

         return;
      }
      else if(SummaryAttrUtil.npVisible(this.dataRef.formula)) {
         if(this.dataRef.numValue == null) {
            this.dataRef.numValue = "1";
         }
      }

      this.onEditAggregate.emit();
   }

   private getDefaultSecondColumn() {
      if(this.availableFields) {
         for(let field of this.availableFields) {
            // weighted average requires numeric value
            if((this.dataRef.formula == AggregateFormula.WEIGHTED_AVG.formulaName ||
                this.dataRef.formula == AggregateFormula.CORRELATION.formulaName ||
                this.dataRef.formula == AggregateFormula.COVARIANCE.formulaName) &&
               !XSchema.isNumericType(field.dataType))
            {
               continue;
            }

            if(this.grayedOutValues && !this.grayedOutValues.includes(field.name)) {
               return field.name;
            }
         }
      }

      return null;
   }

   npValueChange(str: string): void {
      const val = parseInt(str, 10);
      let result: string;

      if(val < 1 || isNaN(val)) {
         result = "1";
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
            "_#(js:table.formula.parameterError)");
      }
      else {
         result = val + "";
      }

      this.dataRef.numValue = result;
      this.onEditAggregate.emit();
   }

   changeSecondColumnValue(val: string): void {
      this.dataRef.secondaryColumnValue = val;

      this.availableFields.forEach((field: DataRef) => {
         if(field.name == val) {
            this.dataRef.secondaryColumn = field;
         }
      });

      this.onEditSecondColumn.emit(this.index);
   }

   private isWithFormula(): boolean {
      return Tool.isDynamic(this.dataRef.formula) ? true :
         SummaryAttrUtil.isWithFormula(this.dataRef.formula);
   }

   formulaVisible(): boolean {
      return !this.isDynamic && !Tool.equalsIgnoreCase("none", this.forceFormula);
   }

   withVisible(): boolean {
      return this.formulaVisible() && !this.forceFormula && this.isWithFormula();
   }

   isByFormula(): boolean {
      return SummaryAttrUtil.isByFormula(this.dataRef.formula);
   }

   npVisible(): boolean {
      return this.formulaVisible() && !this.forceFormula &&
         SummaryAttrUtil.npVisible(this.dataRef.formula);
   }

   get secondColumnValue(): string {
      let column = this.dataRef.secondaryColumn;

      return this.dataRef.secondaryColumnValue ?
         this.dataRef.secondaryColumnValue : column ? column.name : null;
   }

   getNPLabel(): string {
      return AggregateFormula.getNPLabel(this.dataRef.formula);
   }

   private fixSecondaryColumn(): void {
      if(this.isWithFormula() && this.availableFields && this.availableFields[0] &&
         this.grayedOutValues == null)
      {
         this.dataRef.secondaryColumn = this.dataRef.secondaryColumn ?
            this.dataRef.secondaryColumn : this.availableFields[0];
         this.dataRef.secondaryColumnValue = this.dataRef.secondaryColumnValue ?
            this.dataRef.secondaryColumnValue : this.availableValues[0].value;
      }
      else {
         this.dataRef.secondaryColumn = null;
         this.dataRef.secondaryColumnValue = null;
      }
   }

   getAvailableFields(): any[] {
      let flds: any[] = Tool.isDynamic(this.dataRef.formula) ?
         [{label: "_#(js:None)", value: "None", tooltip: "_#(js:None)"}] : [];

      if(!this.availableFields) {
         return flds;
      }

      for(let i = 0; i < this.availableFields.length; i++) {
         let fld: DataRef = this.availableFields[i];

         // aggregate calcfield can't be used as secondary column for aggregate
         // which assumes to be available in detail table
         if(fld.classType != "CalculateRef" || (<CalculateRef> fld).baseOnDetail) {
            flds.push({label: fld.view ? fld.view : fld.name, value: fld.name,
               tooltip: fld.description});
         }
      }

      return flds;
   }

   isAggrCalculateRef(): boolean {
      return this.availableFields && this.availableFields
         .some(f => f.name == this.dataRef.fullName && f.classType == "CalculateRef" &&
            !(<any>f).baseOnDetail);
   }

   get isDynamic(): boolean {
      return Tool.isDynamic(this.dataRef.columnValue);
   }

   isDimension(): boolean {
      return false;
   }

   convertBtnTitle(): string {
      return "_#(js:Convert To Dimension)";
   }

   changeFormat(): void {
      this.onEditAggregateFormat.emit();
      this.formatPane.close();
   }
}
