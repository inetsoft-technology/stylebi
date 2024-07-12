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
import { Injector, Type } from "@angular/core";
import { forkJoin as observableForkJoin, of as observableOf } from "rxjs";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../../shared/util/tool";
import { AssemblyActionEvent } from "../../../../common/action/assembly-action-event";
import { ViewsheetClientService } from "../../../../common/viewsheet-client";
import { CheckCycleDependencyService } from "../../../../composer/gui/check-cycle-dependency.service";
import { AbstractActionHandler } from "../../../../composer/gui/vs/action/abstract-action-handler";
import { AxisPropertyDialog } from "../../../../graph/dialog/axis-property-dialog.component";
import { LegendFormatDialog } from "../../../../graph/dialog/legend-format-dialog.component";
import { TitleFormatDialog } from "../../../../graph/dialog/title-format-dialog.component";
import { ChartAreaName } from "../../../../graph/model/chart-area-name";
import { ChartRegion } from "../../../../graph/model/chart-region";
import { ChartTool } from "../../../../graph/model/chart-tool";
import { AxisPropertyDialogModel } from "../../../../graph/model/dialog/axis-property-dialog-model";
import { LegendFormatDialogModel } from "../../../../graph/model/dialog/legend-format-dialog-model";
import { TitleFormatDialogModel } from "../../../../graph/model/dialog/title-format-dialog-model";
import { InputNameDialog } from "../../../../widget/dialog/input-name-dialog/input-name-dialog.component";
import { loadingScriptTreeModel, ScriptPaneTreeModel } from "../../../../widget/dialog/script-pane/script-pane-tree-model";
import { ModelService } from "../../../../widget/services/model.service";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { SlideOutOptions } from "../../../../widget/slide-out/slide-out-options";
import { ChartPropertyDialog } from "../../../dialog/graph/chart-property-dialog.component";
import { GroupFieldsEvent } from "../../../event/group-fields-event";
import { ChartPropertyDialogModel } from "../../../model/chart-property-dialog-model";
import { VSChartModel } from "../../../model/vs-chart-model";
import { DataTipService } from "../../data-tip/data-tip.service";
import { ContextProvider } from "../../../context-provider.service";
import { DateComparisonDialog } from "../../../dialog/date-comparison-dialog/date-comparison-dialog.component";
import { DateComparisonDialogModel } from "../../../model/date-comparison-dialog-model";
import { GraphTypes } from "../../../../common/graph-types";

const CHART_PROPERTY_URI: string = "composer/vs/chart-property-dialog-model/";
const AXIS_PROPERTY_URI: string = "composer/vs/axis-property-dialog-model/";
const LEGEND_FORMAT_URI: string = "composer/vs/legend-format-dialog-model/";
const TITLE_FORMAT_URI: string = "composer/vs/title-format-dialog-model/";
const GROUP_URI: string = "composer/viewsheet/groupFields";
const SCRIPT_TREE_URL: string = "../api/vsscriptable/scriptTree";
const DATE_COMPARISON_DIALOG_URI: string = "composer/vs/date-comparison-dialog-model";
const DATE_COMPARISON_CLEAR_URI: string = "composer/vs/date-comparison-dialog-model/clear";

export class VSChartActionHandler extends AbstractActionHandler {
   constructor(private modelService: ModelService,
               private viewsheetClient: ViewsheetClientService,
               protected modalService: DialogService,
               private injector: Injector,
               private viewer: boolean,
               private dataTipService: DataTipService,
               protected context: ContextProvider) {
      super(modalService, context);
   }

   handleEvent(event: AssemblyActionEvent<VSChartModel>, variableValues: string[]): void {
      switch(event.id) {
      case "chart axis-properties":
         this.showAxisPropertiesDialog(event.model);
         break;
      case "chart title-properties":
         this.showTitlePropertiesDialog(event.model, variableValues);
         break;
      case "chart legend-properties":
         this.showLegendPropertiesDialog(event.model, variableValues);
         break;
      case "chart properties":
         this.showPropertyDialog(event.model, variableValues);
         break;
      case "chart edit-script":
         this.showPropertyDialog(event.model, variableValues, true);
         break;
      case "chart group":
         this.showGroupDialog(event.model);
         break;
      case "chart rename":
         this.showRenameDialog(event.model);
         break;
      case "chart ungroup":
         this.ungroup(event.model);
         break;
      case "chart date-comparison":
         this.showDateComparisonDialog(event.model, variableValues);
         break;
      }
   }

