/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Tool } from "../../../../../../shared/util/tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { SelectionValue } from "../../../composer/data/vs/selection-value";
import { GetVSObjectModelEvent } from "../../../vsview/event/get-vs-object-model-event";
import { ApplySelectionListEvent } from "../../event/apply-selection-list-event";
import { SortSelectionListEvent } from "../../event/sort-selection-list-event";
import { CompositeSelectionValueModel } from "../../model/composite-selection-value-model";
import { SelectionListModel } from "../../model/selection-list-model";
import { SelectionValueModel } from "../../model/selection-value-model";
import { VSFormatModel } from "../../model/vs-format-model";
import { VSSelectionTreeModel } from "../../model/vs-selection-tree-model";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { SelectionBaseController, SelectionStateModel } from "./selection-base-controller";

export const MODE = {
   COLUMN: 1,
   ID: 2
};

export class SelectionTreeController extends SelectionBaseController<VSSelectionTreeModel> {
   private openMap: {[path: string]: boolean} = {};
   private currentValues: SelectionValueModel[] = null;
   protected toggleLevels: number[] = [];

   constructor(protected viewsheetClient: ViewsheetClientService,
               private formDataService: CheckFormDataService,
               private assemblyName: string)
   {
      super(viewsheetClient);
   }

   public getCellFormat(value: SelectionValueModel): VSFormatModel {
      let result: VSFormatModel = null;
      let slist: SelectionListModel;

      if(value.parentNode) {
         slist = value.parentNode.selectionList;
      }
      else {
         slist = this.model.root.selectionList;
      }

      if(value.formatIndex < 0) {
         if(slist.formats[0]) {
            result = slist.formats[0];
         }
         else {
            result = this.model.objectFormat;
         }
      }
      else {
         result = slist.formats[value.formatIndex];
      }

      return result;
   }

   public setVisibleValues(): void {
      if(!this.currentValues) {
         this.currentValues = [];
         this.expandNode(this.model.root, this.currentValues);
      }

      this.showAll = this.showAll || this.model.mode === MODE.ID;
      this.showOther = false;
      this.visibleValues = this.currentValues.filter(value => this.filterSelectionValue(value));

      // If all values are excluded, show the values and don't display the "show others" text
      // and plus icon
      if(this.visibleValues.length == 0 && this.currentValues.length > 0) {
         this.visibleValues = this.currentValues;
         this.showOther = false;
      }
   }

   public filterSelectionValue(svalue: SelectionValueModel): SelectionValueModel {
      super.filterSelectionValue(svalue);
      const selected = SelectionValue.isSelected(svalue.state);

      // If svalue is excluded but the parent is not root and is not excluded, return svalue
      return (svalue.parentNode && svalue.parentNode.level >= 0 && !svalue.parentNode.excluded)
              || !svalue.excluded || selected ? svalue : null;
   }

   public showAllValues(): void {
      this.showAll = true;
      this.setVisibleValues();
   }

   public resetValues(): void {
      this.currentValues = null;
   }

   public isAdhocFilter(): boolean {
      return false;
   }

   public expandAllNodes(): void {
      if(!this.model.root || !this.model.root.selectionList) {
         return;
      }

      this.resetValues();
      let children: SelectionValueModel[] = [];
      this.expandNode(this.model.root, children, true);
      const parents: SelectionValueModel[] = children.filter(node => "selectionList" in node);

      for(let parent of parents) {
         this.openMap[SelectionTreeController.getNodePath(parent)] = true;
      }

      this.visibleValues = children;
   }

   expandNode(node: CompositeSelectionValueModel, children: SelectionValueModel[],
              expandAll?: boolean)
   {
      if(node && node.selectionList) {
         for(let child of node.selectionList.selectionValues) {
            children.push(child);
            child.parentNode = node;

            if("selectionList" in child && (this.isNodeOpen(child) || expandAll)) {
               this.expandNode(<CompositeSelectionValueModel> child, children, expandAll);
            }
         }
      }
   }

