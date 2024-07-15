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
   Output,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Tool } from "../../../../../../shared/util/tool";
import { DataRef } from "../../../common/data/data-ref";
import { ObjectType, TableTransfer } from "../../../common/data/dnd-transfer";
import { DndService } from "../../../common/dnd/dnd.service";
import { UIContextService } from "../../../common/services/ui-context.service";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { ComboMode } from "../../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { DynamicComboBox } from "../../../widget/dynamic-combo-box/dynamic-combo-box.component";
import { AbstractBindingRef } from "../../data/abstract-binding-ref";
import { BAggregateRef } from "../../data/b-aggregate-ref";
import { BDimensionRef } from "../../data/b-dimension-ref";
import { BaseField } from "../../data/base-field";
import { CalculateRef } from "../../data/calculate-ref";
import { SourceInfo } from "../../data/source-info";
import { BaseTableBindingModel } from "../../data/table/base-table-binding-model";
import { TableBindingModel } from "../../data/table/table-binding-model";
import { BindingService } from "../../services/binding.service";
import { TableEditorService } from "../../services/table/table-editor.service";
import { AggregateFormula } from "../../util/aggregate-formula";
import { FieldMC } from "../field-mc";
import { CrosstabOptionInfo } from "../../data/table/crosstab-option-info";
import { CrosstabBindingModel } from "../../data/table/crosstab-binding-model";
import { ComponentTool } from "../../../common/util/component-tool";
import { DateComparisonService } from "../../../vsobjects/util/date-comparison.service";
import {
   FeatureFlagsService,
} from "../../../../../../shared/feature-flags/feature-flags.service";

@Component({
   selector: "table-fieldmc",
   templateUrl: "table-fieldmc.component.html",
   styleUrls: ["../fieldmc.component.scss"]
})
export class TableFieldmc extends FieldMC {
   @Input() fieldType: string;
   @Input() groupNum: number;
   @Input() dragIndex: number;
   @Input() implyDynamic: boolean = false;
   @Input() grayedOutValues: string[] = [];
   @Input() bindingModel: BaseTableBindingModel;
   @Input() columnsForCalc: CalculateRef[];
   @ViewChild("fieldOptionDialog") fieldOptionDialog: TemplateRef<any>;
   @ViewChild(FixedDropdownDirective) dropdown: FixedDropdownDirective;
   @ViewChild("combobox") combobox: DynamicComboBox;
   @Output() onPopUpWarning: EventEmitter<any> = new EventEmitter<any>();
   private _field: AbstractBindingRef;
   private _originalField: AbstractBindingRef;
   dialogOpened: boolean = false;

   @Input() set field(field: AbstractBindingRef) {
      this._field = field;
      this._originalField = Tool.clone(field);
   }

   get field(): AbstractBindingRef {
      return this._field;
   }

   get isAllRows(): boolean {
      return this.getIndex(this.getSource()) > -1;
   }

   get tableBindingModel(): TableBindingModel {
      return this.bindingModel as TableBindingModel;
   }

   public constructor(bindingService: BindingService,
                      private editorService: TableEditorService,
                      private dialogService: NgbModal,
                      private dndService: DndService,
                      private dcService: DateComparisonService,
                      protected uiContextService: UIContextService,
                      private featureFlagsService: FeatureFlagsService)
   {
      super(bindingService, uiContextService);
   }

   getSourceAttr(): SourceInfo {
      return this.bindingModel ? this.bindingModel.source : null;
   }

   getAllRows(): string[] {
      return this.bindingModel.allRows;
   }

   showFieldOption() {
      let datasource = this.getSourceAttr();

      if(datasource == null || datasource.supportFullOutJoin == false) {
         return false;
      }

      if(this.field.dataRefModel.classType == "FormulaField") {
         return false;
      }

      if(datasource.type != 1 && (datasource.joinSources == null ||
         datasource.joinSources.length <= 0))
      {
         return false;
      }

      return true;
   }

   openFieldOption(event: MouseEvent) {
      if(!this.showFieldOption()) {
         return;
      }

      event.stopPropagation();

      this.dialogService.open(this.fieldOptionDialog).result.then(
         (result: boolean) => {
            this.changeAllRows(result);
            this.editorService.setBindingModel();
         },
         () => {
         }
      );
   }

