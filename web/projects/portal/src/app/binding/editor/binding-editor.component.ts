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
import { AfterViewInit, Component, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild } from "@angular/core";
import { DataRef } from "../../common/data/data-ref";
import { FormatInfoModel } from "../../common/data/format-info-model";
import { GraphTypes } from "../../common/graph-types";
import { UIContextService } from "../../common/services/ui-context.service";
import { GuiTool } from "../../common/util/gui-tool";
import { TreeNodeModel } from "../../widget/tree/tree-node-model";
import { BindingModel } from "../data/binding-model";
import { ChartBindingModel } from "../data/chart/chart-binding-model";
import { CrosstabBindingModel } from "../data/table/crosstab-binding-model";
import { TableBindingModel } from "../data/table/table-binding-model";
import { BindingService } from "../services/binding.service";
import { SidebarTab, DataEditorTabPane } from "../widget/binding-tree/data-editor-tab-pane.component";
import { FormatsPane } from "./formats-pane.component";
import { NotificationsComponent } from "../../widget/notifications/notifications.component";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { ConsoleDialogComponent } from "../../widget/console-dialog/console-dialog.component";
import { ConsoleMessage } from "../../widget/console-dialog/console-message";
import { Tool } from "../../../../../shared/util/tool";
import { GraphUtil } from "../util/graph-util";
import { AestheticInfo } from "../data/chart/aesthetic-info";
import { TextLayoutModel } from "../../common/data/visual-frame-model";
import { TextLayoutDesignerComponent } from "./chart/aesthetic/text-layout-designer.component";
import { ChartEditorService } from "../services/chart/chart-editor.service";
import { ModelService } from "../../widget/services/model.service";
import { StatusBar } from "../../status-bar/status-bar.component";
import { CalcDataPane } from "./table/calc-data-pane.component";
import { CrosstabDataPane } from "./table/crosstab-data-pane.component";
import { TableDataPane } from "./table/table-data-pane.component";
import { ChartDataPane } from "./chart/chart-data-pane.component";
import { CalcOptionPane } from "./table/calc-option-pane.component";
import { CrosstabOption } from "./table/crosstab-option.component";
import { TableOption } from "./table/table-option.component";
import { AestheticPane } from "./chart/aesthetic/aesthetic-pane.component";
import { ChartHighLowPane } from "./chart/chart-high-low-pane.component";
import { ChartEditorToolbar } from "./chart/chart-editor-toolbar.component";
import { DataEditorBindingTree } from "../widget/binding-tree/data-editor-binding-tree.component";
import { SplitPane } from "../../widget/split-pane/split-pane.component";
import { EditorTitleBar } from "./editor-title-bar.component";
import { NgClass } from "@angular/common";

const GET_MESSAGE_LEVELS_URI = "../api/composer/console-dialog/get-message-levels/";

@Component({
    selector: "binding-editor",
    templateUrl: "binding-editor.component.html",
    styleUrls: ["binding-editor.component.scss"],
    imports: [EditorTitleBar, NgClass, SplitPane, DataEditorTabPane, FormatsPane, DataEditorBindingTree, ChartEditorToolbar, ChartHighLowPane, AestheticPane, TableOption, CrosstabOption, CalcOptionPane, ChartDataPane, TableDataPane, CrosstabDataPane, CalcDataPane, StatusBar, NotificationsComponent, ConsoleDialogComponent, TextLayoutDesignerComponent]
})
export class BindingEditor implements OnInit, AfterViewInit, OnDestroy {
   @Input() objectModel: any;
   @Input() currentFormat: FormatInfoModel;
   @Input() linkUri: string;
   @Input() runtimeId: string;
   @Input() assetId: string;
   @Input() objectType: string;
   @Input() formatPaneDisabled: boolean;
   @Input() sourceName: string;
   @Input() selectedNodes: TreeNodeModel[] = [];
   @Input() goToWizardVisible: boolean = false;
   @Input() backToReportWizardVisible: boolean = false;
   @Input() rmode: number;
   @Input() consoleMessages: ConsoleMessage[] = [];
   @Output() onUpdateFormat = new EventEmitter<FormatInfoModel>();
   @Output() onUpdateData: EventEmitter<string> = new EventEmitter<string>();
   @Output() onClose: EventEmitter<boolean> = new EventEmitter<boolean>();
   @Output() onOpenWizardPane = new EventEmitter<any>();
   @Output() onOpenReportWizard = new EventEmitter<any>();
   @Output() resizePane = new EventEmitter<any>();
   @Output() onInitialized = new EventEmitter<void>();
   @Output() onChangeReportMode = new EventEmitter<number>();
   @Output() onMessageChange = new EventEmitter<ConsoleMessage[]>();
   @ViewChild("formatsPane") formatsPane: FormatsPane;
   @ViewChild("notifications") notifications: NotificationsComponent;
   @ViewChild("consoleDialog") consoleDialog: ConsoleDialogComponent;
   SidebarTab = SidebarTab;
   selectedTab: SidebarTab = SidebarTab.BINDING_TREE;
   grayedOutValues: string[] = [];
   hideFormatPane: boolean;
   chartMaxMode: boolean;
   showDragTip: boolean = true;
   showDcAppliedTip: boolean = false;
   messageLevels: string[] = [];
   // Text Layout Designer overlay state.
   showLayoutDesigner: boolean = false;
   layoutDesignerLayout: TextLayoutModel | null = null;
   layoutDesignerTextFields: AestheticInfo[] = [];
   layoutDesignerAggregateName: string | null = null;
   // FIELD item type constant (mirrors TextLayoutDesignerComponent.FIELD).
   private readonly LAYOUT_FIELD_TYPE = 0;
   private _bindingModel: BindingModel;
   private _variableValues: string[];
   private _assemblyName: string;

