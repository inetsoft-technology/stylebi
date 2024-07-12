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
import { Tool } from "../../../../../shared/util/tool";
import { SourceInfoType } from "../../binding/data/source-info-type";
import { AssemblyAction } from "../../common/action/assembly-action";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { XSchema } from "../../common/data/xschema";
import { GraphTypes } from "../../common/graph-types";
import { DrillLevel } from "../../composer/data/vs/drill-level";
import { Axis } from "../../graph/model/axis";
import { ChartRegion } from "../../graph/model/chart-region";
import { ChartTool } from "../../graph/model/chart-tool";
import { Legend } from "../../graph/model/legend";
import { ContextProvider } from "../context-provider.service";
import { VSChartModel } from "../model/vs-chart-model";
import { DataTipService } from "../objects/data-tip/data-tip.service";
import { PopComponentService } from "../objects/data-tip/pop-component.service";
import { AbstractVSActions } from "./abstract-vs-actions";
import { ActionStateProvider } from "./action-state-provider";
import { AnnotatableActions } from "./annotatable-actions";
import { Plot } from "../../graph/model/plot";
import { MiniToolbarService } from "../objects/mini-toolbar/mini-toolbar.service";

export class ChartActions extends AbstractVSActions<VSChartModel> implements AnnotatableActions {
   constructor(model: VSChartModel,
               popService: PopComponentService,
               contextProvider: ContextProvider,
               securityEnabled: boolean = false,
               protected stateProvider: ActionStateProvider = null,
               dataTipService: DataTipService = null,
               miniToolbarService: MiniToolbarService = null)
   {
      super(model, contextProvider, securityEnabled, stateProvider,
            dataTipService, popService, miniToolbarService);
   }

