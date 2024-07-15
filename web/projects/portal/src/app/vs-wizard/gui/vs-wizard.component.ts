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
import {
   Component,
   EventEmitter,
   Injector,
   Input,
   NgZone,
   OnDestroy,
   OnInit,
   Output
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subscription } from "rxjs";
import { AssetEntry } from "../../../../../shared/data/asset-entry";
import { BindingTreeService } from "../../binding/widget/binding-tree/binding-tree.service";
import { CloseSheetEvent } from "../../composer/gui/vs/event/close-sheet-event";
import { ModelService } from "../../widget/services/model.service";
import { UIContextService } from "../../common/services/ui-context.service";
import { ViewsheetClientService } from "../../common/viewsheet-client/viewsheet-client.service";
import { VSObjectType } from "../../common/data/vs-object-type";
import { ChartService } from "../../graph/services/chart.service";
import { ClearLoadingCommand } from "../../vsobjects/command/clear-loading-command";
import { ShowLoadingMaskCommand } from "../../vsobjects/command/show-loading-mask-command";
import { VSChartService } from "../../vsobjects/objects/chart/services/vs-chart.service";
import { AdhocFilterService } from "../../vsobjects/objects/data-tip/adhoc-filter.service";
import { DataTipService } from "../../vsobjects/objects/data-tip/data-tip.service";
import { PopComponentService } from "../../vsobjects/objects/data-tip/pop-component.service";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { VSScaleService } from "../../widget/services/scale/vs-scale.service";
import {
   DialogService,
   VsWizardDialogServiceFactory,
} from "../../widget/slide-out/dialog-service.service";
import { SlideOutService } from "../../widget/slide-out/slide-out.service";
import { OpenVsWizardEvent } from "../model/event/open-vs-wizard-event";
import { SetRuntimeIdCommand } from "../../vsobjects/command/set-runtime-id-command";
import { CommandProcessor } from "../../common/viewsheet-client/command-processor";
import { HttpClient, HttpParams } from "@angular/common/http";
import { SetViewsheetInfoCommand } from "../../vsobjects/command/set-viewsheet-info-command";
import { VSObjectModel } from "../../vsobjects/model/vs-object-model";
import { RefreshVSWizardEvent } from "../model/event/refresh-vs-wizard-event";
import { TouchAssetEvent } from "../../composer/gui/ws/socket/touch-asset-event";
import { SaveSheetCommand } from "../../composer/gui/ws/socket/save-sheet-command";
import { VsWizardEditModes } from "../model/vs-wizard-edit-modes";
import { CollectParametersCommand } from "../../vsobjects/command/collect-parameters-command";
import { CollectParametersOverEvent } from "../../common/event/collect-parameters-over-event";
import { ComponentTool } from "../../common/util/component-tool";
import { DndService } from "../../common/dnd/dnd.service";
import { VariableInfo } from "../../common/data/variable-info";
import { VariableInputDialog } from "../../widget/dialog/variable-input-dialog/variable-input-dialog.component";
import { VariableInputDialogModel } from "../../widget/dialog/variable-input-dialog/variable-input-dialog-model";
import { OpenWizardObjectEvent } from "../model/event/open-wizard-object-event";
import { VsWizardModel } from "../model/vs-wizard-model";
import { CloseWizardModel } from "../model/close-wizard-model";
import { CloseVsWizardEvent } from "../model/event/close-vs-wizard-event";
import { UpdateRuntimeIdEvent } from "../model/event/update-runtime-id-event";
import { VSDndService } from "../../common/dnd/vs-dnd.service";
import { VSWizardBindingTreeService } from "../services/vs-wizard-binding-tree.service";
import { OpenObjectWizardCommand } from "../model/command/open-object-wizard-command";
import { Tool } from "../../../../../shared/util/tool";
import { Point } from "../../common/data/point";
import { ExpiredSheetCommand } from "../../composer/gui/ws/socket/expired-sheet/expired-sheet-command";