   constructor(private bindingService: BindingService,
               private uiContextService: UIContextService,
               protected modalService: NgbModal,
               private modelService: ModelService,
               private chartEditorService: ChartEditorService)
   {
   }

   @Input()
   set assemblyName(assemblyName: string) {
      this._assemblyName = assemblyName;
      this.bindingService.assemblyName = assemblyName;
   }

   get assemblyName(): string {
      return this._assemblyName;
   }

   get isVS(): boolean {
      return this.uiContextService.isVS();
   }

   get formatsInactive(): boolean {
      return this.selectedTab !== SidebarTab.FORMAT_PANE;
   }

   get formatsDisabled(): boolean {
      return this.hideFormatPane || this.formatPaneDisabled;
   }

   ngOnInit() {
      this.bindingService.runtimeId = this.runtimeId;
      this.bindingService.assemblyName = this.assemblyName;
      this.bindingService.objectType = this.objectType;
      this.bindingService.bindingModel = this.bindingModel;
      let type = !!this.bindingModel ? this.bindingModel.type : null;

      if(type == "chart") {
         this.showDcAppliedTip = (<ChartBindingModel> this.bindingModel).hasDateComparison;
      }

      if(type == "crosstab") {
         this.showDcAppliedTip = (<CrosstabBindingModel> this.bindingModel).hasDateComparison;
      }
   }

   ngAfterViewInit(): void {
      this.onInitialized.emit();
   }

   ngOnDestroy() {
      this.bindingService.clear();
   }

   @Input()
   set grayedOutFields(fields: DataRef[]) {
      this.bindingService.setGrayedOutFields(fields);

      if(!fields) {
         return;
      }

      this.grayedOutValues = [];

      if(fields.length > 0) {
         for(let fld of fields) {
            this.grayedOutValues.push(fld.name);
         }
      }
   }

   @Input()
   set bindingModel(_bindingModel: BindingModel) {
      this._bindingModel = _bindingModel;
      this.bindingService.bindingModel = _bindingModel;
      this.showDragTip = !this.isBound(_bindingModel);
   }

   get bindingModel(): BindingModel {
      return this._bindingModel;
   }

   get tableBindingModel(): TableBindingModel {
      return this._bindingModel as TableBindingModel;
   }

   get crosstabBindingModel(): CrosstabBindingModel {
      return this._bindingModel as CrosstabBindingModel;
   }

   @Input()
   set variableValues(_variableValues: string[]) {
      this._variableValues = _variableValues;
      this.bindingService.variableValues = _variableValues;
   }

   get variableValues(): string[] {
      return this._variableValues;
   }

   switchTab(tab: SidebarTab): void {
      this.selectedTab = tab;
   }

   get formatPaneVisible(): boolean {
      return this.selectedTab == SidebarTab.FORMAT_PANE;
   }

   get miniToolbarHeight(): number {
      return GuiTool.MINI_TOOLBAR_HEIGHT;
   }

   showHighLowPane(): boolean {
      let chartBinding: ChartBindingModel =
         this.bindingModel && this.bindingModel.hasOwnProperty("chartType") ?
         <ChartBindingModel> this.bindingModel : null;

      return chartBinding &&
         (GraphTypes.CHART_STOCK === chartBinding.chartType ||
          GraphTypes.CHART_CANDLE === chartBinding.chartType ||
          GraphTypes.isGeo(chartBinding.chartType) ||
          GraphTypes.CHART_TREE === chartBinding.chartType ||
          GraphTypes.CHART_NETWORK === chartBinding.chartType ||
          GraphTypes.CHART_CIRCULAR === chartBinding.chartType ||
          GraphTypes.CHART_GANTT === chartBinding.chartType ||
          chartBinding.supportsPathField);
   }

