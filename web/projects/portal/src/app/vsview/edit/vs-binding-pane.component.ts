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
import { HttpClient, HttpParams } from "@angular/common/http";
import { ChangeDetectorRef, Component, EventEmitter, HostListener, Injector, Input, NgZone,
         OnDestroy, OnInit, Optional, Output, ViewChild
} from "@angular/core";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { Subject, Subscription } from "rxjs";
import { RefreshBindingTreeCommand } from "../../binding/command/refresh-binding-tree-command";
import { SetGrayedOutFieldsCommand } from "../../binding/command/set-grayed-out-fields-command";
import { VSBindingTrapCommand } from "../../binding/command/vs-binding-trap-command";
import { BindingModel } from "../../binding/data/binding-model";
import { FeatureMappingInfo } from "../../binding/data/chart/feature-mapping-info";
import { ApplyVSAssemblyInfoEvent } from "../../binding/event/apply-vs-assembly-info-event";
import { RefreshBindingTreeEvent } from "../../binding/event/refresh-binding-tree-event";
import { RefreshVSBindingEvent } from "../../binding/event/refresh-vs-binding-event";
import { BindingService } from "../../binding/services/binding.service";
import { ChartEditorService } from "../../binding/services/chart/chart-editor.service";
import { VSChartEditorService } from "../../binding/services/chart/vs-chart-editor.service";
import { TableEditorService } from "../../binding/services/table/table-editor.service";
import { VSCalcTableEditorService } from "../../binding/services/table/vs-calc-table-editor.service";
import { VSTableEditorService } from "../../binding/services/table/vs-table-editor.service";
import { VSBindingService } from "../../binding/services/vs-binding.service";
import { BindingTreeService } from "../../binding/widget/binding-tree/binding-tree.service";
import { DataRef } from "../../common/data/data-ref";
import { VSObjectFormatInfoModel } from "../../common/data/vs-object-format-info-model";
import { DndService } from "../../common/dnd/dnd.service";
import { VSDndService } from "../../common/dnd/vs-dnd.service";
import { UIContextService } from "../../common/services/ui-context.service";
import { GraphTypes } from "../../common/graph-types";
import { Tool } from "../../../../../shared/util/tool";
import { CommandProcessor, ViewsheetClientService } from "../../common/viewsheet-client";
import { MessageCommand } from "../../common/viewsheet-client/message-command";
import { ExpiredSheetCommand } from "../../composer/gui/ws/socket/expired-sheet/expired-sheet-command";
import { TouchAssetEvent } from "../../composer/gui/ws/socket/touch-asset-event";
import { ChartTool } from "../../graph/model/chart-tool";
import { ChartService } from "../../graph/services/chart.service";
import { AssemblyActionFactory } from "../../vsobjects/action/assembly-action-factory.service";
import { AddVSObjectCommand } from "../../vsobjects/command/add-vs-object-command";
import { RefreshVSObjectCommand } from "../../vsobjects/command/refresh-vs-object-command";
import { SetCurrentFormatCommand } from "../../vsobjects/command/set-current-format-command";
import { CancelViewsheetLoadingEvent } from "../../vsobjects/event/cancel-viewsheet-loading-event";
import { ClearLoadingCommand } from "../../vsobjects/command/clear-loading-command";
import { ShowLoadingMaskCommand } from "../../vsobjects/command/show-loading-mask-command";
import {
   BindingContextProviderFactory,
   ComposerToken,
   ContextProvider
} from "../../vsobjects/context-provider.service";
import { FormatVSObjectEvent } from "../../vsobjects/event/format-vs-object-event";
import { GetVSObjectFormatEvent } from "../../vsobjects/event/get-vs-object-format-event";
import { BaseTableModel } from "../../vsobjects/model/base-table-model";
import { VSChartModel } from "../../vsobjects/model/vs-chart-model";
import { VSObjectModel } from "../../vsobjects/model/vs-object-model";
import { VSChartService } from "../../vsobjects/objects/chart/services/vs-chart.service";
import { AdhocFilterService } from "../../vsobjects/objects/data-tip/adhoc-filter.service";
import { DataTipService } from "../../vsobjects/objects/data-tip/data-tip.service";
import { PopComponentService } from "../../vsobjects/objects/data-tip/pop-component.service";
import { VSUtil } from "../../vsobjects/util/vs-util";
import { PresenterPropertyDialogModel } from "../../widget/presenter/data/presenter-property-dialog-model";
import { DebounceService } from "../../widget/services/debounce.service";
import { ModelService } from "../../widget/services/model.service";
import { DefaultScaleService } from "../../widget/services/scale/default-scale-service";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { BindingDialogServiceFactory, DialogService } from "../../widget/slide-out/dialog-service.service";
import { SlideOutService } from "../../widget/slide-out/slide-out.service";
import { SetVSBindingModelCommand } from "../command/set-vs-binding-model-command";
import { OpenEditGeographicCommand } from "../../binding/command/open-edit-geographic-command";
import { VSGeoProvider } from "../../binding/editor/vs-geo-provider";
import { GeoProvider } from "../../common/data/geo-provider";
import { EditGeographicDialog } from "../../binding/widget/binding-tree/edit-geographic-dialog.component";
import { ChangeChartRefEvent } from "../../binding/event/change-chart-ref-event";
import { ChartGeoRef } from "../../binding/data/chart/chart-geo-ref";
import { ComponentTool } from "../../common/util/component-tool";
import { NotificationsComponent } from "../../widget/notifications/notifications.component";
import { VsWizardEditModes } from "../../vs-wizard/model/vs-wizard-edit-modes";
import { VsWizardModel, WizardOriginalInfo } from "../../vs-wizard/model/vs-wizard-model";
import { UpdateWizardObjectEvent } from "../../vs-wizard/model/event/update-wizard-object-event";
import { OpenObjectWizardCommand } from "../../vs-wizard/model/command/open-object-wizard-command";
import { VSWizardUtil } from "../../vs-wizard/util/vs-wizard-util";
import { VSObjectView } from "../view/vs-object-view.component";
import { BindingPaneData } from "../model/binding-pane-data";
import { RenameVSObjectCommand } from "../../vsobjects/command/rename-vs-object-command";
import { AssemblyChangedCommand } from "../../vs-wizard/model/command/assembly-changed-command";
import { CrosstabBindingModel } from "../../binding/data/table/crosstab-binding-model";
import { BDimensionRef } from "../../binding/data/b-dimension-ref";
import { BAggregateRef } from "../../binding/data/b-aggregate-ref";
import { ChartBindingModel } from "../../binding/data/chart/chart-binding-model";
import { ChartRef } from "../../common/data/chart-ref";
import { AestheticInfo } from "../../binding/data/chart/aesthetic-info";
import { VirtualScrollService } from "../../widget/tree/virtual-scroll.service";
import { VSBindingTreeService } from "../../binding/widget/binding-tree/vs-binding-tree.service";
import { ConsoleMessage } from "../../widget/console-dialog/console-message";