const TOUCH_EVENT_URI = "/events/composer/touch-asset";
const OPEN_WIZARD_URL = "/events/vswizard/dialog/open";
const OPEN_WIZARD_URL0 = "../api/vswizard/dialog/open";
const OPEN_OBJECT_WIZARD_URI = "/events/vswizard/object/open";
const REFRESH_WIZARD_PANE_URI = "/events/composer/vswizard/wizard-pane/refresh";
const UPDATE_RUNTIME_ID_URL = "/events/vswizard/dialog/update-runtimeid";
const VIEWSHEET_WIZARD_CLOSE_URI = "/events/vswizard/dialog/close";
const COLLECT_PARAMS_URI = "/events/vs/collectParameters";
const CLOSE_VIEWSHEET_URI = "/events/composer/viewsheet/close";

export enum WizardPanes {
   NONE,
   WIZARD_PANE,
   OBJECT_WIZARD_PANE
}
@Component({
   selector: "vs-wizard",
   templateUrl: "vs-wizard.component.html",
   styleUrls: ["vs-wizard.component.scss"],
   providers: [
      ViewsheetClientService,
      AdhocFilterService,
      DataTipService,
      VSWizardBindingTreeService,
      VSChartService,
      PopComponentService,
      {
         provide: BindingTreeService,
         useExisting: VSWizardBindingTreeService
      },
      {
         provide: ScaleService,
         useClass: VSScaleService
      },
      {
         provide: DialogService,
         useFactory: VsWizardDialogServiceFactory,
         deps: [NgbModal, SlideOutService, Injector, UIContextService]
      },
      {
         provide: DndService,
         useClass: VSDndService,
         deps: [ModelService, NgbModal, ViewsheetClientService]
      },
      {
         provide: ChartService,
         useExisting: VSChartService
      }
   ]
})
export class VsWizardComponent extends CommandProcessor implements OnInit, OnDestroy {
   private _model: VsWizardModel;
   private subscriptions: Subscription = new Subscription();
   private loadingEventCount = 0;
   preparingData: boolean = false;
   WizardPanes = WizardPanes;
   currentPane: WizardPanes = WizardPanes.NONE;
   objectWizardLoading: boolean = false;
   source: AssetEntry;
   private confirmExpiredDisplayed: boolean = false;

   @Input() set model(m: VsWizardModel) {
      if(m && !m.editMode) {
         m.editMode = m.oinfo.editMode;
      }

      if(m && !m.oinfo && m.objectModel) {
         m.oinfo = {
            runtimeId: null,
            editMode: VsWizardEditModes.VIEWSHEET_PANE,
            objectType: m.objectModel.objectType,
            absoluteName: m.objectModel.absoluteName
         };
      }

      this._model = m;
   }

   get model(): VsWizardModel {
      return this._model;
   }

   get objectName(): string {
      return this.model.objectModel ? this.model.objectModel.absoluteName : null;
   }

   get objectType(): VSObjectType {
      return this.model.objectModel ? this.model.objectModel.objectType : null;
   }

   get isNewViewsheetWizard(): boolean {
      return !!this.model && this.model.oinfo.editMode == VsWizardEditModes.WIZARD_DASHBOARD;
   }


   @Output() onCommit = new EventEmitter<CloseWizardModel>();
   @Output() onCancel = new EventEmitter<CloseWizardModel>();
   @Output() onFullEditor: EventEmitter<CloseWizardModel> = new EventEmitter<CloseWizardModel>();

   constructor(protected zone: NgZone,
               private http: HttpClient,
               private dialogService: DialogService,
               public modalService: NgbModal,
               public viewsheetClient: ViewsheetClientService)
   {
      super(viewsheetClient, zone, true);
   }

