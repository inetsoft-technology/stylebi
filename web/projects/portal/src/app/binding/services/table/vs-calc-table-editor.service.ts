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
import { Injectable } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable } from "rxjs";
import { AggregateRef } from "../../../common/data/aggregate-ref";
import { Rectangle } from "../../../common/data/rectangle";
import { CalcTableCell } from "../../../common/data/tablelayout/calc-table-cell";
import { CalcTableLayout } from "../../../common/data/tablelayout/calc-table-layout";
import { Tool } from "../../../../../../shared/util/tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ModelService } from "../../../widget/services/model.service";
import { GetCellBindingCommand } from "../../command/get-cell-binding-command";
import { CellBindingInfo } from "../../data/table/cell-binding-info";
import { ChangeColumnValueEvent } from "../../event/change-column-value-event";
import { GetCellBindingEvent } from "../../event/get-cell-binding-event";
import { GetCellScriptEvent } from "../../event/get-cell-script-event";
import { GetPredefinedNamedGroupEvent } from "../../event/get-predefined-named-group-event";
import { GetTableLayoutEvent } from "../../event/get-table-layout-event";
import { SetCellBindingEvent } from "../../event/set-cell-binding-event";
import { BindingService } from "../binding.service";
import { ComponentTool } from "../../../common/util/component-tool";

const CALC_TABLE_PROPERTY_URI: string = "composer/vs/calc-table-property-dialog-model/";

@Injectable()
export class VSCalcTableEditorService {
   _cellBinding: CellBindingInfo = new CellBindingInfo();
   _cellNames: string[];
   _aggregates: AggregateRef[];
   _hasRowGroup: boolean;
   _hasColGroup: boolean;
   _groupNum: number;
   _namedGroups: string[];
   _layoutModel: CalcTableLayout;

   constructor(private bindingService: BindingService,
               private clientService: ViewsheetClientService,
               private modalService: NgbModal,
               private modelService: ModelService)
   {
   }

   loadTableModel() {
      let evt: GetTableLayoutEvent =
         new GetTableLayoutEvent(this.bindingService.assemblyName);
      this.clientService.sendEvent("/events/vs/calctable/tablelayout/getlayout", evt);
   }

   getTableLayout(): CalcTableLayout {
      return this._layoutModel;
   }

   setTableLayout(model: CalcTableLayout): void {
      this._layoutModel = model;
   }

   loadCellBinding() {
      let evt: GetCellBindingEvent = new GetCellBindingEvent(
         this.bindingService.assemblyName, this.getSelectCells());
      this.clientService.sendEvent("/events/vs/calctable/tablelayout/getcellbinding", evt);
   }

   setCellBinding(cellBinding?: any) {
      let evt = new SetCellBindingEvent(
         this.bindingService.assemblyName, this.getSelectCells(),
         cellBinding ? cellBinding : this._cellBinding);

      // make sure the cellBinding not changed between now and in subscribe
      evt = Tool.clone(evt);

      // check trap before set cell binding.
      this.checkTrap(cellBinding).subscribe(
         (data: any) => {
            let body = data && data["body"] ?  data["body"] : {};

            if(body != null && body.btype != null) {
               ComponentTool.showTrapAlert(this.modalService).then((result: string) => {
                  if(result == "undo") {
                     this._cellBinding = body;
                  }
                  else {
                     this.setCellBinding0(evt);
                  }
               });
            }
            else {
               this.setCellBinding0(evt);
            }
         }
      );
   }

   private checkTrap(cellBinding?: any): Observable<any> {
      let binding: any = cellBinding ? cellBinding : this._cellBinding;
      let cell: CalcTableCell = this.getSelectCells()[this.getSelectCells().length - 1];
      const params = this.bindingService.getURLParams()
         .set("row", cell.row + "")
         .set("col", cell.col + "");

      return this.modelService.putModel("../api/vs/calctable/tablelayout/checktrap",
         binding, params);
   }

   private setCellBinding0(evt: SetCellBindingEvent) {
      this.clientService.sendEvent(
         "/events/vs/calctable/tablelayout/setcellbinding", evt);
   }

   changeColumnValue(val: string) {
      const event: ChangeColumnValueEvent = new ChangeColumnValueEvent(
         this.bindingService.assemblyName,
         val,
         this.getSelectRect().y,
         this.getSelectRect().x, false, true);

      this.clientService.sendEvent(
         "/events/vs/calctable/tablelayout/changeColumnValue", event);
   }

   getCellScript(): void {
      if(this.getSelectCells() != null && this.getSelectCells().length >= 0) {
         let cell: CalcTableCell = this.getSelectCells()[this.getSelectCells().length - 1];
         let evt: GetCellScriptEvent = new GetCellScriptEvent(
            this.bindingService.assemblyName, cell.row, cell.col);
         this.clientService.sendEvent("/events/vs/calctable/tablelayout/getcellscript", evt);
      }
   }

   private loadNamedGroups(): void {
      let evt: GetPredefinedNamedGroupEvent =
         new GetPredefinedNamedGroupEvent(this.bindingService.assemblyName,
         this._cellBinding.value);
      this.clientService.sendEvent("/events/vs/calctable/tablelayout/getnamedgroup", evt);
   }

   resetCellBinding(command: GetCellBindingCommand): void {
      if(!this.isSelectedCell(command.cellRow, command.cellCol)) {
         return;
      }

      this._cellBinding = command.binding;
      this._cellNames = command.cellNames;
      this._aggregates = command.aggregates;
      this._hasRowGroup = command.rowGroup;
      this._hasColGroup = command.colGroup;
      this._groupNum = command.groupNum;
      this.loadNamedGroups();
   }

   isSelectedCell(row: number, col: number) {
      return !!this.getSelectCells().find((element) => {
                return element.row == row && element.col == col;
             });
   }

   getCellBinding(): CellBindingInfo {
      return this._cellBinding;
   }

   getCellNames(): string[] {
      return this._cellNames;
   }

   // Get cells with (none) and (default)
   getCellNamesWithDefaults(): any[] {
      let names = this.getCellNames();

      if(names == null) {
         return null;
      }

      return [
         { label: "(none)", value: null },
         { label: "(default)", value: "(default)" }
      ].concat(names.sort().map(n => ({label: n, value: n})));
   }

   getAggregates(): AggregateRef[] {
      return this._aggregates;
   }

   getGroupNum(): number {
      return this._groupNum;
   }

   hasRowGroup(): boolean {
      return this._hasRowGroup;
   }

   hasColGroup(): boolean {
      return this._hasColGroup;
   }

   setSelectCells(cells: CalcTableCell[]) {
      if(this.getTableLayout()) {
         this.getTableLayout().selectedCells = cells;
      }
   }

   getSelectCells(): CalcTableCell[] {
      return this.getTableLayout() ? this.getTableLayout().selectedCells : [];
   }

   setSelectRect(rect: Rectangle) {
      if(this.getTableLayout()) {
         this.getTableLayout().selectedRect = rect;
      }
   }

   getSelectRect(): Rectangle {
      return this.getTableLayout() ?
         this.getTableLayout().selectedRect : new Rectangle(0, 0, 0, 0);
   }

   set namedGroups(names: string[]) {
      this._namedGroups = names;
   }

   get namedGroups(): string[] {
      return this._namedGroups;
   }

   clearAllData(): void {
      this._cellBinding = new CellBindingInfo();
      this._cellNames = [];
      this._aggregates = [];
      this._hasRowGroup = false;
      this._hasColGroup = false;
      this._groupNum = null;
      this._namedGroups = [];
   }
}
