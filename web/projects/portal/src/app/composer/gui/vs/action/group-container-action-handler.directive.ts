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
import { GroupContainerActions } from "../../../../vsobjects/action/group-container-actions";
import { VSGroupContainerModel } from "../../../../vsobjects/model/vs-group-container-model";
import { VSUtil } from "../../../../vsobjects/util/vs-util";
import { loadingScriptTreeModel, ScriptPaneTreeModel } from "../../../../widget/dialog/script-pane/script-pane-tree-model";
import { ModelService } from "../../../../widget/services/model.service";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { GroupContainerPropertyDialogModel } from "../../../data/vs/group-container-property-dialog-model";
import { Viewsheet } from "../../../data/vs/viewsheet";
import { GroupContainerPropertyDialog } from "../../../dialog/vs/group-container-property-dialog.component";
import { AbstractActionHandler } from "./abstract-action-handler";
import { ContextProvider } from "../../../../vsobjects/context-provider.service";

const GROUP_PROPERTY_URI: string = "composer/vs/group-container-property-dialog-model/";

@Directive({
   selector: "[cGroupContainerActionHandler]"
})
export class GroupContainerActionHandlerDirective extends AbstractActionHandler implements OnDestroy {
   @Input() model: VSGroupContainerModel;
   @Input() vsInfo: Viewsheet;
   @Output() onOpenFormatPane = new EventEmitter<VSGroupContainerModel>();
   private subscription: Subscription;

   @Input()
   set actions(value: GroupContainerActions) {
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

   private handleEvent(event: AssemblyActionEvent<VSGroupContainerModel>): void {
      switch(event.id) {
      case "group properties":
         this.showPropertyDialog();
         break;
      case "group show-format-pane":
         this.onOpenFormatPane.emit(this.model);
         break;
      case "group edit-script":
         this.showPropertyDialog(true);
         break;
      }
   }

   private showPropertyDialog(openToScript: boolean = false): void {
      const modelUri: string = "../api/" + GROUP_PROPERTY_URI +
         Tool.encodeURIPath(this.model.absoluteName) + "/" +
         Tool.encodeURIPath(this.vsInfo.runtimeId);
      const scriptUri: string = "../api/vsscriptable/scriptTree";
      const params = new HttpParams()
         .set("vsId", this.vsInfo.runtimeId)
         .set("assemblyName", this.model.absoluteName);
      this.modelService.getModel(modelUri).subscribe((data: GroupContainerPropertyDialogModel) => {
         const options = { windowClass: "property-dialog-window",
                           title: this.getTitle(this.model, "_#(js:Properties)"),
                           objectId: this.model.absoluteName, limitResize: false };
         const dialog: GroupContainerPropertyDialog = this.showDialog(
            GroupContainerPropertyDialog, options,
            (result: GroupContainerPropertyDialogModel) => {
               const eventUri: string =
                  "/events/" + GROUP_PROPERTY_URI + this.model.absoluteName;
               this.vsInfo.socketConnection.sendEvent(eventUri, result);
               this.model.absoluteName = result.groupContainerGeneralPane.generalPropPane
                  .basicGeneralPaneModel.name;
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
}