   ngOnInit() {
      this.viewsheetClient.connect();

      // case1: vs pane -> chart edit -> object wizard.
      // case2: full editor -> go to wizard -> object wizard.
      // Create another runtimeid to avoid changing the original rvs directly.
      if(!!this.model.runtimeId && !!this.model.oinfo.editMode &&
         this.model.oinfo.editMode != VsWizardEditModes.WIZARD_DASHBOARD &&
         this.model.bindingOption !== "cancel")
      {
         this.createNewRuntimeViewsheet();
      }
      // case3: full editor -> finish editing -> wizard dashboard.
      else if(!!this.model.runtimeId && this.model.bindingOption == "finish") {
         this.viewsheetClient.runtimeId = this.model.runtimeId;
         this.currentPane = WizardPanes.WIZARD_PANE;
         const evt = new RefreshVSWizardEvent(this.model.runtimeId, true);
         this.viewsheetClient.sendEvent(REFRESH_WIZARD_PANE_URI, evt);
      }
      // case4: full editor -> cancel
      // case5: wizard -> go to full editor -> go to wizard
      else if(!!this.model.runtimeId) {
         this.viewsheetClient.runtimeId = this.model.runtimeId;

         if(this.model.objectModel && this.supportFullEditor(this.model.objectModel.objectType)) {
            this.showComponentWizard(false);
         }
      }
      else {
         // create by new viewsheet dialog.
         let event = new OpenVsWizardEvent(this.model.entry);
         this.viewsheetClient.sendEvent(OPEN_WIZARD_URL, event);
         this.currentPane = WizardPanes.WIZARD_PANE;
      }

      this.subscriptions.add(this.viewsheetClient.onHeartbeat.subscribe(() => {
         this.touchAsset();
      }));
   }

   ngOnDestroy(): void {
      if(this.subscriptions) {
         this.subscriptions.unsubscribe();
      }

      this.cleanup();
   }

   private createNewRuntimeViewsheet(): void {
      this.loadingEventCount++;
      let params = new HttpParams()
         .set("runtimeId", this.model.runtimeId)
         .set("viewer", !!this.model.viewer + "")
         .set("temporarySheet", !!this.model.temporarySheet + "");

      this.http.get(OPEN_WIZARD_URL0, {
         headers: {
            "Content-Type": "application/json"
         },
         params: params
      }).subscribe((nrid: string) => {
         if(!!nrid) {
            this.updateRuntimeId(nrid);
            this.viewsheetClient.sendEvent(
               UPDATE_RUNTIME_ID_URL, new UpdateRuntimeIdEvent(nrid));

            // chart/table wizard
            if(this.objectType == "VSChart" || this.objectType == "VSTable" ||
               this.objectType == "VSCrosstab")
            {
               this.showComponentWizard(true);
            }
         }

         this.loadingEventCount--;
      },
      (error) => {
         this.loadingEventCount--;
      });
   }

   getAssemblyName() {
      return null;
   }

   changeCurrentObject(currentObj: VSObjectModel): void {
      this.model.objectModel = currentObj;
   }

   finish(gridRowCount: number): void {
      this.close(true, this.model.oinfo.editMode, null, gridRowCount);
   }

   cancel() {
      let event = new CloseSheetEvent(false);
      this.viewsheetClient.sendEvent(CLOSE_VIEWSHEET_URI, event);

      this.onCancel.emit({
         save: false,
         model: this.model
      });
   }

   closeObjectWizard(event: any): void {
      if(this.isNewViewsheetWizard && this.model.oinfo.editMode == VsWizardEditModes.WIZARD_DASHBOARD)
      {
         this.currentPane = WizardPanes.WIZARD_PANE;
         this.changeCurrentObject(event.currentObject);
         const evt = new RefreshVSWizardEvent(this.model.runtimeId, true);
         this.viewsheetClient.sendEvent(REFRESH_WIZARD_PANE_URI, evt);
      }
      else {
         this.close(event.save, event.editMode, event.currentObject);
      }
   }

