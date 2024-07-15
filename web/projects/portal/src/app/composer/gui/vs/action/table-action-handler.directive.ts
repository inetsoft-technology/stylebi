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
import { HttpParams } from "@angular/common/http";
import { Directive, HostListener, Injector, Input, OnDestroy } from "@angular/core";
import { Subscription } from "rxjs";
import { AssemblyActionEvent } from "../../../../common/action/assembly-action-event";
import { TableActions } from "../../../../vsobjects/action/table-actions";
import { VSTableActionHandler } from "../../../../vsobjects/binding/vs-table-action-handler";
import { HyperlinkDialog } from "../../../../vsobjects/dialog/graph/hyperlink-dialog.component";
import { ConvertToFreehandTableEvent } from "../../../../vsobjects/event/convert-to-freehand-table-event";
import { CopyHighlightEvent } from "../../../../vsobjects/event/copy-highlight-event";
import { PasteHighlightEvent } from "../../../../vsobjects/event/paste-highlight-event";
import { HyperlinkDialogModel } from "../../../../vsobjects/model/hyperlink-dialog-model";
import { VSTableModel } from "../../../../vsobjects/model/vs-table-model";
import { VSUtil } from "../../../../vsobjects/util/vs-util";
import { ModelService } from "../../../../widget/services/model.service";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { Viewsheet } from "../../../data/vs/viewsheet";
import { AbstractActionHandler } from "./abstract-action-handler";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../../../../common/util/component-tool";
import { SourceInfoType } from "../../../../binding/data/source-info-type";
import { ContextProvider } from "../../../../vsobjects/context-provider.service";

const HYPERLINK_URI: string = "composer/vs/hyperlink-dialog-model";
const TABLE_CONVERT_TO_CALC_URI: string = "/events/composer/viewsheet/table/convertToFreehand";
const TABLE_COPY_HIGHLIGHT_URI: string = "/events/composer/viewsheet/table/copyHighlight";
const TABLE_PASTE_HIGHLIGHT_URI: string = "/events/composer/viewsheet/table/pasteHighlight";

@Directive({
   selector: "[cTableActionHandler]"
})
export class TableActionHandlerDirective extends AbstractActionHandler implements OnDestroy {
   @Input() model: VSTableModel;
   private _viewsheet: Viewsheet;
   private subscription: Subscription;
   private bindingActionHandler: VSTableActionHandler;

   @Input()
   set actions(value: TableActions) {
      this.unsubscribe();

      if(value) {
         this.subscription = value.onAssemblyActionEvent.subscribe(
            (event) => this.handleEvent(event));
      }
   }

   @Input()
   set vsInfo(value: Viewsheet) {
      this.bindingActionHandler = null;
      this._viewsheet = value;

      if(value) {
         this.bindingActionHandler =
            new VSTableActionHandler(this.modelService, value.socketConnection,
                                     this.modalService, this.injector, this.context);
      }
   }

   constructor(private modelService: ModelService, modalService: DialogService,
               private injector: Injector, private ngbModal: NgbModal,
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

   private handleEvent(event: AssemblyActionEvent<VSTableModel>): void {
      switch(event.id) {
      case "table hyperlink":
         this.showHyperlinkDialog();
         break;
      case "table convert-to-freehand-table":
         this.convertToFreehandTable();
         break;
      case "table copy-highlight":
         this.copyHighlight();
         break;
      case "table paste-highlight":
         this.pasteHighlight();
         break;
      default:
         this.bindingActionHandler.handleEvent(
            event,
            VSUtil.getVariableList(this._viewsheet.vsObjects, this.model.absoluteName));
      }
   }

   @HostListener("onOpenConditionDialog")
   showConditionDialog(): void {
      this.bindingActionHandler.tableActionHandler.showConditionDialog(
         this._viewsheet.runtimeId, this.model.absoluteName,
         VSUtil.getVariableList(this._viewsheet.vsObjects, this.model.absoluteName));
   }

   private convertToFreehandTable(): void {
      if(this.model.sourceType == SourceInfoType.VS_ASSEMBLY && this._viewsheet.metadata) {
         ComponentTool.showMessageDialog(this.ngbModal, "_#(js:Error)",
            "_#(js:composer.vs.table.cannotConvertToFreehand)");
         return;
      }

      this.modalService.objectDelete(this.model.absoluteName);
      const event: ConvertToFreehandTableEvent =
         new ConvertToFreehandTableEvent(this.model.absoluteName);
      this._viewsheet.socketConnection.sendEvent(TABLE_CONVERT_TO_CALC_URI, event);
   }

   private copyHighlight(): void {
      const event: CopyHighlightEvent = new CopyHighlightEvent(this.model.absoluteName,
                                                               this.model.selectedRegions[0]);
      this._viewsheet.socketConnection.sendEvent(TABLE_COPY_HIGHLIGHT_URI, event);
   }

   private pasteHighlight(): void {
      const event: PasteHighlightEvent = new PasteHighlightEvent(this.model.absoluteName,
                                                                 this.model.selectedRegions);
      this._viewsheet.socketConnection.sendEvent(TABLE_PASTE_HIGHLIGHT_URI, event);
   }

   private showHyperlinkDialog(): void {
      if(this.model.firstSelectedRow == -1 || this.model.firstSelectedColumn == -1) {
         return;
      }

      const params = new HttpParams()
         .set("runtimeId", this._viewsheet.runtimeId)
         .set("objectId", this.model.absoluteName)
         .set("row", this.model.firstSelectedRow + "")
         .set("col", this.model.firstSelectedColumn + "");

      const eventUri: string = "/events/" + HYPERLINK_URI + "/" +
         this.model.absoluteName;

      this.bindingActionHandler.showTableDialog(
         HyperlinkDialog, "../api/" + HYPERLINK_URI, eventUri,
         (dialog: HyperlinkDialog, model: HyperlinkDialogModel) => {
            dialog.model = model;
            dialog.runtimeId = this._viewsheet.runtimeId;
            dialog.objectName = this.model.absoluteName;
         }, params, this.model.absoluteName);
   }

   @HostListener("onOpenHighlightDialog")
   showHighlightDialog(): void {
      this.bindingActionHandler.tableActionHandler.showHighlightDialog(
         this._viewsheet.runtimeId, this.model.absoluteName, this.model.firstSelectedRow,
         this.model.firstSelectedColumn,
         VSUtil.getVariableList(this._viewsheet.vsObjects, this.model.absoluteName));
   }
}
