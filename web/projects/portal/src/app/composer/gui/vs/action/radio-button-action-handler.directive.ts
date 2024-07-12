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
import { RadioButtonActions } from "../../../../vsobjects/action/radio-button-actions";
import { VSRadioButtonModel } from "../../../../vsobjects/model/vs-radio-button-model";
import { VSUtil } from "../../../../vsobjects/util/vs-util";
import { loadingScriptTreeModel, ScriptPaneTreeModel } from "../../../../widget/dialog/script-pane/script-pane-tree-model";
import { ModelService } from "../../../../widget/services/model.service";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { RadioButtonPropertyDialogModel } from "../../../data/vs/radiobutton-property-dialog-model";
import { Viewsheet } from "../../../data/vs/viewsheet";
import { RadioButtonPropertyDialog } from "../../../dialog/vs/radiobutton-property-dialog.component";
import { AbstractActionHandler } from "./abstract-action-handler";
import { ContextProvider } from "../../../../vsobjects/context-provider.service";

const RADIOBUTTON_PROPERTY_URI: string = "composer/vs/radiobutton-property-dialog-model/";

@Directive({
   selector: "[cRadioButtonActionHandler]"
})
export class RadioButtonActionHandlerDirective extends AbstractActionHandler implements OnDestroy {
   @Input() model: VSRadioButtonModel;
   @Input() vsInfo: Viewsheet;
   private subscription: Subscription;

   @Input()
   set actions(value: RadioButtonActions) {
      this.unsubscribe();

      if(value) {
         this.subscription = value.onAssemblyActionEvent.subscribe(
            (event) => this.handleEvent(event));
      }
   }

   @Output() onOpenFormatPane = new EventEmitter<VSRadioButtonModel>();

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

   private handleEvent(event: AssemblyActionEvent<VSRadioButtonModel>): void {
      switch(event.id) {
      case "radio-button properties":
         this.showPropertyDialog();
         break;
      case "radio-button edit-script":
         this.showPropertyDialog(true);
         break;
      case "radio-button show-format-pane":
         this.onOpenFormatPane.emit(this.model);
         break;
      }
   }

   private showPropertyDialog(openToScript: boolean = false): void {
      const modelUri: string = "../api/" + RADIOBUTTON_PROPERTY_URI +
         Tool.encodeURIPath(this.model.absoluteName) + "/" +
         Tool.encodeURIPath(this.vsInfo.runtimeId);
      const scriptUri: string = "../api/vsscriptable/scriptTree";
      const params = new HttpParams()
         .set("vsId", this.vsInfo.runtimeId)
         .set("assemblyName", this.model.absoluteName);
      this.modelService.getModel(modelUri).subscribe((data: RadioButtonPropertyDialogModel) => {
         const options = { windowClass: "property-dialog-window",
                           title: this.getTitle(this.model, "_#(js:Properties)"),
                           objectId: this.model.absoluteName, limitResize: false };
         const dialog: RadioButtonPropertyDialog = this.showDialog(
            RadioButtonPropertyDialog, options,
            (result: RadioButtonPropertyDialogModel) => {
               const eventUri: string =
                  "/events/" + RADIOBUTTON_PROPERTY_URI + this.model.absoluteName;
               this.vsInfo.socketConnection.sendEvent(eventUri, result);
               this.model.absoluteName = result.radioButtonGeneralPaneModel.generalPropPaneModel
                  .basicGeneralPaneModel.name;
            });
         dialog.model = data;
         dialog.variableValues =
            VSUtil.getVariableList(this.vsInfo.vsObjects, this.model.absoluteName);
         dialog.runtimeId = this.vsInfo.runtimeId;
         dialog.assemblyName = this.model.absoluteName;
         dialog.openToScript = openToScript;
         dialog.scriptTreeModel = loadingScriptTreeModel;
         this.modelService.getModel(scriptUri, params).subscribe(res => dialog.scriptTreeModel = res);
      });
   }
}
