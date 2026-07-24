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

   protected createMenuActions(groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      groups.push(new AssemblyActionGroup([
         {
            id: () => "crosstab show-details",
            label: () => "_#(js:Show Details)",
            icon: () => "show-detail-icon",
            enabled: () => true,
            visible: () => this.isActionVisibleInViewer("Show Details") && !this.annotationsSelected
         },
         {
            id: () => "crosstab export",
            label: () => "_#(js:Export)",
            icon: () => "export-icon",
            enabled: () => true,
            visible: () => this.isActionVisible("Export") && !this.annotationsSelected
         },
      ]));
      groups.push(new AssemblyActionGroup([
         {
            id: () => "crosstab MenuAction HelperText",
            label: () => "_#(js:composer.vs.action.helperText.menuAction.table)",
            icon: () => "edit-icon",
            enabled: () => false,
            visible: () => this.menuActionHelperTextVisible,
            classes: () => "helper-text"
         }
      ]));

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
            visible: () => this.isActionVisibleInViewer("Show Details")
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
               && this.menuActions.some((g) => g.actions.some((action) => action.visible()))
         }]));

      return groups;
   }
}