@Component({
   selector: "vs-binding-pane",
   templateUrl: "vs-binding-pane.component.html",
   styleUrls: ["vs-binding-pane.component.scss"],
   providers: [
      ViewsheetClientService,
      DataTipService,
      AdhocFilterService,
      PopComponentService,
      ModelService, // use separate instance for custom error handler,
      VSChartService,
      DebounceService,
      {
         provide: ScaleService,
         useClass: DefaultScaleService
      },
      {
         provide: ContextProvider,
         useFactory: BindingContextProviderFactory,
         deps: [[new Optional(), ComposerToken]]
      },
      AssemblyActionFactory,
      {
         provide: BindingService,
         useClass: VSBindingService,
         deps: [ModelService, HttpClient, UIContextService]
      },
      {
         provide: ChartEditorService,
         useClass: VSChartEditorService,
         deps: [BindingService, ModelService, ViewsheetClientService]
      },
      {
         provide: TableEditorService,
         useClass: VSTableEditorService,
         deps: [BindingService, ViewsheetClientService, ModelService]
      },
      {
         provide: DndService,
         useClass: VSDndService,
         deps: [ModelService, NgbModal, ViewsheetClientService]
      },
      {
         provide: VSCalcTableEditorService,
         useClass: VSCalcTableEditorService,
         deps: [BindingService, ViewsheetClientService, NgbModal, ModelService]
      },
      {
         provide: ChartService,
         useExisting: VSChartService
      },
      {
         provide: DialogService,
         useFactory: BindingDialogServiceFactory,
         deps: [NgbModal, SlideOutService, Injector, UIContextService]
      },
      {
         provide: BindingTreeService,
         useClass: VSBindingTreeService
      },
   ]
})
export class VSBindingPane extends CommandProcessor implements OnInit, OnDestroy {
   @Input() set runtimeId(id: string) {
      this._oldRuntimeId = this._runtimeId = id;
   }

