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
import { Directive, Input, OnDestroy } from "@angular/core";
import { forkJoin as observableForkJoin, Subscription } from "rxjs";
import { AssemblyActionEvent } from "../../../../common/action/assembly-action-event";
import { VSConditionDialogModel } from "../../../../common/data/condition/vs-condition-dialog-model";
import { Tool } from "../../../../../../../shared/util/tool";
import { GaugeActions } from "../../../../vsobjects/action/gauge-actions";
import { HyperlinkDialog } from "../../../../vsobjects/dialog/graph/hyperlink-dialog.component";
import { VSConditionDialog } from "../../../../vsobjects/dialog/vs-condition-dialog.component";
import { HyperlinkDialogModel } from "../../../../vsobjects/model/hyperlink-dialog-model";
import { VSGaugeModel } from "../../../../vsobjects/model/output/vs-gauge-model";
import { VSUtil } from "../../../../vsobjects/util/vs-util";
import { loadingScriptTreeModel, ScriptPaneTreeModel } from "../../../../widget/dialog/script-pane/script-pane-tree-model";
import { ModelService } from "../../../../widget/services/model.service";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { SlideOutOptions } from "../../../../widget/slide-out/slide-out-options";
import { GaugePropertyDialogModel } from "../../../data/vs/gauge-property-dialog-model";
import { Viewsheet } from "../../../data/vs/viewsheet";
import { GaugePropertyDialog } from "../../../dialog/vs/gauge-property-dialog.component";
import { AbstractActionHandler } from "./abstract-action-handler";
import { ContextProvider } from "../../../../vsobjects/context-provider.service";

const GAUGE_PROPERTY_URI: string = "composer/vs/gauge-property-dialog-model/";
const CONDITION_URI: string = "composer/vs/vs-condition-dialog-model";
const HYPERLINK_URI: string = "composer/vs/hyperlink-dialog-model";

@Directive({
   selector: "[cGaugeActionHandler]"
})
export class GaugeActionHandlerDirective extends AbstractActionHandler implements OnDestroy {
   @Input() model: VSGaugeModel;
   @Input() vsInfo: Viewsheet;
   private subscription: Subscription;

   @Input()
   set actions(value: GaugeActions) {
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

   private handleEvent(event: AssemblyActionEvent<VSGaugeModel>): void {
      switch(event.id) {
      case "gauge properties":
         this.showPropertyDialog();
         break;
      case "gauge conditions":
         this.showConditionDialog();
         break;
      case "gauge hyperlink":
         this.showHyperlinkDialog();
         break;
      case "gauge edit-script":
         this.showPropertyDialog(true);
         break;
      }
   }

   private showPropertyDialog(openToScript: boolean = false): void {
      const modelUri: string = "../api/" + GAUGE_PROPERTY_URI +
         Tool.encodeURIPath(this.model.absoluteName) + "/" +
         Tool.encodeURIPath(this.vsInfo.runtimeId);
      const scriptUri: string = "../api/vsscriptable/scriptTree";
      const params = new HttpParams()
         .set("vsId", this.vsInfo.runtimeId)
         .set("assemblyName", this.model.absoluteName);
      this.modelService.getModel(modelUri).subscribe((data: GaugePropertyDialogModel) => {
         const options = { windowClass: "property-dialog-window",
                           title: this.getTitle(this.model, "_#(js:Properties)"),
                           objectId: this.model.absoluteName, limitResize: false };
         const dialog: GaugePropertyDialog = this.showDialog(
            GaugePropertyDialog, options,
            (result: GaugePropertyDialogModel) => {
               const eventUri: string =
                  "/events/" + GAUGE_PROPERTY_URI + this.model.absoluteName;
               this.vsInfo.socketConnection.sendEvent(eventUri, result);
               this.model.absoluteName = result.gaugeGeneralPaneModel.outputGeneralPaneModel
                  .generalPropPaneModel.basicGeneralPaneModel.name;
            });
         dialog.model = data;
         dialog.variableValues =
            VSUtil.getVariableList(this.vsInfo.vsObjects, this.model.absoluteName);
         dialog.runtimeId = this.vsInfo.runtimeId;
         dialog.assemblyName = this.model.absoluteName;
         dialog.openToScript = openToScript;
         dialog.linkUri = this.vsInfo.linkUri;
         dialog.scriptTreeModel = loadingScriptTreeModel;
         this.modelService.getModel(scriptUri, params).subscribe(res => dialog.scriptTreeModel = res);
      });
   }

   private showConditionDialog(): void {
      const params = new HttpParams()
         .set("runtimeId", this.vsInfo.runtimeId)
         .set("assemblyName", this.model.absoluteName);
      const options: SlideOutOptions = {
         size: "lg",
         windowClass: "condition-dialog",
         objectId: this.model.absoluteName,
         limitResize: false
      };

      this.modelService.getModel("../api/" + CONDITION_URI, params).toPromise().then(
         (data: any) => {
            const dialog: VSConditionDialog = this.showDialog(
               VSConditionDialog, options,
               (result: VSConditionDialogModel) => {
                  const eventUri: string = "/events/" + CONDITION_URI + "/"
                     + this.model.absoluteName;
                  this.vsInfo.socketConnection.sendEvent(eventUri, result);
               });
            dialog.model = <VSConditionDialogModel> data;
            dialog.runtimeId = this.vsInfo.runtimeId;
            dialog.assemblyName = this.model.absoluteName;
            dialog.variableValues =
               VSUtil.getVariableList(this.vsInfo.vsObjects, this.model.absoluteName);
         },
         (error: any) => {
            //TODO handle error
            console.error("Failed to load condition dialog model: ", error);
         }
      );
   }

   private showHyperlinkDialog(): void {
      const params = new HttpParams()
         .set("runtimeId", this.vsInfo.runtimeId)
         .set("objectId", this.model.absoluteName);

      this.modelService.getModel("../api/" + HYPERLINK_URI, params).toPromise().then(
         (data: any) => {
            const options = { objectId: this.model.absoluteName, windowClass: "property-dialog-window" };
            const dialog: HyperlinkDialog = this.showDialog(
               HyperlinkDialog, options, (result: HyperlinkDialogModel) => {
                  const eventUri: string =
                     "/events/" + HYPERLINK_URI + "/" + this.model.absoluteName;
                  this.vsInfo.socketConnection.sendEvent(eventUri, result);
               });
            dialog.model = <HyperlinkDialogModel> data;
            dialog.runtimeId = this.vsInfo.runtimeId;
            dialog.objectName = this.model.absoluteName;
         },
         (error: any) => {
            //TODO handle error
            console.error("Failed to load hyperlink model: ", error);
         }
      );
   }
}