   get bindingType(): string {
      return this.bindingModel.type;
   }

   updateData(action: string) {
      if(action.startsWith("openLayoutDesigner")) {
         const parts = action.split(":");
         const aggregateName = parts.length > 1 ? parts[1] : null;
         this.openLayoutDesignerOverlay(aggregateName);
         return;
      }

      switch(action) {
         case "getCurrentFormat":
            this.hideFormatPane = false;
            this.onUpdateData.emit(action);
            break;
         case "showTextFormat":
            this.selectedTab = SidebarTab.FORMAT_PANE;
            this.hideFormatPane = false;
            this.onUpdateData.emit("getTextFormat");
            break;
         case "getTextFormat":
            this.hideFormatPane = false;
            this.onUpdateData.emit("getTextFormat");
            break;
         case "hideFormatPane":
            this.hideFormatPane = true;
            break;
         case "openFormatPane":
            this.hideFormatPane = false;
            this.selectedTab = SidebarTab.FORMAT_PANE;
            this.onUpdateData.emit("getCurrentFormat");
            break;
         default:
             this.onUpdateData.emit(action);
      }
   }

   private openLayoutDesignerOverlay(aggregateName: string | null): void {
      const bindingModel = this.bindingModel as ChartBindingModel;
      if(!bindingModel) return;

      this.layoutDesignerAggregateName = aggregateName;

      const target = this.getLayoutTarget(bindingModel, aggregateName);

      this.layoutDesignerLayout = target?.textLayout ?? null;
      // The designer's FIELD items index into this real binding list.
      this.layoutDesignerTextFields = (target?.textFields ?? []).slice();

      // Seed from the legacy single Text binding: if there are no textFields yet but a
      // scalar textField exists, show it as field 0 and initialize the layout to a single
      // row with one FIELD item referencing index 0. Seed the WORKING COPY only; it is applied
      // to the model on Commit (the backend maps textFields -> textLayoutFields), so Cancel
      // leaves the bound model untouched.
      if(this.layoutDesignerTextFields.length === 0 && target?.textField?.dataInfo) {
         const seed = Tool.clone(target.textField) as AestheticInfo;
         this.layoutDesignerTextFields = [seed];

         if(!this.layoutDesignerLayout?.rows?.length) {
            this.layoutDesignerLayout = {
               rows: [{ items: [{ type: this.LAYOUT_FIELD_TYPE, fieldIndex: 0 }] }]
            };
         }
      }

      this.showLayoutDesigner = true;
   }

   /**
    * Resolve the layout target: the per-aggregate ref when an aggregate name is supplied
    * (multi-style), otherwise the chart-level binding model. Both carry textField /
    * textFields / textLayout.
    */
   private getLayoutTarget(bindingModel: ChartBindingModel,
                           aggregateName: string | null): any
   {
      if(aggregateName) {
         const aggrs = GraphUtil.getAestheticAggregateRefs(bindingModel, false);
         return aggrs.find(a => a.fullName === aggregateName) ?? null;
      }

      return bindingModel;
   }

   /**
    * A tree column was dropped onto the layout grid. The designer built the AestheticInfo
    * client-side; append it to the designer's WORKING COPY only (layoutDesignerTextFields). The
    * live model (target.textFields) is applied once, on Commit — so Cancel discards.
    */
   onLayoutAddField(e: { field: AestheticInfo; insertRow: number; insertIndex: number }): void {
      if(!this.bindingModel || !e?.field) {
         return;
      }

      this.layoutDesignerTextFields = [...this.layoutDesignerTextFields, e.field];
   }

   /**
    * A FIELD chip was removed in the designer; drop the binding at this index from the WORKING
    * COPY only. The component has already compacted the remaining FIELD items' indices to match.
    * The live model is left untouched until Commit, so Cancel discards the removal.
    */
   onLayoutRemoveField(fieldIndex: number): void {
      if(fieldIndex < 0 || fieldIndex >= this.layoutDesignerTextFields.length) {
         return;
      }

      const next = this.layoutDesignerTextFields.slice();
      next.splice(fieldIndex, 1);
      this.layoutDesignerTextFields = next;
   }

   onLayoutDesignerCommit(result: TextLayoutModel): void {
      const bindingModel = this.bindingModel as ChartBindingModel;
      if(!bindingModel) return;

      const target = this.getLayoutTarget(bindingModel, this.layoutDesignerAggregateName);

      if(target) {
         target.textLayout = result;
         // layoutDesignerTextFields is the authoritative field list; apply it on commit so the
         // sent model carries the fields.
         target.textFields = this.layoutDesignerTextFields.slice();
      }

      // Apply textFields AND textLayout together via the full model round-trip so the layout's
      // field indices resolve against the same assembly that carries the fields.
      this.chartEditorService.changeChartAesthetic("text");
      this.showLayoutDesigner = false;
   }

