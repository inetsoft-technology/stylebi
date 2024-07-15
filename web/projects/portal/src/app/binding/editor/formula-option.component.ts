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
import { Component, Input, OnInit, EventEmitter, Output } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { BAggregateRef } from "../data/b-aggregate-ref";
import { CalculateRef } from "../data/calculate-ref";
import { DataRef } from "../../common/data/data-ref";
import { XSchema } from "../../common/data/xschema";
import { ComponentTool } from "../../common/util/component-tool";
import { AggregateFormula } from "../util/aggregate-formula";
import { SummaryAttrUtil } from "../util/summary-attr-util";
import { BindingService } from "../services/binding.service";
import { Tool } from "../../../../../shared/util/tool";
import isEmpty = Tool.isEmpty;
import { DataRefType } from "../../common/data/data-ref-type";

@Component({
   selector: "formula-option",
   templateUrl: "formula-option.component.html",
})
export class FormulaOption implements OnInit {
   @Input() vsId: any;
   @Input() variables: any;
   @Input() availableFields: DataRef[];
   @Input() grayedOutValues: string[] = [];
   @Input() formulaObjs: any[];
   @Input() aggregate: BAggregateRef;

   @Input() enabled: boolean = true;
   @Input() aggregated: boolean = true;
   @Output() formulaChange: EventEmitter<any> = new EventEmitter<any>();
   availableValues: any[];

   constructor(private bindingService: BindingService, private modalService: NgbModal) {
   }

   ngOnInit() {
      this.availableValues = this.getAvailableFields();
      this.initSecondaryColumn();
   }

   get formulaLabel(): string {
      let formula: AggregateFormula =
         AggregateFormula.getFormula(this.aggregate.formula);

      return formula ? formula.label : "";
   }

   npValueChange(str: string) {
      if(Tool.isDynamic(str)) {
         this.aggregate.numValue = str;
      }
      else {
         const val = parseInt(str, 10);
         this.aggregate.numValue = val < 1 || isNaN(val) ? "1" : val + "";
      }
   }

   initSecondaryColumn() {
      if(this.isWithFormula() && this.aggregate.secondaryColumnValue == null
          && this.aggregate.secondaryColumn == null && this.availableFields[0])
      {
         this.aggregate.secondaryColumn = this.aggregate.secondaryColumn ?
             this.aggregate.secondaryColumn : this.availableFields[0];
         this.aggregate.secondaryColumnValue = this.aggregate.secondaryColumnValue ?
             this.aggregate.secondaryColumnValue : this.availableValues[0].value;
      }
      else if((SummaryAttrUtil.isNthFormula(this.aggregate.formula) ||
          SummaryAttrUtil.isPthFormula(this.aggregate.formula)) && !this.aggregate.numValue)
      {
         this.aggregate.numValue = "1";
      }
   }

   changeFormulaValue(val: string) {
      let dtype = this.aggregate.originalDataType;
      dtype = !!dtype ? dtype : this.aggregate.dataType;

      if(AggregateFormula.isNumeric(val) && !AggregateFormula.isNumeric(this.aggregate.formula)) {
         if(dtype == XSchema.STRING) {
            ComponentTool.showMessageDialog(this.modalService, "_#(js:Warning)",
                                            "_#(js:composer.vs.binding.string.aggregate)");
         }
      }

      this.aggregate.formula = val;
      this.availableValues = this.getAvailableFields();
      this.fixSecondaryColumn();
      this.formulaChange.emit();
   }

   isWithFormula(): boolean {
      return Tool.isDynamic(this.aggregate.formula) ? true :
         SummaryAttrUtil.isWithFormula(this.aggregate.formula);
   }

   isByFormula(): boolean {
      return SummaryAttrUtil.isByFormula(this.aggregate.formula);
   }

   hasN(): boolean {
      return SummaryAttrUtil.isNthFormula(this.aggregate.formula) ||
         SummaryAttrUtil.isPthFormula(this.aggregate.formula);
   }

   // when with combox is disabled, don't need set secondaryColumn and value,
   // its should be keep "null value"
   get secondColumnValue(): string {
      let column = this.aggregate.secondaryColumn;

      return this.aggregate.secondaryColumnValue ?
         this.aggregate.secondaryColumnValue : column ? column.name : null;
   }

   changeSecondColumnValue(val: string) {
      this.aggregate.secondaryColumnValue = val;

      this.availableFields.forEach((field: DataRef) => {
         if(field.name == val) {
            this.aggregate.secondaryColumn = field;
         }
      });
   }

   getAvailableFields(): any[] {
      let flds: any[] = Tool.isDynamic(this.aggregate.formula) ?
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

   private fixSecondaryColumn() {
      if(this.isWithFormula() && this.availableFields[0]) {
         this.aggregate.secondaryColumn = this.aggregate.secondaryColumn ?
            this.aggregate.secondaryColumn : this.availableFields[0];
         this.aggregate.secondaryColumnValue = this.aggregate.secondaryColumnValue ?
            this.aggregate.secondaryColumnValue : this.availableValues[0].value;
      }
      else {
         this.aggregate.secondaryColumn = null;
         this.aggregate.secondaryColumnValue = null;

         if(SummaryAttrUtil.isNthFormula(this.aggregate.formula) ||
            SummaryAttrUtil.isPthFormula(this.aggregate.formula))
         {
            this.aggregate.numValue = "1";
         }
      }
   }

   get assemblyName(): string {
      return this.bindingService.assemblyName;
   }

   isCalculateRef(): boolean {
      if(this.aggregate != null &&
         (this.aggregate.refType & DataRefType.AGG_CALC) == DataRefType.AGG_CALC)
      {
         return true;
      }

      return this.availableFields && this.availableFields
         .some(f => f.name == this.aggregate.fullName && f.classType == "CalculateRef" &&
               !(<any>f).baseOnDetail);
   }

   getNPLabel(): string {
      return AggregateFormula.getNPLabel(this.aggregate.formula);
   }

   isNValid(): boolean {
      return Tool.isDynamic(this.aggregate.numValue) || parseInt(this.aggregate.numValue, 10) > 0;
   }

   isFormulaEnabled() {
      return !(this.aggregate && this.aggregate.formulaOptionModel?.aggregateStatus ||
         this.isCalculateRef());
   }
}