   protected createMenuActions(groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      groups.push(new AssemblyActionGroup([
         {
            id: () => "chart axis-properties",
            label: () => "_#(js:Axis Properties)...",
            icon: () => "setting-icon",
            enabled: () => true,
            visible: () => this.axisSelected && !this.isPopComponent() && !this.mobileDevice &&
               this.isActionVisibleInViewer("Axis Properties") && !this.annotationsSelected
         },
         {
            id: () => "chart legend-properties",
            label: () => "_#(js:Legend Properties)...",
            icon: () => "fa fa-slider",
            enabled: () => true,
            visible: () => this.legendSelected && !this.isPopComponent() && !this.mobileDevice &&
               this.isActionVisibleInViewer("Legend Properties") && !this.annotationsSelected
         },
         {
            id: () => "chart properties",
            label: () => "_#(js:Properties)...",
            icon: () => "fa fa-slider",
            enabled: () => true,
            visible: () => this.isActionVisibleInViewer("Properties") && !this.annotationsSelected
               && !this.isPopComponent() && !this.mobileDevice
         },
         {
            id: () => "chart title-properties",
            label: () => "_#(js:Title Properties)...",
            icon: () => "fa fa-sliders",
            enabled: () => true,
            visible: () => this.titleSelected && !this.isPopComponent() && !this.mobileDevice &&
               this.isActionVisibleInViewer("Title Properties") && !this.annotationsSelected
         },
         {
            id: () => "chart show-format-pane",
            label: () => "_#(js:Format)...",
            icon: () => "fa fa-format",
            enabled: () => true,
            visible: () => !this.preview && !this.isPopComponent() && !this.mobileDevice &&
               this.isActionVisibleInViewer("Format") && !this.annotationsSelected &&
               !this.showFormatPaneDisabled()
         },
         {
            id: () => "chart hide-title",
            label: () => "_#(js:Hide Title)",
            icon: () => "eye-off-icon",
            enabled: () => true,
            visible: () => (this.titleSelected || this.model.titleSelected) && !this.isPopComponent() && !this.mobileDevice &&
               this.isActionVisibleInViewer("Hide Title") && !this.annotationsSelected
         },
         {
            id: () => "chart show-title",
            label: () => "_#(js:Show Chart Title)",
            icon: () => "eye-off-icon",
            enabled: () => true,
            visible: () => !this.model.titleVisible && !this.model.titleHidden && !this.isPopComponent() && !this.mobileDevice &&
               this.isActionVisibleInViewer("Show Title") && !this.annotationsSelected
         },
         {
            id: () => "chart hide-axis",
            label: () => "_#(js:Hide Axis)",
            icon: () => "eye-off-icon",
            enabled: () => true,
            visible: () => this.axisSelected && !this.isPopComponent() && !this.mobileDevice &&
               this.isActionVisibleInViewer("Hide Axis") && !this.annotationsSelected &&
               this.isNotSelectedPeriod
         },
         {
            id: () => "chart hide-legend",
            label: () => "_#(js:Hide Legend)",
            icon: () => "eye-off-icon",
            enabled: () => true,
            visible: () => this.legendSelected && !this.isPopComponent() && !this.mobileDevice &&
               this.isActionVisibleInViewer("Hide Legend") && !this.annotationsSelected
         },
         {
            id: () => "chart save-image-as",
            label: () => "_#(js:Save as Image)",
            icon: () => "save-icon",
            enabled: () => true,
            visible: () => !this.binding && !this.composer &&
               this.isActionVisibleInViewer("Save Image As") && !this.annotationsSelected
         },
         {
            id: () => "chart resize-plot",
            label: () => "_#(js:Resize Plot)",
            icon: () => "plus-icon",
            enabled: () => true,
            visible: () => !this.model.showPlotResizers && !this.isPopComponent() &&
               this.plotResizable && !this.annotationsSelected &&
               this.isActionVisible("Resize Plot")
         },
         {
            id: () => "chart reset-size",
            label: () => "_#(js:Reset Size)",
            icon: () => "reset-icon",
            enabled: () => true,
            visible: () => !this.isDataTip() && this.model.resized &&
               this.plotResizable && !this.isPopComponent() &&
               (!GraphTypes.isPolar(this.model.chartType) ||
                this.model.facets && this.model.facets.length > 0) &&
               this.isActionVisible("Reset Size")
         },
      ]));
      groups.push(new AssemblyActionGroup([
         {
            id: () => "chart axis-hyperlink",
            label: () => this.axisHyperlinkLabel,
            icon: () => "fa fa-link",
            enabled: () => true,
            visible: () => this.axisHyperlinkVisible &&
               this.isActionVisibleInViewer(this.axisHyperlinkLabel) && !this.annotationsSelected
         },
         {
            id: () => "chart plot-hyperlink",
            label: () => this.plotHyperlinkLabel,
            icon: () => "fa fa-link",
            enabled: () => true,
            visible: () => this.chartHyperlinkVisible()
         },
         {
            id: () => "chart highlight",
            label: () => this.highlightLabel,
            icon: () => "fa fa-filter",
            enabled: () => true,
            visible: () => this.chartHighlightVisible()
         },
         {
            id: () => "chart clear-zoom",
            label: () => "_#(js:View All Data)",
            icon: () => "eye-plus-icon",
            enabled: () => true,
            visible: () => this.model.zoomed && this.isActionVisibleInViewer("Clear Zoom")
               && !this.isPopComponent()
         },
         {
            id: () => "chart clear-brush",
            label: () => "_#(js:Clear Brushing)",
            icon: () => "brush-no-icon",
            enabled: () => true,
            visible: () => this.model.brushed && this.isActionVisibleInViewer("Clear Brush") &&
               !this.isPopComponent()
         }
      ]));
      groups.push(new AssemblyActionGroup([
         {
            id: () => "chart conditions",
            label: () => "_#(js:Conditions)...",
            icon: () => "fa fa-filter",
            enabled: () => true,
            visible: () => this.composer && !this.annotationsSelected && !this.isPopComponent() &&
               (!this.model.cubeType || this.model.worksheetCube)
         },
         {
            id: () => "chart filter",
            label: () => "_#(js:Filter)",
            icon: () => "fa fa-filter",
            enabled: () => this.filterEnabled,
            visible: () => this.filterVisible && !this.isPopComponent() &&
               this.isActionVisibleInViewer("Filter") && !this.annotationsSelected
         },
         {
            id: () => "chart date-comparison",
            label: () => "_#(js:Date Comparison)...",
            icon: () => "fa fa-filter",
            enabled: () => true,
            visible: () => this.dateComparisonVisible() && !this.annotationsSelected &&
               !this.isPopComponent() && this.isActionVisibleInViewer("Date Comparison")
         },
      ]));

      groups.push(new AssemblyActionGroup([
         {
            id: () => "chart group",
            label: () => "_#(js:Group Items)...",
            icon: () => "fa fa-object-group",
            enabled: () => true,
            visible: () => this.groupFieldsVisible && !this.isPopComponent() &&
               !this.mobileDevice &&
               this.isActionVisibleInViewer("Group Items") && !this.annotationsSelected
         },
         {
            id: () => "chart rename",
            label: () => "_#(js:Rename Group)...",
            icon: () => "fa fa-pencil-square-o",
            enabled: () => true,
            visible: () => this.ungroupFieldsVisible && !this.isPopComponent() &&
               !this.mobileDevice &&
               this.isActionVisibleInViewer("Rename Group") && !this.annotationsSelected
         },
         {
            id: () => "chart ungroup",
            label: () => "_#(js:Ungroup Items)",
            icon: () => "fa fa-object-ungroup",
            enabled: () => true,
            visible: () => this.ungroupFieldsVisible && !this.isPopComponent() &&
               !this.mobileDevice &&
               this.isActionVisibleInViewer("Ungroup Items") && !this.annotationsSelected
         }
      ]));
      groups.push(new AssemblyActionGroup([
         {
            id: () => "chart show-titles",
            label: () => "_#(js:Show All Titles)",
            icon: () => "eye-icon",
            enabled: () => true,
            visible: () => this.model.titleHidden && !this.isPopComponent() &&
               this.isActionVisibleInViewer("Show All Titles") && !this.annotationsSelected
         },
         {
            id: () => "chart show-axes",
            label: () => "_#(js:Show All Axes)",
            icon: () => "eye-icon",
            enabled: () => true,
            visible: () => this.model.axisHidden && !this.isPopComponent() &&
               this.isActionVisibleInViewer("Show All Axes") && !this.annotationsSelected
         },
         {
            id: () => "chart show-legends",
            label: () => "_#(js:Show Legend)",
            icon: () => "eye-icon",
            enabled: () => true,
            visible: () => this.model.legendHidden && !this.isPopComponent() &&
               this.isActionVisibleInViewer("Show Legend") && !this.annotationsSelected
         }
      ]));
      groups.push(new AssemblyActionGroup([
         {
            id: () => "chart annotate",
            label: () => this.plotPointSelected ? "_#(js:Annotate Point)" : "_#(js:Annotate Component)",
            icon: () => "edit-icon",
            enabled: () => this.securityEnabled,
            visible: () => !this.annotationsSelected && (this.viewer || this.preview) &&
               this.securityEnabled && !this.model.maxMode && !this.isPopComponent() &&
               this.model.enabled && !this.mobileDevice && this.model.chartType != GraphTypes.CHART_SCATTER_CONTOUR &&
               this.isActionVisibleInViewer(this.plotPointSelected ? "Annotate Point" : "Annotate Component") &&
               (!this.stateProvider || this.stateProvider.isActionVisible("Annotation", this.model))
         }
      ]));
      groups.push(this.createDefaultEditMenuActions());
      groups.push(this.createDefaultOrderMenuActions());
      groups.push(this.createDefaultAnnotationMenuActions());

      groups.push(new AssemblyActionGroup([
         {
            id: () => "chart MenuAction HelperText",
            label: () => "_#(js:composer.vs.action.helperText.menuAction.chart)",
            icon: () => "edit-icon",
            enabled: () => false,
            visible: () => true,
            classes: () => "helper-text"
         }
      ]));

      return super.createMenuActions(groups);
   }

