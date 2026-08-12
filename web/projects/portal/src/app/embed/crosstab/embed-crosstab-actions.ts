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
import { VSCrosstabModel } from "../../vsobjects/model/vs-crosstab-model";
import { CrosstabActions } from "../../vsobjects/action/crosstab-actions";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { PopComponentService } from "../../vsobjects/objects/data-tip/pop-component.service";
import { ContextProvider } from "../../vsobjects/context-provider.service";
import { ActionStateProvider } from "../../vsobjects/action/action-state-provider";
import { DataTipService } from "../../vsobjects/objects/data-tip/data-tip.service";
import { MiniToolbarService } from "../../vsobjects/objects/mini-toolbar/mini-toolbar.service";

export class EmbedCrosstabActions extends CrosstabActions {
   constructor(model: VSCrosstabModel, contextProvider: ContextProvider,
               securityEnabled: boolean, stateProvider: ActionStateProvider,
               dataTipService: DataTipService, popService: PopComponentService,
               miniToolbarService: MiniToolbarService,
               private isWizMaximized: () => boolean,
               private onWizFullscreenToggle: () => void)
   {
      super(model, contextProvider, securityEnabled, stateProvider,
         dataTipService, popService, miniToolbarService);
   }

   /**
    * Menu-only commands, on the same rule as EmbedTableActions.createMenuActions: an entry belongs
    * here only if it is not already a toolbar button. Show Details and Export were listed in both
    * places and so duplicated themselves under "More" (Bug #75951); Set Cell Size, Hide Column /
    * Show Columns and the drill-hierarchy pair exist only here (Bug #75961). The override stays
    * because what it replaces, CrosstabActions.createMenuActions, is composer/annotation commands
    * the embed cannot use.
    *
    * Every entry is selection-gated, so an untouched crosstab still shows nothing - "More"
    * self-hides and EmbedContextMenu.open suppresses the empty popup.
    */
   protected createMenuActions(groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      groups.push(new AssemblyActionGroup([
         {
            // Same visibility rule as CrosstabActions' own "table cell size" - oneCellSelected
            // is protected on BaseTableActions already, nothing to widen.
            id: () => "table cell size",
            label: () => "_#(js:Set Cell Size)",
            icon: () => "place-holder-icon icon-edit",
            enabled: () => true,
            visible: () => this.oneCellSelected && this.isActionVisibleInViewer("Set Cell Size")
         },
      ]));
      groups.push(new AssemblyActionGroup([
         {
            id: () => "crosstab hide column",
            label: () => "_#(js:Hide Column)",
            icon: () => "place-holder-icon icon-hyperlink",
            enabled: () => true,
            visible: () => !this.annotationsSelected &&
               !this.model.titleSelected && !this.model.metadata && this.cellSelected &&
               this.isActionVisibleInViewer("Hide Column")
         },
         {
            id: () => "crosstab show columns",
            label: () => "_#(js:Show Columns)",
            icon: () => "place-holder-icon icon-highlight",
            enabled: () => true,
            visible: () => !this.annotationsSelected && this.model.hasHiddenColumn &&
               !this.model.metadata
         }
      ]));
      groups.push(new AssemblyActionGroup([
         {
            // getDrillLabel()/getDrillContextMenuVisible() are CrosstabActions' own drill-hierarchy
            // logic (protected for this reuse, same precedent as detailCellsSelected) - the
            // criterion is whether the selected field has a drill level defined (drillOp), not
            // whether it looks like a date field.
            id: () => "expand all",
            label: () => this.getDrillLabel(),
            icon: () => "place-holder-icon",
            enabled: () => true,
            visible: () => this.getDrillContextMenuVisible()
         },
         {
            id: () => "collapse all",
            label: () => this.getDrillLabel(false),
            icon: () => "place-holder-icon",
            enabled: () => true,
            visible: () => this.getDrillContextMenuVisible(false, true)
         },
         {
            id: () => "expand field",
            label: () => this.getDrillLabel(true, true),
            icon: () => "place-holder-icon",
            enabled: () => true,
            visible: () => this.getDrillContextMenuVisible(true)
         },
         {
            id: () => "collapse field",
            label: () => this.getDrillLabel(false, true),
            icon: () => "place-holder-icon",
            enabled: () => true,
            visible: () => this.getDrillContextMenuVisible(true)
         }
      ]));

      // The "MenuAction HelperText" entry is deliberately not carried over, for the reason spelled
      // out in EmbedTableActions.createMenuActions: menuActionHelperTextVisible is true throughout
      // an embed, so keeping it would defeat both the "More" button's self-hiding and
      // EmbedContextMenu.open's empty-menu guard.

      return groups;
   }

   protected createToolbarActions(groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      groups.push(new AssemblyActionGroup([
         {
            // Copied verbatim from CrosstabActions.createToolbarActions - id/label/icon/enabled/
            // visible unchanged. Click handling (drillAction()) already lives in the shared
            // vs-crosstab.component.ts switch on the action id, and the resulting DrillEvent
            // travels the same viewsheetClient/STOMP path the regular Viewer uses, so restoring
            // these two entries is enough to make drill-down/up work in the embed too - nothing
            // else needs to change.
            id: () => "crosstab drilldown",
            label: () => "_#(js:Drill Down Filter)",
            icon: () => "drill-down-filter-icon",
            enabled: () => true,
            visible: () => this.drillActionVisible() && this.isActionVisibleInViewer("Drill Down Filter")
               && !this.isDataTip() && !this.isPopComponent()
         },
         {
            id: () => "crosstab drillup",
            label: () => "_#(js:Drill Up Filter)",
            icon: () => "drill-up-filter-icon",
            enabled: () => true,
            visible: () => this.drillActionVisible(true) && this.isActionVisibleInViewer("Drill Up Filter")
               && !this.isDataTip() && !this.isPopComponent()
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
            // way (e.g. clicking its own backdrop) - see EmbedCrosstabComponent's wizMaximized
            // input.
            id: () => "crosstab wiz-fullscreen",
            label: () => this.isWizMaximized() ? "_#(js:Show Actual Size)" : "_#(js:Show Enlarged)",
            icon: () => this.isWizMaximized() ? "contract-icon" : "expand-icon",
            enabled: () => true,
            visible: () => !this.isDataTip() && !this.isPopComponent(),
            action: () => this.onWizFullscreenToggle()
         },
         {
            id: () => "crosstab show-details",
            label: () => "_#(js:Show Details)",
            icon: () => "show-detail-icon",
            enabled: () => true,
            visible: () => this.detailCellsSelected &&
               this.isActionVisibleInViewer("Show Details")
         },
         {
            id: () => "crosstab export",
            label: () => "_#(js:Export)",
            icon: () => "export-icon",
            visible: () => this.isActionVisible("Export"),
            enabled: () => true
         },
         {
            id: () => "crosstab multi-select",
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
            visible: () => !this.mobileDevice
               && this.isActionVisibleInViewer("Menu Actions")
               && AssemblyActionGroup.anyVisible(this.menuActions)
         }]));

      return groups;
   }
}