   getSource(): string {
      let fld = this.field.dataRefModel;
      let source: string = fld.entity;

      if(source == null && fld.classType == "BaseField") {
         source = (<BaseField> fld).source;
      }

      if(source == null && this.getSourceAttr() != null) {
         source = this.getSourceAttr().source;
      }

      return source;
   }

   getIndex(currentSource: string) {
      let allRows: string[] = this.getAllRows();

      for(let i = 0; i < allRows.length; i++) {
         let entity = allRows[i];

         if(entity == currentSource) {
            return i;
         }
      }

      return -1;
   }

   changeAllRows(isAllRow: boolean) {
      if(this.getSourceAttr() == null) {
         return;
      }

      let currentSource = this.getSource();
      let idx = this.getIndex(currentSource);

      if(isAllRow && idx == -1) {
         this.getAllRows().push(currentSource);
      }
      else if(!isAllRow && idx != -1) {
         this.getAllRows().splice(idx, 1);
      }
   }

   get tooltip(): string {
      if(this.field.view) {
         return this.field.view;
      }

      return this.field.classType === "BAggregateRefModel"
         ? (<BAggregateRef> this.field).fullName : (<BDimensionRef> this.field).name;
   }

   get cellValue(): string {
      let str: string = "";

      if(this.field == null) {
         return "";
      }

      let name: string = null;

      if(!this.implyDynamic) {
         name = this.getColumnValue(this.field);
         let dynamic: boolean = Tool.isDynamic(name);

         if(!dynamic) {

            if(this.field instanceof BDimensionRef) {
               name = (<BDimensionRef> this.field).caption;
            }

            if(this.field instanceof BAggregateRef) {
               name = (<BAggregateRef> this.field).caption;
            }

            if(!name) {
               name = this.getFieldName();
            }
         }
      }
      else {
         if(this.field.comboType == ComboMode.VARIABLE ||
            this.field.comboType == ComboMode.EXPRESSION)
         {
            name = this.getColumnValue(this.field);
         }

         if(!name && this.field instanceof BDimensionRef) {
            name = (<BDimensionRef> this.field).caption;
         }

         if(!name && this.field instanceof BAggregateRef) {
            name = (<BAggregateRef> this.field).caption;
         }

         if(!name) {
            name = this.getFieldName();
         }

         if(!name) {
            name = this.getColumnValue(this.field);
         }
      }

      return name;
   }

   changeColumnValue(value: string) {
      if(this.isEmptyDynamicValue(value) || value == this.getColumnValue(this.field)) {
         return;
      }

      this.setColumnValue(value);
      this.field.comboType = this.combobox.type;
      this.editorService.setBindingModel();
   }

   private getColumnValue(ref: AbstractBindingRef): string {
      if(ref instanceof BDimensionRef) {
         return (<BDimensionRef> ref).columnValue;
      }

      return (<BAggregateRef> ref).columnValue;
   }

   private setColumnValue(value: string): void {
      if(this.field instanceof BDimensionRef) {
         (<BDimensionRef> this.field).columnValue = value;
      }
      else {
         (<BAggregateRef> this.field).columnValue = value;
      }
   }

   dragStart(event: any) {
      const transfer = new TableTransfer(this.fieldType, this.dragIndex, this.assemblyName);
      transfer.objectType = <ObjectType>this.bindingService.objectType;
      Tool.setTransferData(event.dataTransfer,
         {dragSource: transfer, dragLastFiled: false});
      this.dndService.setDragStartStyle(event, this.getFieldName());
   }

   getFieldName(): string {
      if(!this.field) {
         return null;
      }

      if(this.field.view != null) {
         return this.field.view;
      }

      if(this.field.dataRefModel != null) {
         return this.field.dataRefModel.view;
      }

      return null;
   }

   public toggled(open: boolean): void {
      if(!open) {
         this.dropdown.close();

         if(this.isCrosstab() && (<CrosstabBindingModel> this.bindingModel).hasDateComparison &&
            this.dcService.checkBindingField(this.field, this._originalField))
         {
            ComponentTool.showConfirmDialog(this.dialogService, "_#(js:Confirm)",
               "_#(js:date.comparison.changeBindingField.confirm)")
               .then((buttonClicked) => {
                  if(buttonClicked === "ok") {
                     this.processChange();
                  }
                  else {
                     Object.assign(this.field, this._originalField);
                  }
               });
         }
         else {
            if(!!this.field) {
               let dim: any = this.field;
               delete dim["specificOrderType"];
            }

            this.processChange();
         }
      }
   }