   private showAxisPropertiesDialog(model: VSChartModel): void {
      const axisType = this.getAxisType(model);
      const selectedAxisIndex = ChartTool.getSelectedAxisIndex(model);
      const selectedAxisField = ChartTool.getSelectedAxisField(model);
      const modelUri: string = "../api/" + AXIS_PROPERTY_URI +
         Tool.encodeURIPath(model.absoluteName) + "/" +
         Tool.byteEncode(this.viewsheetClient.runtimeId);
      const params = new HttpParams()
         .set("axisType", axisType)
         .set("index", selectedAxisIndex + "")
         .set("field", selectedAxisField);

      const submit = (result: any) => {
         const eventUri: string = "/events/" + AXIS_PROPERTY_URI +
            model.absoluteName + "/" + axisType + "/" + selectedAxisIndex + "/" + selectedAxisField;
         this.viewsheetClient.sendEvent(eventUri, result);
      };

      this.showChartDialog(
         AxisPropertyDialog, modelUri, submit,
         (dialog: AxisPropertyDialog, dlgModel: AxisPropertyDialogModel) => {
            dialog.model = dlgModel;
            dialog.axisType = axisType;
         }, params, model.absoluteName);
   }

   private showTitlePropertiesDialog(model: VSChartModel, variableValues: string[]): void {
      const axisTitleType = this.getAxisTitleType(model);
      const modelUri: string = "../api/" + TITLE_FORMAT_URI + model.absoluteName
         + "/" + Tool.byteEncode(this.viewsheetClient.runtimeId);
      const params = new HttpParams().set("axisType", axisTitleType);
      const submit = (result: any) => {
         const eventUri: string = "/events/" + TITLE_FORMAT_URI + model.absoluteName
            + "/" + axisTitleType;
         this.viewsheetClient.sendEvent(eventUri, result);
      };

      this.showChartDialog(
         TitleFormatDialog, modelUri, submit,
         (dialog: TitleFormatDialog, dlgModel: TitleFormatDialogModel) => {
            dialog.model = dlgModel;
            dialog.variableValues = variableValues;
            dialog.viewer = this.viewer;
            dialog.vsId = this.viewsheetClient.runtimeId;
         }, params, model.absoluteName);
   }

   private showLegendPropertiesDialog(model: VSChartModel, variableValues: string[]): void {
      const selectedLegendIndex = this.getSelectedLegendIndex(model);
      const modelUri: string = "../api/" + LEGEND_FORMAT_URI +
         model.absoluteName + "/" + Tool.byteEncode(this.viewsheetClient.runtimeId);
      const params = new HttpParams().set("index", selectedLegendIndex + "");
      const submit = (result: any) => {
         const eventUri: string = "/events/" + LEGEND_FORMAT_URI + model.absoluteName + "/" +
            selectedLegendIndex;
         this.viewsheetClient.sendEvent(eventUri, result);
      };

      this.showChartDialog(
         LegendFormatDialog, modelUri, submit,
         (dialog: LegendFormatDialog, dlgModel: LegendFormatDialogModel) => {
            dialog.model = dlgModel;
            dialog.variableValues = variableValues;
            dialog.vsId = this.viewsheetClient.runtimeId;
         }, params, model.absoluteName);
   }

