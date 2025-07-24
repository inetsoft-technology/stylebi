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
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { SelectionValue } from "../../composer/data/vs/selection-value";
import { ContextProvider } from "../context-provider.service";
import { SelectionListModel } from "../model/selection-list-model";
import { isCompositeSelectionValue, SelectionValueModel } from "../model/selection-value-model";
import { VSSelectionListModel } from "../model/vs-selection-list-model";
import { DataTipService } from "../objects/data-tip/data-tip.service";
import { PopComponentService } from "../objects/data-tip/pop-component.service";
import { AbstractVSActions } from "./abstract-vs-actions";
import { ActionStateProvider } from "./action-state-provider";
import { GuiTool } from "../../common/util/gui-tool";
import { MiniToolbarService } from "../objects/mini-toolbar/mini-toolbar.service";

export class SelectionListActions extends AbstractVSActions<VSSelectionListModel> {
   constructor(model: VSSelectionListModel, contextProvider: ContextProvider,
               securityEnabled: boolean = false,
               stateProvider: ActionStateProvider = null,
               dataTipService: DataTipService = null,
               popService: PopComponentService = null,
               miniToolbarService: MiniToolbarService = null)
   {
      super(model, contextProvider, securityEnabled, stateProvider,
            dataTipService, popService, miniToolbarService);
   }

   protected createMenuActions(groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      groups.push(new AssemblyActionGroup([
         {
            id: () => "selection-list properties",
            label: () => "_#(js:Properties)...",
            icon: () => "fa fa-sliders",
            enabled: () => true,
            visible: () => this.composer && !this.model.adhocFilter
         },
         {
            id: () => "selection-list show-format-pane",
            label: () => "_#(js:Format)...",
            icon: () => "fa fa-format",
            enabled: () => true,
            visible: () => this.composer && !this.model.adhocFilter
         },
         {
            id: () => "selection-list convert-to-range-slider",
            label: () => "_#(js:Convert to Range Slider)",
            icon: () => "fa fa-calculator",
            enabled: () => true,
            visible: () => this.composer && !this.model.adhocFilter && this.inSelectionContainer
         }
      ]));

      groups.push(new AssemblyActionGroup([
         {
            id: () => "selection-list select-all",
            label: () => "_#(js:Select All)",
            icon: () => "fa fa-trash",
            enabled: () => true,
            visible: () => !this.model.singleSelection &&
               SelectionListActions.isSelectAllVisible(this.model.selectionList) &&
               this.isActionVisibleInViewer("Select All")
         }
      ]));

      groups.push(new AssemblyActionGroup([
         {
            id: () => "vs-object remove",
            label: () => "_#(js:Remove)",
            icon: () => "fa fa-trash",
            enabled: () => true,
            visible: () => this.composer && this.inSelectionContainer && !this.model.adhocFilter
         },
         {
            id: () => "selection-list viewer-remove-from-container",
            label: () => "_#(js:Remove)",
            icon: () => "fa fa-trash",
            enabled: () => true,
            visible: () => (this.preview || this.viewer)
                           && this.model.supportRemoveChild
                           && this.inSelectionContainer
                           && !this.model.adhocFilter
                           && this.isActionVisibleInViewer("Remove")
         }
      ]));

      if(!this.model.adhocFilter && !this.inSelectionContainer) {
         groups.push(this.createDefaultEditMenuActions());
         groups.push(this.createDefaultOrderMenuActions());
      }

      return super.createMenuActions(groups);
   }

