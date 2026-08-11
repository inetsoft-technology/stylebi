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
               miniToolbarService: MiniToolbarService,
               private isWizMaximized: () => boolean,
               private onWizFullscreenToggle: () => void)
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
      // ChartActions' "MenuAction HelperText" entry is deliberately not carried over, for the same
      // reason EmbedTableActions and EmbedCrosstabActions drop theirs: its visibility is
      // menuActionHelperTextVisible, which is `!embed || embed && !annotationsSelected`
      // (abstract-vs-actions.ts), so inside an embed it is true whenever no annotation is selected -
      // permanently, in practice. A greyed-out hint that the current selection has no commands is a
      // composer affordance, not something to show an embedding page's end users, and while it is
      // here AssemblyActionGroup.anyVisible can never report this menu as empty.
      //
      // Unlike table and crosstab, that does NOT leave the chart menu selection-gated throughout:
      // "chart save-image-as" above is visible on `!binding && !composer`, both always false in an
      // embed, so it stands on isActionVisibleInViewer("Save Image As") alone. That is a real
      // menu-only command - the chart toolbar carries only the fullscreen toggle, refresh,
      // multi-select and "More" - so a right click on plain plot area legitimately still opens a
      // menu holding it. EmbedContextMenu.open's empty-menu guard therefore engages for the chart
      // only where the server hides Save Image As.

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
            // Deliberately independent of this.model.maxMode / openMaxMode()/closeMaxMode(): in
            // the embed context CoreLifecycleService.applyEmbedChartSize() always sets the
            // assembly's maxSize to whatever pixel size the embed container was given, so
            // model.maxMode is true from the very first load - it does not mean "the user asked
            // to enlarge this," it just means "render at the size the embed container gave you."
            // Reusing the generic open-max-mode/close-max-mode pair (as this project briefly did)
            // showed the wrong icon on load and did nothing useful when clicked. This action
            // instead has its own inline `action` callback (addActionHandler() only installs its
            // id-based default when `action` is unset - see assembly-actions.ts), so clicking it
            // never touches model.maxMode or the server at all. isWizMaximized()/
            // onWizFullscreenToggle() are backed by a plain component-level flag (not model state,
            // which gets replaced wholesale on every server refresh) so the icon/label correctly
            // toggle, and stay correct even when the embedding page closes fullscreen some other
            // way (e.g. clicking its own backdrop) - see EmbedChartComponent's wizMaximized input.
            id: () => "chart wiz-fullscreen",
            label: () => this.isWizMaximized() ? "_#(js:Show Actual Size)" : "_#(js:Show Enlarged)",
            icon: () => this.isWizMaximized() ? "contract-icon" : "expand-icon",
            enabled: () => true,
            visible: () => !this.isDataTip() && !this.isPopComponent(),
            action: () => this.onWizFullscreenToggle()
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
               && AssemblyActionGroup.anyVisible(this.menuActions)
         }]));

      return groups;
   }

   protected createClickAction(): AssemblyAction {
      return super.createClickAction();
   }
}
