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
import { Directive, EventEmitter, Input, OnDestroy, Output } from "@angular/core";
import { forkJoin as observableForkJoin, Subscription } from "rxjs";
import { Tool } from "../../../../../../../shared/util/tool";
import { AssemblyActionEvent } from "../../../../common/action/assembly-action-event";
import { RangeSliderActions } from "../../../../vsobjects/action/range-slider-actions";
import { RangeSliderPropertyDialog } from "../../../../vsobjects/dialog/range-slider-property-dialog.component";
import { ConvertToSelectionListEvent } from "../../../../vsobjects/event/convert-to-selection-list-event";
import { RangeSliderPropertyDialogModel } from "../../../../vsobjects/model/range-slider-property-dialog-model";
import { VSRangeSliderModel } from "../../../../vsobjects/model/vs-range-slider-model";
import { VSUtil } from "../../../../vsobjects/util/vs-util";
import { loadingScriptTreeModel, ScriptPaneTreeModel } from "../../../../widget/dialog/script-pane/script-pane-tree-model";
import { ModelService } from "../../../../widget/services/model.service";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { Viewsheet } from "../../../data/vs/viewsheet";
import { AbstractActionHandler } from "./abstract-action-handler";
import { ContextProvider } from "../../../../vsobjects/context-provider.service";

const RANGESLIDER_PROPERTY_URI: string = "composer/vs/range-slider-property-dialog-model/";

@Directive({
   selector: "[cRangeSliderActionHandler]"
})
export class RangeSliderActionHandlerDirective extends AbstractActionHandler implements OnDestroy {
   @Input() model: VSRangeSliderModel;
   @Input() vsInfo: Viewsheet;
   private subscription: Subscription;

   @Input()
   set actions(value: RangeSliderActions) {
      this.unsubscribe();

      if(value) {
         this.subscription = value.onAssemblyActionEvent.subscribe(
            (event) => this.handleEvent(event));
      }
   }

   @Output() onOpenFormatPane = new EventEmitter<VSRangeSliderModel>();

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

   private handleEvent(event: AssemblyActionEvent<VSRangeSliderModel>): void {
      switch(event.id) {
      case "range-slider properties":
         this.showPropertyDialog();
         break;
      case "range-slider convert-to-selection-list":
         this.convertToSelectionList();
         break;
      case "range-slider edit-script":
         this.showPropertyDialog(true);
         break;
      case "range-slider show-format-pane":
         this.onOpenFormatPane.emit(this.model);
         break;
      }
   }

   private showPropertyDialog(openToScript: boolean = false): void {
      const modelUri: string = "../api/" + RANGESLIDER_PROPERTY_URI +
         Tool.encodeURIPath(this.model.absoluteName) + "/" +
         Tool.encodeURIPath(this.vsInfo.runtimeId);
      const scriptUri: string = "../api/vsscriptable/scriptTree";
      const params = new HttpParams()
         .set("vsId", this.vsInfo.runtimeId)
         .set("assemblyName", this.model.absoluteName);
      this.modelService.getModel(modelUri).subscribe((data: RangeSliderPropertyDialogModel) => {
         const options = { windowClass: "property-dialog-window",
                           title: this.getTitle(this.model, "_#(js:Properties)"),
                           objectId: this.model.absoluteName, limitResize: false };
         const dialog: RangeSliderPropertyDialog = this.showDialog(
            RangeSliderPropertyDialog, options,
            (result: RangeSliderPropertyDialogModel) => {
               const eventUri: string =
                  "/events/" + RANGESLIDER_PROPERTY_URI + this.model.absoluteName;
               this.vsInfo.socketConnection.sendEvent(eventUri, result);
               this.model.absoluteName = result.rangeSliderGeneralPaneModel
                  .generalPropPaneModel.basicGeneralPaneModel.name;
            });
         dialog.model = data;
         dialog.model.rangeSliderGeneralPaneModel.generalPropPaneModel
            .basicGeneralPaneModel.containerType = this.model.containerType;
         dialog.variableValues =
            VSUtil.getVariableList(this.vsInfo.vsObjects, this.model.absoluteName);
         dialog.openToScript = openToScript;
         dialog.runtimeId = this.vsInfo.runtimeId;
         dialog.assemblyName = this.model.absoluteName;
         dialog.scriptTreeModel = loadingScriptTreeModel;
         this.modelService.getModel(scriptUri, params).subscribe(res => dialog.scriptTreeModel = res);
      });
   }

   private convertToSelectionList(): void {
      const event: ConvertToSelectionListEvent =
         new ConvertToSelectionListEvent(this.model.absoluteName);
      this.vsInfo.socketConnection.sendEvent(
         "/events/composer/viewsheet/rangeSlider/convertToSelectionList", event);
   }
}
