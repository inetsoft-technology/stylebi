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
import { Directive, Input, OnDestroy } from "@angular/core";
import { forkJoin as observableForkJoin, Subscription } from "rxjs";
import { Tool } from "../../../../../../../shared/util/tool";
import { AssemblyActionEvent } from "../../../../common/action/assembly-action-event";
import { CalendarActions } from "../../../../vsobjects/action/calendar-actions";
import { VSCalendarModel } from "../../../../vsobjects/model/calendar/vs-calendar-model";
import { VSUtil } from "../../../../vsobjects/util/vs-util";
import { loadingScriptTreeModel, ScriptPaneTreeModel } from "../../../../widget/dialog/script-pane/script-pane-tree-model";
import { ModelService } from "../../../../widget/services/model.service";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { CalendarPropertyDialogModel } from "../../../data/vs/calendar-property-dialog-model";
import { Viewsheet } from "../../../data/vs/viewsheet";
import { CalendarPropertyDialog } from "../../../dialog/vs/calendar-property-dialog.component";
import { AbstractActionHandler } from "./abstract-action-handler";
import { ContextProvider } from "../../../../vsobjects/context-provider.service";

const SERVICE_URI: string = "composer/vs/calendar-property-dialog-model/";

@Directive({
   selector: "[cCalendarActionHandler]"
})
export class CalendarActionHandlerDirective extends AbstractActionHandler implements OnDestroy {
   @Input() model: VSCalendarModel;
   @Input() vsInfo: Viewsheet;
   private subscription: Subscription;

   @Input()
   set actions(value: CalendarActions) {
      this.unsubscribe();

      if(value) {
         this.subscription = value.onAssemblyActionEvent.subscribe(
            (event) => this.handleEvent(event));
      }
   }

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

   private handleEvent(event: AssemblyActionEvent<VSCalendarModel>): void {
      switch(event.id) {
      case "calendar properties":
         this.showPropertyDialog();
         break;
      case "calendar edit-script":
         this.showPropertyDialog(true);
         break;
      }
   }

   private showPropertyDialog(openToScript: boolean = false): void {
      const runtimeId: string = this.vsInfo.runtimeId;
      const objectId: string = this.model.absoluteName;
      const modelUri: string = "../api/" + SERVICE_URI + objectId + "/" +
         Tool.encodeURIPath(runtimeId);
      const scriptUri: string = "../api/vsscriptable/scriptTree";
      const params = new HttpParams()
         .set("assemblyName", objectId)
         .set("vsId", runtimeId);
      this.modelService.getModel(modelUri).subscribe((data: CalendarPropertyDialogModel) => {
         const options = { windowClass: "property-dialog-window",
                           title: this.getTitle(this.model, "_#(js:Properties)"),
                           objectId: this.model.absoluteName, limitResize: false};
         const dialog: CalendarPropertyDialog = this.showDialog(
            CalendarPropertyDialog, options,
            (result: CalendarPropertyDialogModel) => {
               const eventUri: string = "/events/" + SERVICE_URI + this.model.absoluteName;
               this.vsInfo.socketConnection.sendEvent(eventUri, result);
               this.model.absoluteName = result.calendarGeneralPaneModel.generalPropPaneModel
                  .basicGeneralPaneModel.name;
            });
         dialog.model = data;
         dialog.runtimeId = runtimeId;
         dialog.variableValues =
            VSUtil.getVariableList(this.vsInfo.vsObjects, this.model.absoluteName);
         dialog.openToScript = openToScript;
         dialog.scriptTreeModel = loadingScriptTreeModel;
         this.modelService.getModel(scriptUri, params).subscribe(res => dialog.scriptTreeModel = res);
      },
      (error: any) => {
            //TODO handle error
            console.error("Failed to get calendar property model: ", error);
      });
   }
}