   protected createToolbarActions(groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      groups.push(new AssemblyActionGroup([
         {
            id: () => "selection-list open-max-mode",
            label: () => "_#(js:Show Enlarged)",
            icon: () => "expand-icon",
            enabled: () => true,
            visible: () => this.openMaxModeVisible
         },
         {
            id: () => "selection-list close-max-mode",
            label: () => "_#(js:Show Actual Size)",
            icon: () => "contract-icon",
            enabled: () => true,
            visible: () => this.closeMaxModeVisible
         },
         {
            id: () => "selection-list search",
            label: () => "_#(js:Search)",
            icon: () => this.model.searchString ? "search-result-icon" : "search-icon",
            enabled: () => true,
            visible: () => !this.model.hidden && this.isActionVisibleInViewer("Search")
         },
         {
            id: () => "selection-list sort",
            label: () => "_#(js:Sort Ascending)",
            icon: () => "sort-icon",
            enabled: () => true,
            visible: () => !this.model.hidden && this.model.sortType == 8
               && this.isActionVisibleInViewer("Sort", "Sort Selection")
         },
         {
            id: () => "selection-list sort-asc",
            label: () => "_#(js:Sort Descending)",
            icon: () => "sort-ascending-icon",
            enabled: () => true,
            visible: () => !this.model.hidden && this.model.sortType == 1
               && this.isActionVisibleInViewer("Sort", "Sort Selection")
         },
         {
            id: () => "selection-list sort-desc",
            label: () => "_#(js:Sort Hide Others)",
            icon: () => "sort-descending-icon",
            enabled: () => true,
            visible: () => !this.model.hidden && this.model.sortType == 2
               && this.isActionVisibleInViewer("Sort", "Sort Selection")
         },
         {
            id: () => "selection-list reverse",
            label: () => "_#(js:Reverse)",
            icon: () => "selection-swap-icon",
            enabled: () => true,
            visible: () => !this.model.singleSelection
               && this.isActionVisibleInViewer("Reverse", "Reverse Selection")
         },
         {
            id: () => "selection-list unselect",
            label: () => "_#(js:Unselect)",
            icon: () => "eraser-icon",
            enabled: () => true,
            visible: () => !this.model.singleSelection
               && this.isActionVisibleInViewer("Unselect", "Clear Selection")
         },
         {
            id: () => "selection-list apply",
            label: () => "_#(js:Apply)",
            icon: () => "submit-icon",
            enabled: () => true,
            visible: () => !this.model.submitOnChange
               && this.isActionVisibleInViewer("Apply")
         }
      ]));
      return super.createToolbarActions(groups, true);
   }

   protected getEditScriptActionId(): string {
      return "selection-list edit-script";
   }

   private get openMaxModeVisible():  boolean {
      return !this.model.maxMode && !this.binding && !this.composer &&
         !this.inSelectionContainer &&  !this.model.adhocFilter &&
         this.isActionVisibleInViewer("Open Max Mode")
         && this.isActionVisibleInViewer("Maximize") && !this.isDataTip() &&
         !this.isPopComponent() && this.isActionVisibleInViewer("Show Enlarged");
   }

   private get closeMaxModeVisible():  boolean {
      return this.model.maxMode && !this.inSelectionContainer && (GuiTool.isMobileDevice() ||
         ((!this.binding && this.model.maxMode &&
            this.isActionVisibleInViewer("Close Max Mode") && !this.isDataTip() &&
            !this.isPopComponent()) && this.isActionVisibleInViewer("Show Actual Size")));
   }

   public static isSelectAllVisible(selectionList: SelectionListModel): boolean {
      if(selectionList != null && selectionList.selectionValues != null) {
         for(let i = 0; i < selectionList.selectionValues.length; i++) {
            const value = selectionList.selectionValues[i];

            if(this.selectVis(value)) {
               return true;
            }

            if(isCompositeSelectionValue(value) && this.isSelectAllVisible(value.selectionList)) {
               return true;
            }
         }
      }

      return false;
   }

   private static selectVis(selectValue: SelectionValueModel): boolean {
      const displayStatus = selectValue.state & SelectionValue.DISPLAY_STATES;
      const compatible = SelectionValue.isCompatible(selectValue.state);
      return displayStatus == SelectionValue.STATE_INCLUDED || displayStatus == 0 && compatible;
   }
}