   private chartHyperlinkVisible(): boolean {
      let plotMeasure = this.model?.chartSelection?.regions?.some(r =>
         ChartTool.getMea(this.model, r) != null && ChartTool.areaType(this.model, r) == "axis");

      if(GraphTypes.isRadar(this.model.chartType) && plotMeasure) {
         return false;
      }

      return !this.annotationsSelected && this.composer &&
         this.model.plotHighlightEnabled &&
         (this.model.mapInfo && ChartTool.isPlotSelected(this.model) ||
            ChartTool.isPlotMeasureSelected(this.model, true));
   }

   private chartHighlightVisible(): boolean {
      const textSelected = this.model?.chartSelection?.regions &&
         this.model.chartSelection.regions.some(r => ChartTool.areaType(this.model, r) == "text");
      return !this.annotationsSelected && this.composer && ChartTool.isHighlightable(this.model);
   }

   protected createToolbarActions(groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      groups.push(new AssemblyActionGroup([
         {
            id: () => "chart drill-down",
            label: () => "_#(js:Drill Down Filter)",
            icon: () => "drill-down-filter-icon",
            enabled: () => true,
            visible: () => this.drillDownVisible() && this.isActionVisibleInViewer("Drill Down Filter")
               && !this.isDataTip() && !this.isPopComponent()
         },
         {
            id: () => "chart drill-up",
            label: () => "_#(js:Drill Up Filter)",
            icon: () => "drill-up-filter-icon",
            enabled: () => true,
            visible: () => this.drillUpVisible() && this.isActionVisibleInViewer("Drill Up Filter")
               && !this.isDataTip() && !this.isPopComponent()
         },
         {
            id: () => "chart brush",
            label: () => "_#(js:Brush Chart)",
            icon: () => "brush-icon",
            enabled: () => true,
            visible: () => this.brushable && this.isActionVisibleInViewer("Brush") &&
               !this.isDataTip() && !this.isPopComponent() && this.isNotSelectedPeriod
         },
         {
            id: () => "chart clear-brush",
            label: () => "_#(js:Clear Brush)",
            icon: () => "brush-no-icon",
            enabled: () => true,
            visible: () => this.model.brushed && this.isActionVisibleInViewer("Clear Brush")
               && !this.isDataTip() && !this.isPopComponent()
         },
         {
            id: () => "chart zoom",
            label: () => "_#(js:Zoom Chart)",
            icon: () => "zoom-in-icon",
            enabled: () => true,
            visible: () => this.brushable && this.isActionVisibleInViewer("Zoom Chart") &&
               !this.isDataTip() && !this.isPopComponent() && this.isNotSelectedPeriod
         },
         {
            id: () => "chart clear-zoom",
            label: () => "_#(js:Clear Zoom)",
            icon: () => "zoom-no-icon",
            enabled: () => true,
            visible: () => this.model.zoomed && this.isActionVisibleInViewer("Clear Zoom") &&
               !this.isDataTip() && !this.isPopComponent()
         },
         {
            id: () => "chart exclude-data",
            label: () => "_#(js:Exclude Data)",
            icon: () => "eye-off-icon",
            enabled: () => true,
            visible: () => this.brushable && this.isActionVisibleInViewer("Exclude Data") &&
               !this.isDataTip() && !this.isPopComponent() && this.isNotSelectedPeriod
         },
         {
            id: () => "chart show-data",
            label: () => "_#(js:Show Summary Data)",
            icon: () => "show-summary-icon",
            enabled: () => true,
            visible: () => this.isActionVisibleInViewer("Show Data") &&
               this.isActionVisibleInViewer("Show Summary Data")
         },
         {
            id: () => "chart show-details",
            label: () => "_#(js:Show Details)",
            icon: () => "show-detail-icon",
            enabled: () => true,
            visible: () => this.dataAreaSelected && !this.model.changedByScript &&
               this.isActionVisibleInViewer("Show Details") && this.isNotSelectedPeriod
         },
         {
            id: () => "chart open-max-mode",
            label: () => "_#(js:Show Enlarged)",
            icon: () => "expand-icon",
            enabled: () => true,
            visible: () => !this.model.maxMode && !this.vsWizardPreview &&
               (this.binding || this.isActionVisibleInViewer("Open Max Mode")
                && this.isActionVisibleInViewer("Maximize") && !this.isDataTip() &&
                !this.isPopComponent()) && this.isActionVisibleInViewer("Show Enlarged")
         },
         {
            id: () => "chart close-max-mode",
            label: () => "_#(js:Show Actual Size)",
            icon: () => "contract-icon",
            enabled: () => true,
            visible: () => this.model.maxMode && !this.vsWizardPreview &&
               (this.binding || this.model.maxMode &&
                this.isActionVisibleInViewer("Close Max Mode") && !this.isDataTip() &&
                !this.isPopComponent()) && this.isActionVisibleInViewer("Show Actual Size")
         },
         {
            id: () => "chart manual-refresh",
            label: () => "_#(js:Enable Auto Refresh)",
            icon: () => "shape-filled-circle-icon auto-refresh-false",
            enabled: () => true,
            visible: () => this.binding && this.manualVisible &&
               this.isActionVisibleInViewer("Enable Auto Refresh") && !this.isDataTip()
         },
         {
            id: () => "chart auto-refresh",
            label: () => "_#(js:Enable Manual Refresh)",
            icon: () => "shape-filled-circle-icon auto-refresh-true",
            enabled: () => true,
            visible: () => this.binding && this.autoVisible &&
               this.isActionVisibleInViewer("Enable Manual Refresh")
         },
         {
            id: () => "chart refresh",
            label: () => "_#(js:Refresh)",
            icon: () => "refresh-icon",
            enabled: () => true,
            visible: () => this.binding && this.manualVisible &&
               this.isActionVisibleInViewer("Refresh") && !this.isDataTip() &&
               !this.isPopComponent()
         },
         {
            id: () => "chart multi-select",
            label: () => this.model.multiSelect ? "_#(js:Change to Single-select)"
               : "_#(js:Change to Multi-select)",
            icon: () => this.model.multiSelect ? "select-multi-icon" : "select-single-icon",
            enabled: () => true,
            visible: () => this.mobileDevice &&
               this.isActionVisibleInViewer("Change to Single-select") &&
               this.isActionVisibleInViewer("Change to Multi-select")
         },
         {
            id: () => "chart edit",
            label: () => "_#(js:Edit)",
            icon: () => "edit-icon",
            enabled: () => true,
            visible: () => !this.vsWizardPreview && !this.binding &&
               (this.viewer && this.model.enableAdhoc && !this.mobileDevice &&
               this.isActionVisibleInViewer("Edit") && !this.isDataTip() && !this.isPopComponent()
               || this.composer && !this.annotationsSelected && !this.isPopComponent())
         }
      ]));
      return super.createToolbarActions(groups, true);
   }

