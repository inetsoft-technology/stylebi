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
import { Directive, Input, OnDestroy } from "@angular/core";
import { forkJoin as observableForkJoin, Subscription } from "rxjs";
import { Tool } from "../../../../../../../shared/util/tool";
import { AssemblyActionEvent } from "../../../../common/action/assembly-action-event";
import { VSConditionDialogModel } from "../../../../common/data/condition/vs-condition-dialog-model";
import { TextActions } from "../../../../vsobjects/action/text-actions";
import { HyperlinkDialog } from "../../../../vsobjects/dialog/graph/hyperlink-dialog.component";
import { HighlightDialog } from "../../../../vsobjects/dialog/highlight-dialog.component";
import { VSConditionDialog } from "../../../../vsobjects/dialog/vs-condition-dialog.component";
import { HyperlinkDialogModel } from "../../../../vsobjects/model/hyperlink-dialog-model";
import { VSTextModel } from "../../../../vsobjects/model/output/vs-text-model";
import { VSUtil } from "../../../../vsobjects/util/vs-util";
import { loadingScriptTreeModel, ScriptPaneTreeModel } from "../../../../widget/dialog/script-pane/script-pane-tree-model";
import { HighlightDialogModel } from "../../../../widget/highlight/highlight-dialog-model";
import { ModelService } from "../../../../widget/services/model.service";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { SlideOutOptions } from "../../../../widget/slide-out/slide-out-options";
import { TextPropertyDialogModel } from "../../../data/vs/text-property-dialog-model";
import { Viewsheet } from "../../../data/vs/viewsheet";
import { TextPropertyDialog } from "../../../dialog/vs/text-property-dialog.component";
import { AbstractActionHandler } from "./abstract-action-handler";
import { ContextProvider } from "../../../../vsobjects/context-provider.service";

const TEXT_PROPERTY_URI: string = "composer/vs/text-property-dialog-model/";
const CONDITION_URI: string = "composer/vs/vs-condition-dialog-model";
const HIGHLIGHT_URI: string = "composer/vs/highlight-dialog-model";
const HYPERLINK_URI: string = "composer/vs/hyperlink-dialog-model";

@Directive({
   selector: "[cTextActionHandler]"
})
export class TextActionHandlerDirective extends AbstractActionHandler implements OnDestroy {
   @Input() model: VSTextModel;
   @Input() vsInfo: Viewsheet;
   private subscription: Subscription;

   @Input()
   set actions(value: TextActions) {
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

   private handleEvent(event: AssemblyActionEvent<VSTextModel>): void {
      switch(event.id) {
      case "text properties":
         this.showPropertyDialog();
         break;
      case "text conditions":
         this.showConditionDialog();
         break;
      case "text hyperlink":
         this.showHyperlinkDialog();
         break;
      case "text highlight":
         this.showHighlightDialog();
         break;
      case "text edit-script":
         this.showPropertyDialog(true);
         break;
      }
   }

   private showPropertyDialog(openToScript: boolean = false): void {
      const modelUri: string = "../api/" + TEXT_PROPERTY_URI +
         Tool.encodeURIPath(this.model.absoluteName) + "/" +
         Tool.encodeURIPath(this.vsInfo.runtimeId);
      const scriptUri: string = "../api/vsscriptable/scriptTree";
      const params = new HttpParams()
         .set("vsId", this.vsInfo.runtimeId)
         .set("assemblyName", this.model.absoluteName);
       this.modelService.getModel(modelUri).subscribe((data: TextPropertyDialogModel) => {
         const options = { windowClass: "property-dialog-window",
                           title: this.getTitle(this.model, "_#(js:Properties)"),
                           objectId: this.model.absoluteName, limitResize: false };
         const dialog: TextPropertyDialog = this.showDialog(
            TextPropertyDialog, options,
            (result: TextPropertyDialogModel) => {
               const eventUri: string =
                  "/events/" + TEXT_PROPERTY_URI + this.model.absoluteName;
               this.vsInfo.socketConnection.sendEvent(eventUri, result);
               this.model.absoluteName = result.textGeneralPaneModel.outputGeneralPaneModel
                  .generalPropPaneModel.basicGeneralPaneModel.name;
            });
         dialog.model = data;
         dialog.variableValues =
            VSUtil.getVariableList(this.vsInfo.vsObjects, this.model.absoluteName, true);
         dialog.runtimeId = this.vsInfo.runtimeId;
         dialog.assemblyName = this.model.absoluteName;
         dialog.openToScript = openToScript;
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

      this.modelService.getModel("../api/" + CONDITION_URI, params).subscribe(
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

   private showHighlightDialog(): void {
      const params = new HttpParams()
         .set("runtimeId", this.vsInfo.runtimeId)
         .set("objectId", this.model.absoluteName);

      this.modelService.getModel("../api/" + HIGHLIGHT_URI, params).toPromise().then(
         (data: any) => {
            const options = {
               objectId: this.model.absoluteName,
               windowClass: "property-dialog-window"
            };

            const dialog: HighlightDialog = this.showDialog(
               HighlightDialog, options, (result: HighlightDialogModel) => {
                  const eventUri: string =
                     "/events/" + HIGHLIGHT_URI + "/" + this.model.absoluteName;
                  this.vsInfo.socketConnection.sendEvent(eventUri, result);
               });
            dialog.model = <HighlightDialogModel> data;
            dialog.runtimeId = this.vsInfo.runtimeId;
            dialog.assemblyName = this.model.absoluteName;
            dialog.variableValues =
               VSUtil.getVariableList(this.vsInfo.vsObjects, null);
         },
         (error: any) => {
            //TODO handle error
            console.error("Failed to load highlight model", error);
         }
      );
   }
}
