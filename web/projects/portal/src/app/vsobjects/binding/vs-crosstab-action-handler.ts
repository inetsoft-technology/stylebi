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
import { HttpParams } from "@angular/common/http";
import { Injector, Type } from "@angular/core";
import { NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { forkJoin as observableForkJoin } from "rxjs";
import { AssemblyActionEvent } from "../../common/action/assembly-action-event";
import { Tool } from "../../../../../shared/util/tool";
import { ViewsheetClientService } from "../../common/viewsheet-client";
import { CheckCycleDependencyService } from "../../composer/gui/check-cycle-dependency.service";
import { AbstractActionHandler } from "../../composer/gui/vs/action/abstract-action-handler";
import { loadingScriptTreeModel, ScriptPaneTreeModel } from "../../widget/dialog/script-pane/script-pane-tree-model";
import { ModelService } from "../../widget/services/model.service";
import { DialogService } from "../../widget/slide-out/dialog-service.service";
import { CrosstabPropertyDialog } from "../dialog/crosstab-property-dialog.component";
import { VSObjectEvent } from "../event/vs-object-event";
import { BaseTableModel } from "../model/base-table-model";
import { CrosstabPropertyDialogModel } from "../model/crosstab-property-dialog-model";
import { VSCrosstabModel } from "../model/vs-crosstab-model";
import { CrosstabActionHandler } from "../objects/table/crosstab-action-handler";
import { ContextProvider } from "../context-provider.service";

const CROSSTAB_PROPERTY_URI: string = "composer/vs/crosstab-property-dialog-model/";

export class VSCrosstabActionHandler extends AbstractActionHandler {
   crosstabActionHandler: CrosstabActionHandler;

   constructor(private modelService: ModelService,
               private viewsheetClient: ViewsheetClientService,
               modalService: DialogService,
               private injector: Injector,
               protected context: ContextProvider) {
      super(modalService, context);
      this.crosstabActionHandler =
         new CrosstabActionHandler(modelService, viewsheetClient, modalService, context);
   }

   handleEvent(event: AssemblyActionEvent<BaseTableModel>, variableValues: string[]) {
      switch(event.id) {
      case "crosstab properties":
         this.showCrosstabPropertiesDialog(<VSCrosstabModel> event.model, variableValues);
         break;
      case "crosstab reset-table-layout":
         this.resetCrosstabLayout(<VSCrosstabModel> event.model);
         break;
      case "crosstab edit-script":
         this.showCrosstabPropertiesDialog(<VSCrosstabModel> event.model, variableValues, true);
         break;
      }
   }

   private showCrosstabPropertiesDialog(model: VSCrosstabModel, variableValues: string[],
                                        openToScript: boolean = false): void
   {
      const modelUri: string = "../api/" + CROSSTAB_PROPERTY_URI +
         Tool.encodeURIPath(model.absoluteName) + "/" +
         Tool.encodeURIPath(this.viewsheetClient.runtimeId);
      const scriptUri: string = "../api/vsscriptable/scriptTree";

      const checkCycleDependency = new CheckCycleDependencyService(this.modelService,
         this.viewsheetClient.runtimeId, model.absoluteName);
      const modalInjector = Injector.create({
         providers: [
            {
               provide: CheckCycleDependencyService,
               useValue: checkCycleDependency
            }],
         parent: this.injector});

      const params = new HttpParams()
         .set("vsId", this.viewsheetClient.runtimeId)
         .set("assemblyName", model.absoluteName);
      this.modelService.getModel(modelUri).subscribe((data: CrosstabPropertyDialogModel) => {
         const options = { windowClass: "property-dialog-window",
                           title: this.getTitle(model, "_#(js:Properties)"),
                           injector: modalInjector, objectId: model.absoluteName, limitResize: false };
         const dialog: CrosstabPropertyDialog = this.showDialog0(
            CrosstabPropertyDialog, options,
            (result: CrosstabPropertyDialogModel) => {
               const eventUri: string = "/events/" + CROSSTAB_PROPERTY_URI + model.absoluteName;
               model.absoluteName = result.tableViewGeneralPaneModel
                  .generalPropPaneModel.basicGeneralPaneModel.name;
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

   private resetCrosstabLayout(model: VSCrosstabModel): void {
      const vsEvent: VSObjectEvent = new VSObjectEvent(model.absoluteName);
      this.viewsheetClient.sendEvent(
         "/events/composer/viewsheet/table/resetTableLayout", vsEvent);
   }

   private showDialog0<D>(dialogType: Type<D>, options: NgbModalOptions = {},
                         onCommit: (value: any) => any,
                         onCancel: (value: any) => any = () => {},
                         commitEmitter: string = "onCommit",
                         cancelEmitter: string = "onCancel"): D
   {
      return this.crosstabActionHandler.showDialog(
         dialogType, options, onCommit, onCancel, commitEmitter, cancelEmitter);
   }
}
