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
import { Component, EventEmitter, Input, NgZone, OnDestroy, OnInit, Optional, Output,
         ViewChild } from "@angular/core";
import { Subject } from "rxjs";
import { CommonKVModel } from "../../../common/data/common-kv-model";
import { CommandProcessor, ViewsheetClientService, ViewsheetCommand } from "../../../common/viewsheet-client";
import { SplitPane } from "../../../widget/split-pane/split-pane.component";
import { RefreshRecommendCommand } from "../../model/command/refresh-recommend-command";
import { VSRecommendationModel } from "../../model/recommender/vs-recommendation-model";
import { FilterBindingModel } from "../../model/filter-binding-model";
import { VSWizardBindingTreeService } from "../../services/vs-wizard-binding-tree.service";
import {
   ComposerToken,
   ContextProvider,
   VSWizardPreviewContextProviderFactory
} from "../../../vsobjects/context-provider.service";
import { VSChartService } from "../../../vsobjects/objects/chart/services/vs-chart.service";
import { CloseObjectWizardEvent } from "../../model/event/close-object-wizard-event";
import { CloseObjectWizardCommand } from "../../model/command/close-object-wizard-command";
import { ComponentTool } from "../../../common/util/component-tool";
import { DataRef } from "../../../common/data/data-ref";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ChartBindingModel } from "../../../binding/data/chart/chart-binding-model";
import { TableBindingModel } from "../../../binding/data/table/table-binding-model";
import { RefreshVsWizardBindingCommand } from "../../model/command/refresh-vs-wizard-binding-command";
import { UpdateVsWizardBindingEvent } from "../../model/event/update-vs-wizard-binding-event";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { ChartRef } from "../../../common/data/chart-ref";
import { VsWizardEditModes } from "../../model/vs-wizard-edit-modes";
import { WizardBindingModel } from "../../model/wizard-binding-model";
import { SetWizardBindingFormatCommand } from "../../model/command/set-wizard-binding-format-command";
import { VSObjectFormatInfoModel } from "../../../common/data/vs-object-format-info-model";
import { SetWizardBindingFormatEvent } from "../../model/event/set-wizard-binding-format-event";
import { ChartAggregateRef } from "../../../binding/data/chart/chart-aggregate-ref";
import { NotificationsComponent } from "../../../widget/notifications/notifications.component";
import { TableWarningCommand } from "../../model/command/table-warning-command";
import { VSObjectModel } from "../../../vsobjects/model/vs-object-model";
import { VSChartModel } from "../../../vsobjects/model/vs-chart-model";
import { RefreshVSObjectCommand } from "../../../vsobjects/command/refresh-vs-object-command";
import { VSUtil } from "../../../vsobjects/util/vs-util";
import { Tool } from "../../../../../../shared/util/tool";
import { AddVSObjectCommand } from "../../../vsobjects/command/add-vs-object-command";
import { RemoveVSObjectCommand } from "../../../vsobjects/command/remove-vs-object-command";
import { VSWizardConstants } from "../../model/vs-wizard-constants";
import { VSWizardUtil } from "../../util/vs-wizard-util";
import { VSObjectRecommendation } from "../../model/recommender/vs-object-recommendation";
import { VSSubType } from "../../model/recommender/vs-sub-type";
import { SetVSBindingModelCommand } from "../../../vsview/command/set-vs-binding-model-command";
import { ChartDimensionRef } from "../../../binding/data/chart/chart-dimension-ref";
import { VSRecommendType } from "../../model/recommender/vs-recommend-type";
import { FireRecommandCommand } from "../../model/command/fire-recommand-command";
import { MessageCommand } from "../../../common/viewsheet-client/message-command";
import { SwitchToMetaModeEvent } from "../../model/event/switch-to-meta-mode-event";
import { ModelService } from "../../../widget/services/model.service";
import { UIContextService } from "../../../common/services/ui-context.service";
import { VSWizardPreviewPane } from "./wizard-preview-pane.component";
import { ConsoleMessage } from "../../../widget/console-dialog/console-message";