   public isNodeOpen(node: SelectionValueModel): boolean {
      return this.openMap[SelectionTreeController.getNodePath(node)]; // <-- always false?!
   }

   public toggleNode(node: SelectionValueModel) {
      this.resetValues();
      const path = SelectionTreeController.getNodePath(node);

      if(this.openMap[path]) {
         delete this.openMap[path];
      }
      else {
         this.openMap[path] = true;
      }

      this.setVisibleValues();
   }

   /**
    * Gets the unique path that identifies a node.
    *
    * @param node the tree node.
    *
    * @returns {string} the node path.
    */
   private static getNodePath(node: SelectionValueModel): string {
      let path: string;

      if(node.path) {
         // cached path
         path = node.path;
      }
      else {
         path = SelectionTreeController.getPathPrefix(node);
         let current: SelectionValueModel = node.parentNode;

         while(current) {
            path = SelectionTreeController.getPathPrefix(current) + "/" + path;
            current = current.parentNode;
         }

         node.path = path;
      }

      return path;
   }

   private static getPathPrefix(node: SelectionValueModel): string {
      return node.label || node.value || "";
   }

   private updateParentNodes(node: SelectionValueModel, values: string[],
                             state: number, selectParents: boolean = true): SelectionValueModel[]
   {
      const updatedNodes = [node];

      while(node != null && node.parentNode != null && node.parentNode.level >= 0) {
         if(selectParents) {
            values.splice(0, 0, node.parentNode.value);
         }

         node = node.parentNode;

         if(SelectionValue.isSelected(state) && selectParents) {
            // select parent nodes
            updatedNodes.push(node);
            node.state = state;
         }
      }
      return updatedNodes;
   }

   private updateChildrenState(node: CompositeSelectionValueModel, state: number): void {
      if(node && node.selectionList) {
         for(let child of node.selectionList.selectionValues) {
            if(this.model.submitOnChange || SelectionValue.isSelected(child.state)) {
               child.state = state;
            }

            if("selectionList" in child) {
               this.updateChildrenState(<CompositeSelectionValueModel> child, state);
            }
         }
      }
   }

   private updateSelectionValueArray(node: CompositeSelectionValueModel, values: string[]): void {
      if(node && node.selectionList) {
         node.selectionList.selectionValues.forEach((child) => {
            // Only the selected values will remain on the array
            // Same as shrinkSelectionValues in VSSelectionListObj.as
            if(this.model.mode == MODE.ID || SelectionValue.isSelected(child.state)) {
               values.push(child.value);

               if("selectionList" in child) {
                  this.updateSelectionValueArray(<CompositeSelectionValueModel> child, values);
               }
            }
         });
      }
   }

   private shrinkSelectionValues(node: CompositeSelectionValueModel, values: string[]): void {
      if(node && node.selectionList) {
         node.selectionList.selectionValues.forEach((child) => {
            if(SelectionValue.isSelected(child.state) && values.indexOf(child.value) != -1) {
               values.splice(values.indexOf(child.value), 1);
            }

            if("selectionList" in child) {
               this.shrinkSelectionValues(<CompositeSelectionValueModel> child, values);
            }
         });
      }
   }