   protected drillDownVisible(): boolean {
      if(this.vsWizardPreview || this.binding || this.model.changedByScript ||
         this.model.dateComparisonDefined)
      {
         return false;
      }

      for(let level of this.getDrillLevels()) {
         if(level != DrillLevel.None) {
            return true;
         }
      }

      return false;
   }

   protected drillUpVisible(): boolean {
      if(this.vsWizardPreview || this.binding || this.model.changedByScript ||
         this.model.dateComparisonDefined)
      {
         return false;
      }

      // if drill down is not allowed (e.g. column is dynamic), it means drill-down happend
      // from other assembly and drilling is not supported on this chart.
      if(this.hasDrillFilter() && this.drillDownVisible()) {
         return true;
      }

      for(let level of this.getDrillLevels()) {
         if(level == DrillLevel.Leaf || level == DrillLevel.Middle) {
            return true;
         }
      }

      return false;
   }

   private hasDrillFilter(): boolean {
      if(!this.model.chartSelection || Tool.isEmpty(this.model.chartSelection.regions)) {
         return false;
      }

      let chartSelection = this.model.chartSelection;
      let areaName = chartSelection.chartObject ? chartSelection.chartObject.areaName : null;
      let selectObject = chartSelection.chartObject;

      if(areaName == "plot_area" && this.drillFilterPlotSupport()) {
         return (<Plot> selectObject).drillFilter;
      }
      else if(areaName === "bottom_x_axis" ||
              areaName === "top_x_axis" ||
              areaName === "left_y_axis" ||
              areaName === "right_y_axis")
      {
         return (<Axis> selectObject).drillFilter;
      }
      else if(areaName === "legend_content") {
         return (<Legend> selectObject).drillFilter;
      }

      return false;
   }

