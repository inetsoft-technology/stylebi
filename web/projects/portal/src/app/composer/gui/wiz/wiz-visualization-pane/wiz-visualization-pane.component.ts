/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
import { Component, Injector, Input, NgZone, OnDestroy, OnInit } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subscription } from "rxjs";
import { WizPortalService } from "../../../../../../../shared/wiz-portal/wiz-portal.service";
import { BindingTreeService } from "../../../../binding/widget/binding-tree/binding-tree.service";
import { DndService } from "../../../../common/dnd/dnd.service";
import { VSDndService } from "../../../../common/dnd/vs-dnd.service";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { ChartService } from "../../../../graph/services/chart.service";
import { VSWizardBindingTreeService } from "../../../../vs-wizard/services/vs-wizard-binding-tree.service";
import {

   ContextProvider,
   wizardContextProviderFactory,
} from "../../../../vsobjects/context-provider.service";
import { VSChartService } from "../../../../vsobjects/objects/chart/services/vs-chart.service";
import { AdhocFilterService } from "../../../../vsobjects/objects/data-tip/adhoc-filter.service";
import { DataTipService } from "../../../../vsobjects/objects/data-tip/data-tip.service";
import { PopComponentService } from "../../../../vsobjects/objects/data-tip/pop-component.service";
import { ModelService } from "../../../../widget/services/model.service";
import { ScaleService } from "../../../../widget/services/scale/scale-service";
import { VSScaleService } from "../../../../widget/services/scale/vs-scale.service";
import {
   DialogService, VsWizardDialogServiceFactory
} from "../../../../widget/slide-out/dialog-service.service";
import { SlideOutService } from "../../../../widget/slide-out/slide-out.service";
import { WizDashboard } from "../../../data/vs/wizDashboard";
import { CommandProcessor, ViewsheetClientService } from "../../../../common/viewsheet-client";
import { InitGridCommand } from "../../../../vsobjects/command/init-grid-command";
import { SetRuntimeIdCommand } from "../../../../vsobjects/command/set-runtime-id-command";
import { AddVSObjectCommand } from "../../../../vsobjects/command/add-vs-object-command";
import { RemoveVSObjectCommand } from "../../../../vsobjects/command/remove-vs-object-command";
import { RefreshVSObjectCommand } from "../../../../vsobjects/command/refresh-vs-object-command";
import { UpdateUndoStateCommand } from "../../../../vsobjects/command/update-unto-state-command";
import { VSObjectModel } from "../../../../vsobjects/model/vs-object-model";
import { VSUtil } from "../../../../vsobjects/util/vs-util";
import { OpenViewsheetEvent } from "../../../../vsobjects/event/open-viewsheet-event";
import { VSRefreshEvent } from "../../../../vsobjects/event/vs-refresh-event";
import { NewViewsheetEvent } from "../../vs/event/new-viewsheet-event";
import { CloseSheetCommand } from "../../ws/socket/close-sheet-command";
import { TouchAssetEvent } from "../../ws/socket/touch-asset-event";
import { GuiTool } from "../../../../common/util/gui-tool";
import { SetViewsheetInfoCommand } from "../../../../vsobjects/command/set-viewsheet-info-command";
import { WizService } from "../services/wiz.service";

@Component({
   selector: "wiz-visualization-pane",
   templateUrl: "./wiz-visualization-pane.component.html",
   styleUrls: ["./wiz-visualization-pane.component.scss"],
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
      },
      {
         provide: ContextProvider,
         useFactory: wizardContextProviderFactory
      }
   ]
})
export class WizVisualizationPane extends CommandProcessor implements OnInit, OnDestroy {
   @Input() currentVisualization: WizDashboard;
   initError: string = null;
   private connected: boolean = false;
   private heartbeatSubscription: Subscription = Subscription.EMPTY;

   constructor(private viewsheetClient: ViewsheetClientService, zone: NgZone,
               private wizService: WizService, public wizPortalService: WizPortalService)
   {
      super(viewsheetClient, zone, true);
   }

   getAssemblyName(): string {
      return null;
   }

   ngOnInit(): void {
      if(!this.currentVisualization.wizSheetRuntimeId &&
         !this.currentVisualization.standaloneVisualization)
      {
         this.initError = "_#(js:wiz.visualization.missing.runtime.id)";
         return;
      }

      this.connected = true;
      this.viewsheetClient.connect();
      this.heartbeatSubscription = this.viewsheetClient.onHeartbeat.subscribe(() => {
         this.touchAsset();
      });
      this.currentVisualization.socketConnection = this.viewsheetClient;

      const size: [number, number] = GuiTool.getViewportSize();
      const mobile: boolean = GuiTool.isMobileDevice();

      if(this.currentVisualization.newSheet) {
         const event = new NewViewsheetEvent(
            this.currentVisualization.id, size[0], size[1], mobile,
            window.navigator.userAgent, null, false, true,
            this.currentVisualization.standaloneVisualization,
            this.currentVisualization?.visualizationSheet, this.currentVisualization.wizSheetRuntimeId);
         event.dataSources = this.currentVisualization.baseEntries;
         event.viewer = false;
         this.viewsheetClient.sendEvent("/events/composer/viewsheet/new", event);
      }
      else {
         const event = new OpenViewsheetEvent(
            this.currentVisualization.id, size[0], size[1], mobile,
            window.navigator.userAgent, this.currentVisualization.meta,
            false, false, true, null, this.currentVisualization.wizSheetRuntimeId);
         event.viewer = false;

         this.viewsheetClient.sendEvent("/events/open", event);
      }
   }