const OBJECT_WIZARD_CLOSE_CANCEL_URI = "/events/vswizard/object/close/cancel";
const OBJECT_WIZARD_CLOSE_SAVE_URI = "/events/vswizard/object/close/save";
const REFRESH_WIZARD_BINDING_INFO = "/events/vswizard/binding/refresh";
const UPDATE_WIZARD_BINDING_FORMAT = "/events/vswizard/object/format";
const SWITCH_TO_META = "/events/vs/wizard/use-meta";

@Component({
   selector: "object-wizard-pane",
   templateUrl: "object-wizard-pane.component.html",
   styleUrls: ["object-wizard-pane.component.scss"],
   providers: [
      {
         provide: ContextProvider,
         useFactory: VSWizardPreviewContextProviderFactory,
         deps: [[new Optional(), ComposerToken]]
      }
   ]
})
export class ObjectWizardPane extends CommandProcessor implements OnInit, OnDestroy {
   @Input() runtimeId: string;
   @Input() linkuri: string;
   @Input() editMode: VsWizardEditModes = VsWizardEditModes.WIZARD_DASHBOARD;
   @Input() originalMode: VsWizardEditModes = VsWizardEditModes.WIZARD_DASHBOARD;
   @Input() isNewViewsheetWizard: boolean = true;
   @Input() temporarySheet: boolean;
   @Input() originalObjectType: string;
   @Input() originalName: string;
   @Input() oldOriginalName: string;
   @Input() viewer: boolean;
   @Output() onFullEditor = new EventEmitter<any>();
   @Output() onClose = new EventEmitter<any>();
   @Output() onSwitchMeta = new EventEmitter<any>();
   @ViewChild("splitPane") splitPane: SplitPane;
   @ViewChild("notifications") notifications: NotificationsComponent;
   @ViewChild("wizardPreviewPane") wizardPreviewPane: VSWizardPreviewPane;
   private closeProgressSubject = new Subject<any>();
   recommenderModel: VSRecommendationModel;
   _bindingModel: WizardBindingModel;
   hiddenFormulaColumns: string[];
   formatMap: Map<string, VSObjectFormatInfoModel>;
   vsObject: VSObjectModel;
   lastSubType: VSSubType;
   currentSubType: VSSubType;
   blocking: boolean = false;
   originalIsDetail: boolean;
   consoleMessages: ConsoleMessage[] = [];
   textLimitConfirmed: boolean = false;
   columnLimitConfirmed: boolean = false;

   private loadingEventCount = 0;
   readonly DEFAULT_VERTICAL_SIZE: number[] = [55, 45];

   constructor(protected zone: NgZone,
               public bindingTreeService: VSWizardBindingTreeService,
               public viewsheetClient: ViewsheetClientService,
               public modalService: NgbModal,
               private modelService: ModelService,
               private chartService: VSChartService,
               private uiContext: UIContextService,
               private clientService: ViewsheetClientService)
   {
      super(viewsheetClient, zone, true);
   }

   ngOnInit() {
      this.closeProgressSubject.subscribe(() => {
         this.bindingTreeService.recommender(false);
      });
   }

   ngOnDestroy(): void {
      this.cleanup();
   }

   getAssemblyName() {
      return null;
   }

   get chartBindingModel(): ChartBindingModel {
      return !!this._bindingModel ? this._bindingModel.chartBindingModel : null;
   }

   get tableBindingModel(): TableBindingModel {
      return !!this._bindingModel ? this._bindingModel.tableBindingModel : null;
   }

   get filterBindingModel(): FilterBindingModel {
      return !!this._bindingModel ? this._bindingModel.filterBindingModel : null;
   }

   get sourceName(): string {
      return !!this._bindingModel ? this._bindingModel.sourceName : null;
   }

   get assemblyType(): number {
      return !!this._bindingModel ? this._bindingModel.assemblyType : null;
   }

   get autoOrder(): boolean {
      return !!this._bindingModel ? this._bindingModel.autoOrder : true;
   }

   set autoOrder(auto: boolean) {
      if(!!this._bindingModel) {
         this._bindingModel.autoOrder = auto;
      }
   }

   get showAutoOrder() {
      if(!!this.recommenderModel &&
         this.recommenderModel.selectedType == VSRecommendType.ORIGINAL_TYPE && this.isDetail)
      {
         return false;
      }

      return this.vsObject != null &&
         (this.vsObject.objectType == "VSChart" || this.vsObject.objectType == "VSCrosstab");
   }

