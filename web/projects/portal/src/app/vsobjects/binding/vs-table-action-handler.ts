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
import { Injector, Type } from "@angular/core";
import { forkJoin as observableForkJoin } from "rxjs";
import { AssemblyActionEvent } from "../../common/action/assembly-action-event";
import { Tool } from "../../../../../shared/util/tool";
import { ViewsheetClientService } from "../../common/viewsheet-client";
import { CheckCycleDependencyService } from "../../composer/gui/check-cycle-dependency.service";
import { AbstractActionHandler } from "../../composer/gui/vs/action/abstract-action-handler";
import { loadingScriptTreeModel, ScriptPaneTreeModel } from "../../widget/dialog/script-pane/script-pane-tree-model";
import { ModelService } from "../../widget/services/model.service";
import { DialogService } from "../../widget/slide-out/dialog-service.service";
import { SlideOutOptions } from "../../widget/slide-out/slide-out-options";
import { ColumnOptionDialog } from "../dialog/column-option-dialog.component";
import { HideColumnsDialog } from "../dialog/hide-columns-dialog.component";
import { TableViewPropertyDialog } from "../dialog/table-view-property-dialog.component";
import { RemoveTableColumnsEvent } from "../event/remove-table-columns-event";
import { VSObjectEvent } from "../event/vs-object-event";
import { BaseTableModel } from "../model/base-table-model";
import { ColumnOptionDialogModel } from "../model/column-option-dialog-model";
import { HideColumnsDialogModel } from "../model/hide-columns-dialog-model";
import { TableViewPropertyDialogModel } from "../model/table-view-property-dialog-model";
import { VSTableModel } from "../model/vs-table-model";
import { TableActionHandler } from "../objects/table/table-action-handler";
import { ContextProvider } from "../context-provider.service";

const TABLEVIEW_PROPERTY_URI: string = "composer/vs/table-view-property-dialog-model/";
const COLUMN_OPTION_URI: string = "composer/vs/column-option-dialog-model";
const HIDE_COLUMNS_URI: string = "composer/vs/hide-columns-dialog-model";

export class VSTableActionHandler extends AbstractActionHandler {
   tableActionHandler: TableActionHandler;

   constructor(private modelService: ModelService,
               private viewsheetClient: ViewsheetClientService,
               protected modalService: DialogService,
               private injector: Injector,
               protected context: ContextProvider) {
      super(modalService, context);
      this.tableActionHandler =
         new TableActionHandler(modelService, viewsheetClient, modalService, context);
   }

   handleEvent(event: AssemblyActionEvent<BaseTableModel>, variableValues: string[]) {
      switch(event.id) {
      case "table properties":
         this.showTablePropertiesDialog(<VSTableModel> event.model, variableValues);
         break;
      case "table reset-table-layout":
         this.resetTableLayout(<VSTableModel> event.model);
         break;
      case "table hide-columns":
         this.hideTableColumns(<VSTableModel> event.model);
         break;
      case "table delete-columns":
         this.removeTableColumns(<VSTableModel> event.model);
         break;
      case "table column-options":
         this.showTableColumnOptionsDialog(<VSTableModel> event.model);
         break;
      case "table edit-script":
         this.showTablePropertiesDialog(<VSTableModel> event.model, variableValues, true);
         break;
      }
   }