   ngOnDestroy(): void {
      if(this.connected) {
         this.heartbeatSubscription.unsubscribe();
         super.cleanup();
      }
   }

   private touchAsset(): void {
      if(this.currentVisualization?.runtimeId) {
         const event = new TouchAssetEvent();
         event.setDesign(true);
         event.setChanged(false);
         event.setUpdate(false);
         this.viewsheetClient.sendEvent("/events/composer/touch-asset", event);
      }
   }

   private processSetViewsheetInfoCommand(command: SetViewsheetInfoCommand): void {
      if(command.linkUri) {
         this.currentVisualization.linkUri = command.linkUri;
      }

      if(command.chatSessionId) {
         this.currentVisualization.chatSessionId = command.chatSessionId;
      }
   }

   private processSetRuntimeIdCommand(command: SetRuntimeIdCommand): void {
      this.currentVisualization.runtimeId = command.runtimeId;
      this.viewsheetClient.runtimeId = command.runtimeId;
   }

   private processInitGridCommand(command: InitGridCommand): void {
      if(command.initing) {
         this.currentVisualization.vsObjects = [];
         this.currentVisualization.id = command.entry.identifier;
      }
   }

   private processCloseSheetCommand(_command: CloseSheetCommand): void {
      this.currentVisualization.runtimeId = null;
   }

   private processAddVSObjectCommand(command: AddVSObjectCommand): void {
      for(let i = 0; i < this.currentVisualization.vsObjects.length; i++) {
         if(this.currentVisualization.vsObjects[i].absoluteName === command.name) {
            this.replaceObject(command.model, i);
            return;
         }
      }

      this.currentVisualization.vsObjects.push(command.model);
      this.currentVisualization.variableNames =
         VSUtil.getVariableList(this.currentVisualization.vsObjects, null);

      this.currentVisualization.vsObjects.forEach((vsObject) => {
         if(this.currentVisualization.currentFocusedAssemblies.length > 0 &&
            vsObject.absoluteName ===
            this.currentVisualization.currentFocusedAssemblies[0].absoluteName)
         {
            this.currentVisualization.clearFocusedAssemblies();
            this.currentVisualization.selectAssembly(vsObject);
         }
      });
   }

   private processRemoveVSObjectCommand(command: RemoveVSObjectCommand): void {
      this.currentVisualization.vsObjects.forEach((vsObject, index) => {
         if(vsObject.absoluteName === command.name) {
            this.currentVisualization.vsObjects.splice(index, 1);
         }
      });
   }

   private processRefreshVSObjectCommand(command: RefreshVSObjectCommand): void {
      this.refreshVSObject(command.info);
   }

   private processUpdateUndoStateCommand(command: UpdateUndoStateCommand): void {
      this.currentVisualization.points = command.points;
      this.currentVisualization.current = command.current;
      this.currentVisualization.currentTS = (new Date()).getTime();
      this.currentVisualization.savePoint = command.savePoint;
   }

   refreshVSObject(obj: VSObjectModel): void {
      let updated = false;

      for(let i = 0; i < this.currentVisualization.vsObjects.length; i++) {
         if(this.currentVisualization.vsObjects[i].absoluteName === obj.absoluteName) {
            this.replaceObject(obj, i);
            updated = true;
            break;
         }
      }

      for(let i = 0; i < this.currentVisualization.currentFocusedAssemblies.length; i++) {
         if(this.currentVisualization.currentFocusedAssemblies[i].absoluteName === obj.absoluteName) {
            this.currentVisualization.currentFocusedAssemblies[i] = obj;
            this.currentVisualization.focusedAssembliesChanged();
         }
      }

      if(!updated) {
         this.currentVisualization.vsObjects.push(obj);

         this.currentVisualization.variableNames =
            VSUtil.getVariableList(this.currentVisualization.vsObjects, null);
      }
   }

   private replaceObject(newModel: VSObjectModel, index: number): void {
      this.currentVisualization.vsObjects[index] =
         VSUtil.replaceObject(this.currentVisualization.vsObjects[index], newModel);
      this.currentVisualization.updateSelectedAssembly(this.currentVisualization.vsObjects[index]);
   }

   refreshVisualization(): void {
      const event = new VSRefreshEvent();
      event.setUserRefresh(true);
      this.viewsheetClient.sendEvent("/events/vs/refresh", event);
   }
}
