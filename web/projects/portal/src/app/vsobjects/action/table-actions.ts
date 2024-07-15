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
import { ContextProvider } from "../context-provider.service";
import { VSTableModel } from "../model/vs-table-model";
import { ActionStateProvider } from "./action-state-provider";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { BaseTableActions } from "./base-table-actions";
import { DataTipService } from "../objects/data-tip/data-tip.service";
import { PopComponentService } from "../objects/data-tip/pop-component.service";
import { SourceInfoType } from "../../binding/data/source-info-type";
import { MiniToolbarService } from "../objects/mini-toolbar/mini-toolbar.service";

export class TableActions extends BaseTableActions<VSTableModel> {
   constructor(model: VSTableModel, contextProvider: ContextProvider,
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
            id: () => "table properties",
            label: () => "_#(js:Properties)...",
            icon: () => "place-holder-icon icon-properties",
            enabled: () => true,
            visible: () => !this.annotationsSelected && (this.composerBinding || this.composer)
         },
         {
            id: () => "table show-format-pane",
            label: () => "_#(js:Format)...",
            icon: () => "fa fa-format",
            enabled: () => true,
            visible: () => !this.annotationsSelected && (this.binding || this.composer)
         },
      ]));
      groups.push(new AssemblyActionGroup([
         {
            id: () => "table hyperlink",
            label: () => "_#(js:Hyperlink)...",
            icon: () => "place-holder-icon icon-hyperlink",
            enabled: () => true,
            visible: () => this.hyperlinkVisible && !this.annotationsSelected
         },
         {
            id: () => "table highlight",
            label: () => "_#(js:Highlight)...",
            icon: () => "place-holder-icon, icon-hightlight",
            enabled: () => true,
            visible: () => this.highlightVisible && !this.annotationsSelected
         },
         {
            id: () => "table copy-highlight",
            label: () => "_#(js:Copy Highlight)",
            icon: () => "fa fa-files-o",
            enabled: () => true,
            visible: () => this.copyHighlightVisible && !this.annotationsSelected
         },
         {
            id: () => "table paste-highlight",
            label: () => "_#(js:Paste Highlight)",
            icon: () => "place-holder-icon icon-highlight",
            enabled: () => true,
            visible: () => this.pasteHighlightVisible && !this.annotationsSelected
         },
         {
            id: () => "table cell size",
            label: () => "_#(js:Set Cell Size)",
            icon: () => "place-holder-icon icon-edit",
            enabled: () => true,
            visible: () => this.oneCellSelected && this.isActionVisibleInViewer("Set Cell Size")
         },
      ]));
      groups.push(new AssemblyActionGroup([
         {
            id: () => "table conditions",
            label: () => "_#(js:Conditions)...",
            icon: () => "place-holder-icon icon-conditions",
            enabled: () => true,
            visible: () => this.conditionsVisible && !this.annotationsSelected &&
               (!this.model.cubeType || this.model.worksheetCube)
         },
         {
            id: () => "table reset-table-layout",
            label: () => "_#(js:Reset Table Layout)",
            icon: () => "place-holder-icon icon-reset-layout",
            enabled: () => true,
            visible: () => this.resetTableLayoutVisible && !this.annotationsSelected
         },
         {
            id: () => "table convert-to-freehand-table",
            label: () => "_#(js:Convert to Freehand Table)",
            icon: () => "place-holder-icon icon-convert-freehand",
            enabled: () => !this.model.form,
            visible: () => this.convertVisible && !this.annotationsSelected
         },
         {
            id: () => "table annotate title",
            label: () => "_#(js:Annotate Component)",
            icon: () => "edit-icon",
            enabled: () => true,
            visible: () => !this.annotationsSelected
               && (!this.vsWizardPreview && this.viewer || this.preview) &&
               !this.model.maxMode && this.model.enabled && this.securityEnabled &&
               !this.cellSelected && !this.mobileDevice &&
               this.isActionVisibleInViewer("Annotate Component") &&
               (!this.stateProvider || this.stateProvider.isActionVisible("Annotation", this.model))
         },
         {
            id: () => "table annotate cell",
            label: () => "_#(js:Annotate Cell)",
            icon: () => "edit-icon",
            enabled: () => true,
            visible: () => !this.annotationsSelected && !this.mobileDevice &&
               !this.model.maxMode &&
               (!this.vsWizardPreview && this.viewer || this.preview) && this.securityEnabled &&
               !this.model.form && this.cellSelected &&
               this.isActionVisibleInViewer("Annotate Cell") &&
               (!this.stateProvider || this.stateProvider.isActionVisible("Annotation", this.model))
         },
         {
            id: () => "table filter",
            label: () => "_#(js:Filter)",
            icon: () => "place-holder-icon icon-filter",
            enabled: () => true,
            visible: () => this.filterVisible && !this.annotationsSelected
         }
      ]));
      groups.push(new AssemblyActionGroup([
         {
            id: () => "table insert-row",
            label: () => "_#(js:Insert Row)",
            icon: () => "place-holder-icon icon-insert-row",
            enabled: () => true,
            visible: () => this.addRowsVisible && !this.mobileDevice &&
                  this.isActionVisibleInViewer("Insert Row") && !this.annotationsSelected
         },
         {
            id: () => "table append-row",
            label: () => "_#(js:Append Row)",
            icon: () => "place-holder-icon icon-append-row",
            enabled: () => true,
            visible: () => this.addRowsVisible && !this.mobileDevice &&
               this.isActionVisibleInViewer("Append Row") && !this.annotationsSelected
         },
         {
            id: () => "table delete-rows",
            label: () => "_#(js:Delete Rows)",
            icon: () => "place-holder-icon icon-delete-row",
            enabled: () => true,
            visible: () => this.deleteRowsVisible && !this.annotationsSelected &&
               !this.mobileDevice
         },
         {
            id: () => "table delete-columns",
            label: () => "_#(js:Delete Columns)",
            icon: () => "place-holder-icon",
            enabled: () => true,
            visible: () => this.removeColumnsVisible && !this.annotationsSelected &&
               !this.mobileDevice
         },
         {
            id: () => "table column-options",
            label: () => "_#(js:Column Options)...",
            icon: () => "place-holder-icon",
            enabled: () => true,
            visible: () => this.columnOptionsVisible && !this.annotationsSelected &&
               !this.mobileDevice
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
            id: () => "table open-max-mode",
            label: () => "_#(js:Show Enlarged)",
            icon: () => "expand-icon",
            enabled: () => true,
            visible: () => this.openMaxModeVisible
         },
         {
            id: () => "table close-max-mode",
            label: () => "_#(js:Show Actual Size)",
            icon: () => "contract-icon",
            enabled: () => true,
            visible: () => this.closeMaxModeVisible
         },
         {
            id: () => "table show-details",
            label: () => "_#(js:Show Details)",
            icon: () => "show-detail-icon",
            visible: () => this.showDetailsVisible,
            enabled: () => true
         },
         {
            id: () => "table export",
            label: () => "_#(js:Export)",
            icon: () => "export-icon",
            visible: () => !this.vsWizardPreview && this.isActionVisibleInViewer("Export"),
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
         {
            id: () => "table edit",
            label: () => "_#(js:Edit)",
            icon: () => "edit-icon",
            visible: () => !this.vsWizardPreview && !this.isPopComponent() &&
               (!this.preview && !this.composer && !this.mobileDevice &&
                !this.binding && this.model.enableAdhoc && this.isActionVisibleInViewer("Edit")
                || this.composer && !this.annotationsSelected),
            enabled: () => true
         },
      ]));
      groups.push(new AssemblyActionGroup([
         {
            id: () => "table selection-reset",
            label: () => "_#(js:Reset)",
            icon: () => "reset-icon",
            visible: () => this.selectionApplyVisible && this.isActionVisibleInViewer("Reset"),
            enabled: () => true
         },
         {
            id: () => "table selection-apply",
            label: () => "_#(js:Apply)",
            icon: () => "submit-icon",
            visible: () => this.selectionApplyVisible && this.isActionVisibleInViewer("Apply"),
            enabled: () => true
         }
      ]));
      groups.push(new AssemblyActionGroup([
         {
            id: () => "table form-apply",
            label: () => "_#(js:Apply)",
            icon: () => "submit-icon",
            visible: () => this.formApplyVisible && this.isActionVisibleInViewer("Form Apply"),
            enabled: () => true
         }
      ]));

      return super.createToolbarActions(groups, true);
   }

   protected getEditScriptActionId(): string {
      return "table edit-script";
   }

   private get openMaxModeVisible():  boolean {
      return !this.model.maxMode && !this.binding && !this.composer &&
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

   private get showDetailsVisible(): boolean {
      return this.model.summary && this.model.selectedData && !this.model.form
         && this.model.selectedData.size > 0 && this.isActionVisibleInViewer("Show Details");
   }

   private get selectionApplyVisible(): boolean {
      // For form and embed table, when preview, the apply only submit to ws data, can
      // not reset data.
      if(this.viewer || this.preview) {
         if(this.model.embedded && this.model.form) {
            return false;
         }
      }

      return this.model.embedded && !this.model.submitOnChange && !this.formApplyVisible;
   }

   private get formApplyVisible(): boolean {
      return this.model.embedded && this.model.form &&
         this.model.writeBack && this.model.formChanged;
   }

   private get filterVisible(): boolean {
      return !this.binding && (!this.viewer && !this.preview || this.model.adhocFilterEnabled) &&
         !this.model.maxMode && !this.model.titleSelected && this.oneCellSelected &&
         !this.model.form && !this.model.embedded &&
         this.model.sourceType !== SourceInfoType.VS_ASSEMBLY;
   }

   private get addRowsVisible(): boolean {
      return (this.viewer || this.preview) && this.model.form && this.model.formVisible
         && this.model.insert && !this.model.titleSelected && !this.headerCellSelected
         && this.dataCellSelected;
   }

   private get deleteRowsVisible(): boolean {
      return (this.viewer || this.preview) && this.model.form && this.model.formVisible
         && this.model.del && !this.model.titleSelected && !this.headerCellSelected
         && this.dataCellSelected && this.isActionVisibleInViewer("Delete Rows");
   }

   private get hideColumnsVisible(): boolean {
      return !this.viewer && !this.preview && !this.model.selectedHeaders &&
         !this.model.selectedData && this.model.form;
   }

   private get removeColumnsVisible(): boolean {
      return !this.viewer && !this.preview && !this.model.titleSelected &&
         this.model.selectedHeaders &&
         (!this.model.selectedData || this.model.selectedData.size == 0);
   }

   private get columnOptionsVisible(): boolean {
      return !this.viewer && !this.preview && !this.model.titleSelected &&
         this.model.selectedHeaders && !this.model.selectedData && this.model.form;
   }

   private get conditionsVisible(): boolean {
      return (this.composer || (this.viewer || this.preview) && this.model.enableAdvancedFeatures
         && this.isActionVisible("Condition")) && !this.model.embedded
         && !this.model.selectedHeaders && !this.model.selectedData;
   }

   private get resetTableLayoutVisible(): boolean {
      return (this.composer && !this.model.selectedHeaders &&
              !this.model.selectedData || this.binding) && this.model.explicitTableWidth;
   }

   private get convertVisible(): boolean {
      return this.composer && !this.model.selectedHeaders && !this.model.selectedData;
   }

   private get hyperlinkVisible(): boolean {
      if(!this.composer) {
         return false;
      }

      if(this.model.titleSelected || !this.oneCellSelected) {
         return false;
      }

      if(this.model.form) {
         return false;
      }

      return !this.model.embedded;
   }

   private get highlightVisible(): boolean {
      return (this.composer || (this.viewer || this.preview)
         && this.model.enableAdvancedFeatures && this.isActionVisible("Highlight"))
         && !this.model.titleSelected && this.oneCellSelected;
   }

   private get copyHighlightVisible(): boolean {
      return this.composer && !this.model.titleSelected && this.oneCellSelected
         && this.hasHighlight;
   }

   private get pasteHighlightVisible(): boolean {
      return this.composer && !this.model.titleSelected && this.oneCellSelected &&
         this.model.isHighlightCopied;
   }
}
