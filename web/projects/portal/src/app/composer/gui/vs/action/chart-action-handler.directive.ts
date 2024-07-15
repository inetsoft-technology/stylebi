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
import { Directive, Injector, Input, OnDestroy } from "@angular/core";
import { Subscription } from "rxjs";
import { AssemblyActionEvent } from "../../../../common/action/assembly-action-event";
import { VSConditionDialogModel } from "../../../../common/data/condition/vs-condition-dialog-model";
import { ViewsheetClientService } from "../../../../common/viewsheet-client";
import { ChartTool } from "../../../../graph/model/chart-tool";
import { ChartActions } from "../../../../vsobjects/action/chart-actions";
import { HyperlinkDialog } from "../../../../vsobjects/dialog/graph/hyperlink-dialog.component";
import { HighlightDialog } from "../../../../vsobjects/dialog/highlight-dialog.component";
import { VSConditionDialog } from "../../../../vsobjects/dialog/vs-condition-dialog.component";
import { HyperlinkDialogModel } from "../../../../vsobjects/model/hyperlink-dialog-model";
import { VSChartModel } from "../../../../vsobjects/model/vs-chart-model";
import { VSChartActionHandler } from "../../../../vsobjects/objects/chart/services/vs-chart-action-handler";
import { DataTipService } from "../../../../vsobjects/objects/data-tip/data-tip.service";
import { VSUtil } from "../../../../vsobjects/util/vs-util";
import { HighlightDialogModel } from "../../../../widget/highlight/highlight-dialog-model";
import { ModelService } from "../../../../widget/services/model.service";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { Viewsheet } from "../../../data/vs/viewsheet";
import { AbstractActionHandler } from "./abstract-action-handler";
import { SlideOutOptions } from "../../../../widget/slide-out/slide-out-options";
import { ContextProvider } from "../../../../vsobjects/context-provider.service";

const CONDITION_URI: string = "composer/vs/vs-condition-dialog-model";
const HIGHLIGHT_URI: string = "composer/vs/highlight-dialog-model";
const HYPERLINK_URI: string = "composer/vs/hyperlink-dialog-model";

@Directive({
   selector: "[cChartActionHandler]"
})
export class ChartActionHandlerDirective extends AbstractActionHandler implements OnDestroy {
   @Input() model: VSChartModel;
   private _viewsheet: Viewsheet;
   private subscription: Subscription;
   private bindingActionHandler: VSChartActionHandler;

   @Input()
   set actions(value: ChartActions) {
      this.unsubscribe();

      if(value) {
         this.subscription = value.onAssemblyActionEvent.subscribe(
            (event) => this.handleEvent(event));
      }
   }

   @Input()
   set vsInfo(value: Viewsheet) {
      this.bindingActionHandler = null;
      this._viewsheet = value;

      if(value) {
         this.bindingActionHandler = new VSChartActionHandler(
            this.modelService, value.socketConnection, this.modalService, this.injector,
            false, this.dataTipService, this.context);
      }
   }

