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
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { ContextProvider } from "../context-provider.service";
import { VSSelectionTreeModel } from "../model/vs-selection-tree-model";
import { DataTipService } from "../objects/data-tip/data-tip.service";
import { PopComponentService } from "../objects/data-tip/pop-component.service";
import { AbstractVSActions } from "./abstract-vs-actions";
import { ActionStateProvider } from "./action-state-provider";
import { SelectionListActions } from "./selection-list-actions";
import { SelectionValueModel } from "../model/selection-value-model";
import { CompositeSelectionValueModel } from "../model/composite-selection-value-model";
import { MODE } from "../objects/selection/selection-tree-controller";
import { MiniToolbarService } from "../objects/mini-toolbar/mini-toolbar.service";

export class SelectionTreeActions extends AbstractVSActions<VSSelectionTreeModel> {
   constructor(model: VSSelectionTreeModel, contextProvider: ContextProvider,
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
            id: () => "selection-tree properties",
            label: () => "_#(js:Properties)...",
            icon: () => "fa fa-sliders",
            enabled: () => true,
            visible: () => this.composer
         },
         {
            id: () => "selection-tree show-format-pane",
            label: () => "_#(js:Format)...",
            icon: () => "fa fa-format",
            enabled: () => true,
            visible: () => this.composer
         },
      ]));
      groups.push(new AssemblyActionGroup([
         {
            id: () => "selection-tree select-all",
            label: () => "_#(js:Select All)",
            icon: () => "fa fa-trash",
            enabled: () => true,
            visible: () => this.isSelectAllVisible() && this.isActionVisibleInViewer("Select All")
         },
         {
            id: () => "selection-tree select-subtree",
            label: () => "_#(js:Select Subtree)",
            icon: () => "fa fa-sliders",
            enabled: () => this.isSelectSubTreeEnable(),
            visible: () => this.isSelectSubtreeVisible() && this.isActionVisibleInViewer("Select Subtree")
         },
         {
            id: () => "selection-tree clear-subtree",
            label: () => "_#(js:Clear Subtree)",
            icon: () => "fa fa-sliders",
            enabled: () => this.isSelectSubTreeEnable(),
            visible: () => this.isClearSubtreeVisible() && this.isActionVisibleInViewer("Clear Subtree")
         }
      ]));
      groups.push(this.createDefaultEditMenuActions());
      groups.push(this.createDefaultOrderMenuActions());
      return super.createMenuActions(groups);
   }

   protected createToolbarActions(groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      groups.push(new AssemblyActionGroup([
         {
            id: () => "selection-tree open-max-mode",
            label: () => "_#(js:Show Enlarged)",
            icon: () => "expand-icon",
            enabled: () => true,
            visible: () => this.openMaxModeVisible
         },
         {
            id: () => "selection-tree close-max-mode",
            label: () => "_#(js:Show Actual Size)",
            icon: () => "contract-icon",
            enabled: () => true,
            visible: () => this.closeMaxModeVisible
         },
         {
            id: () => "selection-tree search",
            label: () => "_#(js:Search)",
            icon: () => this.model.searchString ? "search-result-icon" : "search-icon",
            enabled: () => true,
            visible: () => !this.model.hidden && this.isActionVisibleInViewer("Search")
         },
         {
            id: () => "selection-tree sort",
            label: () => "_#(js:Sort Ascending)",
            icon: () => "sort-icon",
            enabled: () => true,
            visible: () => !this.model.hidden && this.model.sortType == 8
               && this.isActionVisibleInViewer("Sort", "Sort Selection")
         },
         {
            id: () => "selection-tree sort-asc",
            label: () => "_#(js:Sort Descending)",
            icon: () => "sort-ascending-icon",
            enabled: () => true,
            visible: () => !this.model.hidden && this.model.sortType == 1
               && this.isActionVisibleInViewer("Sort", "Sort Selection")
         },
         {
            id: () => "selection-tree sort-desc",
            label: () => "_#(js:Sort Hide Others)",
            icon: () => "sort-descending-icon",
            enabled: () => true,
            visible: () => !this.model.hidden && this.model.sortType == 2
               && this.isActionVisibleInViewer("Sort", "Sort Selection")
         },
         {
            id: () => "selection-tree reverse",
            label: () => "_#(js:Reverse)",
            icon: () => "selection-swap-icon",
            enabled: () => true,
            visible: () => !this.model.singleSelection
               && this.isActionVisibleInViewer("Reverse")
         },
         {
            id: () => "selection-tree unselect",
            label: () => "_#(js:Unselect)",
            icon: () => "eraser-icon",
            enabled: () => true,
            visible: () => this.isUnselectActionVisible()
         },
         {
            id: () => "selection-tree apply",
            label: () => "_#(js:Apply)",
            icon: () => "submit-icon",
            enabled: () => true,
            visible: () => !this.model.submitOnChange &&
               this.isActionVisibleInViewer("Apply")
         }
      ]));

      return super.createToolbarActions(groups, true);
   }

   private isUnselectActionVisible(): boolean {
      if(!this.isActionVisibleInViewer("Unselect")) {
         return false;
      }

      return !this.model.singleSelection ||
         this.model.singleSelectionLevels?.length != this.model.levels;
   }

   private get openMaxModeVisible():  boolean {
      return !this.model.maxMode && !this.binding && !this.composer &&
         !this.inSelectionContainer &&
         this.isActionVisibleInViewer("Open Max Mode") &&
         this.isActionVisibleInViewer("Maximize") && !this.isDataTip() &&
         !this.isPopComponent() && this.isActionVisibleInViewer("Show Enlarged");
   }

   private get closeMaxModeVisible():  boolean {
      return this.model.maxMode && (this.mobileDevice ||
         ((!this.binding && this.model.maxMode &&
            this.isActionVisibleInViewer("Close Max Mode") && !this.isDataTip() &&
            !this.isPopComponent()) && this.isActionVisibleInViewer("Show Actual Size")));
   }

   protected getEditScriptActionId(): string {
      return "selection-tree edit-script";
   }

   private isSelectSubtreeVisible(): boolean {
      return !!this.model.contextMenuCell && !!this.model.selectedRegions &&
         (this.model.mode != MODE.ID || !this.model.singleSelection)
         && this.isActionVisibleInViewer("Select Subtree");
   }

   private isClearSubtreeVisible(): boolean {
      return !!this.model.contextMenuCell && !!this.model.selectedRegions &&
         (this.model.mode != MODE.ID || !this.model.singleSelection)
         && this.isActionVisibleInViewer("Clear Subtree");
   }

   private isSelectAllVisible(): boolean {
      return this.model.root != null && !this.model.singleSelection &&
             SelectionListActions.isSelectAllVisible(this.model.root.selectionList);
   }

   private isSelectSubTreeEnable(): boolean {
      let notLeafCell: boolean = "selectionList" in this.model.contextMenuCell;

      return !this.model.singleSelection && notLeafCell || this.model.singleSelection && notLeafCell
         && !!this.model.singleSelectionLevels
         && !this.containSingleSelectionLevel(this.model.contextMenuCell, this.model.singleSelectionLevels);
   }

   private containSingleSelectionLevel(cell: SelectionValueModel, singleLevels: number[]): boolean {
      if(singleLevels[singleLevels.length - 1] < cell["level"]) {
         return false;
      }

      if(!("selectionList" in cell)) {
         return true;
      }

      let curModel: CompositeSelectionValueModel = <CompositeSelectionValueModel> cell;
      let selectionList = curModel.selectionList;
      let selectionValues: Array<SelectionValueModel> = selectionList.selectionValues;

      if(singleLevels.includes(selectionValues[0].level)) {
         return true;
      }

      return this.containSingleSelectionLevel(selectionValues[0], singleLevels);
   }
}
