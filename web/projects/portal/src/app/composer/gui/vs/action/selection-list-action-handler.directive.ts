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
import { SelectionListActions } from "../../../../vsobjects/action/selection-list-actions";
import { ConvertToRangeSliderEvent } from "../../../../vsobjects/event/convert-to-range-slider-event";
import { VSSelectionListModel } from "../../../../vsobjects/model/vs-selection-list-model";
import { VSUtil } from "../../../../vsobjects/util/vs-util";
import { loadingScriptTreeModel, ScriptPaneTreeModel } from "../../../../widget/dialog/script-pane/script-pane-tree-model";
import { ModelService } from "../../../../widget/services/model.service";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { SelectionListPropertyDialogModel } from "../../../data/vs/selection-list-property-dialog-model";
import { Viewsheet } from "../../../data/vs/viewsheet";
import { SelectionListPropertyDialog } from "../../../dialog/vs/selection-list-property-dialog.component";
import { AbstractActionHandler } from "./abstract-action-handler";
import { ContextProvider } from "../../../../vsobjects/context-provider.service";

const SELECTIONLIST_PROPERTY_URI: string = "composer/vs/selection-list-property-dialog-model/";

@Directive({
   selector: "[cSelectionListActionHandler]"
})
export class SelectionListActionHandlerDirective extends AbstractActionHandler implements OnDestroy {
   @Input() model: VSSelectionListModel;
   @Input() vsInfo: Viewsheet;
   private subscription: Subscription;

   @Input()
   set actions(value: SelectionListActions) {
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

   private handleEvent(event: AssemblyActionEvent<VSSelectionListModel>): void {
      switch(event.id) {
      case "selection-list properties":
         this.showPropertyDialog();
         break;
      case "selection-list convert-to-range-slider":
         this.convertToRangeSlider();
         break;
      case "selection-list edit-script":
         this.showPropertyDialog(true);
         break;
      }
   }

   private showPropertyDialog(openToScript: boolean = false): void {
      const modelUri: string = "../api/" + SELECTIONLIST_PROPERTY_URI +
         Tool.encodeURIPath(this.model.absoluteName) + "/" +
         Tool.encodeURIPath(this.vsInfo.runtimeId);
      const scriptUri: string = "../api/vsscriptable/scriptTree";
      const params = new HttpParams()
         .set("vsId", this.vsInfo.runtimeId)
         .set("assemblyName", this.model.absoluteName);
      this.modelService.getModel(modelUri).subscribe((data: SelectionListPropertyDialogModel) => {
         const options = { windowClass: "property-dialog-window",
                           title: this.getTitle(this.model, "_#(js:Properties)"),
                           objectId: this.model.absoluteName, limitResize: false };
         const dialog: SelectionListPropertyDialog = this.showDialog(
            SelectionListPropertyDialog, options,
            (result: SelectionListPropertyDialogModel) => {
               const eventUri: string =
                  "/events/" + SELECTIONLIST_PROPERTY_URI + this.model.absoluteName;
               this.vsInfo.socketConnection.sendEvent(eventUri, result);
               this.model.absoluteName = result.selectionGeneralPaneModel
                  .generalPropPaneModel.basicGeneralPaneModel.name;
            });
         dialog.model = data;
         dialog.model.selectionGeneralPaneModel.generalPropPaneModel
            .basicGeneralPaneModel.containerType = this.model.containerType;
         dialog.variableValues =
            VSUtil.getVariableList(this.vsInfo.vsObjects, this.model.absoluteName);
         dialog.runtimeId = this.vsInfo.runtimeId;
         dialog.openToScript = openToScript;
         dialog.scriptTreeModel = loadingScriptTreeModel;
         this.modelService.getModel(scriptUri, params).subscribe(res => dialog.scriptTreeModel = res);
      });
   }

   private convertToRangeSlider(): void {
      const event: ConvertToRangeSliderEvent =
         new ConvertToRangeSliderEvent(this.model.absoluteName);
      this.vsInfo.socketConnection.sendEvent(
         "/events/composer/viewsheet/selectionList/convertToRangeSlider", event);
   }
}