   get runtimeId(): string {
      return this._runtimeId;
   }

   @Input() assemblyName: string;
   @Input() objectType: string;
   @Input() temporarySheet: boolean;
   @Input() variableValues: string[];
   @Input() linkUri: string;
   @Input() viewer: boolean = false;
   @Input() originalObjectModel: string;
   @Input() wizardOriginalInfo: WizardOriginalInfo;
   @Input() isCube: boolean = false;
   @Output() onCloseBindingPane: EventEmitter<any> = new EventEmitter<any>();
   @Output() onOpenWizardPane = new EventEmitter<VsWizardModel>();
   @Output() onAssemblyChanged = new EventEmitter<AssemblyChangedCommand>();
   @Output() onRenamed = new EventEmitter<string>();
   private _runtimeId;
   private _oldRuntimeId: string;
   private useMeta: boolean = true;
   loading: boolean = false;

   blocking: boolean = false;
   bindingModel: BindingModel;
   currentFormat: VSObjectFormatInfoModel = <VSObjectFormatInfoModel> {};
   origFormat: VSObjectFormatInfoModel = <VSObjectFormatInfoModel> {};
   objectModel: VSObjectModel;
   grayedOutFields: DataRef[];
   consoleMessages: ConsoleMessage[] = [];
   textLimitConfirmed: boolean = false;
   columnLimitConfirmed: boolean = false;

   private confirmExpiredDisplayed = false;
   private closeProgressSubject = new Subject<any>();
   private refreshSubscription = new Subscription();
   private heartBeat: Subscription;
   private closed: boolean = false;
   private committed: boolean = false;
   private textFormat: boolean = false; // true if editing format from text field
   @ViewChild("notifications") notifications: NotificationsComponent;
   @ViewChild("objectView") objectView: VSObjectView;

   constructor(private changeDetectorRef: ChangeDetectorRef,
               private clientService: ViewsheetClientService,
               private treeService: BindingTreeService,
               private dndService: DndService,
               private modelService: ModelService,
               private modalService: NgbModal,
               private injector: Injector,
               private bindingService: BindingService,
               private chartService: ChartEditorService,
               private dialogService: DialogService,
               actionFactory: AssemblyActionFactory,
               protected zone: NgZone)
   {
      super(clientService, zone, true);
      this.bindingService.setClientService(clientService);

      actionFactory.stateProvider = {
         isActionEnabled: (id: string, model: VSObjectModel) => this.isActionEnabled(id, model),
         isActionVisible: (id: string, model: VSObjectModel) => this.isActionEnabled(id, model)
      };

      modelService.errorHandler = (error: any) => {
         if(error.error === "expiredSheet") {
            this.handleExpiredSheet();
            return true;
         }

         return false;
      };
   }

   ngOnInit() {
      this.blocking = true;

      this.modelService.getModel("../api/vsbinding/open", this.params)
         .subscribe((data: BindingPaneData) => {
            if(!data) {
               return;
            }

            this._runtimeId = data.runtimeId;
            this.useMeta = data.useMeta;

            this.refreshSubscription.add(this.clientService.whenConnected().subscribe(() => {
               this.refreshSubscription.add(this.clientService.connectRefresh(this.runtimeId));
            }));

            this.clientService.runtimeId = this._runtimeId;
            this.clientService.connect();
            this.heartBeat = this.clientService.onHeartbeat.subscribe(() => {
               this.touchAsset();
            });

            this.refreshVSBinding();
         }
      );
   }

   private touchAsset(): void {
      if(this.runtimeId) {
         let event = new TouchAssetEvent();
         event.setDesign(true);
         event.setChanged(false);
         event.setUpdate(false);
         this.clientService.sendEvent("/events/composer/touch-asset", event);
      }
   }

   ngOnDestroy() {
      super.cleanup();
      this.refreshSubscription.unsubscribe();

      if(this.heartBeat) {
         this.heartBeat.unsubscribe();
         this.heartBeat = null;
      }

      // hack to fix https://github.com/angular/angular/issues/22466
      this.dialogService.ngOnDestroy();
   }

