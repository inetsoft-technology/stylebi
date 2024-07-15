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
import { SourceInfoType } from "../../binding/data/source-info-type";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { XSchema } from "../../common/data/xschema";
import { GuiTool } from "../../common/util/gui-tool";
import { ContextProvider } from "../context-provider.service";
import { VSRangeSliderModel } from "../model/vs-range-slider-model";
import { DataTipService } from "../objects/data-tip/data-tip.service";
import { PopComponentService } from "../objects/data-tip/pop-component.service";
import { AbstractVSActions } from "./abstract-vs-actions";
import { ActionStateProvider } from "./action-state-provider";
import { MiniToolbarService } from "../objects/mini-toolbar/mini-toolbar.service";

export class RangeSliderActions extends AbstractVSActions<VSRangeSliderModel> {
   constructor(model: VSRangeSliderModel, contextProvider: ContextProvider,
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
            id: () => "range-slider properties",
            label: () => "_#(js:Properties)...",
            icon: () => "fa fa-sliders",
            enabled: () => true,
            visible: () => this.composer && !this.model.adhocFilter
               && !GuiTool.isMobileDevice()
         },
         {
            id: () => "range-slider viewer-advanced-pane",
            label: () => "_#(js:Properties)...",
            icon: () => "fa fa-sliders",
            enabled: () => true,
            visible: () => (this.preview || this.viewer)
               && this.model.containerType === "VSSelectionContainer"
               && !this.model.adhocFilter && this.model.supportRemoveChild
               && this.isActionVisibleInViewer("Properties")
               && !GuiTool.isMobileDevice()
         },
         {
            id: () => "range-slider show-format-pane",
            label: () => "_#(js:Format)...",
            icon: () => "fa fa-format",
            enabled: () => true,
            visible: () => this.composer && !this.model.adhocFilter && !GuiTool.isMobileDevice()
         },
         {
            id: () => "range-slider edit-range",
            label: () => "_#(js:Edit)",
            icon: () => "fa fa-calculator",
            enabled: () => true,
            visible: () => this.isActionVisibleInViewer("Edit")
               && !this.model.composite && XSchema.isNumericType(this.model.dataType)
               && !this.model.adhocFilter
         },
         {
            id: () => "range-slider convert-to-selection-list",
            label: () => "_#(js:Convert to Selection List)",
            icon: () => "fa fa-calculator",
            enabled: () => true,
            visible: () => this.composer && this.inSelectionContainer
               && !this.model.adhocFilter && !this.isConvertDisabled()
         }
      ]));

      groups.push(new AssemblyActionGroup([
         {
            id: () => "vs-object remove",
            label: () => "_#(js:Remove)",
            icon: () => "fa fa-trash",
            enabled: () => true,
            visible: () => this.composer
            && this.model.containerType === "VSSelectionContainer"
            && !this.model.adhocFilter
         },
         {
            id: () => "range-slider viewer-remove-from-container",
            label: () => "_#(js:Remove)",
            icon: () => "fa fa-trash",
            enabled: () => true,
            visible: () => (this.preview || this.viewer)
            && this.model.supportRemoveChild
            && this.model.containerType === "VSSelectionContainer"
            && !this.model.adhocFilter
            && this.isActionVisibleInViewer("Remove")
         }
      ]));

      if(!this.model.adhocFilter && this.model.containerType !== "VSSelectionContainer") {
         groups.push(this.createDefaultEditMenuActions());
         groups.push(this.createDefaultOrderMenuActions());
      }

      return super.createMenuActions(groups);
   }

   protected createToolbarActions(groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      groups.push(new AssemblyActionGroup([
         {
            id: () => "range-slider open-max-mode",
            label: () => "_#(js:Show Enlarged)",
            icon: () => "expand-icon",
            enabled: () => true,
            visible: () => this.openMaxModeVisible
         },
         {
            id: () => "range-slider close-max-mode",
            label: () => "_#(js:Show Actual Size)",
            icon: () => "contract-icon",
            enabled: () => true,
            visible: () => this.closeMaxModeVisible
         },
         {
            id: () => "range-slider unselect",
            label: () => "_#(js:Unselect)",
            icon: () => "eraser-icon",
            enabled: () => true,
            visible: () => this.isActionVisibleInViewer("Unselect") && this.model.adhocFilter
         }
      ]));

      return super.createToolbarActions(groups, true);
   }

   private isConvertDisabled(): boolean {
      if(this.model.sourceType == SourceInfoType.VS_ASSEMBLY) {
         return true;
      }

      const advancedStatus = this.model.advancedStatus;

      if(!!advancedStatus) {
         let fieldIndex = advancedStatus.indexOf(":");
         let fieldString = fieldIndex >= 0 ?
            advancedStatus.substring(fieldIndex) : advancedStatus;
         return fieldString.indexOf("(") >= 0 && fieldString.indexOf(")") >= 0;
      }

      return false;
   }

   private get openMaxModeVisible():  boolean {
      return !this.model.maxMode && !this.binding && !this.composer &&
         !this.inSelectionContainer &&  !this.model.adhocFilter &&
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

   protected getEditScriptActionId(): string {
      return "range-slider edit-script";
   }
}
