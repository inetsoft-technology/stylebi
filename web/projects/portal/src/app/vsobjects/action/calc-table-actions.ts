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
import { ContextProvider } from "../context-provider.service";
import { VSCalcTableModel } from "../model/vs-calctable-model";
import { ActionStateProvider } from "./action-state-provider";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { BaseTableActions } from "./base-table-actions";
import { DataTipService } from "../objects/data-tip/data-tip.service";
import { PopComponentService } from "../objects/data-tip/pop-component.service";
import { SourceInfoType } from "../../binding/data/source-info-type";
import { CellBindingInfo } from "../../binding/data/table/cell-binding-info";
import { MiniToolbarService } from "../objects/mini-toolbar/mini-toolbar.service";

export class CalcTableActions extends BaseTableActions<VSCalcTableModel> {
   public inBindingPane = false;

   constructor(model: VSCalcTableModel, contextProvider: ContextProvider,
               securityEnabled: boolean = false,
               protected stateProvider: ActionStateProvider = null,
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
            id: () => "calc-table properties",
            label: () => "_#(js:Properties)...",
            icon: () => "fa fa-sliders",
            enabled: () => true,
            visible: () => (this.composer || this.composerBinding) && !this.annotationsSelected
         },
         {
            id: () => "calc-table show-format-pane",
            label: () => "_#(js:Format)...",
            icon: () => "fa fa-format",
            enabled: () => true,
            visible: () => (this.composer || this.composerBinding) && !this.annotationsSelected
         },
         {
            id: () => "calc-table conditions",
            label: () => "_#(js:Conditions)...",
            icon: () => "fa fa-filter",
            enabled: () => true,
            visible: () => (this.composer || (this.viewer || this.preview) &&
                            this.model.enableAdvancedFeatures &&
                            this.isActionVisibleInViewer("Condition")) &&
               !this.model.selectedHeaders && !this.model.selectedData &&
               !this.annotationsSelected && (!this.model.cubeType || this.model.worksheetCube)
         },
         {
            id: () => "calc-table reset-table-layout",
            label: () => "_#(js:Reset Table Layout)",
            icon: () => "fa fa-undo",
            enabled: () => true,
            visible: () => this.resetTableLayoutVisible && !this.annotationsSelected
         },
         {
            id: () => "calc-table annotate",
            label: () => this.oneCellSelected ? "_#(js:Annotate Cell)" : "_#(js:Annotate Component)",
            icon: () => "edit-icon",
            enabled: () => this.securityEnabled,
            visible: () => !this.annotationsSelected && (this.viewer || this.preview) &&
               !this.model.maxMode &&
               this.securityEnabled && this.model.enabled && !this.mobileDevice &&
               this.isActionVisibleInViewer(this.oneCellSelected ? "Annotate Cell" : "Annotate Component") &&
               (!this.stateProvider || this.stateProvider.isActionVisible("Annotation", this.model))
         },
         {
            id: () => "calc-table filter",
            label: () => "_#(js:Filter)",
            icon: () => "fa fa-filter",
            enabled: () => true,
            visible: () => !this.annotationsSelected &&
               !this.binding && !this.model.titleSelected && this.oneCellSelected &&
               !this.model.maxMode &&
               (!(this.viewer || this.preview) || this.model.adhocFilterEnabled) &&
               this.model.sourceType !== SourceInfoType.VS_ASSEMBLY &&
               this.selectedCellBindingType === 2 // not text nor formula
         }
      ]));
      groups.push(new AssemblyActionGroup([
         {
            id: () => "calc-table sorting",
            label: () => "_#(js:Sorting)...",
            icon: () => "fa fa-sort",
            enabled: () => true,
            visible: () => !this.annotationsSelected &&
               this.composer && !this.model.selectedHeaders && !this.model.selectedData
         }
      ]));
      groups.push(new AssemblyActionGroup([
         {
            id: () => "calc-table hyperlink",
            label: () => "_#(js:Hyperlink)...",
            icon: () => "fa fa-link",
            enabled: () => true,
            visible: () => !this.annotationsSelected &&
               this.composer && !this.model.titleSelected && this.oneCellSelected
         },
         {
            id: () => "calc-table highlight",
            label: () => "_#(js:Highlight)...",
            icon: () => "fa fa-filter",
            enabled: () => true,
            visible: () => !this.annotationsSelected &&
               (this.composer || (this.viewer || this.preview) &&
                  this.model.enableAdvancedFeatures && this.isActionVisibleInViewer("Highlight")) &&
               !this.model.titleSelected && this.oneCellSelected
         },
         {
            id: () => "table cell size",
            label: () => "_#(js:Set Cell Size)",
            icon: () => "place-holder-icon icon-edit",
            enabled: () => true,
            visible: () => this.oneCellSelected && !this.inBindingPane && this.isActionVisibleInViewer("Set Cell Size")
         },
      ]));
      groups.push(new AssemblyActionGroup([
         {
            id: () => "calc-table merge-cells",
            label: () => "_#(js:Merge Cells)",
            icon: () => "fa fa-plus",
            enabled: () => this.isStateEnabled("calc-table merge-cells", false),
            visible: () => this.binding && !this.annotationsSelected
         },
         {
            id: () => "calc-table split-cells",
            label: () => "_#(js:Split Cells)",
            icon: () => "fa fa-plus",
            enabled: () => this.isStateEnabled("calc-table split-cells", false),
            visible: () => this.binding && !this.annotationsSelected
         },
         {
            id: () => "calc-table insert-rows-columns",
            label: () => "_#(js:Insert Rows/Columns)",
            icon: () => "fa fa-plus",
            enabled: () => this.cellSelected,
            visible: () => this.binding && !this.annotationsSelected
         }
      ]));
      groups.push(new AssemblyActionGroup([
         {
            id: () => "calc-table insert-row",
            label: () => "_#(js:Insert Row)",
            icon: () => "fa fa-plus",
            enabled: () => this.cellSelected,
            visible: () => this.binding && !this.annotationsSelected && !this.mobileDevice
         },
         {
            id: () => "calc-table append-row",
            label: () => "_#(js:Append Row)",
            icon: () => "fa fa-plus",
            enabled: () => this.cellSelected,
            visible: () => this.binding && !this.annotationsSelected && !this.mobileDevice
         },
         {
            id: () => "calc-table delete-row",
            label: () => "_#(js:Delete Row)",
            icon: () => "fa fa-plus",
            enabled: () => this.isStateEnabled("calc-table delete-row", false) &&
               this.cellSelected,
            visible: () => this.binding && !this.annotationsSelected && !this.mobileDevice
         }
      ]));
      groups.push(new AssemblyActionGroup([
         {
            id: () => "calc-table insert-column",
            label: () => "_#(js:Insert Column)",
            icon: () => "fa fa-plus",
            enabled: () => this.cellSelected,
            visible: () => this.binding && !this.annotationsSelected
         },
         {
            id: () => "calc-table append-column",
            label: () => "_#(js:Append Column)",
            icon: () => "fa fa-plus",
            enabled: () => this.cellSelected,
            visible: () => this.binding && !this.annotationsSelected
         },
         {
            id: () => "calc-table delete-column",
            label: () => "_#(js:Delete Column)",
            icon: () => "fa fa-plus",
            enabled: () => this.isStateEnabled("calc-table delete-column", false) &&
               this.cellSelected,
            visible: () => this.binding && !this.annotationsSelected
         }
      ]));
      groups.push(new AssemblyActionGroup([
         {
            id: () => "calc-table copy-cell",
            label: () => "_#(js:Copy)",
            icon: () => "fa fa-plus",
            enabled: () => this.cellSelected,
            visible: () => this.binding && !this.annotationsSelected
         },
         {
            id: () => "calc-table cut-cell",
            label: () => "_#(js:Cut)",
            icon: () => "fa fa-plus",
            enabled: () => this.cellSelected,
            visible: () => this.binding && !this.annotationsSelected
         },
         {
            id: () => "calc-table paste-cell",
            label: () => "_#(js:Paste)",
            icon: () => "fa fa-plus",
            enabled: () => this.cellSelected,
            visible: () => this.binding && !this.annotationsSelected
         },
         {
            id: () => "calc-table remove-cell",
            label: () => "_#(js:Remove)",
            icon: () => "fa fa-plus",
            enabled: () => this.cellSelected,
            visible: () => this.binding && !this.annotationsSelected
         }
      ]));
      groups.push(this.createDefaultEditMenuActions(
         // copy enabled, visible
         () => true,
         () => !this.annotationsSelected && this.composer && !this.cellSelected,
         // cut enabled, visible
         () => true,
         () => !this.annotationsSelected && this.composer && !this.cellSelected,
         // remove enabled, visible
         () => true,
         () => !this.annotationsSelected && this.composer && !this.cellSelected,
         // group enabled, visible
         () => this.isStateEnabled("vs-object group"),
         () => !this.annotationsSelected && this.composer && !this.cellSelected,
         // ungroup enabled, visible
         () => this.isStateEnabled("vs-object ungroup"),
         () => !this.annotationsSelected && this.composer && !this.cellSelected
      ));
      groups.push(this.createDefaultOrderMenuActions(
         () => this.isStateEnabled("vs-object bring-to-front"),
         () => !this.annotationsSelected && this.composer && !this.cellSelected,
         () => this.isStateEnabled("vs-object send-to-back"),
         () => !this.annotationsSelected && this.composer && !this.cellSelected
      ));
      groups.push(this.createDefaultAnnotationMenuActions());

      groups.push(new AssemblyActionGroup([
         {
            id: () => "chart MenuAction HelperText",
            label: () => "_#(js:composer.vs.action.helperText.menuAction.table)",
            icon: () => "edit-icon",
            enabled: () => false,
            visible: () => true,
            classes: () => "helper-text"
         }
      ]));

      return super.createMenuActions(groups);
   }

   protected createToolbarActions(groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      groups.push(new AssemblyActionGroup([
         {
            id: () => "calc-table open-max-mode",
            label: () => "_#(js:Show Enlarged)",
            icon: () => "expand-icon",
            enabled: () => true,
            visible: () => this.openMaxModeVisible
         },
         {
            id: () => "calc-table close-max-mode",
            label: () => "_#(js:Show Actual Size)",
            icon: () => "contract-icon",
            enabled: () => true,
            visible: () => this.closeMaxModeVisible
         },
         {
            id: () => "calc-table show-details",
            label: () => "_#(js:Show Details)",
            icon: () => "show-detail-icon",
            visible: () => this.showDetailsVisible,
            enabled: () => true
         },
         {
            id: () => "calc-table export",
            label: () => "_#(js:Export)",
            icon: () => "export-icon",
            visible: () => this.isActionVisibleInViewer("Export"),
            enabled: () => true
         },
         {
            id: () => "calc-table multi-select",
            label: () => this.model.multiSelect ? "_#(js:Change to Single-select)"
               : "_#(js:Change to Multi-select)",
            icon: () => this.model.multiSelect ? "select-multi-icon" : "select-single-icon",
            enabled: () => true,
            visible: () => this.mobileDevice &&
               this.isActionVisibleInViewer("Change to Single-select") &&
               this.isActionVisibleInViewer("Change to Multi-select")
         },
         {
            id: () => "calc-table edit",
            label: () => "_#(js:Edit)...",
            icon: () => "edit-icon",
            enabled: () => true,
            visible: () => !this.isPopComponent() && this.composer && !this.annotationsSelected
         },
      ]));
      return super.createToolbarActions(groups, true);
   }

   protected getEditScriptActionId(): string {
      return "calc-table edit-script";
   }

   private get openMaxModeVisible():  boolean {
      return !this.model.maxMode && !this.binding &&
         !this.composer && this.isActionVisibleInViewer("Open Max Mode") &&
         this.isActionVisibleInViewer("Maximize") && !this.isDataTip() &&
         !this.isPopComponent() && this.isActionVisibleInViewer("Show Enlarged");
   }

   private get closeMaxModeVisible():  boolean {
      return this.model.maxMode &&
         (this.binding || this.model.maxMode &&
         this.isActionVisibleInViewer("Close Max Mode") && !this.isDataTip() &&
         !this.isPopComponent()) && this.isActionVisibleInViewer("Show Actual Size");
   }

   private get showDetailsVisible(): boolean {
      return !this.binding && this.cellSelected && !this.isBindingTypeSelected(CellBindingInfo.BIND_TEXT)
         && this.isActionVisibleInViewer("Show Details");
   }

   private get selectedCellBindingType(): number {
      if(this.oneCellSelected && this.model.selectedRegions &&
         this.model.selectedRegions.length === 1)
      {
         return this.model.selectedRegions[0].bindingType;
      }

      return undefined;
   }

   private isBindingTypeSelected(bindingType: number): boolean {
      return !!this.model.selectedRegions &&
         this.model.selectedRegions
            .filter(region => region.bindingType == bindingType).length > 0;
   }

   private get resetTableLayoutVisible(): boolean {
      return this.composer && !this.model.selectedHeaders && !this.model.selectedData &&
         this.model.explicitTableWidth === true;
   }
}