   private getDrillLevels(): DrillLevel[] {
      if(!this.model || this.model.sourceType == SourceInfoType.VS_ASSEMBLY
         || !this.model.chartSelection
         || Tool.isEmpty(this.model.chartSelection.regions))
      {
         return [];
      }

      let selectObject = this.model.chartSelection.chartObject;
      let areaName = selectObject ? selectObject.areaName : null;

      if(!this.drillFilterPlotSupport()) {
         return [];
      }

      if(ChartTool.isPlotAreaSelected(this.model)) {
         return this.model.plot.drillLevels;
      }
      else if(areaName === "bottom_x_axis" ||
              areaName === "top_x_axis" ||
              areaName === "left_y_axis" ||
              areaName === "right_y_axis")
      {

         let columnName = ChartTool.getSelectedAxisColumnName(this.model);
         let axis: Axis = (<Axis> selectObject);
         let index: number = axis.axisFields.indexOf(columnName);

         return index != -1 ? [axis.drillLevels[index]] : [];
      }
      else if(areaName === "legend_content") {
         return (selectObject as Legend).drillLevels;
      }

      return [];
   }

   private drillFilterPlotSupport(): boolean {
      return !GraphTypes.isHistogram(this.model);
   }

   protected createClickAction(): AssemblyAction {
      if(!this.viewer && !this.preview) {
         return null;
      }

      return {
         id: () => "chart show-hyperlink",
         label: () => "_#(js:Show Hyperlinks)",
         icon: () => "fa fa-link",
         enabled: () => true,
         visible: () => !!this.model.hyperlinks && (this.viewer || this.preview) &&
            this.isActionVisibleInViewer("Show Hyperlinks")
      };
   }