   private showTablePropertiesDialog(model: VSTableModel, variableValues: string[],
                                     openToScript: boolean = false): void
   {
      const modelUri: string = "../api/" + TABLEVIEW_PROPERTY_URI
         + Tool.encodeURIComponentExceptSlash(model.absoluteName) + "/" + Tool.byteEncode(this.viewsheetClient.runtimeId);
      const scriptUri: string = "../api/vsscriptable/scriptTree";

      const checkCycleDependency = new CheckCycleDependencyService(this.modelService, this.viewsheetClient.runtimeId, model.absoluteName);
      const modalInjector = Injector.create({providers: [{provide: CheckCycleDependencyService, useValue: checkCycleDependency}], parent: this.injector});

      const params = new HttpParams()
         .set("vsId", this.viewsheetClient.runtimeId)
         .set("assemblyName", model.absoluteName);
      this.modelService.getModel(modelUri).subscribe((data: TableViewPropertyDialogModel) => {
         const options = { windowClass: "property-dialog-window",
                           title: this.getTitle(model, "_#(js:Properties)"),
                           injector: modalInjector, objectId: model.absoluteName, limitResize: false };
         const dialog: TableViewPropertyDialog = this.showDialog(
            TableViewPropertyDialog, options,
            (result: TableViewPropertyDialogModel) => {
               const eventUri: string = "/events/" + TABLEVIEW_PROPERTY_URI + model.absoluteName;
               model.absoluteName = result.tableViewGeneralPaneModel.generalPropPaneModel
                  .basicGeneralPaneModel.name;
               this.viewsheetClient.sendEvent(eventUri, result);
            });
         dialog.model = data;
         dialog.variableValues = variableValues;
         dialog.openToScript = openToScript;
         dialog.runtimeId = this.viewsheetClient.runtimeId;
         dialog.scriptTreeModel = loadingScriptTreeModel;
         this.modelService.getModel(scriptUri, params).subscribe(res => dialog.scriptTreeModel = res);
      });
   }

   private hideTableColumns(model: VSTableModel): void {
      if(model.selectedHeaders || model.selectedData || !model.form) {
         return;
      }

      const params = new HttpParams()
         .set("runtimeId", this.viewsheetClient.runtimeId)
         .set("objectId", model.absoluteName);

      const eventUri: string = "/events/" + HIDE_COLUMNS_URI + "/" + model.absoluteName;

      this.showTableDialog(
         HideColumnsDialog, "../api/" + HIDE_COLUMNS_URI, eventUri,
         (dialog: HideColumnsDialog, dlgModel: HideColumnsDialogModel) => {
            dialog.model = dlgModel;
         }, params, model.absoluteName, { objectId: model.absoluteName });
   }

   private removeTableColumns(model: VSTableModel): void {
      if(!model.selectedHeaders) {
         return;
      }

      let map: Map<number, number[]> = model.selectedHeaders;
      let cols: number[] = [];

      map.forEach(function(value) {
         cols = cols.concat(value);
      });

      let event: RemoveTableColumnsEvent = new RemoveTableColumnsEvent(model.absoluteName, cols);

      this.viewsheetClient.sendEvent("/events/composer/viewsheet/table/deleteColumns", event);
      this.clearSelection(model);
   }

   private showTableColumnOptionsDialog(model: VSTableModel): void {
      if(!model.selectedHeaders || !model.form) {
         return;
      }

      const params = new HttpParams()
         .set("runtimeId", this.viewsheetClient.runtimeId)
         .set("objectId", model.absoluteName)
         .set("col", model.firstSelectedColumn + "");

      const eventUri: string = "/events/" + COLUMN_OPTION_URI + "/" +
         model.absoluteName + "/" + model.firstSelectedColumn;

      this.showTableDialog(
         ColumnOptionDialog, "../api/" + COLUMN_OPTION_URI, eventUri,
         (dialog: ColumnOptionDialog, dlgModel: ColumnOptionDialogModel) => {
            dialog.model = dlgModel;
            dialog.runtimeId = this.viewsheetClient.runtimeId;
         }, params, model.absoluteName);
   }

   private resetTableLayout(model: VSTableModel): void {
      const vsEvent: VSObjectEvent = new VSObjectEvent(model.absoluteName);
      this.viewsheetClient.sendEvent(
         "/events/composer/viewsheet/table/resetTableLayout", vsEvent);
   }

   private clearSelection(model: VSTableModel): void {
      model.selectedHeaders = null;
      model.selectedData = null;
      model.firstSelectedColumn = -1;
      model.firstSelectedRow = -1;
   }

   showTableDialog<D, M>(dialogType: Type<D>, modelUri: string,
                         eventUri: string, bind: (dialog: D, model: M) => any,
                         params: HttpParams = null, absoluteName?: string,
                         options?: SlideOutOptions): void
   {
      if(!options) {
         options = {
            windowClass: "property-dialog-window",
            objectId: absoluteName
         };
      }

      this.tableActionHandler.showTableDialog(
         dialogType, modelUri, eventUri, bind, params, options);
   }
}
