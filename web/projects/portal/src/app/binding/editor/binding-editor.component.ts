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
import { SidebarTab } from "../widget/binding-tree/data-editor-tab-pane.component";
import { FormatsPane } from "./formats-pane.component";
import { NotificationsComponent } from "../../widget/notifications/notifications.component";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { ConsoleDialogComponent } from "../../widget/console-dialog/console-dialog.component";
import { ConsoleMessage } from "../../widget/console-dialog/console-message";
import { Tool } from "../../../../../shared/util/tool";
import { ModelService } from "../../widget/services/model.service";

const GET_MESSAGE_LEVELS_URI = "../api/composer/console-dialog/get-message-levels/";

@Component({
   selector: "binding-editor",
   templateUrl: "binding-editor.component.html",
   styleUrls: ["binding-editor.component.scss"]
})
export class BindingEditor implements OnInit, AfterViewInit, OnDestroy {
   @Input() objectModel: any;
   @Input() currentFormat: FormatInfoModel;
   @Input() linkUri: string;
   @Input() runtimeId: string;
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
   private _bindingModel: BindingModel;
   private _variableValues: string[];
   private _assemblyName: string;

   constructor(private bindingService: BindingService,
               private uiContextService: UIContextService,
               protected modalService: NgbModal,
               private modelService: ModelService)
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