   protected getEditScriptActionId(): string {
      return "chart edit-script";
   }

   protected get groupFieldsVisible(): boolean {
      if(this.model.cubeType != null) {
         return false;
      }

      return !!this.model.chartSelection && !!this.model.chartSelection.regions
         && this.model.chartSelection.regions.length > 1
         && ChartTool.dimIdx(this.model, this.model.chartSelection.regions[0]) > -1
         && ChartTool.dataType(this.model, this.model.chartSelection.regions[0]) != XSchema.DATE
         && ChartTool.dataType(this.model, this.model.chartSelection.regions[0]) != XSchema.TIME
         && ChartTool.dataType(this.model, this.model.chartSelection.regions[0]) != XSchema.TIME_INSTANT
         && ChartTool.dataType(this.model, this.model.chartSelection.regions[0]) != XSchema.BOOLEAN
         && !this.model.chartSelection.regions[0].isAggr;
   }

   protected get ungroupFieldsVisible(): boolean {
      if(this.model.chartSelection && this.model.chartSelection.regions) {
         let group: ChartRegion = this.model.chartSelection.regions.find((column) => {
            return column.grouped;
         });

         return !!group;
      }

      return false;
   }

   private get selectedAxisRegions(): ChartRegion[] {
      return ChartTool.getSelectedAxisRegions(this.model);
   }

   protected get legendSelected(): boolean {
      return ChartTool.legendSelected(this.model);
   }

   private get legendContentSelected(): boolean {
      return ChartTool.getSelectedRegions(this.model.chartSelection, "legend_content").length > 0;
   }

   protected get axisSelected(): boolean {
      return ChartTool.axisSelected(this.model);
   }

   protected get titleSelected(): boolean {
      return ChartTool.titleSelected(this.model);
   }

   private get plotSelected(): boolean {
      return ChartTool.plotSelected(this.model);
   }

   private get plotAreaSelected(): boolean {
      return ChartTool.isPlotAreaSelected(this.model);
   }

   private get plotPointSelected(): boolean {
      return ChartTool.isPlotPointSelected(this.model);
   }

   private get axisHyperlinkLabel(): string {
      let label: string = "_#(js:Hyperlink)";
      const regions = this.selectedAxisRegions;

      if(regions && regions.length > 0) {
         label += "-" + ChartTool.getDim(this.model, regions[0]) + "...";
      }

      return label;
   }

