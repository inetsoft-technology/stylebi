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
import { Type } from "@angular/core";
import { NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { VSConditionDialogModel } from "../../../common/data/condition/vs-condition-dialog-model";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { AbstractActionHandler } from "../../../composer/gui/vs/action/abstract-action-handler";
import { HighlightDialogModel } from "../../../widget/highlight/highlight-dialog-model";
import { ModelService } from "../../../widget/services/model.service";
import { DialogService } from "../../../widget/slide-out/dialog-service.service";
import { HighlightDialog } from "../../dialog/highlight-dialog.component";
import { VSConditionDialog } from "../../dialog/vs-condition-dialog.component";
import { ContextProvider } from "../../context-provider.service";

const CONDITION_URI: string = "composer/vs/vs-condition-dialog-model";
const HIGHLIGHT_URI: string = "composer/vs/highlight-dialog-model";

export class TableActionHandler extends AbstractActionHandler {
   constructor(private modelService: ModelService,
               private viewsheetClient: ViewsheetClientService,
               protected modalService: DialogService,
               protected context: ContextProvider) {
      super(modalService, context);
   }

   showConditionDialog(runtimeId: string, absoluteName: string, variableValues: string[]): void {
      const params = new HttpParams()
         .set("runtimeId", runtimeId)
         .set("assemblyName", absoluteName);
      const options = {
         windowClass: "condition-dialog",
         objectId: absoluteName,
         limitResize: false
      };
      const eventUri: string = "/events/" + CONDITION_URI + "/" + absoluteName;
      this.showTableDialog(
         VSConditionDialog, "../api/" + CONDITION_URI, eventUri,
         (dialog: VSConditionDialog, model: VSConditionDialogModel) => {
            dialog.model = model;
            dialog.runtimeId = runtimeId;
            dialog.assemblyName = absoluteName;
            dialog.variableValues = variableValues;
         }, params, options);
   }

   showHighlightDialog(runtimeId: string, absoluteName: string, firstSelectedRow: number,
                       firstSelectedColumn: number, variableValues: string[]): void
   {
      if(firstSelectedRow == -1 || firstSelectedColumn == -1) {
         return;
      }

      const params = new HttpParams()
         .set("runtimeId", runtimeId)
         .set("objectId", absoluteName)
         .set("row", firstSelectedRow + "")
         .set("col", firstSelectedColumn + "");

      const eventUri: string = "/events/" + HIGHLIGHT_URI + "/" + absoluteName;
      const options = { windowClass: "property-dialog-window",
                        objectId: absoluteName };

      this.showTableDialog(
         HighlightDialog, "../api/" + HIGHLIGHT_URI, eventUri,
         (dialog: HighlightDialog, model: HighlightDialogModel) => {
            dialog.model = model;
            dialog.runtimeId = runtimeId;
            dialog.assemblyName = absoluteName;
            dialog.variableValues = variableValues;
         }, params, options);
   }

   showTableDialog<D, M>(dialogType: Type<D>, modelUri: string,
                         eventUri: string, bind: (dialog: D, model: M) => any,
                         params: HttpParams = null,
                         options: NgbModalOptions = {windowClass: "property-dialog-window"}): void
   {
      this.modelService.getModel(modelUri, params).toPromise().then(
         (data: any) => {
            const dialog: D = this.showDialog(dialogType, options, (result: M) => {
               this.viewsheetClient.sendEvent(eventUri, result);
            });
            bind(dialog, <M> data);
         },
         (error: any) => {
            //TODO handle error
            console.error("Failed to get plot property model: ", error);
         }
      );
   }
}