   get assemblyName(): string {
      return !!this.vsObject ? this.vsObject.absoluteName : null;
   }

   get availableFields(): DataRef[] {
      return this.chartBindingModel ? this.chartBindingModel.availableFields :
         this.tableBindingModel ? this.tableBindingModel.availableFields : [];
   }

   get dimensions(): ChartRef[] {
      return !!this.chartBindingModel ? this.chartBindingModel.xfields : [];
   }

   get measures(): ChartRef[] {
      return !!this.chartBindingModel ? this.chartBindingModel.yfields : [];
   }

   get isDetail(): boolean {
      if(!this.recommenderModel) {
         return false;
      }

      let type = this.recommenderModel.selectedType;

      if(this.recommenderModel.selectedType == VSRecommendType.ORIGINAL_TYPE &&
         this.originalIsDetail != null)
      {
         return this.originalIsDetail;
      }

      return type == VSRecommendType.TABLE || type == VSRecommendType.FILTER ||
         !!this.tableBindingModel || !!this.filterBindingModel;
   }

   get isAssemblyBinding() {
      return !!this.chartBindingModel ? this.chartBindingModel.source?.source?.startsWith("__vs_assembly__") : false;
   }

   get isCube() {
      return !!this.chartBindingModel ? this.chartBindingModel.source?.source?.startsWith("___inetsoft_cube_") : false;
   }

   get details() {
      if(!this.vsObject) {
         return null;
      }

      if(this.isDetail) {
         return !!this.tableBindingModel ? this.tableBindingModel.details :
            !!this.filterBindingModel ? this.filterBindingModel.bindingRefs : null;
      }

      return null;
   }

   isFullEditorVisible(): boolean {
      if(this.originalName != null &&
         this.originalName.startsWith(VSWizardConstants.TEMP_ASSEMBLY_PREFIX) &&
         this.bindingTreeService.getSelectedBindingNodePaths().length == 0)
      {
         return false;
      }

      let objectType = !!this.vsObject ? this.vsObject.objectType : this.originalObjectType;

      return this.isSupportFullEditor(objectType);
   }

   goToFullEditor(objectModel: VSObjectModel) {
      this.editMode = VsWizardEditModes.FULL_EDITOR;
      this.onFullEditor.emit({
         editMode: this.editMode,
         objectModel: objectModel
      });
   }

   close(save: boolean = false): void {

      if(save && this.originalMode == VsWizardEditModes.FULL_EDITOR) {
         this.editMode = this.originalMode = VsWizardEditModes.VIEWSHEET_PANE;
      }

      this.blocking = true;
      this.uiContext.sheetClose(this.runtimeId);
      const event = new CloseObjectWizardEvent(this.editMode, this.originalMode, this.oldOriginalName, this.viewer);
      const url = save ? OBJECT_WIZARD_CLOSE_SAVE_URI : OBJECT_WIZARD_CLOSE_CANCEL_URI;
      this.viewsheetClient.sendEvent(url, event);
   }

   private processTableWarningCommand(command: TableWarningCommand): void {
      if(command.message) {
         this.notifications.success(command.message);
      }
   }

   processRefreshRecommendCommand(command: RefreshRecommendCommand) {
      this.recommenderModel = command.recommenderModel;

      if(this.originalIsDetail == null) {
         this.originalIsDetail = this.isDetail;
      }
   }

   processCloseObjectWizardCommand(command: CloseObjectWizardCommand) {
      this.bindingTreeService.reset();
      this.onClose.emit({save: command.save, editMode: this.editMode,
         currentObject: command.currentObject});
   }

   private processSetVSBindingModelCommand(command: SetVSBindingModelCommand): void {
      if(!!this._bindingModel && command.binding.type == "table"
         && !Tool.isEquals(this._bindingModel.tableBindingModel, command.binding))
      {
         this._bindingModel.tableBindingModel = command.binding;
      }
   }

