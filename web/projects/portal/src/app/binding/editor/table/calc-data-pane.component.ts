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
   Component, Input, ViewChildren,
   QueryList, ChangeDetectorRef
} from "@angular/core";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { VSCalcTableEditorService } from "../../services/table/vs-calc-table-editor.service";
import { CellBindingInfo } from "../../data/table/cell-binding-info";
import { DataRef } from "../../../common/data/data-ref";
import { BindingService } from "../../services/binding.service";
import { AggregateRef } from "../../../common/data/aggregate-ref";
import { AggregateFormula } from "../../util/aggregate-formula";
import { AssetUtil } from "../../util/asset-util";
import { Tool } from "../../../../../../shared/util/tool";
import { FormulaEditorDialog } from "../../../widget/formula-editor/formula-editor-dialog.component";
import { CalcTableBindingModel } from "../../data/table/calc-table-binding-model";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { DataRefType } from "../../../common/data/data-ref-type";
import { XSchema } from "../../../common/data/xschema";
import { XConstants } from "../../../common/util/xconstants";
import { ComponentTool } from "../../../common/util/component-tool";

@Component({
   selector: "calc-data-pane",
   templateUrl: "calc-data-pane.component.html",
   styleUrls: ["calc-data-pane.component.scss"]
})
export class CalcDataPane {
   @Input() vsId: string;
   @Input() assemblyName: string;
   @ViewChildren(FixedDropdownDirective) dropdown: QueryList<FixedDropdownDirective>;
   dropDownIndex: number;
   cells: any[] = [];
   columnTreeModel: TreeNodeModel = {};
   dialogOpened: boolean = false;
   private _bindingModel: CalcTableBindingModel;
   private availableFields: DataRef[];
   valueList: {value: string, label: string, tooltip: string}[];

   public constructor(private editorService: VSCalcTableEditorService,
                      private dialogService: NgbModal, public bindingService: BindingService,
                      private changeRef: ChangeDetectorRef)
   {
   }

   @Input() set bindingModel(_bindingModel: CalcTableBindingModel) {
      this._bindingModel = _bindingModel;
      this.availableFields = this._bindingModel ?
         this._bindingModel.availableFields : null;
      this.valueList = [{value: "", label: "", tooltip: ""}];

      if(this.availableFields) {
         this.availableFields.forEach((ref: DataRef) => {
            this.valueList.push({value: ref.name, label: ref.view, tooltip: ref.description});
         });
      }
   }

   get bindingModel(): CalcTableBindingModel {
      return this._bindingModel;
   }

   getAggregates(): AggregateRef[] {
      return this.editorService.getAggregates() ? this.editorService.getAggregates() : [];
   }

   get cellBinding(): CellBindingInfo {
      return this.editorService.getCellBinding();
   }

   get field(): DataRef {
      return this.cellBinding == null ||
         this.cellBinding.type != CellBindingInfo.BIND_COLUMN ?
         null : this.getSelectedRef(this.cellBinding.value);
   }

   isCalcField(): boolean {
      return this.field ? this.field.classType == "CalculateRef" : false;
   }

   isGroupAggregateDisabled(): boolean {
      return !this.cellBindingEnabled
             || this.cellBinding.type != CellBindingInfo.BIND_COLUMN
             || this.isCalcField() && this.field.refType == DataRefType.AGG_CALC;
   }

   get cellName(): string {
      return this.cellBinding.name || this.cellBinding.runtimeName;
   }

   getAllCellNames() {
      return this.editorService.getCellNames();
   }

   getCellNames() {
      const cells: any[] = this.editorService.getCellNamesWithDefaults();

      if(cells == null) {
         return this.cells;
      }

      if(!Tool.isEquals(this.cells, cells)) {
         this.cells = cells;
         this.changeRef.detectChanges();
      }

      return this.cells;
   }

   get expansionModel(): boolean {
      if(this.cellBinding == null) {
         return false;
      }

      return this.cellBinding.expansion > CellBindingInfo.EXPAND_NONE;
   }

