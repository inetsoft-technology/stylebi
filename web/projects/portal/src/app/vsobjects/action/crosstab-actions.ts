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
import { Tool } from "../../../../../shared/util/tool";
import { SourceInfoType } from "../../binding/data/source-info-type";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { TableDataPathTypes } from "../../common/data/table-data-path-types";
import { XSchema } from "../../common/data/xschema";
import { ChartConstants } from "../../common/util/chart-constants";
import { DrillLevel } from "../../composer/data/vs/drill-level";
import { ContextProvider } from "../context-provider.service";
import { BaseTableCellModel } from "../model/base-table-cell-model";
import { VSCrosstabModel } from "../model/vs-crosstab-model";
import { DataTipService } from "../objects/data-tip/data-tip.service";
import { PopComponentService } from "../objects/data-tip/pop-component.service";
import { VSCrosstab } from "../objects/table/vs-crosstab.component";
import { ActionStateProvider } from "./action-state-provider";
import { BaseTableActions } from "./base-table-actions";
import { MiniToolbarService } from "../objects/mini-toolbar/mini-toolbar.service";

export class CrosstabActions extends BaseTableActions<VSCrosstabModel> {
   constructor(model: VSCrosstabModel, contextProvider: ContextProvider,
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
            id: () => "crosstab properties",
            label: () => "_#(js:Properties)...",
            icon: () => "place-holder-icon icon-properties",
            enabled: () => true,
            visible: () => !this.annotationsSelected && (this.composerBinding || this.composer)
         },
         {
            id: () => "crosstab show-format-pane",
            label: () => "_#(js:Format)...",
            icon: () => "fa fa-format",
            enabled: () => true,
            visible: () => !this.annotationsSelected && (this.binding || this.composer)
         },
      ]));
      groups.push(new AssemblyActionGroup([
         {
            id: () => "crosstab hyperlink",
            label: () => "_#(js:Hyperlink)...",
            icon: () => "place-holder-icon icon-hyperlink",
            enabled: () => true,
            visible: () => !this.annotationsSelected && this.hyperlinkVisible &&
               this.isActionVisibleInViewer("Hyperlink")
         },
         {
            id: () => "crosstab highlight",
            label: () => "_#(js:Highlight)...",
            icon: () => "place-holder-icon icon-highlight",
            enabled: () => true,
            visible: () => this.highlightVisible && !this.annotationsSelected
         },
         {
            id: () => "crosstab copy-highlight",
            label: () => "_#(js:Copy Highlight)",
            icon: () => "place-holder-icon icon-copy-highlight",
            enabled: () => true,
            visible: () => this.copyHighlightVisible && !this.annotationsSelected
         },
         {
            id: () => "crosstab paste-highlight",
            label: () => "_#(js:Paste Highlight)",
            icon: () => "place-holder-icon icon-paste-highlight",
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
            id: () => "crosstab hide column",
            label: () => "_#(js:Hide Column)",
            icon: () => "place-holder-icon icon-hyperlink",
            enabled: () => true,
            visible: () => !this.annotationsSelected &&
               !this.model.titleSelected && !this.model.metadata && this.cellSelected &&
               this.isActionVisibleInViewer("Hide Column")
         },
         {
            id: () => "crosstab show columns",
            label: () => "_#(js:Show Columns)",
            icon: () => "place-holder-icon icon-highlight",
            enabled: () => true,
            visible: () => !this.annotationsSelected && this.model.hasHiddenColumn &&
               !this.model.metadata
         }
      ]));
      groups.push(new AssemblyActionGroup([
         {
            id: () => "crosstab conditions",
            label: () => "_#(js:Conditions)...",
            icon: () => "place-holder-icon icon-conditions",
            enabled: () => true,
            visible: () => this.conditionVisible && !this.annotationsSelected &&
               (!this.model.cubeType || this.model.worksheetCube)
         },
         {
            id: () => "crosstab reset-table-layout",
            label: () => "_#(js:Reset Table Layout)",
            icon: () => "place-holder-icon, icon-reset-layout",
            enabled: () => true,
            visible: () => this.resetTableLayoutVisible && !this.annotationsSelected
         },
         {
            id: () => "crosstab convert-to-freehand-table",
            label: () => "_#(js:Convert to Freehand Table)",
            icon: () => "place-holder-icon, icon-convert-freehand",
            enabled: () => this.model.cubeType == null || this.model.worksheetCube,
            visible: () => this.convertVisible && !this.annotationsSelected
         },
         {
            id: () => "crosstab annotate",
            label: () => this.cellSelected ? "_#(js:Annotate Cell)" : "_#(js:Annotate Component)",
            icon: () => "edit-icon",
            enabled: () => this.securityEnabled,
            visible: () => !this.annotationsSelected && (this.viewer || this.preview) &&
               !this.model.maxMode &&
               this.securityEnabled && this.model.enabled && !this.mobileDevice &&
               this.isActionVisibleInViewer(this.cellSelected ? "Annotate Cell" : "Annotate Component") &&
               (!this.stateProvider || this.stateProvider.isActionVisible("Annotation", this.model))
         },
         {
            id: () => "crosstab filter",
            label: () => "_#(js:Filter)",
            icon: () => "place-holder-icon icon-filter",
            enabled: () => true,
            visible: () => this.filterVisible && !this.annotationsSelected
         },
         {
            id: () => "crosstab date-comparison",
            label: () => "_#(js:Date Comparison)...",
            icon: () => "fa fa-filter",
            enabled: () => true,
            visible: () => this.dateComparisonVisible() && this.isActionVisibleInViewer("Date Comparison")
         },
      ]));
      groups.push(new AssemblyActionGroup([
         {
            id: () => "crosstab group",
            label: () => "_#(js:Group)...",
            icon: () => "place-holder-icon icon-group",
            enabled: () => true,
            visible: () => this.groupVisible && !this.annotationsSelected &&
               (this.composer || this.isActionVisibleInViewer("Group"))
         },
         {
            id: () => "crosstab rename",
            label: () => "_#(js:Rename)...",
            icon: () => "place-holder-icon icon-rename",
            enabled: () => true,
            visible: () => this.renameVisible && !this.annotationsSelected &&
               (this.composer || this.isActionVisibleInViewer("Rename"))
         },
         {
            id: () => "crosstab ungroup",
            label: () => "_#(js:Ungroup)",
            icon: () => "place-holder-icon icon-ungroup",
            enabled: () => true,
            visible: () => this.renameVisible && !this.annotationsSelected &&
               (this.composer || this.isActionVisibleInViewer("Ungroup"))
         }
      ]));

      groups.push(new AssemblyActionGroup([
         {
            id: () => "expand all",
            label: () => this.getDrillLabel(),
            icon: () => "place-holder-icon",
            enabled: () => true,
            visible: () => this.getDrillContextMenuVisible()
         },
         {
            id: () => "collapse all",
            label: () => this.getDrillLabel(false),
            icon: () => "place-holder-icon",
            enabled: () => true,
            visible: () => this.getDrillContextMenuVisible(false, true)
         },
         {
            id: () => "expand field",
            label: () => this.getDrillLabel(true, true),
            icon: () => "place-holder-icon",
            enabled: () => true,
            visible: () => this.getDrillContextMenuVisible(true)
         },
         {
            id: () => "collapse field",
            label: () => this.getDrillLabel(false, true),
            icon: () => "place-holder-icon",
            enabled: () => true,
            visible: () => this.getDrillContextMenuVisible(true)
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
            id: () => "crosstab drilldown",
            label: () => "_#(js:Drill Down Filter)",
            icon: () => "drill-down-filter-icon",
            enabled: () => true,
            visible: () => this.drillActionVisible() && this.isActionVisibleInViewer("Drill Down Filter")
               && !this.isDataTip() && !this.isPopComponent()
         },
         {
            id: () => "crosstab drillup",
            label: () => "_#(js:Drill Up Filter)",
            icon: () => "drill-up-filter-icon",
            enabled: () => true,
            visible: () => this.drillActionVisible(true) && this.isActionVisibleInViewer("Drill Up Filter")
               && !this.isDataTip() && !this.isPopComponent()
         },
         {
            id: () => "crosstab open-max-mode",
            label: () => "_#(js:Show Enlarged)",
            icon: () => "expand-icon",
            enabled: () => true,
            visible: () => this.openMaxModeVisible
         },
         {
            id: () => "crosstab close-max-mode",
            label: () => "_#(js:Show Actual Size)",
            icon: () => "contract-icon",
            enabled: () => true,
            visible: () => this.closeMaxModeVisible
         },
         {
            id: () => "crosstab show-details",
            label: () => "_#(js:Show Details)",
            icon: () => "show-detail-icon",
            visible: () => this.showDetailsVisible,
            enabled: () => true
         },
         {
            id: () => "crosstab export",
            label: () => "_#(js:Export)",
            icon: () => "export-icon",
            visible: () => !this.vsWizardPreview && this.isActionVisible("Export"),
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
         {
            id: () => "crosstab edit",
            label: () => "_#(js:Edit)",
            icon: () => "edit-icon",
            visible: () => this.editVisibility(),
            enabled: () => true
         },
      ]));
      return super.createToolbarActions(groups, true);
   }

   private editVisibility(): boolean {
      return !this.vsWizardPreview && !this.isPopComponent() && !this.embed &&
      (this.viewer && this.isActionVisible("Edit") && !this.mobileDevice &&
         !this.binding && this.model.enableAdhoc && this.model.visible &&
         !this.isDataTip() || this.composer && !this.annotationsSelected);
   }

   protected getEditScriptActionId(): string {
      return "crosstab edit-script";
   }

   private get openMaxModeVisible():  boolean {
      return !this.model.maxMode && !this.binding &&
         !this.composer && this.isActionVisibleInViewer("Open Max Mode") &&
         this.isActionVisibleInViewer("Maximize") && !this.isDataTip() &&
         !this.isPopComponent() && this.isActionVisibleInViewer("Show Enlarged");
   }

   private get closeMaxModeVisible():  boolean {
      return this.model.maxMode &&
         !this.binding && this.isActionVisibleInViewer("Close Max Mode") &&
         !this.isDataTip() && !this.isPopComponent() &&
         this.isActionVisibleInViewer("Show Actual Size");
   }

   private get hyperlinkVisible(): boolean {
      return this.composer && !this.model.titleSelected && this.oneCellSelected;
   }

   private get highlightVisible(): boolean {
      if(!this.composer && !((this.viewer || this.preview) &&
         this.model.enableAdvancedFeatures && this.isActionVisible("Highlight")))
      {
         return false;
      }

      if(this.isPeridHeader()) {
         return false;
      }

      return !this.binding && this.oneCellSelected && this.selectedCellAllowed
         && this.cellTypeAllowed;
   }

   private isPeridHeader(): boolean {
      if(this.model == null || this.model.selectedRegions == null) {
         return false;
      }

      const region = this.model.selectedRegions[0];

      if(!!!region || !!!region.path || region.path.length == 0) {
         return false;
      }

      if(this.model.containsFakeAggregate &&
         region.path[region.path.length - 1] === "Aggregate")
      {
         return false;
      }

      if(region.type != TableDataPathTypes.GROUP_HEADER) {
         return false;
      }

      let path = region.path[region.path.length - 1];

      if(this.model.rowNames.length == 0 && this.model.colNames.length == 0) {
         return true;
      }

      for(let row of this.model.rowNames) {
         if(this.isSameField(path, row)) {
            return false;
         }
      }

      for(let col of this.model.colNames) {
         if(this.isSameField(path, col)) {
            return false;
         }
      }

      for(let agg of this.model.aggrNames) {
         if(path == agg) {
            return false;
         }
      }

      return true;
   }

   private isSameField(path: string, name: string) {
      path = path.startsWith("None(") && path.endsWith(")") ? path.substring(5, path.length - 1) : path;
      name = name.startsWith("None(") && name.endsWith(")") ? name.substring(5, name.length - 1) : name;

      return path == name;
   }

   private get copyHighlightVisible(): boolean {
      return this.highlightVisible && this.composer && this.hasHighlight;
   }

   private get pasteHighlightVisible(): boolean {
      return this.composer && this.model.isHighlightCopied && !this.binding &&
         this.cellTypeAllowed && this.selectedCellAllowed;
   }

   private get cellTypeAllowed(): boolean {
      const cellType = this.model.selectedRegions[0].type;

      return cellType === TableDataPathTypes.GROUP_HEADER || cellType === TableDataPathTypes.SUMMARY
         || (cellType === TableDataPathTypes.GRAND_TOTAL && !this.model.selectedHeaders);
   }

   private get selectedCellAllowed(): boolean {
      return !this.model.titleSelected && this.model.selectedRegions
         && this.model.selectedRegions.length !== 0;
   }

   private get conditionVisible(): boolean {
      return (this.composer || (this.viewer || this.preview) &&
         this.model.enableAdvancedFeatures &&
         this.isActionVisibleInViewer("Condition")) &&
         !this.model.selectedHeaders && !this.model.selectedData;
   }

   private get resetTableLayoutVisible(): boolean {
      return ((this.composer && !this.model.selectedHeaders && !this.model.selectedData) || this.binding) &&
         this.model.explicitTableWidth === true;
   }

   private get convertVisible(): boolean {
      return this.composer && !this.model.selectedHeaders && !this.model.selectedData &&
         this.isActionVisibleInViewer("Convert to Freehand Table");
   }

   private get groupVisible(): boolean {
      if(!this.model.selectedHeaders || this.model.selectedData && this.model.selectedData.size ||
         this.model.firstSelectedColumn == null || this.model.firstSelectedRow == null ||
         this.model.titleSelected || this.oneCellSelected || this.model.cubeType != null)
      {
         return false;
      }

      const map = this.model.selectedHeaders;
      let sameColumn: boolean = true;
      let validCell: boolean = true;
      let prevCol: number = -1;

      map.forEach((columns, row) => {
         row = row - this.startRowIndex;

         if(sameColumn && validCell) {
            if(columns.length > 1) {
               sameColumn = false;
            }
            else if(prevCol == -1) {
               prevCol = columns[0];
               sameColumn = true;
            }
            else {
               sameColumn = sameColumn && columns.length === 1 && columns[0] === prevCol;
            }

            validCell = validCell && !columns.some((column) =>
               this.model.cells[row][column].dataPath.type !== TableDataPathTypes.GROUP_HEADER);
         }
      });

      const rowIndex = this.model.firstSelectedRow - this.startRowIndex;
      const dataPath = this.model.cells[rowIndex][this.model.firstSelectedColumn].dataPath;
      const dataType: string = dataPath.dataType;
      const dateType: boolean = dataType == XSchema.TIME_INSTANT
         || dataType == XSchema.DATE || dataType == XSchema.TIME;
      const dateRange: boolean =
         this.model.dateRangeNames.indexOf(dataPath.path[dataPath.path.length - 1]) >= 0;
      const booleanType: boolean = dataType == XSchema.BOOLEAN;
      const field = this.model.cells[rowIndex][this.model.firstSelectedColumn].field;

      for(let agg of this.model.aggrNames) {
         if(field == agg) {
            return false;
         }
      }

      if(this.model.dataTypeMap != null) {
         let dtype: string = this.model.dataTypeMap[field];

         if(dtype == XSchema.BOOLEAN) {
            return false;
         }
      }

      const dir = VSCrosstab.getDrillDirection(
         this.model, rowIndex, map.get(this.model.firstSelectedRow)[0]);

      return (map.size === 1 && dir == ChartConstants.DRILL_DIRECTION_X ||
         sameColumn && dir == ChartConstants.DRILL_DIRECTION_Y)
         && validCell && !dateType && !booleanType && !dateRange;
   }

   private get renameVisible(): boolean {
      if(!this.model.selectedHeaders || this.model.selectedData || this.model.titleSelected ||
         !this.model.cells || this.model.firstSelectedRow == -1 ||
         this.model.firstSelectedColumn == -1 || !this.oneCellSelected)
      {
         return false;
      }

      const row = this.model.firstSelectedRow;
      const col = this.model.firstSelectedColumn;
      const rows = this.model.cells.find((r) => r.some((c) => c.row === row));

      let cell;

      if(rows) {
         cell = rows.find((c) => c.col === col);
      }

      const startRow = this.model.cells[0][0].row;
      let dataPath;

      if(row - startRow >= 0) {
         dataPath = this.model.cells[row - startRow][col]?.dataPath;
      }
      else {
         dataPath = this.model.cells[row][col]?.dataPath;
      }

      const dateRange: boolean =
         this.model.dateRangeNames.indexOf(dataPath?.path[dataPath.path.length - 1]) >= 0;

      return cell != null && cell.grouped && !dateRange;
   }

   private drillActionVisible(drillUp: boolean = false): boolean {
      if(this.vsWizardPreview || this.binding || this.model.dateComparisonDefined ||
         this.model.sourceType == SourceInfoType.VS_ASSEMBLY)
      {
         return false;
      }

      const selectedCells = Tool.getSortedSelectedHeaderCell(this.model, false, false);

      return selectedCells.some(cell => {
         // period do not support drill filter.
         if(cell.period) {
            return false;
         }

         if(cell != null && cell.dataPath != null &&
            cell.dataPath.type == TableDataPathTypes.HEADER)
         {
            return false;
         }

         if(drillUp) {
            return cell.drillLevel == DrillLevel.Middle
               || cell.drillLevel == DrillLevel.Leaf
               || this.model.filterFields.includes(cell.field);
         }
         else {
            return cell.drillLevel != null && cell.drillLevel != DrillLevel.None;
         }
      });
   }

   private getDrillLabel(isExpand: boolean = true, drillField: boolean = false): string {
      const row: number = +this.model.firstSelectedRow - this.startRowIndex;
      const col: number = +this.model.firstSelectedColumn;
      let prefix = "";
      let suffix = "";

      if(isExpand) {
         prefix = drillField ? "_#(js:Expand Field)" : "_#(js:Expand Hierarchy)";
      }
      else {
         prefix = drillField ? "_#(js:Collapse Field)" : "_#(js:Collapse Hierarchy)";
      }

      if(drillField && row >= 0 && col >= 0) {
         suffix = `[${this.model.cells[row][col].field}]`;
      }
      else if(this.model.titleSelected || (row < 0 && col < 0 && this.model.cells.length > 0)) {
         suffix = "";
      }
      else {
         const selectedCells = Tool.getSortedSelectedHeaderCell(this.model, false);

         suffix = selectedCells.filter(cell => !!cell.drillOp)
            .map(cell => cell.cellLabel)
            .join(",");

         suffix = " (" + suffix + ")";
      }

      return `${prefix} ${suffix}`;
   }

   private getDrillContextMenuVisible(drillField: boolean = false, isCollapse = false): boolean {
      if(!this.model.cells || this.model.cells.length <= 0 || this.annotationsSelected ||
         this.model.dateComparisonDefined)
      {
         return false;
      }

      const row: number = +this.model.firstSelectedRow - this.startRowIndex;
      const col: number = +this.model.firstSelectedColumn;

      if(!drillField && (this.model.titleSelected
         || (row < 0 && col < 0 && this.model.cells.length > 0)))
      {
         // Drill all cells
         return this.hasDrillableCells(this.model.cells, (op) => op ===
            (isCollapse ? ChartConstants.DRILL_UP_OP : ChartConstants.DRILL_DOWN_OP));
      }
      else if(row < 0 || col < 0 || row >= this.model.cells.length
         || !!!this.model.cells[row] || col >= this.model.cells[row].length)
      {
         return false;
      }
      else if(drillField) {
         const selectedCell: BaseTableCellModel = this.model.cells[row][col];

         return this.singleSelected && !this.model.titleSelected && !!selectedCell.drillOp && !selectedCell.period;
      }

      const selectedCells = Tool.getSortedSelectedHeaderCell(this.model, false);

      return selectedCells.some((cell) => !!cell.drillOp && !cell.period);
   }

   private get singleSelected(): boolean {
      return !!this.model.selectedRegions && this.model.selectedRegions.length === 1;
   }

   private hasDrillableCells(cells: BaseTableCellModel[][],
                             accept: (drillOp) => boolean = (drillOp) => !!drillOp): boolean
   {
      for(let i = 0; i < cells.length; i++) {
         for(let j = 0; j < cells[i].length; j++) {
            if(accept(cells[i][j].drillOp) && !cells[i][j].period) {
               return true;
            }
         }
      }

      return false;
   }

   private get showDetailsVisible(): boolean {
      if(!this.isActionVisibleInViewer("Show Details")) {
         return false;
      }

      const selectedCells = this.model.selectedData && this.model.selectedData.size ?
         this.model.selectedData : this.model.selectedHeaders;
      let detailsCell: boolean = false;

      // complicated logic from ShowDetailEvent that dictates whether a crosstab cell
      // is able to show details. Checks if
      if(selectedCells) {
         selectedCells.forEach((cols, row) => {
            cols.forEach((col) => {
               detailsCell = detailsCell ||
                  (row > 0 || this.model.runtimeColHeaderCount > 0) &&
                  (col > 0 || this.model.runtimeRowHeaderCount > 0 ||
                     this.model.runtimeColHeaderCount === 0) &&
                  !(row < this.model.headerRowCount && col < this.model.headerColCount);
            });
         });
      }

      return detailsCell;
   }

   private get filterVisible(): boolean {
      if(this.binding || !this.oneCellSelected || this.model.titleSelected ||
         this.model.maxMode || !this.model.selectedRegions ||
         this.model.selectedRegions.length === 0 ||
         this.model.sourceType === SourceInfoType.VS_ASSEMBLY)
      {
         return false;
      }

      if((this.viewer || this.preview) && !this.model.adhocFilterEnabled) {
         return false;
      }

      const region = this.model.selectedRegions[0];

      if(this.model.containsFakeAggregate &&
         region.path[region.path.length - 1] === "Aggregate")
      {
         return false;
      }

      let paths = region.path;

      if(!paths || paths.length === 0 ||
         (!!this.model.dcMergedColumn && paths[paths.length - 1] == this.model.dcMergedColumn ||
            !!this.model.dateRangeNames && this.model.dateComparisonDefined &&
         this.model.dateRangeNames.findIndex((name) => name == paths[paths.length - 1]) >= 0))
      {
         return false;
      }

      const cellType = region.type;

      if(this.isPeridHeader()) {
         return false;
      }

      if(this.model.cubeType != null && (cellType === TableDataPathTypes.SUMMARY
         || cellType === TableDataPathTypes.GRAND_TOTAL))
      {
         return false;
      }

      return cellType === TableDataPathTypes.GROUP_HEADER ||
         cellType === TableDataPathTypes.SUMMARY ||
         cellType === TableDataPathTypes.GRAND_TOTAL && !this.model.selectedHeaders;
   }

   /**
    * @return the real measure header for the duplicated measure.
    */
   private getRealAggrHeader(header: string): string {
      let aggrNames = this.model.aggrNames;

      if(header == null || aggrNames == null || aggrNames.length == 0) {
         return header;
      }

      if(aggrNames.indexOf(header) != -1) {
         return header;
      }

      let idx = header.lastIndexOf(".");

      if(idx == -1 || idx >= header.length - 1) {
         return header;
      }

      let tail = header.substring(idx + 1);
      let num = parseInt(tail, 10);
      return isNaN(num) ? header : header.substring(0, idx);
   }

   /**
    * row index is not start with 0 after loading data.
    */
   get startRowIndex(): number {
      return !!this.model && !!this.model.cells && this.model.cells.length > 0
         ? this.model.cells[0][0].row : 0;
   }

   private dateComparisonVisible(): boolean {
      if(!this.model || this.mobileDevice) {
         return false;
      }

      return this.model.dateComparisonEnabled && !this.model.selectedHeaders &&
         !this.model.selectedData && !this.annotationsSelected;
   }
}