   processRefreshVsWizardBindingCommand(command: RefreshVsWizardBindingCommand): void {
      if(!Tool.isEquals(this._bindingModel, command.bindingModel)) {
         this._bindingModel = command.bindingModel;
      }

      // update the tempBinding in treeInfo
      // so that the tempBinding is always the latest chartBindingModel
      if(!!this.bindingTreeService.treeInfo) {
         this.bindingTreeService.treeInfo.tempBinding = this.chartBindingModel;
      }
   }

   processSetWizardBindingFormatCommand(command: SetWizardBindingFormatCommand): void {
      this.formatMap = new Map<string, VSObjectFormatInfoModel>();

      if(!!command && !!command.models) {
         command.models.forEach(item => this.formatMap.set(item.fieldName, item.formatModel));
      }
   }

   private processRefreshVSObjectCommand(command: RefreshVSObjectCommand): void {
      this.setVSObjectModel(command.info);
   }

   private processAddVSObjectCommand(command: AddVSObjectCommand): void {
      this.setVSObjectModel(command.model);
   }

   private processRemoveVSObjectCommand(command: RemoveVSObjectCommand): void {
      if(this.isLatestTempAssembly(this.assemblyName, command.name)) {
         this.updateVSObjectModel(null);
      }
   }

   private setVSObjectModel(model: VSObjectModel) {
      if(!this.isLatestTempAssembly(this.assemblyName, model.absoluteName)) {
         return;
      }

      if(!this.vsObject) {
         this.vsObject = model;
      }
      else if(this.vsObject.objectType == "VSChart" || this.vsObject.objectType == "VSGauge") {
         let typeChanged: boolean = false;

         if(!this.currentSubType && !this.lastSubType) {
            typeChanged = true;
         }
         else {
            typeChanged = !Tool.isEquals(this.lastSubType, this.currentSubType);
         }

         if(typeChanged) {
            this.vsObject = model;
            this.changeSubtype(this.currentSubType);
         }
         else {
            this.updateVSObjectModel(model);
         }
      }
      else {
         this.vsObject = VSUtil.replaceObject(Tool.clone(this.vsObject), model);
      }
   }

   private updateVSObjectModel(model: VSObjectModel): void {
      if(model && model.objectType == "VSText" && model.objectFormat) {
         model.objectFormat.hAlign = "center";
      }

      this.vsObject = model;
   }

   private isLatestTempAssembly(oldAssemblyName: string,
                                newAssemblyName: string): boolean
   {
      if(!VSWizardUtil.isTempAssembly(newAssemblyName)) {
         return false;
      }

      if(!!!oldAssemblyName || !VSWizardUtil.isTempAssembly(oldAssemblyName)) {
         return true;
      }

      let oldTime: number =
         +oldAssemblyName.split(VSWizardConstants.TEMP_ASSEMBLY_SEPARATOR)[1];
      let newTime: number =
         +newAssemblyName.split(VSWizardConstants.TEMP_ASSEMBLY_SEPARATOR)[1];

      return newTime >= oldTime;
   }

   updateFormat(fieldName: string) {
      if(!this.formatMap) {
         return;
      }

      const format = this.formatMap.get(fieldName);

      if(format) {
         const event: SetWizardBindingFormatEvent =
            new SetWizardBindingFormatEvent(fieldName, format);
         this.viewsheetClient.sendEvent(UPDATE_WIZARD_BINDING_FORMAT, event);
      }

   }

   showLegend(show: boolean) {
      if(show) {
         this.chartService.showAllLegends(<VSChartModel> this.vsObject, this.viewsheetClient, true);
      }
      else {
         this.chartService.hideLegend(<VSChartModel> this.vsObject, this.viewsheetClient, null,
                                      null, null, true);
      }
   }

   onEditSecondColumn(idx: number) {
      let entries: AssetEntry[] = this.bindingTreeService.selectedNodes
         .filter(node => !!node && !!node.data)
         .map((node) => node.data);

      let event = new UpdateVsWizardBindingEvent(this.chartBindingModel, entries);

      this.bindingTreeService.checkAggTrap(this.runtimeId, event)
         .subscribe((data: boolean) => {
            let promise: Promise<boolean> = Promise.resolve(true);
            let trap: boolean = data;

            if(trap) {
               promise = promise.then(() =>
                  ComponentTool.showTrapAlert(this.modalService)
                     .then((result: string) =>  {
                        let val: boolean = result === "continue";
                        return val;
                     }));
         }

         promise.then((confirmed) => {
            if(confirmed) {
               this.sendRefreshWizardBindingEvent();
            }
            else if(this.measures != null && idx < this.measures.length) {
               (<ChartAggregateRef> this.measures[idx]).secondaryColumn = null;
               (<ChartAggregateRef> this.measures[idx]).secondaryColumnValue = null;
            }
         });
      });
   }

