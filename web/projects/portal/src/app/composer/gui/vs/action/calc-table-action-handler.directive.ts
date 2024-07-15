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
import { HttpParams } from "@angular/common/http";
import { Directive, HostListener, Injector, Input, OnDestroy, } from "@angular/core";
import { forkJoin as observableForkJoin, Subscription } from "rxjs";
import { Tool } from "../../../../../../../shared/util/tool";
import { AssemblyActionEvent } from "../../../../common/action/assembly-action-event";
import { CalcTableActions } from "../../../../vsobjects/action/calc-table-actions";
import { CalcTablePropertyDialog } from "../../../../vsobjects/dialog/calc-table-property-dialog.component";
import { HyperlinkDialog } from "../../../../vsobjects/dialog/graph/hyperlink-dialog.component";
import { VSObjectEvent } from "../../../../vsobjects/event/vs-object-event";
import { CalcTablePropertyDialogModel } from "../../../../vsobjects/model/calc-table-property-dialog-model";
import { HyperlinkDialogModel } from "../../../../vsobjects/model/hyperlink-dialog-model";
import { VSCalcTableModel } from "../../../../vsobjects/model/vs-calctable-model";
import { CalcTableActionHandler } from "../../../../vsobjects/objects/table/calc-table-action-handler";
import { VSUtil } from "../../../../vsobjects/util/vs-util";
import {loadingScriptTreeModel, ScriptPaneTreeModel} from "../../../../widget/dialog/script-pane/script-pane-tree-model";
import { ModelService } from "../../../../widget/services/model.service";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { Viewsheet } from "../../../data/vs/viewsheet";
import { VSSortingDialogModel } from "../../../data/vs/vs-sorting-dialog-model";
import { VSSortingDialog } from "../../../dialog/vs/vs-sorting-dialog.component";
import { CheckCycleDependencyService } from "../../check-cycle-dependency.service";
import { AbstractActionHandler } from "./abstract-action-handler";
import { ContextProvider } from "../../../../vsobjects/context-provider.service";

const CALC_TABLE_PROPERTY_URI: string = "composer/vs/calc-table-property-dialog-model/";
const SORTING_URI: string = "composer/vs/vs-sorting-dialog-model";
const HYPERLINK_URI: string = "composer/vs/hyperlink-dialog-model";

@Directive({
   selector: "[cCalcTableActionHandler]"
})
export class CalcTableActionHandlerDirective extends AbstractActionHandler implements OnDestroy {
   @Input() model: VSCalcTableModel;
   @Input() scrollX: number = 0;
   private _viewsheet: Viewsheet;
   private subscription: Subscription;
   private actionHandler: CalcTableActionHandler;

   @Input()
   set actions(value: CalcTableActions) {
      this.unsubscribe();

      if(value) {
         this.subscription = value.onAssemblyActionEvent.subscribe(
            (event) => this.handleEvent(event));
      }
   }

   @Input()
   set vsInfo(value: Viewsheet) {
      this.actionHandler = null;
      this._viewsheet = value;

      if(value) {
         this.actionHandler = new CalcTableActionHandler(
            this.modelService, value.socketConnection, this.modalService, this.context);
      }
   }

   constructor(private modelService: ModelService, modalService: DialogService,
               private injector: Injector,
               protected context: ContextProvider) {
      super(modalService, context);
   }

   ngOnDestroy(): void {
      this.unsubscribe();
   }

   private unsubscribe() {
      if(this.subscription) {
         this.subscription.unsubscribe();
         this.subscription = null;
      }
   }

   private handleEvent(event: AssemblyActionEvent<VSCalcTableModel>): void {
      switch(event.id) {
      case "calc-table properties":
         this.showPropertyDialog();
         break;
      case "calc-table sorting":
         this.showSortingDialog();
         break;
      case "calc-table hyperlink":
         this.showHyperlinkDialog();
         break;
      case "calc-table edit-script":
         this.showPropertyDialog(true);
         break;
      case "calc-table reset-table-layout":
         this.resetTableLayout();
         break;
      }
   }

