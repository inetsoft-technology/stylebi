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
import { ContextProvider } from "../context-provider.service";
import { VSSelectionContainerModel } from "../model/vs-selection-container-model";
import { AbstractVSActions } from "./abstract-vs-actions";
import { ActionStateProvider } from "./action-state-provider";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { DataTipService } from "../objects/data-tip/data-tip.service";
import { PopComponentService } from "../objects/data-tip/pop-component.service";
import { MiniToolbarService } from "../objects/mini-toolbar/mini-toolbar.service";

export class SelectionContainerActions extends AbstractVSActions<VSSelectionContainerModel> {
   constructor(model: VSSelectionContainerModel, contextProvider: ContextProvider,
               securityEnabled: boolean = false,
               stateProvider: ActionStateProvider = null,
               dataTipService: DataTipService = null,
               popService: PopComponentService = null,
               miniToolbarService: MiniToolbarService = null)
   {
      super(model, contextProvider, securityEnabled, stateProvider,
            dataTipService, popService, miniToolbarService);
   }

   // Case 2: the kebab is the whole strip at any width.
   protected get kebabOnly(): boolean {
      return true;
   }

   protected createMenuActions(groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      groups.push(new AssemblyActionGroup([
         {
            id: () => "selection-container properties",
            label: () => "_#(js:Properties)...",
            icon: () => "fa fa-sliders",
            enabled: () => true,
            visible: () => this.composer
         },
         {
            id: () => "selection-container show-format-pane",
            label: () => "_#(js:Format)...",
            icon: () => "fa fa-format",
            enabled: () => true,
            visible: () => this.composer
         },
      ]));
      groups.push(this.createDefaultEditMenuActions());
      groups.push(this.createDefaultOrderMenuActions());

      // The container's four actions were toolbar-only, so right-click could not reach any of them.
      // With the title hidden the anchored lane is zero and neither the strip nor the kebab draws,
      // which leaves the menu as the only surface. Predicates are copied verbatim from
      // createToolbarActions; the menu renders labels only, so no icon. Appended last so the
      // positional assertions in selection-container-actions.spec.ts do not shift.
      if(this.carriesContainerActions) {
         groups.push(new AssemblyActionGroup([
         {
            id: () => "selection-container open-max-mode",
            label: () => "_#(js:Show Enlarged)",
            icon: () => null,
            enabled: () => true,
            visible: () => this.openMaxModeVisible
         },
         {
            id: () => "selection-container close-max-mode",
            label: () => "_#(js:Show Actual Size)",
            icon: () => null,
            enabled: () => true,
            visible: () => this.closeMaxModeVisible
         },
         {
            id: () => "selection-container unselect-all",
            label: () => "_#(js:Unselect All)",
            icon: () => null,
            enabled: () => true,
            visible: () => this.isActionVisibleInViewer("Unselect All", "Clear All Selections")
         },
         {
            id: () => "selection-container addfilter",
            label: () => "_#(js:Add Filter)",
            icon: () => null,
            enabled: () => true,
            visible: () => this.isActionVisibleInViewer("Add Filter") &&
               !this.model.inEmbeddedViewsheet && this.model.supportRemoveChild
         }
         ]));
      }

      return super.createMenuActions(groups);
   }

   /**
    * Whether this class's menu carries the container's own toolbar actions. False for the
    * outer-selection row, which shares the container's model but has neither its toolbar nor a
    * route for its events — see CurrentSelectionActions.
    */
   protected get carriesContainerActions(): boolean {
      return true;
   }

   protected createToolbarActions(groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      groups.push(new AssemblyActionGroup([
         {
            id: () => "selection-container open-max-mode",
            label: () => "_#(js:Show Enlarged)",
            icon: () => "expand-icon",
            enabled: () => true,
            visible: () => this.openMaxModeVisible
         },
         {
            id: () => "selection-container close-max-mode",
            label: () => "_#(js:Show Actual Size)",
            icon: () => "contract-icon",
            enabled: () => true,
            visible: () => this.closeMaxModeVisible
         },
         {
            id: () => "selection-container unselect-all",
            label: () => "_#(js:Unselect All)",
            icon: () => "eraser-icon",
            visible: () => this.isActionVisibleInViewer("Unselect All", "Clear All Selections"),
            enabled: () => true
         },
         {
            id: () => "selection-container addfilter",
            label: () => "_#(js:Add Filter)",
            icon: () => "shape-plus-icon",
            visible: () => this.isActionVisibleInViewer("Add Filter") &&
               !this.model.inEmbeddedViewsheet && this.model.supportRemoveChild,
            enabled: () => true
         }
      ]));

      return super.createToolbarActions(groups, true);
   }

   protected getEditScriptActionId(): string {
      return "selection-container edit-script";
   }

   private get openMaxModeVisible():  boolean {
      return !this.model.maxMode && !this.binding && !this.composer &&
         !this.inSelectionContainer &&
         this.isActionVisibleInViewer("Open Max Mode")
         && this.isActionVisibleInViewer("Maximize") && !this.isDataTip() &&
         !this.isPopComponent() && this.isActionVisibleInViewer("Show Enlarged");
   }

   private get closeMaxModeVisible():  boolean {
      return this.model.maxMode &&
         (!this.binding && this.model.maxMode &&
            this.isActionVisibleInViewer("Close Max Mode") && !this.isDataTip() &&
            !this.isPopComponent()) && this.isActionVisibleInViewer("Show Actual Size");
   }
}