   goToFullEditor(event) {
      let model = Tool.clone(this.model);
      model.editMode = event.editMode;
      model.objectModel = !!event.objectModel ? event.objectModel : this.model.objectModel;
      this.onFullEditor.emit({save: false, model: model});
   }

   /**
    * @param save          if need to save the action.
    * @param editMode      which component should display after the close action.
    * @param nobjectModel  the new vsobject model.
    * @param gridRowCount  current grid row count which will be used to insert empty space
    *                      between objects in server for the new created viewsheet.
    */
   close(save: boolean, editMode: VsWizardEditModes,
         nobjectModel?: VSObjectModel, gridRowCount?: number)
   {
      // object cancel
      if(!save) {
         this.onCommit.emit({
            save: false,
            model: this.model
         });
         return;
      }

      if(!!nobjectModel) {
         this.changeCurrentObject(nobjectModel);
      }

      // edit mode maybe changed in object wizard.
      this.model.editMode = editMode;

      // finish editing in object wizard should go to vs pane instead of full editor.
      if(this.model.oinfo.editMode == VsWizardEditModes.FULL_EDITOR ||
         this.model.oinfo.editMode == VsWizardEditModes.VIEWSHEET_PANE)
      {
         this.model.editMode = this.model.oinfo.editMode = VsWizardEditModes.VIEWSHEET_PANE;
      }

      // save and close wizard dialog.
      gridRowCount = gridRowCount !== undefined ? gridRowCount : 0;
      this.viewsheetClient.sendEvent(VIEWSHEET_WIZARD_CLOSE_URI,
         new CloseVsWizardEvent(this.model.editMode, gridRowCount, this.objectName));
   }

   switchToMeta(): void {
      // this method switches to vspane if in wizard, and full editor if not in wizard.
      // this flow is confusing and unexpected. changed (on the server side too) to
      // just mark the vs as meta data mode and continue without switching ui.
      /*
      let vmodel = Tool.clone(this.model);

      // go to vs pane.
      if(this.model.oinfo.editMode == VsWizardEditModes.WIZARD_DASHBOARD) {
         vmodel.editMode = VsWizardEditModes.WIZARD_DASHBOARD;
      }
      // if edit chart\table\crosstab, go to full editor.
      else {
         vmodel.runtimeId = this.model.oinfo.runtimeId;
         vmodel.editMode = VsWizardEditModes.FULL_EDITOR;
         vmodel.objectModel.objectType = <VSObjectType> this.model.oinfo.objectType;
         vmodel.objectModel.absoluteName = this.model.oinfo.absoluteName;
      }

      let closeModel = {
         save: true,
         model: vmodel
      };

      this.onCommit.emit(closeModel);
      */
   }

   private supportFullEditor(type: string): boolean {
      return type == "VSChart" || type == "VSCrosstab" || type == "VSTable";
   }

   showComponentWizard(newObject: boolean): void {
      if(!!this.objectName) {
         this.goToComponentWizard({objectType: this.objectType, objectName: this.objectName},
                                 newObject);
      }
   }

   goToComponentWizard(evt: any, newObject: boolean) {
      const point = evt.point;
      this.currentPane = WizardPanes.NONE;
      let event: OpenWizardObjectEvent = new OpenWizardObjectEvent(this.model.runtimeId,
         !!point ? point.x : 0, !!point ? point.y : 0,
         this.model.bindingOption, evt.objectName);
      this.viewsheetClient.sendEvent(OPEN_OBJECT_WIZARD_URI, event);
      this.model.oinfo.objectType = evt.objectType;

      if(newObject) {
         this.model.oinfo.absoluteName = evt.objectName;
      }

      this.objectWizardLoading = true;
      this.model.bindingOption = null;
   }

   private processSetRuntimeIdCommand(command: SetRuntimeIdCommand) {
      this.updateRuntimeId(command.runtimeId);
   }

