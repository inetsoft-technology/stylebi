/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { HttpParams } from "@angular/common/http";
import {
   AfterViewInit,
   Directive,
   EventEmitter,
   Injector,
   Input,
   NgZone,
   OnDestroy,
   Output,
   Renderer2
} from "@angular/core";
import { forkJoin as observableForkJoin, Subscription } from "rxjs";
import { InsertRowColDialog } from "../../binding/editor/table/insert-row-col-dialog.component";
import { CopyCutCalcCellEvent } from "../../binding/event/copy-cut-calc-cell-event";
import { ModifyTableLayoutEvent } from "../../binding/event/modify-table-layout-event";
import { AssemblyActionEvent } from "../../common/action/assembly-action-event";
import { Rectangle } from "../../common/data/rectangle";
import { CalcTableLayout } from "../../common/data/tablelayout/calc-table-layout";
import { ViewsheetClientService } from "../../common/viewsheet-client";
import { CheckCycleDependencyService } from "../../composer/gui/check-cycle-dependency.service";
import { AbstractActionHandler } from "../../composer/gui/vs/action/abstract-action-handler";
import { CalcTableActions } from "../../vsobjects/action/calc-table-actions";
import { CalcTablePropertyDialog } from "../../vsobjects/dialog/calc-table-property-dialog.component";
import { BaseTableModel } from "../../vsobjects/model/base-table-model";
import { CalcTablePropertyDialogModel } from "../../vsobjects/model/calc-table-property-dialog-model";
import { VSCalcTableModel } from "../../vsobjects/model/vs-calctable-model";
import { VSObjectModel } from "../../vsobjects/model/vs-object-model";
import { VSUtil } from "../../vsobjects/util/vs-util";
import { loadingScriptTreeModel, ScriptPaneTreeModel } from "../../widget/dialog/script-pane/script-pane-tree-model";
import { ModelService } from "../../widget/services/model.service";
import { DialogService } from "../../widget/slide-out/dialog-service.service";
import { Tool } from "../../../../../shared/util/tool";
import { ContextProvider } from "../../vsobjects/context-provider.service";

const CALC_TABLE_PROPERTY_URI: string = "composer/vs/calc-table-property-dialog-model/";

@Directive({
   selector: "[bCalcTableActionHandler]"
})
export class BCalcTableActionHandlerDirective extends AbstractActionHandler implements AfterViewInit, OnDestroy {
   @Input() vsObject: VSCalcTableModel;
   @Input() layoutModel: CalcTableLayout;
   @Output() onOpenFormatPane = new EventEmitter<VSCalcTableModel>();
   private subscription: Subscription;
   _oselection: Rectangle;
   pasteType: string = null;
   private keydownListener: () => void;

   @Input()
   set actions(value: CalcTableActions) {
      this.unsubscribe();

      if(value) {
         value.inBindingPane = true;
         this.subscription = value.onAssemblyActionEvent.subscribe(
            (event) => this.handleEvent(event));
      }
   }

   constructor(private clientService: ViewsheetClientService, modalService: DialogService,
               private modelService: ModelService, private injector: Injector,
               private renderer: Renderer2, private zone: NgZone,
               protected context: ContextProvider) {
      super(modalService, context);
   }

   ngAfterViewInit(): void {
      this.zone.runOutsideAngular(() => {
         this.keydownListener = this.renderer.listen(
            "document", "keydown", ($event) => this.onKeydown($event));
      });
   }

   ngOnDestroy(): void {
      this.unsubscribe();

      if(this.keydownListener) {
         this.keydownListener();
      }
   }

   private unsubscribe() {
      if(this.subscription) {
         this.subscription.unsubscribe();
         this.subscription = null;
      }
   }

   private handleEvent(event: AssemblyActionEvent<BaseTableModel>): void {
      switch(event.id) {
      case "calc-table properties":
         this.showPropertiesDialog();
         break;
      case "calc-table merge-cells":
         this.mergeCells();
         break;
      case "calc-table split-cells":
         this.splitCells();
         break;
      case "calc-table insert-rows-columns":
         this.openInsertRowColDialog();
         break;
      case "calc-table insert-row":
         this.insertRow();
         break;
      case "calc-table append-row":
         this.appendRow();
         break;
      case "calc-table delete-row":
         this.deleteRow(event.model.selectedData.size);
         event.model.selectedData.clear();
         break;
      case "calc-table insert-column":
         this.insertColumn();
         break;
      case "calc-table append-column":
         this.appendColumn();
         break;
      case "calc-table delete-column":
         this.deleteColumn(this.getColumnNum(event.model));
         event.model.selectedData.clear();
         break;
      case "calc-table copy-cell":
         this.copyCell();
         break;
      case "calc-table cut-cell":
         this.cutCell();
         break;
      case "calc-table paste-cell":
         this.pasteCell();
         break;
      case "calc-table remove-cell":
         this.removeCell();
         break;
      case "calc-table show-format-pane":
         this.onOpenFormatPane.emit(this.vsObject);
         break;
      }
   }