   public selectionStateUpdated(selectedValue: SelectionValueModel,
                                state: number,
                                toggle?: boolean,
                                toggleAll?: boolean): void
   {
      if(this.model.mode == MODE.ID && !this.model.singleSelection &&
         this.model.selectChildren && this.model.submitOnChange)
      {
         this.setSubtree(selectedValue, state, toggle, toggleAll);
         return;
      }

      selectedValue.state = state;
      const values = [selectedValue.value];
      let node: SelectionValueModel = selectedValue;

      const updatedNodes: SelectionValueModel[] = this.model.mode == MODE.COLUMN ?
         this.updateParentNodes(node, values, state) : [];

      if("selectionList" in selectedValue && !SelectionValue.isSelected(state)) {
         // update all child states
         this.updateChildrenState(<CompositeSelectionValueModel> node, state);
      }

      const selected = SelectionValue.isSelected(state);
      let selection: SelectionStateModel = {value: values, selected};

      if(this.model.submitOnChange) {
         this.updateSelection([selection], null, toggle, toggleAll,
            toggle ? [selectedValue.level] : null);
      }
      else {
         let idx = selectedValue.level;
         let singleSelection = this.model.singleSelectionLevels?.indexOf(idx) >= 0;

         if(singleSelection) {
            this.unappliedSelections = [selection];
            let currentPath = SelectionTreeController.getNodePath(selectedValue);
            this.visibleValues.forEach((visibleVal) => {
               if(this.model.mode === MODE.ID) {
                  visibleVal.state = visibleVal == selectedValue ? state : 0;
               }
               else {
                  const matchingNode = updatedNodes.find(
                     (n) => n.level === visibleVal.level && n.label === visibleVal.label);

                  if(matchingNode != null) {
                     visibleVal.state = state;
                  }
                  else {
                     let path = SelectionTreeController.getNodePath(visibleVal);

                     if(!currentPath || !path || path.indexOf(currentPath) == -1) {
                        visibleVal.state = 0;
                     }
                  }
               }
            });

            if(this.model.mode === MODE.ID) {
               this.updateParentNodes(node, values, state, false);
            }
         }
         else {
            if(this.model.mode == MODE.ID && !this.model.singleSelection && this.model.selectChildren &&
               "selectionList" in selectedValue)
            {
               this.updateSelectionValueArray(<CompositeSelectionValueModel> node, values);
            }

            this.unappliedSelections.push(selection);
         }

         this.fireChange();

         if(toggle) {
            this.toggle = !this.toggle;
            this.toggleSingle(selectedValue.level);
         }

         if(toggleAll) {
            this.toggleAll = !this.toggleAll;
         }
      }
   }

   protected toggleSingle(level?: number): void {
      let index = this.toggleLevels.indexOf(level);

      if(index < 0) {
         this.toggleLevels.push(level);
      }
      else {
         this.toggleLevels.splice(index, 1);
      }
   }

   public setSubtree(value: SelectionValueModel, state: number, toggle: boolean = false,
                     toggleAll = false): void
   {
      value.state = state;
      let values: string[] = [ value.value ];
      let node: SelectionValueModel = value;

      if(this.model.mode == MODE.COLUMN) {
         this.updateParentNodes(node, values, state);
      }

      if("selectionList" in value) {
         node = value;
         this.updateChildrenState(<CompositeSelectionValueModel> node, state);
         this.updateSelectionValueArray(<CompositeSelectionValueModel> node, values);
      }

      const selected = SelectionValue.isSelected(state);
      const stateModels: SelectionStateModel[] = [{value: values, selected}];

      if(this.model.submitOnChange) {
         this.updateSelection(stateModels, null, toggle, toggleAll);
      }
      else {
         this.unappliedSelections.push(stateModels[0]);
      }
   }

   public updateSelection(values: SelectionStateModel[],
                          eventSource?: string,
                          toggle?: boolean,
                          toggleAll?: boolean,
                          toggleLevels?: number[]): void
   {
      toggleLevels = !!toggleLevels ? toggleLevels : this.toggleLevels;

      this.formDataService.checkFormData(
         this.viewsheetClient.runtimeId, this.model.absoluteName, null,
         () => {
            this.viewsheetClient.sendEvent(
               "/events/selectionList/update/" + this.assemblyName,
               new ApplySelectionListEvent(values, ApplySelectionListEvent.APPLY, -1,
                  -1, eventSource ? eventSource : this.model.absoluteName,
                  toggle, toggleAll, toggleLevels));
            this.toggleLevels = [];
         },
         () => {
            this.updateSelectionTreeModel();
         }
      );
   }

   public clearSelections(): void {
      this.formDataService.checkFormData(
         this.viewsheetClient.runtimeId, this.model.absoluteName, null,
         () => {
            this.viewsheetClient.sendEvent(
               "/events/selectionList/update/" + this.assemblyName,
               new ApplySelectionListEvent(null, ApplySelectionListEvent.APPLY));
         },
         () => {
            this.updateSelectionTreeModel();
         }
      );
   }