   private showPropertyDialog(openToScript: boolean = false): void {
      const objectId = this.model.absoluteName;
      const modelUri: string = "../api/" + CALC_TABLE_PROPERTY_URI +
         Tool.encodeURIPath(objectId) + "/" + this.scrollX + "/" +
         Tool.encodeURIPath(this._viewsheet.runtimeId);
      const scriptUri: string = "../api/vsscriptable/scriptTree";

      const checkCycleDependency = new CheckCycleDependencyService(this.modelService, this._viewsheet.runtimeId, objectId);
      const modalInjector = Injector.create({providers: [{provide: CheckCycleDependencyService, useValue: checkCycleDependency}], parent: this.injector});

      const params = new HttpParams()
         .set("vsId", this._viewsheet.runtimeId)
         .set("assemblyName", objectId);
      this.modelService.getModel(modelUri).subscribe((data: CalcTablePropertyDialogModel) => {
         const options = { windowClass: "property-dialog-window",
                           title: this.getTitle(this.model, "_#(js:Properties)"),
                           injector: modalInjector, objectId: objectId, limitResize: false };
         const dialog: CalcTablePropertyDialog = this.showDialog(
            CalcTablePropertyDialog, options,
            (result: CalcTablePropertyDialogModel) => {
               const eventUri: string =
                  "/events/" + CALC_TABLE_PROPERTY_URI + this.model.absoluteName;
               this._viewsheet.socketConnection.sendEvent(eventUri, result);
               this.model.absoluteName = result.tableViewGeneralPaneModel.generalPropPaneModel
                  .basicGeneralPaneModel.name;
            });
         dialog.model = data;
         dialog.runtimeId = this._viewsheet.runtimeId;
         dialog.variableValues =
            VSUtil.getVariableList(this._viewsheet.vsObjects, this.model.absoluteName);
         dialog.openToScript = openToScript;
         dialog.scriptTreeModel = loadingScriptTreeModel;
         this.modelService.getModel(scriptUri, params).subscribe(res => dialog.scriptTreeModel = res);
      });
   }

   @HostListener("onOpenConditionDialog")
   showConditionDialog(): void {
      this.actionHandler.showConditionDialog(
         this._viewsheet.runtimeId, this.model.absoluteName,
         VSUtil.getVariableList(this._viewsheet.vsObjects, null));
   }

   private showSortingDialog(): void {
      let params = new HttpParams()
         .set("runtimeId", this._viewsheet.runtimeId)
         .set("objectId", this.model.absoluteName);

      this.modelService.getModel("../api/" + SORTING_URI, params).toPromise().then(
         (data: any) => {
            const options = { objectId: this.model.absoluteName };
            const dialog: VSSortingDialog = this.showDialog(
               VSSortingDialog, options, (result: VSSortingDialogModel) => {
                  const eventUri: string =
                     "/events/" + SORTING_URI + "/" + this.model.absoluteName;
                  this._viewsheet.socketConnection.sendEvent(eventUri, result);
               });
            dialog.model = <VSSortingDialogModel> data;
         },
         (error: any) => {
            //TODO handle error
            console.error("Failed to load calc table sorting model", error);
         }
      );
   }

   private showHyperlinkDialog(): void {
      if(this.model.firstSelectedRow == -1 || this.model.firstSelectedColumn == -1) {
         return;
      }

      let params = new HttpParams()
         .set("runtimeId", this._viewsheet.runtimeId)
         .set("objectId", this.model.absoluteName)
         .set("row", this.model.firstSelectedRow + "")
         .set("col", this.model.firstSelectedColumn + "");

      this.modelService.getModel("../api/" + HYPERLINK_URI, params).toPromise().then(
         (data: any) => {
         const options = { objectId: this.model.absoluteName };
            const dialog: HyperlinkDialog = this.showDialog(
               HyperlinkDialog, options, (result: HyperlinkDialogModel) => {
                  const eventUri: string =
                     "/events/" + HYPERLINK_URI + "/" + this.model.absoluteName;
                  this._viewsheet.socketConnection.sendEvent(eventUri, result);
               });
            dialog.model = <HyperlinkDialogModel> data;
            dialog.runtimeId = this._viewsheet.runtimeId;
            dialog.objectName = this.model.absoluteName;
         },
         (error: any) => {
            //TODO handle error
            console.error("Failed to load hyperlink model: ", error);
         }
      );
   }

   @HostListener("onOpenHighlightDialog")
   showHighlightDialog(): void {
      this.actionHandler.showHighlightDialog(
         this._viewsheet.runtimeId, this.model.absoluteName, this.model.firstSelectedRow,
         this.model.firstSelectedColumn,
         VSUtil.getVariableList(this._viewsheet.vsObjects, null));
   }

   private resetTableLayout(): void {
      const vsEvent: VSObjectEvent = new VSObjectEvent(this.model.absoluteName);
      this._viewsheet.socketConnection.sendEvent(
         "/events/composer/viewsheet/table/resetTableLayout", vsEvent);
   }
}