   private get axisHyperlinkVisible(): boolean {
      if(!this.composer) {
         return false;
      }

      if(!this.axisSelected) {
         return false;
      }

      if(ChartTool.isScatterPlotAxisSelected(this.model)) {
         return false;
      }

      const regions = this.selectedAxisRegions;

      // top x axis of mekko chart
      if(ChartTool.isGanttMeasureSelected(this.model) || GraphTypes.isMekko(this.model.chartType) &&
         this.model.chartSelection.chartObject.areaName == "top_x_axis")
      {
         return false;
      }

      if(regions && regions.length > 0) {
         return !!ChartTool.getDim(this.model, regions[0]);
      }

      return false;
   }

   private get plotHyperlinkLabel(): string {
      let label: string = "_#(js:Hyperlink)";

      if(this.model.mapInfo) {
         return label + "...";
      }

      const regions = ChartTool.getSelectedRegions(
         this.model.chartSelection, "plot_area");

      if(regions && regions.length > 0) {
         let measure = ChartTool.getMea(this.model, regions[0]);

         if(!measure) {
            measure = ChartTool.getDim(this.model, regions[0]);
         }

         label = this.getLabel(label, measure);
      }
      // @by changhongyang 2017-10-16, some charts have no selectable regions in plot_area.
      // Or no region is selected when right clicking the chart plot_area
      // Therefore, get the measure name from the plot_area region if available
      // Otherwise, get the measure name from the main y-axis
      else {
         label = this.getLabel(label, ChartTool.getFirstAvailableMeasure(this.model));
      }

      return label;
   }

   private get highlightLabel(): string {
      let label: string = "_#(js:Highlight)";

      if(this.model.mapInfo && !ChartTool.isAxisHighlightVisible(this.model)) {
         return label + "...";
      }

      const regions = ChartTool.getSelectedRegions(this.model.chartSelection, "plot_area");

      if(regions && regions.length > 0) {
         let measure = ChartTool.getMea(this.model, regions[0]);

         if(!measure) {
            measure = ChartTool.getDim(this.model, regions[0]);
         }

         label = this.getLabel(label, measure);
      }
      else if(this.model.chartSelection.chartObject.areaName === "plot_area") {
         for(let region of this.model.chartSelection.chartObject.regions) {
            if(ChartTool.hasMeasure(this.model, region)) {
               label = this.getLabel(label, ChartTool.getMea(this.model, region));
               break;
            }
         }
      }
      else if(ChartTool.isAxisHighlightVisible(this.model)) {
         label = this.getLabel(label, ChartTool.getSelectedAxisField(this.model));
      }

      return label;
   }

   private getLabel(prefix: string, measure: string): string {
      // strip off waterfall all-header prefix
      if(measure && measure.startsWith("__text__")) {
         measure = measure.substring(8);
      }

      if(!measure) {
         return prefix + "...";
      }

      return prefix + "-" + measure + "...";
   }

   private get filterEnabled(): boolean {
      if(this.composer) {
         if(!this.model.chartSelection || !this.model.chartSelection.regions ||
            this.model.chartSelection.regions.length < 1 ||
            (ChartTool.dimIdx(this.model, this.model.chartSelection.regions[0]) == -1 &&
             ChartTool.meaIdx(this.model, this.model.chartSelection.regions[0]) == -1))
         {
            return false;
         }
      }
      return true;
   }

