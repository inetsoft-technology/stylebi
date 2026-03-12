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
import { Component, Input, NgZone, OnDestroy, OnInit } from "@angular/core";
import { WizPortalService } from "../../../../../../../shared/wiz-portal/wiz-portal.service";
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
import { NewViewsheetEvent } from "../../vs/event/new-viewsheet-event";
import { CloseSheetCommand } from "../../ws/socket/close-sheet-command";
import { GuiTool } from "../../../../common/util/gui-tool";
import { WizService } from "../services/wiz.service";

@Component({
   selector: "wiz-visualization-pane",
   templateUrl: "./wiz-visualization-pane.component.html",
   styleUrls: ["./wiz-visualization-pane.component.scss"],
   providers: [
      ViewsheetClientService
   ]
})
export class WizVisualizationPane extends CommandProcessor implements OnInit, OnDestroy {
   @Input() currentVisualization: WizDashboard;

   get styleBIUrl(): string {
      return this.wizPortalService.styleBIUrl;
   }

   get wizServiceUrl(): string {
      return this.wizPortalService.wizServiceUrl;
   }

   get userId(): string {
      return this.wizPortalService.userId;
   }

   get domain(): string {
      return this.wizPortalService.domain;
   }

   constructor(private viewsheetClient: ViewsheetClientService, zone: NgZone,
               private wizService: WizService, private wizPortalService: WizPortalService)
   {
      super(viewsheetClient, zone, true);
   }

   getAssemblyName(): string {
      return null;
   }

   ngOnInit(): void {
      this.viewsheetClient.connect();
      this.currentVisualization.socketConnection = this.viewsheetClient;

      const size: [number, number] = GuiTool.getViewportSize();
      const mobile: boolean = GuiTool.isMobileDevice();

      if(this.currentVisualization.newSheet) {
         const event = new NewViewsheetEvent(
            this.currentVisualization.id, size[0], size[1], mobile,
            window.navigator.userAgent, null, false, true, this.currentVisualization?.visualizationSheet);
         event.dataSources = this.currentVisualization.baseEntries;
         event.viewer = false;
         this.viewsheetClient.sendEvent("/events/composer/viewsheet/new", event);
      }
      else {
         const event = new OpenViewsheetEvent(
            this.currentVisualization.id, size[0], size[1], mobile,
            window.navigator.userAgent, this.currentVisualization.meta, false);
         event.viewer = false;

         this.viewsheetClient.sendEvent("/events/open", event);
      }
   }

   exit(): void {
      this.wizService.onExitVisualization();
   }

   accept(): void {
      this.wizService.onSaveVisualization(this.currentVisualization);
   }

   ngOnDestroy(): void {
      super.cleanup();
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
         if(this.currentVisualization.currentFocusedAssemblies[i].absoluteName == obj.absoluteName) {
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
}
