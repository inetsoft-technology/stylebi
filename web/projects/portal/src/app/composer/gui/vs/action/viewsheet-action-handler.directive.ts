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
import { Directive, EventEmitter, Input, OnDestroy, Output } from "@angular/core";
import { forkJoin as observableForkJoin, Subscription } from "rxjs";
import { Tool } from "../../../../../../../shared/util/tool";
import { AssemblyActionEvent } from "../../../../common/action/assembly-action-event";
import { ViewsheetActions } from "../../../../vsobjects/action/viewsheet-actions";
import { VSViewsheetModel } from "../../../../vsobjects/model/vs-viewsheet-model";
import { VSUtil } from "../../../../vsobjects/util/vs-util";
import { loadingScriptTreeModel, ScriptPaneTreeModel } from "../../../../widget/dialog/script-pane/script-pane-tree-model";
import { ModelService } from "../../../../widget/services/model.service";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { Viewsheet } from "../../../data/vs/viewsheet";
import { ViewsheetObjectPropertyDialogModel } from "../../../data/vs/viewsheet-object-property-dialog-model";
import { ViewsheetObjectPropertyDialog } from "../../../dialog/vs/viewsheet-object-property-dialog.component";
import { AbstractActionHandler } from "./abstract-action-handler";
import { ContextProvider } from "../../../../vsobjects/context-provider.service";

const VIEWSHEET_PROPERTY_URI: string = "composer/vs/viewsheet-object-property-dialog-model/";

@Directive({
   selector: "[cViewsheetActionHandler]"
})
export class ViewsheetActionHandlerDirective extends AbstractActionHandler implements OnDestroy {
   @Input() model: VSViewsheetModel;
   @Input() vsInfo: Viewsheet;
   @Output() onOpenFormatPane = new EventEmitter<VSViewsheetModel>();
   private subscription: Subscription;

   @Input()
   set actions(value: ViewsheetActions) {
      this.unsubscribe();

      if(value) {
         this.subscription = value.onAssemblyActionEvent.subscribe(
            (event) => this.handleEvent(event));
      }
   }

   constructor(private modelService: ModelService, modalService: DialogService,
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

   private handleEvent(event: AssemblyActionEvent<VSViewsheetModel>): void {
      switch(event.id) {
      case "viewsheet properties":
         this.showPropertyDialog();
         break;
      case "viewsheet edit-script":
         this.showPropertyDialog(true);
         break;
      }
   }

   private showPropertyDialog(openToScript: boolean = false): void {
      const modelUri: string = "../api/" + VIEWSHEET_PROPERTY_URI +
         Tool.encodeURIPath(this.model.absoluteName) + "/" +
         Tool.encodeURIPath(this.vsInfo.runtimeId);
      const scriptUri: string = "../api/vsscriptable/scriptTree";
      const params = new HttpParams()
         .set("vsId", this.vsInfo.runtimeId)
         .set("assemblyName", this.model.absoluteName);
      this.modelService.getModel(modelUri).subscribe((data: ViewsheetObjectPropertyDialogModel) => {
         const options = { windowClass: "property-dialog-window",
                           title: this.getTitle(this.model, "_#(js:Properties)"),
                           objectId: this.model.absoluteName, limitResize: false };
         const dialog: ViewsheetObjectPropertyDialog = this.showDialog(
            ViewsheetObjectPropertyDialog, options,
            (result: ViewsheetObjectPropertyDialogModel) => {
               const eventUri: string =
                  "/events/" + VIEWSHEET_PROPERTY_URI + this.model.absoluteName;
               this.vsInfo.socketConnection.sendEvent(eventUri, result);
               this.model.absoluteName = result.generalPropPaneModel.basicGeneralPaneModel.name;
            });
         dialog.model = data;
         dialog.runtimeId = this.vsInfo.runtimeId;
         dialog.variableValues =
            VSUtil.getVariableList(this.vsInfo.vsObjects, this.model.absoluteName);
         dialog.openToScript = openToScript;
         dialog.scriptTreeModel = loadingScriptTreeModel;
         this.modelService.getModel(scriptUri, params).subscribe(res => dialog.scriptTreeModel = res);
      });
   }
}