   private get filterVisible(): boolean {
      if(this.binding || this.model.maxMode || !this.model.chartSelection ||
         !this.model.chartSelection.regions ||
         (!this.composer || this.model.scatterMatrix) && !this.model.adhocFilterEnabled ||
         ChartTool.isRegionAreaTypePresent(this.model, this.model.chartSelection, "text") &&
         !GraphTypes.isRelation(this.model.chartType) ||
         this.model.sourceType === SourceInfoType.VS_ASSEMBLY)
      {
         return false;
      }

      if(this.model.dateComparisonEnabled && this.model.appliedDateComparison &&
         this.model.customPeriod && (this.selectedCustomDcRangeRef || this.selectedCustomDcMergeRef))
      {
         return false;
      }

      if((!this.model.plotHighlightEnabled || GraphTypes.isBoxplot(this.model.chartType)) &&
         ChartTool.isRegionAreaTypePresent(this.model, this.model.chartSelection, "vo"))
      {
         return false;
      }

      if(this.model.cubeType != null && !this.model.worksheetCube &&
         this.plotAreaSelected && ChartTool.isPlotMeasureSelected(this.model, true) &&
         !GraphTypes.isTreemap(this.model.chartType) &&
         !GraphTypes.isRelation(this.model.chartType))
      {
         return false;
      }

      if(this.model.cubeType != null && this.model.chartType == GraphTypes.CHART_MEKKO &&
         this.model.chartSelection.regions[0].isAggr)
      {
         return false;
      }

      return (this.axisSelected || this.legendContentSelected)
         && ChartTool.dimIdx(this.model, this.model.chartSelection.regions[0]) > -1
         && !ChartTool.hasMeasure(this.model, this.model.chartSelection.regions[0])
         && !this.titleSelected
         || this.plotAreaSelected && ChartTool.isPlotMeasureSelected(this.model, true)
         || ChartTool.isRegionAreaTypePresent(this.model, this.model.chartSelection, "vo")
            && !this.model.maxMode
            && this.model.plotHighlightEnabled
         && ChartTool.meaIdx(this.model, this.model.chartSelection.regions[0]) > -1
            && this.isAxisDefined();
   }

   private isAxisDefined(): boolean {
      const colName: string = ChartTool.getMea(this.model, this.model.chartSelection.regions[0]);
      return this.model.axes.map(a => a.axisFields)
         .some(axisFields => axisFields.some(af => af == colName));
   }

   private get selectedCustomDcRangeRef(): boolean {
      return this.model?.chartSelection?.chartObject?.containsCustomDcRangeRef;
   }

   private get selectedCustomDcMergeRef(): boolean {
      return this.model?.chartSelection?.chartObject?.containsCustomDcMergeRef;
   }

   protected get brushable(): boolean {
      return ChartTool.isBrushable(this.model) && this.isMekkoSupported();
   }

   protected get autoVisible(): boolean {
      return !this.model.notAuto;
   }

   protected get manualVisible(): boolean {
      return this.model.notAuto;
   }

   protected get dataAreaSelected(): boolean {
      if(!this.isMekkoSupported()) {
         return false;
      }

      const regions = ChartTool.getSelectedRegions(this.model.chartSelection);

      if(regions.length == 0) {
         return false;
      }

      if(this.model.cubeType == null &&
         (this.model.chartSelection.chartObject.areaName == "legend_content" ||
         this.model.chartSelection.chartObject.areaName.indexOf("_axis") >= 0))
      {
         return regions.some(r => ChartTool.dimIdx(this.model, r) >= 0);
      }

      if(this.model.chartSelection.chartObject.areaName != "plot_area") {
         return false;
      }

      // ignore axis label in plot (radar labels)
      return regions.some(r => r.rowIdx >= 0 &&
         (ChartTool.areaType(this.model, r) != "axis"
            || this.model.plotHighlightEnabled));
   }

   protected get plotResizable(): boolean {
      return this.model.verticallyResizable || this.model.horizontallyResizable;
   }

   private isMekkoSupported(): boolean {
      return !GraphTypes.isMekko(this.model.chartType) || !!!this.model.chartSelection
            || !!!this.model.chartSelection.chartObject
         ?  true
         : this.model.chartSelection.chartObject.areaName != "top_x_axis";
   }

   protected get isNotSelectedPeriod(): boolean {
      if(!this.model) {
         return false;
      }

      let regions = ChartTool.getSelectedAxisRegions(this.model);

      if(!regions || regions.length == 0) {
         return true;
      }

      return regions.every((region) => !region.period);
   }

   private dateComparisonVisible(): boolean {
      return !this.mobileDevice && this.model && this.model.dateComparisonEnabled;
   }

   private showFormatPaneDisabled(): boolean {
      return this.model.chartSelection && this.model.chartSelection.chartObject &&
         this.model.chartSelection.regions.length > 0 && ChartTool.isNonEditableVOSelected(this.model);
   }
}
