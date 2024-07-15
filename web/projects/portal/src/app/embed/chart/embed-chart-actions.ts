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
import { VSChartModel } from "../../vsobjects/model/vs-chart-model";
import { ChartActions } from "../../vsobjects/action/chart-actions";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { AssemblyAction } from "../../common/action/assembly-action";
import { PopComponentService } from "../../vsobjects/objects/data-tip/pop-component.service";
import { ContextProvider } from "../../vsobjects/context-provider.service";
import { ActionStateProvider } from "../../vsobjects/action/action-state-provider";
import { DataTipService } from "../../vsobjects/objects/data-tip/data-tip.service";
import { MiniToolbarService } from "../../vsobjects/objects/mini-toolbar/mini-toolbar.service";
import { GraphTypes } from "../../common/graph-types";

export class EmbedChartActions extends ChartActions {
   constructor(model: VSChartModel, popService: PopComponentService,
               contextProvider: ContextProvider, securityEnabled: boolean,
               stateProvider: ActionStateProvider, dataTipService: DataTipService,
               miniToolbarService: MiniToolbarService)
   {
      super(model, popService, contextProvider, securityEnabled, stateProvider,
         dataTipService, miniToolbarService);
   }

   protected createMenuActions(groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      groups.push(new AssemblyActionGroup([
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
            id: () => "chart MenuAction HelperText",
            label: () => "_#(js:composer.vs.action.helperText.menuAction.chart)",
            icon: () => "edit-icon",
            enabled: () => false,
            visible: () => true,
            classes: () => "helper-text"
         }
      ]));

      return groups;
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
            id: () => "chart refresh",
            label: () => "_#(js:Refresh)",
            icon: () => "refresh-icon",
            enabled: () => true,
            visible: () => true
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
      ]));

      groups.push(new AssemblyActionGroup([
         {
            id: () => "menu actions",
            label: () => "_#(js:More)",
            icon: () => "menu-horizontal-icon",
            enabled: () => true,
            visible: () => !this.vsWizardPreview && !this.mobileDevice
               && this.isActionVisibleInViewer("Menu Actions")
               && this.menuActions.some((g) => g.actions.some((action) => action.visible()))
         }]));

      return groups;
   }

   protected createClickAction(): AssemblyAction {
      return super.createClickAction();
   }
}