   get sourceName(): string {
      return Tool.getCurrentSourceLabel(this.bindingModel);
   }

   get originalMode(): VsWizardEditModes {
      return this.wizardOriginalInfo ? this.wizardOriginalInfo.editMode : null;
   }

   public getAssemblyName(): string {
      return null;
   }

   private get params(): HttpParams {
      return new HttpParams()
         .set("vsId", this.runtimeId)
         .set("assemblyName", this.assemblyName)
         .set("temporarySheet", "" + !!this.temporarySheet)
         .set("viewer", "" + !!this.viewer);
   }

   get formatPaneDisabled(): boolean {
      if(this.objectModel) {
         if(this.isChart()) {
            const chart = <VSChartModel> this.objectModel;

            if(chart.chartSelection && chart.chartSelection.chartObject) {
               return ChartTool.isNonEditableVOSelected(chart) && chart.chartSelection.regions.length > 0;
            }
         }
         else if(this.objectModel.objectType == "VSCalcTable") {
            const calc = <BaseTableModel> this.objectModel;
            return calc.firstSelectedColumn == null || calc.firstSelectedRow == null;
         }
      }

      return false;
   }

   updatePresenterProperties(uri: string, model: PresenterPropertyDialogModel): void {
      this.clientService.sendEvent(uri, model);
   }

   private refreshVSBinding(): void {
      this.clientService.sendEvent("/events/vs/binding/getbinding",
         new RefreshVSBindingEvent(this.runtimeId, this.assemblyName));
   }

   private isActionEnabled(id: string, model: VSObjectModel): boolean {
      switch(id) {
      case "calc-table merge-cells":
         return this.isMergeCellsActionEnabled(model);
      case "calc-table split-cells":
         return this.isSplitCellsActionEnabled(model);
      case "calc-table delete-row":
         return this.isDeleteRowActionEnabled(model);
      case "calc-table delete-column":
         return this.isDeleteColumnActionEnabled(model);
      }

      return true;
   }

   private isActionVisible(id: string, model: VSObjectModel): boolean {
      return true;
   }

   private get calcTableEditorService(): VSCalcTableEditorService {
      return this.injector.get(VSCalcTableEditorService);
   }

   private isMergeCellsActionEnabled(model: VSObjectModel): boolean {
      const layoutModel = this.calcTableEditorService.getTableLayout();

      if(layoutModel && layoutModel.selectedCells) {
         return layoutModel.selectedCells.length > 1;
      }

      return false;
   }

   private isSplitCellsActionEnabled(model: VSObjectModel): boolean {
      const layoutModel = this.calcTableEditorService.getTableLayout();

      if(layoutModel) {
         const selectedCells = layoutModel.selectedCells;

         if(!selectedCells || selectedCells.length === 0) {
            return false;
         }

         const cell = selectedCells[0];

         if(cell && cell.span) {
            return cell.span.width > 1 || cell.span.height > 1;
         }
      }

      return false;
   }

   private isDeleteRowActionEnabled(model: VSObjectModel): boolean {
      const layoutModel = this.calcTableEditorService.getTableLayout();

      if(layoutModel) {
         return layoutModel.tableRows.length > 1 &&
            !!layoutModel.selectedCells &&
            !!layoutModel.selectedRect;
      }

      return false;
   }

   private isDeleteColumnActionEnabled(model: VSObjectModel): boolean {
      const layoutModel = this.calcTableEditorService.getTableLayout();

      if(layoutModel) {
         return layoutModel.tableColumns.length > 1 &&
            !!layoutModel.selectedCells &&
            !!layoutModel.selectedRect;
      }

      return false;
   }

   private processSetVSBindingModelCommand(command: SetVSBindingModelCommand): void {
      this.bindingModel = command.binding;

      this.clientService.sendEvent("/events/vs/bindingtree/gettreemodel",
                                      new RefreshBindingTreeEvent(this.assemblyName));
   }