   processChange(): void {
      if((<any> this.field).formula && (<any> this.bindingModel).rows) {
         this.syncAgg((<any> this.bindingModel).rows);
      }

      if((<any> this.field).formula && (<any> this.bindingModel).cols) {
         this.syncAgg((<any> this.bindingModel).cols);
      }

      if(this.isBindingChanged()) {
         this.bindingUpdated();
         this.editorService.setBindingModel();
      }
   }

   isCrosstab(): boolean {
      let objectType = this.bindingService.objectType;
      return objectType == "VSCrosstab" || objectType == "crosstab";
   }

   get crosstabOption(): CrosstabOptionInfo {
      let bindingModel = this.bindingService.bindingModel;
      return this.isCrosstab() && !!bindingModel ?
         (<CrosstabBindingModel> bindingModel).option : null;
   }

   // if aggregate is changed, update the sortByCol and rankingCol
   private syncAgg(cols: BDimensionRef[]) {
      const fullname = this.field.fullName;
      const hasCalc = !!(<any> this.field).calculateInfo;
      const aggr = <BAggregateRef> this.field;

      if(Tool.isDynamic(aggr.formula) || Tool.isDynamic(aggr.secondaryColumnValue)) {
         return;
      }

      if(this.bindingModel.aggregates &&
         this.bindingModel.aggregates.filter(agg => agg.fullName == this.field.fullName).length > 1)
      {
         return;
      }

      cols.forEach(col => {
         if(col.rankingCol == fullname || hasCalc && !!fullname && fullname.endsWith(col.rankingCol)) {
            col.rankingCol = this.getAggregateFullName();

            if(hasCalc) {
               col.rankingAgg = {
                  baseAggregateName: col.rankingCol,
                  calculateInfo: (<any> this.field).calculateInfo
               }
            }
         }

         if(col.sortByCol == fullname || hasCalc && !!fullname && fullname.endsWith(col.sortByCol)) {
            col.sortByCol = this.getAggregateFullName();

            if(hasCalc) {
               col.sortByColAgg = {
                  baseAggregateName: col.sortByCol,
                  calculateInfo: (<any> this.field).calculateInfo
               }
            }
         }
      });
   }

   private getAggregateFullName() {
      const aggr = <BAggregateRef> this.field;
      const formulaName = aggr.formula;
      const formula: AggregateFormula = AggregateFormula.getFormula(formulaName);
      const name = this.field.name;
      const ref2 = aggr.secondaryColumnValue;
      const n = aggr.numValue;

      if(formula.formulaName == "none") {
         return `${name}`;
      }

      if(formula && formula.twoColumns && aggr.secondaryColumnValue) {
         return `${formulaName}(${name}, ${ref2})`;
      }
      else if(formula && formula.hasN && aggr.numValue != null) {
         return `${formulaName}(${name}, ${n})`;
      }

      return `${formulaName}(${name})`;
   }

   /**
    * Judge whether the data type dimension is an inner dimension.
    */
   isOuterDimRef(): boolean {
      const bindingModel: any = this.bindingModel;

      return (bindingModel.rows || bindingModel.cols)
         ? !(this.isLastItem(this.field, bindingModel.rows) ||
             this.isLastItem(this.field, bindingModel.cols))
         : !this.isLastItem(this.field, bindingModel.groups);
   }

   private isLastItem(item: DataRef, arr: DataRef[]): boolean {
      return !!arr && arr.length > 0 && item === arr[arr.length - 1];
   }

   getFieldClassType(): string {
      return this.field.classType == "BAggregateRefModel" ? "Measure" : "Dimension";
   }

   isEditEnable() {
      if(this.fieldType == "details") {
         return false;
      }

      if(this.fieldType == "aggregates" && this.isCube() && !this.isSqlServer()) {
         return false;
      }

      return true;
   }

   getTitle(): string {
      return this.getFieldClassType() == "Dimension"
               ? "_#(js:Edit Dimension)" : "_#(js:Edit Measure)";
   }
}
