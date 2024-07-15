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
import { HttpParams } from "@angular/common/http";
import { Directive, EventEmitter, Input, OnDestroy, Output } from "@angular/core";
import { forkJoin as observableForkJoin, Subscription } from "rxjs";
import { Tool } from "../../../../../../../shared/util/tool";
import { AssemblyActionEvent } from "../../../../common/action/assembly-action-event";
import { RectangleActions } from "../../../../vsobjects/action/rectangle-actions";
import { VSRectangleModel } from "../../../../vsobjects/model/vs-rectangle-model";
import { VSUtil } from "../../../../vsobjects/util/vs-util";
import { loadingScriptTreeModel, ScriptPaneTreeModel } from "../../../../widget/dialog/script-pane/script-pane-tree-model";
import { ModelService } from "../../../../widget/services/model.service";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { RectanglePropertyDialogModel } from "../../../data/vs/rectangle-property-dialog-model";
import { Viewsheet } from "../../../data/vs/viewsheet";
import { RectanglePropertyDialog } from "../../../dialog/vs/rectangle-property-dialog.component";
import { LockVSObjectEvent } from "../objects/event/lock-vs-object-event";
import { AbstractActionHandler } from "./abstract-action-handler";
import { ContextProvider } from "../../../../vsobjects/context-provider.service";

const RECTANGLE_PROPERTY_URI: string = "composer/vs/rectangle-property-dialog-model/";

@Directive({
   selector: "[cRectangleActionHandler]"
})
export class RectangleActionHandlerDirective extends AbstractActionHandler implements OnDestroy {
   @Input() model: VSRectangleModel;
   @Input() vsInfo: Viewsheet;
   private subscription: Subscription;

   @Input()
   set actions(value: RectangleActions) {
      this.unsubscribe();

      if(value) {
         this.subscription = value.onAssemblyActionEvent.subscribe(
            (event) => this.handleEvent(event));
      }
   }

   @Output() onOpenFormatPane = new EventEmitter<VSRectangleModel>();

   constructor(private modelService: ModelService, modalService: DialogService,
               protected context: ContextProvider) {
      super(modalService, context);
   }

   ngOnDestroy(): void {
      this.unsubscribe();
   }

   private unsubscribe() {
      if(this.subscription) {
         this.subscription.unsubscribe();
         this.subscription = null;
      }
   }

   private handleEvent(event: AssemblyActionEvent<VSRectangleModel>): void {
      switch(event.id) {
      case "rectangle properties":
         this.showPropertyDialog();
         break;
      case "rectangle lock":
         this.lockObject();
         break;
      case "rectangle unlock":
         this.unlockObject();
         break;
      case "rectangle edit-script":
         this.showPropertyDialog(true);
         break;
      case "rectangle show-format-pane":
         this.onOpenFormatPane.emit(this.model);
         break;
      }
   }

   private showPropertyDialog(openToScript: boolean = false): void {
      const modelUri: string = "../api/" + RECTANGLE_PROPERTY_URI +
         Tool.encodeURIPath(this.model.absoluteName) + "/" +
         Tool.encodeURIPath(this.vsInfo.runtimeId);
      const scriptUri: string = "../api/vsscriptable/scriptTree";
      const params = new HttpParams()
         .set("vsId", this.vsInfo.runtimeId)
         .set("assemblyName", this.model.absoluteName);
      this.modelService.getModel(modelUri).subscribe((data: RectanglePropertyDialogModel) => {
         const options = { windowClass: "property-dialog-window",
                           title: this.getTitle(this.model, "_#(js:Properties)"),
                           objectId: this.model.absoluteName, limitResize: false };
         const dialog: RectanglePropertyDialog = this.showDialog(
            RectanglePropertyDialog, options,
            (result: RectanglePropertyDialogModel) => {
               const eventUri: string = "/events/" + RECTANGLE_PROPERTY_URI +
                  this.model.absoluteName;
               this.vsInfo.socketConnection.sendEvent(eventUri, result);
               this.model.absoluteName = result.shapeGeneralPaneModel.basicGeneralPaneModel.name;
            });
         dialog.model = data;
         dialog.variableValues =
            VSUtil.getVariableList(this.vsInfo.vsObjects, this.model.absoluteName);
         dialog.runtimeId = this.vsInfo.runtimeId;
         dialog.openToScript = openToScript;
         dialog.scriptTreeModel = loadingScriptTreeModel;
         this.modelService.getModel(scriptUri, params).subscribe(res => dialog.scriptTreeModel = res);
      });
   }

   private lockObject(): void {
      this.setLocked(true);
   }

   private unlockObject(): void {
      this.setLocked(false);
   }

   private setLocked(locked: boolean): void {
      this.model.locked = locked;
      this.vsInfo.focusedAssembliesChanged();
      const event: LockVSObjectEvent =
         new LockVSObjectEvent(this.model.absoluteName, this.model.locked);
      this.vsInfo.socketConnection.sendEvent(
         "/events/composer/viewsheet/objects/lock", event);
   }
}