   private processOpenEditGeographicCommand(command: OpenEditGeographicCommand): void {
      if(command.measureName) {
         const refName = command.measureName;
         const ref: any = Object.assign({},
            command.bindingModel.geoCols.find(col => col.name === refName));

         if(!ref.option) {
            ref.option = {
               layerValue: "",
               mapping: <FeatureMappingInfo> {}
            };
         }
         const dialog: EditGeographicDialog = ComponentTool.showDialog(this.modalService,
            EditGeographicDialog, () => {
               const name = this.assemblyName;
               const idx = command.bindingModel.geoCols.findIndex(col => col.name === refName);
               command.bindingModel.geoCols[idx] = ref;
               ref.option = 0;
               const event = new ChangeChartRefEvent(name, null, command.bindingModel);
               this.clientService.sendEvent("/events/vs/chart/changeChartRef", event);
            });

         let geoProvider: GeoProvider = new VSGeoProvider(
            command.bindingModel, this.params,
            ref, this.modelService, refName);
         dialog.refName = refName;
         dialog.provider = geoProvider;
      }
      else {
         let refName = command.chartGeoRefModel.fullName;
         let ref = command.chartGeoRefModel;
         let geoProvider: GeoProvider = new VSGeoProvider(
            command.bindingModel, this.params,
            ref, this.modelService, refName);
         let dialog: EditGeographicDialog = ComponentTool.showDialog(this.modalService,
            EditGeographicDialog, () => {
               let name: string = this.assemblyName;
               let idx = command.bindingModel.geoCols
                  .findIndex((col) => (<ChartGeoRef>col).fullName === refName);
               command.bindingModel.geoCols[idx] = ref;
               let event: ChangeChartRefEvent = new ChangeChartRefEvent(name,
                  null, command.bindingModel);
               this.clientService.sendEvent("/events/vs/chart/changeChartRef", event);
            });

         dialog.refName = refName;
         dialog.provider = geoProvider;
      }
   }

   public processRefreshVSObjectCommand(command: RefreshVSObjectCommand): void {
      if(!command || !command.info || command.info.absoluteName != this.assemblyName) {
         return;
      }

      if(!!this.objectModel) {
         if(this.isChart() &&
            (<VSChartModel> this.objectModel).notAuto && !command.force)
         {
            return;
         }

         this.updateObjectModel(VSUtil.replaceObject(this.objectModel, command.info));
         this.changeDetectorRef.detectChanges();
      }
   }

   processAddVSObjectCommand(command: AddVSObjectCommand): void {
      let updated: boolean = false;

      if(command.model.absoluteName != this.assemblyName) {
         return;
      }

      if(!!this.objectModel) {
         updated = true;

         if(this.isChart() && (<VSChartModel> this.objectModel).notAuto) {
            return;
         }

         // if model comesin wrong order, should ignore the older version. (50151)
         if(command.model.genTime && this.objectModel.genTime &&
            this.objectModel.genTime > command.model.genTime)
         {
            return;
         }

         this.updateObjectModel(VSUtil.replaceObject(this.objectModel, command.model));
         // needed after format Reset
         this.getCurrentFormat(this.isAggregateTextFormat());
      }

      if(!updated) {
         this.updateObjectModel(command.model);
         this.selectWholes();
      }
   }

   private updateObjectModel(model: VSObjectModel): void {
      this.objectModel = model;
      this.blocking = false;
   }

   private selectWholes(): void {
      const type = this.objectModel.objectType;

      if(type === "VSTable" || type === "VSCrosstab") {
         const table = <BaseTableModel> this.objectModel;
         table.firstSelectedRow = -1;
         table.firstSelectedColumn = -1;
      }

      this.getCurrentFormat(false);
   }

   private processRefreshBindingTreeCommand(command: RefreshBindingTreeCommand): void {
      if(this.treeService == null) {
         return;
      }

      this.treeService.resetTreeModel(command.treeModel);
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
         this.processMessageCommand0(command, this.modalService, this.clientService);
      }

      if(command.message && command.type == "ERROR" && !this.objectModel) {
         this.closeHandler(false);
      }