   private showPropertiesDialog(): void {
      let vsObjects: VSObjectModel[] = [];
      vsObjects[0] = this.vsObject;

      const rid: string = this.clientService.runtimeId;
      const objectId: string = this.vsObject.absoluteName;
      const modelUri: string = "../api/" + CALC_TABLE_PROPERTY_URI +
         Tool.encodeURIPath(objectId) + "/0/" + Tool.encodeURIPath(rid);
      const scriptUri: string = "../api/vsscriptable/scriptTree";

      const checkCycleDependency = new CheckCycleDependencyService(this.modelService,
         this.clientService.runtimeId, this.vsObject.absoluteName);
      const modalInjector = Injector.create(
         {
            providers: [
               {
                  provide: CheckCycleDependencyService,
                  useValue: checkCycleDependency
               }],
            parent: this.injector
         });

      const params = new HttpParams()
         .set("vsId", rid)
         .set("assemblyName", objectId);

      this.modelService.getModel(modelUri).subscribe((data: CalcTablePropertyDialogModel) => {
         const options = { windowClass: "property-dialog-window",
                           injector: modalInjector,
                           objectId: objectId,
                           limitResize: false };
         const dialog: CalcTablePropertyDialog = this.showDialog(
            CalcTablePropertyDialog, options,
            (result: CalcTablePropertyDialogModel) => {
               const eventUri: string =
                  "/events/" + CALC_TABLE_PROPERTY_URI + this.vsObject.absoluteName;
               this.clientService.sendEvent(eventUri, result);
            });

         dialog.model = data;
         dialog.variableValues = VSUtil.getVariableList(vsObjects,
            this.vsObject.absoluteName);
         dialog.openToScript = false;
         dialog.scriptTreeModel = loadingScriptTreeModel;
         this.modelService.getModel(scriptUri, params).subscribe(res => dialog.scriptTreeModel = res);
      });
   }

   private mergeCells(): void {
      this.sendOperationRequest("mergeCells", 0);
   }

   private splitCells(): void {
      this.sendOperationRequest("splitCells", 0);
   }

   private insertRow(num: number = 1): void {
      this.sendOperationRequest("insertRow", num);
      this.clearSelect();
   }

   private appendRow(num: number = 1): void {
      this.sendOperationRequest("appendRow", num);
      this.clearSelect();
   }

   private deleteRow(num: number = 1) {
      this.sendOperationRequest("deleteRow", num);
      this.clearSelect();
   }

   private insertColumn(num: number = 1): void {
      this.sendOperationRequest("insertCol", num);
      this.clearSelect();
   }

   private appendColumn(num: number = 1): void {
      this.sendOperationRequest("appendCol", num);
      this.clearSelect();
   }

   private deleteColumn(num: number = 1): void {
      this.sendOperationRequest("deleteCol", num);
      this.clearSelect();
   }

   getSelectRect(): Rectangle {
      return this.layoutModel && this.layoutModel.selectedRect ?
         this.layoutModel.selectedRect : new Rectangle(0, 0, 0, 0);
   }

   private getColumnNum(model: BaseTableModel): number {
      let num = 0;

      if(model) {
         model.selectedData.forEach((value, key) => {
            num = num < value.length ? value.length : num;
         });
      }

      return num;
   }

   private sendOperationRequest(op: string, num: number) {
      let evt: ModifyTableLayoutEvent = new ModifyTableLayoutEvent(
         this.vsObject.absoluteName, op, num, this.getSelectRect());
      this.clientService.sendEvent("/events/vs/calctable/tablelayout/modifylayout", evt);
   }

   private clearSelect(): void {
      if(this.layoutModel) {
         this.layoutModel.selectedCells = [];
         this.layoutModel.selectedRect = new Rectangle(0, 0, 0, 0);
         this.layoutModel.selectedRegions = [];
      }
   }

   private copyCell(): void {
      this.pasteType = "copy";
      this._oselection = this.getSelectRect();
   }

   private cutCell(): void {
      this.pasteType = "cut";
      this._oselection = this.getSelectRect();
   }

   private pasteCell(): void {
      let content: Rectangle[] = [];
      content.push(this._oselection);
      content.push(this.getSelectRect());
      this.sendCopyCutRequest(this.pasteType, content);
   }

   private removeCell(): void {
      let content: Rectangle[] = [];
      content.push(this.getSelectRect());
      content.push(this.getSelectRect());
      this.sendCopyCutRequest("remove", content);
   }

   private sendCopyCutRequest(op: string, content: Rectangle[]): void {
      let evt: CopyCutCalcCellEvent = new CopyCutCalcCellEvent(
         this.vsObject.absoluteName, op, content);
      this.clientService.sendEvent("/events/vs/calctable/tablelayout/copycutcell", evt);
   }

   private openInsertRowColDialog(): void {
      const options = { objectId: this.vsObject.absoluteName };
      this.showDialog(InsertRowColDialog, options, (result: any) => {
         this.editorTableCell(result);
      });
   }

   private editorTableCell(result: any): void {
      let num = 1;

      if(result.num >= 1 && result.num <= 1000){
         num = result.num;
      }

      if(result.insertRadio == "true" && result.insertBefore == "true") {
         this.insertRow(num);
      }
      else if(result.insertRadio == "false" && result.insertBefore == "true") {
         this.insertColumn(num);
      }
      else if(result.insertRadio == "true" && result.insertBefore == "false") {
         this.appendRow(num);
      }
      else if(result.insertRadio == "false" && result.insertBefore == "false") {
         this.appendColumn(num);
      }
   }

   // ctrl + c/v/x/d is clicked
   onKeydown(event: KeyboardEvent) {
      event.stopPropagation();

      if(event.keyCode == 67 && event.ctrlKey) {
         this.copyCell();
      }

      if(event.keyCode == 86 && event.ctrlKey) {
         this.pasteCell();
      }

      if(event.keyCode == 88 && event.ctrlKey) {
         this.cutCell();
      }

      if(event.keyCode == 68 && event.ctrlKey || event.keyCode == 46) {
         const tag = (<Element> event.target).tagName;

         // ignore key typed in input fields
         if(!tag || tag.toLowerCase() != "input") {
            this.removeCell();
         }
      }
   }
}