   onEditAggregate() {
      this.sendRefreshWizardBindingEvent();
   }

   onEditDimension() {
      this.sendRefreshWizardBindingEvent();
   }

   onUpdateDetails(nodeName: string) {
      if(!!nodeName && !this.isRefInDetails(nodeName)) {
         this.bindingTreeService.unSelectNode(nodeName);
      }
   }

   onAddAggregate() {
      this.sendRefreshWizardBindingEvent();
   }

   onAddDimension() {
      this.sendRefreshWizardBindingEvent();
   }

   onDeleteAggregate(deleteNode: ChartAggregateRef) {
      if(!this.isRefInMeasures(deleteNode.columnValue)) {
         this.bindingTreeService.unSelectNode(deleteNode.columnValue);
         return;
      }

      if(!this.isFullRefInMeasures(deleteNode.fullName)) {
         this.sendRefreshWizardBindingEvent(deleteNode.fullName);
      }
      else {
         this.sendRefreshWizardBindingEvent();
      }
   }

   onDeleteDimension(deleteNode: ChartDimensionRef) {
      if(!this.isRefInDimension(deleteNode.columnValue)) {
         this.bindingTreeService.unSelectNode(deleteNode.columnValue);
         return;
      }

      if(!this.isFullRefInDimension(deleteNode.fullName)) {
         this.sendRefreshWizardBindingEvent(deleteNode.fullName);
      }
      else {
         this.sendRefreshWizardBindingEvent();
      }
   }

   onAutoOrderChange(auto: boolean) {
      this.autoOrder = auto;
      this.sendRefreshWizardBindingEvent();
   }

   isRefInMeasures(name: string): boolean {
      return this.measures.some(item => item.name == name);
   }

   isFullRefInMeasures(fullName: string): boolean {
      return this.measures.some(item => item.fullName == fullName);
   }

   isRefInDimension(name: string): boolean {
      return this.dimensions.some(item => item.name == name);
   }

   isFullRefInDimension(fullName: string): boolean {
      return this.dimensions.some(item => item.fullName == fullName);
   }

   isRefInDetails(name: string): boolean {
      let details = !!this.tableBindingModel ? this.tableBindingModel.details : [];
      return details.some(item => item.name == name);
   }

   private sendRefreshWizardBindingEvent(deleteFormatColumn?: string) {
      let entries: AssetEntry[] = this.bindingTreeService.selectedNodes
         .filter(node => !!node && !!node.data)
         .map((node) => node.data);

      let event = new UpdateVsWizardBindingEvent(this.chartBindingModel, entries,
                                                 deleteFormatColumn, this.autoOrder);
      this.clientService.sendEvent(REFRESH_WIZARD_BINDING_INFO, event);
   }

   get treePaneCollapsed(): boolean {
      return !!this.splitPane && this.splitPane.getSizes()[1] < 10;
   }

   toggleRepositoryTreePane(): void {
      if(this.treePaneCollapsed) {
         this.splitPane.setSizes(this.DEFAULT_VERTICAL_SIZE);
      }
      else {
         this.splitPane.collapse(1);
      }
   }


   private isSupportFullEditor(objectType: string) {
      return objectType == "VSChart" || objectType == "VSCrosstab"
         || objectType == "VSTable";
   }

   isSelectionTree(): boolean {
      let recommendation: VSObjectRecommendation;

      if(this.recommenderModel && this.recommenderModel.recommendationList) {
         recommendation = this.recommenderModel.recommendationList.find(
            item => {
               return item.type == this.recommenderModel.selectedType;
            });
      }

      if(!recommendation || !Tool.isNumber(recommendation.selectedIndex) ||
         !recommendation.subTypes || recommendation.subTypes.length == 0)
      {
         return false;
      }

      let subType: VSSubType = recommendation.subTypes[recommendation.selectedIndex];

      if(subType.type == "SELECTION_TREE") {
         return true;
      }

      return false;
   }