   get isGroup(): boolean {
      if(this.cellBinding == null) {
         return false;
      }

      return this.cellBinding.btype == CellBindingInfo.GROUP;
   }

   get isSum(): boolean {
      if(this.cellBinding == null) {
         return false;
      }

      return this.cellBinding.btype == CellBindingInfo.SUMMARY;
   }

   set columnValue(val: string) {
      if(this.cellBinding == null) {
         return;
      }

      if(val == "") {
         this.cellBinding.type = CellBindingInfo.BIND_TEXT;
         this.cellBinding.value = null;
         this.cellBinding.rowGroup = null;
         this.cellBinding.colGroup = null;
         this.cellBinding.expansion = CellBindingInfo.EXPAND_NONE;
         this.setCellBinding();
      }
      else {
         this.editorService.changeColumnValue(val);
      }

      this.cellBinding.name = null;
   }

   get columnValue(): string {
      if(this.cellBinding == null ||
         this.cellBinding.type != CellBindingInfo.BIND_COLUMN)
      {
         return null;
      }

      return this.cellBinding.value;
   }

   setCellValue(val: any) {
      this.cellBinding.value = <string> val;
      this.setCellBinding();
   }

   get formulaValue(): string {
      if(this.cellBinding == null ||
         this.cellBinding.type != CellBindingInfo.BIND_FORMULA)
      {
         return null;
      }

      return this.cellBinding.value;
   }

   get textValue(): string {
      if(this.cellBinding == null || this.cellBinding.type != CellBindingInfo.BIND_TEXT) {
         return null;
      }

      return this.cellBinding.value;
   }

   setGroupType(evt: any) {
      if(evt.target.checked) {
         this.cellBinding.btype = CellBindingInfo.GROUP;
         this.cellBinding.expansion = 2;
         let dtype = this.field.dataType;

         if(XSchema.isDateType(dtype)) {
            this.cellBinding.order.option = XSchema.TIME == dtype ?
               XConstants.HOUR_DATE_GROUP :
               XConstants.YEAR_DATE_GROUP;
            this.cellBinding.order.interval = 1;
         }
      }
      else {
         this.cellBinding.btype = CellBindingInfo.DETAIL;
      }

      this.setCellBinding();
   }

   setSumType(evt: any) {
      if(!this.cellBinding) {
         return;
      }
      if(evt.target.checked) {
         this.cellBinding.btype = CellBindingInfo.SUMMARY;
         this.cellBinding.formula =
            this.getDefaultFormula(this.getSelectedRef(this.cellBinding.value)).formulaName;

         if(this.expansionModel) {
            this.cellBinding.expansion = CellBindingInfo.EXPAND_NONE;
         }
      }
      else {
         this.cellBinding.btype = CellBindingInfo.DETAIL;
         this.cellBinding.expansion = CellBindingInfo.DETAIL;
      }

      this.setCellBinding();
   }

   setExpansionValue(evt: any) {
      /* why?
      if(this.cellBinding && !evt.target.checked) {
         this.cellBinding.btype = CellBindingInfo.SUMMARY;
         this.cellBinding.formula = "none";
      }
      */

      this.cellBinding.expansion = evt.target.checked ? CellBindingInfo.DETAIL :
         CellBindingInfo.EXPAND_NONE;
      this.setCellBinding();
   }

   setExpansion(evt: any) {
      this.cellBinding.expansion = <number> evt;
      this.setCellBinding();
   }

   setCellName(evt: any) {
      if(!this.isValidName(evt)) {
         return;
      }

      this.cellBinding.name = <string>evt;
      this.setCellBinding();
   }

   changeCellType(evt: any) {
      let ntype: number = parseInt(evt, 10);
      const cellName = this.cellName;
      this.cellBinding.type = ntype;

      if(ntype == CellBindingInfo.BIND_FORMULA) {
         this.editorService.getCellScript();
         this.cellBinding.name = cellName;
      }
      else if(ntype == CellBindingInfo.BIND_TEXT) {
         this.editorService.getCellBinding().value = "";
         this.setCellBinding();
      }
   }