   private showPropertyDialog(model: VSChartModel, variableValues: string[],
                              openToScript: boolean = false): void
   {
      const modelUri: string = "../api/" + CHART_PROPERTY_URI +
         Tool.encodeURIPath(model.absoluteName) + "/" +
         Tool.byteEncode(this.viewsheetClient.runtimeId);
      const checkCycleDependency = new CheckCycleDependencyService(
         this.modelService, this.viewsheetClient.runtimeId, model.absoluteName);
      const modalInjector = Injector.create(
         {providers: [
            {
               provide: CheckCycleDependencyService,
               useValue: checkCycleDependency
            }],
            parent: this.injector
         });

      const params = new HttpParams()
         .set("vsId", this.viewsheetClient.runtimeId)
         .set("assemblyName", model.absoluteName);
      this.modelService.getModel(modelUri).subscribe((data: ChartPropertyDialogModel) => {
         const options = { windowClass: "property-dialog-window", injector: modalInjector,
                           title: this.getTitle(model, "_#(js:Properties)"),
                           objectId: model.absoluteName, limitResize: false};
         const dialog: ChartPropertyDialog = this.showDialog(
            ChartPropertyDialog, options,
            (result: ChartPropertyDialogModel) => {
               const result0: ChartPropertyDialogModel = Tool.clone(result);
               // clear delete info and keep index in sync
               result.chartAdvancedPaneModel.chartTargetLinesPaneModel.deletedIndexList = [];
               result.chartAdvancedPaneModel.chartTargetLinesPaneModel.chartTargets
                  .forEach((v, idx) => v.index = idx);
               const eventUri: string = "/events/" + CHART_PROPERTY_URI + model.absoluteName;
               this.viewsheetClient.sendEvent(eventUri, result0);
               model.absoluteName = result0.chartGeneralPaneModel.generalPropPaneModel
                  .basicGeneralPaneModel.name;
            });
         dialog.model = data;
         dialog.scriptTreeModel = loadingScriptTreeModel;
         dialog.variableValues = variableValues;
         dialog.openToScript = openToScript;
         dialog.runtimeId = this.viewsheetClient.runtimeId;
         dialog.assemblyName = model.absoluteName;
         dialog.viewer = this.viewer || model.inEmbeddedViewsheet;
         dialog.chartType = model.chartType;
         this.modelService.getModel(SCRIPT_TREE_URL, params).subscribe(res => {
            dialog.scriptTreeModel = res;
         });
      });
   }

   private showGroupDialog(model: VSChartModel): void {
      this.showInputNameDialog(model, false);
   }

   private showRenameDialog(model: VSChartModel): void {
      this.showInputNameDialog(model, true);
   }

   private showInputNameDialog(model: VSChartModel, rename: boolean): void {
      let dialogName: string;
      const labels: string[] = [];
      const allLabels: string[] = [];
      let prevName: string = null;
      const columnName: string = ChartTool.getDim(model, model.chartSelection.regions[0]);

      if(model.chartSelection) {
         model.chartSelection.regions.forEach((region) => {
            let label: string = ChartTool.getVal(model, region);

            if(region.grouped) {
               prevName = label;
            }

            labels.push(label);
         });

         model.chartSelection.chartObject.regions.forEach((region) => {
            let label: string = ChartTool.getVal(model, region);

            if(labels.indexOf(label) == -1) {
               allLabels.push(label);
            }
         });
      }

      if(rename) {
         dialogName = ChartTool.getVal(model, model.chartSelection.regions[0]);
      }
      else if(prevName) {
         dialogName = prevName;
      }

      const dialog: InputNameDialog = this.showDialog(
         InputNameDialog, { popup: true }, (result: string) => {
            const event: GroupFieldsEvent = new GroupFieldsEvent(
               model.absoluteName, -1, -1, columnName, labels, result);
            event.legend = ChartTool.isLegendSelected(model);
            event.axis = ChartTool.isAxisSelected(model);

            if(rename) {
               event.prevGroupName = prevName;
            }

            model.chartSelection.regions = [];
            this.dataTipService.unfreeze();
            this.viewsheetClient.sendEvent("/events/" + GROUP_URI, event);
         });
      dialog.title = "_#(js:Group Name)";
      dialog.label = "_#(js:Group Name)";
      dialog.value = dialogName;
      dialog.validators = [
         FormValidators.required,
         //Validators.pattern(/^(([a-zA-Z])[\w ]*)$/)
      ];
      dialog.validatorMessages = [
         {validatorName: "required", message: "_#(js:vs.group.nameRequired)"},
         {validatorName: "pattern", message: "_#(js:vs.group.nameValid)"},
      ];
      dialog.hasDuplicateCheck = (value: string) => {
         return observableOf(allLabels.indexOf(value.trim()) != -1);
      };
   }

