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
               miniToolbarService: MiniToolbarService)
   {
      super(model, contextProvider, securityEnabled, stateProvider,
         dataTipService, popService, miniToolbarService);
   }

   protected createMenuActions(groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      groups.push(new AssemblyActionGroup([
         {
            id: () => "table show-details",
            label: () => "_#(js:Show Details)",
            icon: () => "show-detail-icon",
            enabled: () => true,
            visible: () => this.isActionVisibleInViewer("Show Details") && !this.annotationsSelected
         },
         {
            id: () => "table export",
            label: () => "_#(js:Export)",
            icon: () => "export-icon",
            enabled: () => true,
            visible: () => this.isActionVisible("Export") && !this.annotationsSelected
         },
      ]));
      groups.push(new AssemblyActionGroup([
         {
            id: () => "table open-max-mode",
            label: () => "_#(js:Show Enlarged)",
            icon: () => "expand-icon",
            enabled: () => true,
            visible: () => !this.model.maxMode && this.isActionVisibleInViewer("Show Enlarged")
               && !this.isDataTip() && !this.isPopComponent()
         },
         {
            id: () => "table close-max-mode",
            label: () => "_#(js:Show Actual Size)",
            icon: () => "contract-icon",
            enabled: () => true,
            visible: () => this.model.maxMode && this.isActionVisibleInViewer("Show Actual Size")
               && !this.isDataTip() && !this.isPopComponent()
         },
      ]));
      groups.push(new AssemblyActionGroup([
         {
            id: () => "table MenuAction HelperText",
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
            id: () => "table open-max-mode",
            label: () => "_#(js:Show Enlarged)",
            icon: () => "expand-icon",
            enabled: () => true,
            visible: () => !this.model.maxMode && this.isActionVisibleInViewer("Show Enlarged")
               && !this.isDataTip() && !this.isPopComponent()
         },
         {
            id: () => "table close-max-mode",
            label: () => "_#(js:Show Actual Size)",
            icon: () => "contract-icon",
            enabled: () => true,
            visible: () => this.model.maxMode && this.isActionVisibleInViewer("Show Actual Size")
               && !this.isDataTip() && !this.isPopComponent()
         },
         {
            id: () => "table show-details",
            label: () => "_#(js:Show Details)",
            icon: () => "show-detail-icon",
            enabled: () => true,
            visible: () => this.isActionVisibleInViewer("Show Details")
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
               && this.menuActions.some((g) => g.actions.some((action) => action.visible()))
         }]));

      return groups;
   }
}