      this.addConsoleMessage(command);
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
               this.clientService.sendEvent(key, evt);
            }
         }
      }

      if(checkMv) {
         this.showProgressDialog(
            command, "_#(js:Loading)", {"background": "_#(js:em.mv.background)", "cancel": "_#(js:Cancel)"});
      }
   }

   private showProgressDialog(command: MessageCommand, title: string,
                              buttonOptions: {[key: string]: string}): void
   {
      ComponentTool.showMessageDialog(this.modalService, title, command.message,
         buttonOptions, {backdrop: "static"}, this.closeProgressSubject)
         .then((btn: any) => {
            for(let key in command.events) {
               if(command.events.hasOwnProperty(key)) {
                  let evt: any = Tool.clone(command.events[key]);

                  if(btn == "background") {
                     evt.background = true;
                     // this.vs.loading = false;
                  }

                  evt.confirmed = true;
                  this.clientService.sendEvent(key, evt);

                  if(btn == "background") {
                     let evt0: any = command.events[key];
                     evt0.waitFor = true;
                     this.clientService.sendEvent(key, evt0);
                  }
               }
            }
         })
         .catch(() => {});
   }

   protected isInZone(messageType: string): boolean {
      return messageType != "SetCurrentFormatCommand";
   }

   private processVSTrapCommand(command: MessageCommand): void {
      ComponentTool.showTrapAlert(this.modalService, false).then((result: string) => {
         if(result == "yes") {
            for(let key in command.events) {
               if(command.events.hasOwnProperty(key)) {
                  let evt: any = command.events[key];
                  evt.confirmed = true;
                  this.clientService.sendEvent(key, evt);
               }
            }
         }
      });
   }

   private processSetCurrentFormatCommand(command: SetCurrentFormatCommand): void {
      if(JSON.stringify(this.currentFormat) != JSON.stringify(command.model)) {
         this.zone.run(() => this.currentFormat = command.model);
      }

      this.origFormat = Tool.clone(command.model);
   }

   private processSetGrayedOutFieldsCommand(command: SetGrayedOutFieldsCommand): void {
      this.grayedOutFields = command.fields;
   }

   private processVSBindingTrapCommand(command: VSBindingTrapCommand): void {
      let model: any = command.model;

      if(model != null) {
         ComponentTool.showTrapAlert(this.modalService).then((result: string) => {
            if(result == "undo") {
               this.clientService.sendEvent("/events/vs/binding/setbinding",
                  new ApplyVSAssemblyInfoEvent(this.objectModel.absoluteName,
                  command.model, false));
            }
            else {
              this.grayedOutFields = command.fields;
            }
         });
      }
   }

   private processExpiredSheetCommand(command: ExpiredSheetCommand) {
      this.handleExpiredSheet();
   }

   private getBindingType(): string {
      let bindingType: string = null;

      if(this.objectType === "VSChart") {
         bindingType = "Chart";
      }
      else if(this.objectType === "VSTable") {
         bindingType = "Table";
      }
      else if(this.objectType === "VSCrosstab") {
         bindingType = "Crosstab";
      }
      else if(this.objectType === "VSCalcTable") {
         bindingType = "FreehandTable";
      }

      return bindingType;
   }

   private handleExpiredSheet() {
      if(!this.confirmExpiredDisplayed) {
         this.confirmExpiredDisplayed = true;
         const msg = "The " + (!this.getBindingType() ? "binding"
            : this.getBindingType())
            + " Editor has timed-out and will be closed.";
         const buttons = { ok: "OK" };
         const options: NgbModalOptions = {backdrop: "static"};
         ComponentTool.showMessageDialog(this.modalService, "Expired", msg, buttons, options)
            .then(() => {
               this.closeHandler(false);
               this.confirmExpiredDisplayed = false;
            });
      }
   }

   private confirm(text: string): Promise<boolean> {
      return ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", text,
         {"yes": "Yes", "no": "No"})
         .then((result: string) => {
            return result === "yes";
         });
   }

   updateData(event: string): void {
      switch(event){
         case "getCurrentFormat":
            this.getCurrentFormat(false);
            break;
         case "getTextFormat":
            this.getCurrentFormat(true);
            break;
         case "updateFormat":
            this.updateFormat(this.currentFormat);
            break;
         case "reset":
            this.updateFormat(null);
            break;
      }
   }

   private getCurrentFormat(textFormat: boolean) {
      if(!this.objectModel) {
         return;
      }

      let vsevent: GetVSObjectFormatEvent = textFormat ? this.createTextFormatEvent() :
         VSUtil.prepareGetFormatEvent(this.objectModel);
      vsevent.binding = true;
      this.textFormat = textFormat;

      if(!textFormat && !!vsevent.columnName) {
         this.chartService.measureName = vsevent.columnName;
      }

      this.chartService.textFormat = textFormat;

      this.clientService.sendEvent("/events/composer/viewsheet/getFormat", vsevent);
   }

   private updateFormat(fmt: VSObjectFormatInfoModel): void {
      let event: FormatVSObjectEvent = this.createUpdateFormatEvent();
      event.format = fmt;
      event.origFormat = this.origFormat;
      event.reset = !fmt;
      this.clientService.sendEvent("/events/composer/viewsheet/format", event);
   }

   private createTextFormatEvent(): GetVSObjectFormatEvent {
      const vsevent: GetVSObjectFormatEvent =
         new GetVSObjectFormatEvent(this.objectModel.absoluteName);
      const measure = this.chartService.measureName;
      vsevent.region = "textField";
      vsevent.columnName = measure;

      return vsevent;
   }

   private createUpdateFormatEvent(): FormatVSObjectEvent {
      if(this.isAggregateTextFormat()) {
         let event: FormatVSObjectEvent = new FormatVSObjectEvent();
         const measure = this.chartService.measureName;
         event.charts = [this.objectModel.absoluteName];
         event.columnNames = [[measure]];
         event.regions = [this.textFormat ? "textField" : "text"];
         event.indexes = [[-1]];

         return event;
      }

      return VSUtil.prepareFormatEvent([this.objectModel]);
   }

   private isAggregateTextFormat(): boolean {
      return this.isChart() && this.chartService.textFormat;
   }

   closeHandler(save: boolean, editMode?: string) {
      if(this.closed) {
         return;
      }

      // block interaction while in the process of destroying binding pane (45657).
      this.blocking = true;
      setTimeout(() => this.closeHandler0(save, editMode), 200);
   }

   /**
    * Avoid receiving extra commands after committing.
    * @param message
    */
   protected isWizardExpiredCommand(message: any): boolean {
      if(!!message && message.type == "AssemblyChangedCommand") {
         return false;
      }

      return super.isWizardExpiredCommand(message) || (this.committed && !!message &&
         message.type !== "CloseBindingPaneCommand" && message.type !== "OpenObjectWizardCommand");
   }

   private closeHandler0(save: boolean, editMode: string) {
      let promise: Promise<any> = Promise.resolve(null);
      let params: HttpParams = this.params.set("editMode", editMode)
                                          .set("originalMode", this.originalMode);

      if(save) {
         promise = promise.then(
            () => this.modelService.getModel("../api/vsbinding/commit", params).toPromise());

         if(!!this.originalMode) {
            promise.then(() => {
               // the oldRuntimeId viewsheet will been refreshed, this pane should not process these
               // commands.
               this.committed = true;
               // send event to update assembly by temp assembly and clear tempInfo
               let event: UpdateWizardObjectEvent = new UpdateWizardObjectEvent(
                  this._oldRuntimeId, this.assemblyName, this.originalMode,
                  this.wizardOriginalInfo.absoluteName);
               this.clientService.sendEvent("/events/vs/wizard/update/assembly", event);
            });
         }
      }

      promise.then(() => {
         this.clientService.sendEvent("/events/vs/binding/close");
         this.closed = true;

         // case1: if using meta and viewsheet pane exist
         // case2: finish editing to go to vs pane.
         // case3: cancel to go to viewsheet pane.
         if(!this.originalMode ||
            this.useMeta && this.originalMode == VsWizardEditModes.VIEWSHEET_PANE ||
            (!save && this.originalMode == VsWizardEditModes.FULL_EDITOR))
         {
            this.onCloseBindingPane.emit();
         }
         // case3: cancel to go to wizard pane.
         else if(!save) {
            this.openWizardPane(true, "cancel");
         }
      });
   }

   /**
    * after receiving the command for update assembly, go to openWizardPane
    */
   private processOpenObjectWizardCommand(command: OpenObjectWizardCommand) {
      this.onCloseBindingPane.emit();

      if(command.open) {
         this.openWizardPane(true, "finish");
      }
   }

   processCloseBindingPaneCommand() {
      this.closeHandler(false);
   }

   /**
    * Refresh the assembly after changed from binding pane. Because assembly name
    * maybe changed if assembly type is changed in vs wizard, so here we need to refresh
    * with the latest assembly name.
    */
   processAssemblyChangedCommand(command: AssemblyChangedCommand): void {
      if(this.closed) {
         this.onAssemblyChanged.emit(command);
      }
   }

   /**
    * click go to wizard button.
    */
   goToWizard(): void {
      if(!VSWizardUtil.isTempAssembly(this.assemblyName)) {
         this.openWizardPane();
         return;
      }

      ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
         "_#(js:gotowizard.comfirm.messsage)")
         .then(result => {
            if(result == "ok") {
               this.openWizardPane(true, "cancel");
            }
         });
   }

   /**
    * @param {boolean} goback true if finish/cancel to go back to wizard, else open wizard.
    */
   openWizardPane(goback: boolean = false, bindingOption: string = null): void {
      let editMode = goback ? this.originalMode : VsWizardEditModes.FULL_EDITOR;
      let rid = goback ? this._oldRuntimeId : this.runtimeId;
      let oinfo = this.wizardOriginalInfo;

      if(!oinfo) {
         oinfo = {
            runtimeId: this._oldRuntimeId,
            objectType: this.objectType,
            absoluteName: this.assemblyName,
            editMode: VsWizardEditModes.FULL_EDITOR,
         };
      }

      this.onOpenWizardPane.emit({
         runtimeId: rid,
         linkUri: this.linkUri,
         viewer: this.viewer,
         editMode: editMode,
         objectModel: this.objectModel,
         bindingOption: bindingOption,
         componentWizardEnable: true,
         oinfo: oinfo
      });
   }

   get goToWizardVisible(): boolean {
      return (this.isChart() || this.objectModel.objectType == "VSTable" ||
              this.objectModel.objectType == "VSCrosstab")
         && !this.haveDynamicBinding
         && !this.objectModel.inEmbeddedViewsheet
         && !this.isCube
         && !this.hasExpression();
   }

   private hasExpression() {
      if(this.isCrosstab && this.bindingModel) {
         let crosstab = this.bindingModel as CrosstabBindingModel;

         return this.hasExpressionRef(crosstab.rows) || this.hasExpressionRef(crosstab.cols) ||
            this.hasExpressionRef(crosstab.aggregates);
      }
      else if(this.isChart && this.bindingModel) {
         let chart = this.bindingModel as ChartBindingModel;

         return this.hasExpressionRef(chart.xfields) || this.hasExpressionRef(chart.yfields) ||
            this.hasExpressionRef(chart.groupFields) || this.isExpressionAes(chart.colorField) ||
            this.isExpressionAes(chart.shapeField) || this.isExpressionAes(chart.sizeField);
      }

      return false;
   }

   private hasExpressionRef(arr: BDimensionRef[] | BAggregateRef[] | ChartRef[]) {
      if(arr == null) {
         return false;
      }

      for(let i = 0; i < arr.length; i++) {
         if(arr[i].columnValue.startsWith("=")) {
            return true;
         }
      }

      return false;
   }

   private isExpressionAes(ref: AestheticInfo) {
      return ref.dataInfo.columnValue.startsWith("=");
   }

   private isCrosstab(): boolean {
      return !!this.objectModel && this.objectModel.objectType == "VSCrosstab";
   }

   private isChart(): boolean {
      return !!this.objectModel && this.objectModel.objectType == "VSChart";
   }

   get haveDynamicBinding(): boolean {
      return !!this.objectModel ? this.objectModel.hasDynamic : false;
   }

   popupNotifications(warning: any) {
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

   @HostListener("window:resize", ["$event"])
   onResize(event: any) {
      if(this.objectView) {
         this.objectView.onResize(event);
      }
   }

   resizeObjectView() {
      if(this.objectView) {
         this.objectView.resizeModelView();
      }
   }

   /**
    * Rename an assembly object
    * @param command the command.
    */
   private processRenameVSObjectCommand(command: RenameVSObjectCommand): void {
      if(this.assemblyName === command.oldName) {
         this.objectModel.absoluteName = command.newName;
         this.assemblyName = command.newName;
         this.onRenamed.emit(command.newName);
      }
   }

   processShowLoadingMaskCommand(command: ShowLoadingMaskCommand): void {
      this.loading = true;
   }

   processClearLoadingCommand(command: ClearLoadingCommand): void {
      this.loading = false;
   }

   cancelViewsheetLoading(): void {
      const event: CancelViewsheetLoadingEvent = new CancelViewsheetLoadingEvent(
         this.runtimeId, false, false, true);
      this.clientService.sendEvent("/events/composer/viewsheet/cancelViewsheet", event);
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

   messageChange(messages: ConsoleMessage[]) {
      this.consoleMessages = messages;
   }
}