   private ungroup(model: VSChartModel): void {
      let labels: string[] = [];
      const columnName: string = ChartTool.getDim(model, model.chartSelection.regions[0]);

      if(model.chartSelection && model.chartSelection.regions
         && model.chartSelection.regions.length > 0)
      {
         let regions: ChartRegion[] = model.chartSelection.regions;
         labels.push(ChartTool.getVal(model, regions[0]));
      }

      const event: GroupFieldsEvent =
         new GroupFieldsEvent(model.absoluteName, -1, -1, columnName, labels, null);
      event.legend = ChartTool.isLegendSelected(model);

      this.viewsheetClient.sendEvent("/events/" + GROUP_URI, event);
   }

   private getSelectedLegendIndex(model: VSChartModel): number {
      return model && ChartTool.getCurrentLegendIndex(model.chartSelection, model.legends);
   }

   private getAxisType(model: VSChartModel): string {
      const getType = (name: ChartAreaName) => {
         if(ChartTool.getSelectedRegions(model.chartSelection, name).length == 0) {
            return null;
         }
         else {
            if(name == "bottom_x_axis" || name == "top_x_axis" ||
               name == "left_y_axis" || name == "right_y_axis")
            {
               return name;
            }
         }

         return null;
      };

      return getType("bottom_x_axis") || getType("top_x_axis") ||
         getType("left_y_axis") || getType("right_y_axis") || "_Parallel_Label_";
   }

   protected getAxisTitleType(model: VSChartModel): string {
      if(model.chartSelection.chartObject.areaName.indexOf("x2") == 0) {
         return "x2";
      }
      else if(model.chartSelection.chartObject.areaName.indexOf("x") == 0) {
         return "x";
      }
      else if(model.chartSelection.chartObject.areaName.indexOf("y2") == 0) {
         return "y2";
      }
      else if(model.chartSelection.chartObject.areaName.indexOf("y") == 0) {
         return "y";
      }

      return null;
   }

   showChartDialog<D, M>(dialogType: Type<D>, modelUri: string,
                         submit: (event: any) => any, bind: (dialog: D, model: M) => any,
                         params: HttpParams = null, absoluteName: string,
                         cls: string = "", options?: SlideOutOptions): void
   {
      let windowClass = cls ? cls : "property-dialog-window chart-area-dialog";

      if(!options) {
         options = {
            windowClass: windowClass,
            objectId: absoluteName
         };
      }

      this.modelService.getModel(modelUri, params).toPromise().then(
         (data: any) => {
            const dialog: D = this.showDialog(dialogType, options, (result: M) => {
               submit(result);
            });
            bind(dialog, <M> data);
         },
         (error: any) => {
            //TODO handle error
            console.error("Failed to get plot property model: ", error);
         }
      );
   }

   private showDateComparisonDialog(model: VSChartModel, variableValues: string[]): void {
      const modelUri = "../api/" + DATE_COMPARISON_DIALOG_URI + "/" +
         Tool.encodeURIPath(model.absoluteName) + "/" +
         Tool.byteEncode(this.viewsheetClient.runtimeId);
      const params = new HttpParams()
          .set("vsId", this.viewsheetClient.runtimeId)
          .set("assemblyName", model.absoluteName);

      this.modelService.getModel(modelUri).subscribe((data: DateComparisonDialogModel) => {
         const options = {
            windowClass: "property-dialog-window",
            objectId: model.absoluteName
         };
         const dialog: DateComparisonDialog = this.showDialog(
            DateComparisonDialog, options,
            (result: DateComparisonDialogModel) => {
               const result0: DateComparisonDialogModel = Tool.clone(result);
               const eventUri: string = "/events/" + DATE_COMPARISON_DIALOG_URI + "/" +
                  model.absoluteName;
               this.viewsheetClient.sendEvent(eventUri, result0);
            });
         dialog.onClear.asObservable().subscribe(() => {
            const eventUri: string = "/events/" + DATE_COMPARISON_CLEAR_URI + "/" +
               model.absoluteName;
            this.viewsheetClient.sendEvent(eventUri);
            dialog.close();
         });
         dialog.dateComparisonDialogModel = data;
         dialog.scriptTreeModel = loadingScriptTreeModel;
         dialog.variableValues = variableValues;
         dialog.runtimeId = this.viewsheetClient.runtimeId;
         dialog.assemblyName = model.absoluteName;
         dialog.assemblyType = "VSChart";
         this.modelService.getModel(SCRIPT_TREE_URL, params).subscribe(res => {
            dialog.scriptTreeModel = res;
         });
      });
   }
}
