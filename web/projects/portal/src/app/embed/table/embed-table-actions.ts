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
import { VSTableModel } from "../../vsobjects/model/vs-table-model";
import { TableActions } from "../../vsobjects/action/table-actions";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { PopComponentService } from "../../vsobjects/objects/data-tip/pop-component.service";
import { ContextProvider } from "../../vsobjects/context-provider.service";
import { ActionStateProvider } from "../../vsobjects/action/action-state-provider";
import { DataTipService } from "../../vsobjects/objects/data-tip/data-tip.service";
import { MiniToolbarService } from "../../vsobjects/objects/mini-toolbar/mini-toolbar.service";

export class EmbedTableActions extends TableActions {
   constructor(model: VSTableModel, contextProvider: ContextProvider,
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
    * Menu-only commands. This feeds the mini toolbar's "More" button (abstract-vs-actions.ts hands
    * it menuActions as childAction) and the right-click menu, so the rule for what belongs here is
    * that it is NOT already a toolbar button: show-details and export used to be listed here as
    * well as on the toolbar, which made "More" repeat its neighbours (Bug #75951), while Set Cell
    * Size exists only here (Bug #75961). The override still has to exist regardless: what it
    * replaces, TableActions.createMenuActions, is composer/annotation commands the embed cannot
    * use.
    *
    * Everything below is selection-gated, so on an untouched table nothing is visible - "More"
    * self-hides and EmbedContextMenu.open suppresses the empty popup.
    */
   protected createMenuActions(groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      // Unlike crosstab, TableActions has no Hide Column/Show Columns or drill-hierarchy concept
      // (no row/column grouping on a plain table), so Set Cell Size is the only item to add here -
      // it's the only one that exists on both.
      groups.push(new AssemblyActionGroup([
         {
            // Same visibility rule as TableActions' own "table cell size" - oneCellSelected is
            // protected on BaseTableActions already, nothing to widen.
            id: () => "table cell size",
            label: () => "_#(js:Set Cell Size)",
            icon: () => "place-holder-icon icon-edit",
            enabled: () => true,
            visible: () => this.oneCellSelected && this.isActionVisibleInViewer("Set Cell Size")
         },
      ]));

      // TableActions' "MenuAction HelperText" entry is deliberately NOT carried over. Its
      // visibility is menuActionHelperTextVisible, which is `!embed || embed &&
      // !annotationsSelected` (abstract-vs-actions.ts) - inside an embed that is true whenever no
      // annotation is selected, i.e. essentially always. Keeping it would make the group above
      // permanently non-empty in AssemblyActionGroup.anyVisible's eyes, so both the "More" button
      // and EmbedContextMenu.open's empty-menu guard would treat an untouched table as having
      // something to show, and Bug #75951's empty popup would come straight back - now with a
      // single greyed-out hint in it. The hint is a composer affordance anyway: it tells the
      // author their selection has no menu commands, which is not something to say to the
      // embedding page's end users.

      return groups;
   }

   protected createToolbarActions(groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      groups.push(new AssemblyActionGroup([
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
            // way (e.g. clicking its own backdrop) - see EmbedTableComponent's wizMaximized input.
            id: () => "table wiz-fullscreen",
            label: () => this.isWizMaximized() ? "_#(js:Show Actual Size)" : "_#(js:Show Enlarged)",
            icon: () => this.isWizMaximized() ? "contract-icon" : "expand-icon",
            enabled: () => true,
            visible: () => !this.isDataTip() && !this.isPopComponent(),
            action: () => this.onWizFullscreenToggle()
         },
         {
            id: () => "table show-details",
            label: () => "_#(js:Show Details)",
            icon: () => "show-detail-icon",
            enabled: () => true,
            visible: () => this.detailCellsSelected &&
               this.isActionVisibleInViewer("Show Details")
         },
         {
            id: () => "table export",
            label: () => "_#(js:Export)",
            icon: () => "export-icon",
            visible: () => this.isActionVisible("Export"),
            enabled: () => true
         },
         {
            id: () => "table multi-select",
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