   onLayoutDesignerCancel(): void {
      this.showLayoutDesigner = false;
   }

   /**
    * A FIELD chip's binding was edited in the designer (aggregate/value-type/convert). chart-fieldmc
    * mutates the bound AestheticInfo in place — a live reference inside textFields — so the edit is
    * already captured; it persists on commit. No mid-session round-trip (it previously wiped fields).
    */
   onLayoutFieldChanged(): void {
      // no-op: the chip's in-place mutation persists with the layout on commit.
   }

   onLayoutDesignerPreSave(layout: TextLayoutModel): void {
      const bindingModel = this.bindingModel as ChartBindingModel;

      if(bindingModel) {
         const target = this.getLayoutTarget(bindingModel, this.layoutDesignerAggregateName);

         if(target) {
            target.textLayout = layout;
         }
      }

      // Persist the current arrangement via the full model round-trip (same path as commit) so the
      // field's format edits apply to the saved layout. Designer stays open.
      this.chartEditorService.changeChartAesthetic("text");
   }

   onLayoutFieldFormat(fullName: string): void {
      this.chartEditorService.measureName = fullName;
      this.hideFormatPane = false;
      this.updateData("showTextFormat");
   }

   updateFormat(model: any) {
      if(model) {
         this.onUpdateData.emit("updateFormat");
      }
      else {
         this.onUpdateData.emit("reset");
      }
   }

   updateChartMaxMode(value: {assembly: string, maxMode: boolean}): void {
      this.chartMaxMode = value.maxMode;
   }

   private isBound(bmodel: any): boolean {
      if(!bmodel) {
         return false;
      }

      if(bmodel.type == "calctable") {
         return true;
      }
      else if(bmodel.xfields) {
         let chartBModel: ChartBindingModel = <ChartBindingModel> bmodel;
         return !!chartBModel.xfields && chartBModel.xfields.length > 0 ||
            !!chartBModel.yfields && chartBModel.yfields.length > 0 ||
            !!chartBModel.geoFields && chartBModel.geoFields.length > 0 ||
            !!chartBModel.groupFields && chartBModel.groupFields.length > 0 ||
            !!chartBModel.geoCols && chartBModel.geoCols.length > 0 ||
            !!chartBModel.colorField ||
            !!chartBModel.shapeField ||
            !!chartBModel.sizeField ||
            !!chartBModel.textField ||
            !!chartBModel.pathField ||
            !!chartBModel.openField ||
            !!chartBModel.closeField ||
            !!chartBModel.highField ||
            !!chartBModel.lowField ||
            !!chartBModel.sourceField ||
            !!chartBModel.targetField ||
            !!chartBModel.startField ||
            !!chartBModel.endField ||
            !!chartBModel.milestoneField;
      }
      else if(bmodel.groups) {
         let tableBModel: TableBindingModel = <TableBindingModel> bmodel;
         return tableBModel.groups && tableBModel.groups.length > 0 ||
            tableBModel.details && tableBModel.details.length > 0 ||
            tableBModel.aggregates && tableBModel.aggregates.length > 0;
      }
      else if(bmodel.rows) {
         let crosstabBModel: CrosstabBindingModel = <CrosstabBindingModel> bmodel;
         return crosstabBModel.rows && crosstabBModel.rows.length > 0 ||
            crosstabBModel.cols && crosstabBModel.cols.length > 0 ||
            crosstabBModel.aggregates && crosstabBModel.aggregates.length > 0;
      }

      return false;
   }

   popUpWarning(warning: any) {
      if(warning.type == "info") {
         this.notifications.info(warning.msg);
      }
      else if(warning.type == "warning") {
         this.notifications.warning(warning.msg);
      }
      else if(warning.type == "danger") {
         this.notifications.danger(warning.msg);
      }
   }

   hideDcTip(): void {
      this.showDcAppliedTip = false;
   }

   sizeChanged() {
      if((<ChartBindingModel> this.bindingModel).wordCloud) {
         this.notifications.info("_#(js:viewer.viewsheet.chart.fontScale.hint)");
      }
   }

   changeMessage(messages: ConsoleMessage[]) {
      this.consoleMessages = messages;
      this.onMessageChange.emit(this.consoleMessages);
   }

   openConsoleDialog(): void {
      const options: NgbModalOptions = {
         backdrop: "static",
         windowClass: "console-dialog"
      };

      this.modelService.getModel(GET_MESSAGE_LEVELS_URI + Tool.byteEncodeURLComponent(this.runtimeId))
         .subscribe((res: any) => {
            this.messageLevels = res;
            this.modalService.open(this.consoleDialog, options).result
               .then((messageLevels: string[]) => {
                  this.messageLevels = messageLevels;
               }, () => {});
         });
   }
}