   private updateRuntimeId(rid: string): void {
      this.viewsheetClient.runtimeId = rid;
      this.model.runtimeId = rid;
      this.dialogService.setSheetId(rid);

      if(this.isNewViewsheetWizard) {
         this.goToComponentWizard({point: new Point(20, 20)}, true);
      }
   }

   private processSetViewsheetInfoCommand(command: SetViewsheetInfoCommand) {
      this.model.linkUri = command.linkUri;
      this.model.assetId = !!command.assetId ? command.assetId : this.model.assetId;
   }

   /**
    * Receive parameter prompts.
    * @param {CollectParametersCommand} command
    */
   private processCollectParametersCommand(command: CollectParametersCommand): void {
      let vars: VariableInfo[] = [];
      let disVars: VariableInfo[] = [];

      command.variables.forEach((variable: VariableInfo) => {
         let index: number = command.disabledVariables.indexOf(variable.name);

         if(index == -1) {
            vars.push(variable);
         }
         else {
            variable.values = [];
            disVars.push(variable);
         }
      });

      if(!command.disableParameterSheet && vars.length > 0) {
         this.enterParameters(vars, disVars);
      }
      else {
         const variables: VariableInfo[] = [];
         let event: CollectParametersOverEvent = new CollectParametersOverEvent(variables, true);
         this.viewsheetClient.sendEvent(COLLECT_PARAMS_URI, event);
      }
   }

   private enterParameters(variables: VariableInfo[], disabledVariables: VariableInfo[]) {
      let variableInputDialogModel = <VariableInputDialogModel> {
         varInfos: variables
      };

      const vdialog = ComponentTool.showDialog(this.modalService, VariableInputDialog,
         (model: VariableInputDialogModel) => {
               const vars: VariableInfo[] = model.varInfos.concat(disabledVariables);
               let event: CollectParametersOverEvent = new CollectParametersOverEvent(vars);
               this.viewsheetClient.sendEvent(COLLECT_PARAMS_URI, event);
         }, {backdrop: "static"});
      vdialog.model = variableInputDialogModel;
   }

   private processSaveSheetCommand(command: SaveSheetCommand): void {
      this.model.assetId = command.id;
      this.onCommit.emit({ save: true, model: this.model });
   }

   private touchAsset(): void {
      if(!!this.model.runtimeId) {
         let event = new TouchAssetEvent();
         event.setDesign(true);
         event.setChanged(false);
         event.setUpdate(false);
         this.viewsheetClient.sendEvent(TOUCH_EVENT_URI, event);
      }
   }

   private processOpenObjectWizardCommand(command: OpenObjectWizardCommand) {
      if(command.open) {
         this.objectWizardLoading = false;
         this.currentPane = WizardPanes.OBJECT_WIZARD_PANE;
      }
   }

   private processClearLoadingCommand(command: ClearLoadingCommand): void {
      this.loadingEventCount -= command.count;
   }

   private processShowLoadingMaskCommand(command: ShowLoadingMaskCommand) {
      // don't increment the second command that turns on preparing data label
      if(!command.preparingData) {
         this.loadingEventCount++;
      }

      this.preparingData = command.preparingData;
   }

   showLoading(): boolean {
      return !this.model.runtimeId || this.objectWizardLoading || this.loadingEventCount !== 0;
   }

   hiddenNewBlockChanged(hidden: boolean) {
      this.model.hiddenNewBlock = hidden;
   }

   private processExpiredSheetCommand(command: ExpiredSheetCommand) {
      if(!this.confirmExpiredDisplayed) {
         this.confirmExpiredDisplayed = true;

         const message: string = "_#(js:common.expiredWizard)";
         return ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", message,
                                                {"ok": "_#(js:OK)"})
            .then((result: string) => {
               this.onCancel.emit({
                  save: false,
                  model: this.model
               });
            })
            .catch(() => false);
      }

      return null;
   }
}