   constructor(private modelService: ModelService, modalService: DialogService,
               private viewsheetClient: ViewsheetClientService,
               private injector: Injector, private dataTipService: DataTipService,
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

   private handleEvent(event: AssemblyActionEvent<VSChartModel>): void {
      switch(event.id) {
      case "chart axis-hyperlink":
         this.showHyperlinkDialog();
         break;
      case "chart plot-hyperlink":
         this.showHyperlinkDialog();
         break;
      case "chart highlight":
         this.showHighlightDialog();
         break;
      case "chart conditions":
         this.showConditionDialog();
         break;
      }
   }

   private showHyperlinkDialog(): void {
      let params = new HttpParams()
         .set("runtimeId", this._viewsheet.runtimeId)
         .set("objectId", this.model.absoluteName);
      const submit = (result: any) => {
         const eventUri: string = "/events/" + HYPERLINK_URI + "/" + this.model.absoluteName;
         this.viewsheetClient.sendEvent(eventUri, result);
      };

      let colName: string = null;
      let regions = ChartTool.getSelectedRegions(this.model.chartSelection, "plot_area");

      if(regions.length > 0) {
         colName = ChartTool.getMea(this.model, regions[0]);

         if(!colName) {
            colName = ChartTool.getDim(this.model, regions[0]);
         }
      }
      else {
         regions = ChartTool.getSelectedAxisRegions(this.model);

         if(regions) {
            colName = ChartTool.getDim(this.model, regions[0]);
         }
         else {
            colName = ChartTool.getFirstAvailableMeasure(this.model);
         }

         params.append("isAxis", "true");
      }

      if(colName) {
         params = params.append("colName", colName);
      }

      this.bindingActionHandler.showChartDialog(
         HyperlinkDialog, "../api/" + HYPERLINK_URI, submit,
         (dialog: HyperlinkDialog, model: HyperlinkDialogModel) => {
            dialog.model = model;
            dialog.runtimeId = this._viewsheet.runtimeId;
            dialog.objectName = this.model.absoluteName;
         }, params, this.model.absoluteName);
   }

   private showHighlightDialog(): void {
      let params = new HttpParams()
         .set("runtimeId", this._viewsheet.runtimeId)
         .set("objectId", this.model.absoluteName);
      const regions = ChartTool.getSelectedRegions(this.model.chartSelection, "plot_area");
      const submit = (result: any) => {
         const eventUri: string = "/events/" + HIGHLIGHT_URI + "/" + this.model.absoluteName;
         this.viewsheetClient.sendEvent(eventUri, result);
      };

      if(regions.length > 0) {
         let colName: string = ChartTool.getMea(this.model, regions[0]);

         if(!colName) {
            colName = ChartTool.getDim(this.model, regions[0]);
         }

         if(colName) {
            params = params.set("colName", colName);
         }

         params = params.set("isAxis", (ChartTool.areaType(this.model, regions[0]) == "axis") + "");
         params = params.set("isText", (ChartTool.areaType(this.model, regions[0]) == "text") + "");
      }
      else if(this.model.chartSelection.chartObject.areaName === "plot_area") {
         for(let region of this.model.chartSelection.chartObject.regions) {
            if(ChartTool.hasMeasure(this.model, region)) {
               const colName: string = ChartTool.getMea(this.model, region);
               params = params.set("colName", colName);
               break;
            }
         }
      }
      else {
         params = params.set("colName", ChartTool.getSelectedAxisField(this.model));
         params = params.append("isAxis", "true");
      }

      this.bindingActionHandler.showChartDialog(
         HighlightDialog, "../api/" + HIGHLIGHT_URI, submit,
         (dialog: HighlightDialog, model: HighlightDialogModel) => {
            dialog.model = model;
            dialog.runtimeId = this._viewsheet.runtimeId;
            dialog.assemblyName = this.model.absoluteName;
            dialog.variableValues =
               VSUtil.getVariableList(this._viewsheet.vsObjects, this.model.absoluteName);
         }, params, this.model.absoluteName);
   }

   private showConditionDialog(): void {
      const params = new HttpParams()
         .set("runtimeId", this._viewsheet.runtimeId)
         .set("assemblyName", this.model.absoluteName);

      const options: SlideOutOptions = {
         windowClass: "condition-dialog",
         objectId: this.model.absoluteName,
         limitResize: false
      };
      const submit = (result: any) => {
         const eventUri: string = "/events/" + CONDITION_URI + "/" + this.model.absoluteName;
         this.viewsheetClient.sendEvent(eventUri, result);
      };

      this.bindingActionHandler.showChartDialog(
         VSConditionDialog, "../api/" + CONDITION_URI, submit,
         (dialog: VSConditionDialog, model: VSConditionDialogModel) => {
            dialog.model = model;
            dialog.runtimeId = this._viewsheet.runtimeId;
            dialog.assemblyName = this.model.absoluteName;
            dialog.variableValues =
               VSUtil.getVariableList(this._viewsheet.vsObjects, this.model.absoluteName);
         }, params, this.model.absoluteName, "condition-dialog", options);
   }
}