   changeSubtype(subType: VSSubType): void {
      this.lastSubType = this.currentSubType;
      this.currentSubType = subType;
   }

   private processClearRecommendLoadingCommand(command: ViewsheetCommand): void {
      this.loadingEventCount--;
   }

   private processShowRecommendLoadingCommand(command: ViewsheetCommand) {
      this.loadingEventCount++;
   }

   private processFireRecommandCommand(command: FireRecommandCommand) {
      this.sendRefreshWizardBindingEvent();
   }

   get eventLoading(): boolean {
      return this.loadingEventCount != 0;
   }

   private processMessageCommand(command: MessageCommand): void {
      if(command.message && command.type == "INFO") {
         if(Tool.shouldIgnoreMessage(this.textLimitConfirmed, this.columnLimitConfirmed,
            command.message))
         {
            this.addConsoleMessage(command);
            return;
         }

         this.textLimitConfirmed = Tool.getTextLimit(this.textLimitConfirmed, command.message);
         this.columnLimitConfirmed = Tool.getColumnLimit(this.columnLimitConfirmed, command.message);
         command.message = Tool.getLimitedMessage(command.message);
         this.notifications.info(command.message);
      }
      else {
         this.processMessageCommand0(command, this.modalService, this.viewsheetClient);
      }

      this.addConsoleMessage(command);
   }

   private addConsoleMessage(command: MessageCommand): void {
      if(!!command.message && (command.type == "INFO" || command.type == "WARNING" ||
         command.type == "ERROR"))
      {
         this.consoleMessages.push({
            message: command.message,
            type: command.type
         });
      }
   }

   protected processProgress(command: MessageCommand): void {
      // close progress dialog when mv data is created.
      if(!command.message) {
         this.closeProgressSubject.next(null);
         return;
      }

      let checkMv: boolean = false;

      if(command.events) {
         for(let key in command.events) {
            if(command.events.hasOwnProperty(key)) {
               if(key && key.toLowerCase().indexOf("checkmv") != -1) {
                  checkMv = true;
               }

               let evt: any = command.events[key];
               this.viewsheetClient.sendEvent(key, evt);
            }
         }
      }

      if(checkMv) {
         this.showProgressDialog(
            command, "_#(js:Loading)",
            {"background": "_#(js:em.mv.background)", "cancel": "_#(js:Cancel)"});
      }
   }

   private showProgressDialog(command: MessageCommand, title: string,
                              buttonOptions: {[key: string]: string}): void
   {
      ComponentTool.showMessageDialog(this.modalService, title, command.message,
         buttonOptions, {backdrop: "static" }, this.closeProgressSubject)
         .then((btn: any) => {
            for(let key in command.events) {
               if(command.events.hasOwnProperty(key)) {
                  let evt: any = Tool.clone(command.events[key]);

                  if(btn == "background") {
                     evt.background = true;
                  }

                  evt.confirmed = true;
                  this.viewsheetClient.sendEvent(key, evt);

                  if(btn == "background") {
                     let evt0: any = command.events[key];
                     evt0.waitFor = true;
                     this.viewsheetClient.sendEvent(key, evt0);
                  }
               }
            }
         })
         .catch(() => {});
   }

   switchToMeta(): void {
      this.viewsheetClient.sendEvent(SWITCH_TO_META, new SwitchToMetaModeEvent(this.originalMode));
   }

   processSwitchToMetaModeCommand(): void {
      this.onSwitchMeta.emit();
   }

   get fixedFormulaMap(): CommonKVModel<string, string>[] {
      if(!!!this._bindingModel) {
         return null;
      }

      return Tool.clone(this._bindingModel.fixedFormulaMap);
   }

   splitPaneDragEnd() {
      if(this.wizardPreviewPane) {
         this.wizardPreviewPane.setPreviewPaneSize();
      }
   }

   messageChange(consoleMessages: ConsoleMessage[]) {
      this.consoleMessages = consoleMessages;
   }
}