   setCellBinding() {
      this.editorService.setCellBinding();
   }

   toggled(event: any) {
      if(!event) {
         this.dropdown.toArray()[this.dropDownIndex].close();
         // make sure cell selection has been updated
         setTimeout(() => this.setCellBinding(), 0);
      }
      else if(event == "apply") {
         // make sure cell selection has been updated
         setTimeout(() => this.setCellBinding(), 0);
      }
   }

   openFormulaEdit() {
      const options: NgbModalOptions = {windowClass: "formula-dialog", backdrop: "static"};
      //open formula edit
      let dialog: FormulaEditorDialog = ComponentTool.showDialog(this.dialogService,
            FormulaEditorDialog, (result: any) => {
               this.cellBinding.value = result.expression;
               this.setCellBinding();
            }, options);
      dialog.expression = this.formulaValue;
      dialog.vsId = this.vsId;
      dialog.assemblyName = this.assemblyName;
      dialog.availableFields = this.availableFields;
      dialog.columns = this.availableFields;
      dialog.availableCells = this.getAllCellNames();
      dialog.nameVisible = false;
      dialog.isCondition = true;
      dialog.returnTypeVisible = false;
      dialog.isCalcTable = true;
   }

   private getTreeNodeChildren(nodes: any[]): TreeNodeModel[] {
      let nnodes: TreeNodeModel[] = [];

      for(let i = 0; i < nodes.length; i++) {
         let node: any = nodes[i];
         let children: TreeNodeModel[] = this.getTreeNodeChildren(node.nodes);
         let nnode: TreeNodeModel;

         if(children.length > 0) {
            nnode = <TreeNodeModel> {
               label: node.title,
               children: children,
               leaf: false
            };
         }
         else {
            nnode = <TreeNodeModel> {
               label: node.title,
               data: node.title,
               leaf: true
            };
         }

         nnodes.push(nnode);
      }

      return nnodes;
   }

   isGrayedOut(name: string): boolean {
      return this.bindingService.isGrayedOutField(name);
   }

   changeGroup(): void {
      if(this.cellBinding.rowGroup == "null") {
         this.cellBinding.rowGroup = null;
      }

      if(this.cellBinding.colGroup == "null") {
         this.cellBinding.colGroup = null;
      }

      this.setCellBinding();
   }

   private getSelectedRef(name: string): DataRef {
      for(let i = 0; !!this.availableFields && i < this.availableFields.length; i++) {
         if(this.availableFields[i].name == name) {
            return this.availableFields[i];
         }
      }

      return null;
   }

   private getDefaultFormula(ref: DataRef): AggregateFormula {
      if(ref == null) {
         return AggregateFormula.COUNT_ALL;
      }

      let dType: string = ref.dataType;

      if(dType != null && AssetUtil.isNumberType(dType)) {
         return AggregateFormula.SUM;
      }

      return AggregateFormula.COUNT_ALL;
   }

   private isValidName(name: string): boolean {
      if(name == null || name.trim().length == 0) {
         return true;
      }

      const msg = "_#(js:common.duplicateName)";

      for(let i = 0; i < this.getAllCellNames().length; i++) {
         if(name == this.getAllCellNames()[i]) {
            ComponentTool.showMessageDialog(this.dialogService, "_#(js:Error)", msg);
            return false;
         }
      }

      return true;
   }

   toggleDropdown(val: number) {
      this.dropDownIndex = val;
   }

   get cellBindingEnabled(): boolean {
      return this.cells && this.cells.length > 0;
   }

   get cellSelected(): boolean {
      const cells: Array<any> = this.editorService.getSelectCells();
      return cells && cells.length > 0;
   }

   get cellGroupEnabled(): boolean {
      return this.cellBindingEnabled;
   }

   checkDeleteKey(event: KeyboardEvent): void {
      switch(event.keyCode) {
      case 46: // Delete key
         event.stopPropagation();
         break;
      case 67: // copy
      case 86: // paste
      case 88: // cut
      case 68: // remove
         if(event.ctrlKey) {
            event.stopPropagation();
         }
      }
   }
}