   public selectSubtree(value: SelectionValueModel, state: number): void {
      value.state = state;
      let values: string[] = [ value.value ];
      this.updateParentNodes(value, values, state);
      const selected = SelectionValue.isSelected(state);
      const stateModels: SelectionStateModel[] = [{value: values, selected}];

      if(this.model.submitOnChange) {
         this.formDataService.checkFormData(
             this.viewsheetClient.runtimeId, this.model.absoluteName, null,
             () => {
                this.viewsheetClient.sendEvent(
                    "/events/selectionTree/selectSubtree/" + this.assemblyName,
                    new ApplySelectionListEvent(stateModels));
             },
             () => {
                this.updateSelectionTreeModel();
             }
         );
      }
      else {
         this.unappliedSelections.push(stateModels[0]);

         if((value as CompositeSelectionValueModel).selectionList?.selectionValues) {
            let selectionValues = (value as CompositeSelectionValueModel)?.selectionList?.selectionValues;

            for (let i = 0; i < selectionValues.length; i++) {
               this.selectSubtree(selectionValues[i], state);
            }
         }
      }
   }

   public clearSingleCellSubTree(value: CompositeSelectionValueModel) {
      const state = value?.state & ~SelectionValue.STATE_SELECTED;
      const selected = SelectionValue.isSelected(state);
      let selectionValues = value?.selectionList?.selectionValues;
      const stateModels: SelectionStateModel[] = [];

      if(selectionValues) {
         selectionValues.forEach(child => {
            let values: string[] = [ child.value ];
            this.updateParentNodes(child, values, state);
            let applyValue = {value: values, selected};
            stateModels.push(applyValue);
         });
      }

      if(this.model.submitOnChange) {
         this.updateSelection(stateModels, this.model.absoluteName);
      }
      else {
         this.unappliedSelections = this.unappliedSelections.concat(stateModels);
      }
   }

   public sortSelections(): void {
      this.viewsheetClient.sendEvent(
         "/events/selectionList/sort/" + this.assemblyName,
         new SortSelectionListEvent());
   }

   public reverseSelections(): void {
      this.viewsheetClient.sendEvent(
         "/events/selectionList/update/" + this.assemblyName,
         new ApplySelectionListEvent(Tool.clone(this.unappliedSelections), ApplySelectionListEvent.REVERSE));
      this.unappliedSelections = [];
   }

   public searchSelections(search: string): void {
      this.viewsheetClient.sendEvent(
         "/events/selectionList/sort/" + this.assemblyName,
         new SortSelectionListEvent(search));
   }

   public hideChild(): void {
      // NO-OP
   }

   public showChild(): void {
      // NO-OP
   }

   updateStatusByValues(values: SelectionStateModel[]): void {
      values.forEach(val => {
         let selectionValueModel = this.findNodeByPath(this.model.root, val.value, 0);

         if(selectionValueModel) {
            this.selectionStateUpdated(selectionValueModel, val.selected ?
               SelectionValue.STATE_SELECTED : SelectionValue.STATE_COMPATIBLE);
         }
      });
   }

   findNodeByPath(node: CompositeSelectionValueModel, valuePaths: string[],
                  currentValIndex: number): SelectionValueModel
   {
      if(node && node.selectionList) {
         for(let child of node.selectionList.selectionValues) {
            child.parentNode = node;

            if(child.value != valuePaths[currentValIndex]) {
               continue;
            }

            if(currentValIndex == valuePaths.length - 1) {
               return child;
            }

            return this.findNodeByPath(<CompositeSelectionValueModel> child, valuePaths,
               currentValIndex + 1);
         }
      }

      return null;
   }

   private updateSelectionTreeModel(): void {
      let event: GetVSObjectModelEvent =
         new GetVSObjectModelEvent(this.model.absoluteName);
      this.viewsheetClient.sendEvent("/events/vsview/object/model", event);
   }
}
