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

export class CalcTableActionHandler extends AbstractActionHandler {
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

      this.modelService.getModel("../api/" + CONDITION_URI, params).toPromise().then(
         (data: VSConditionDialogModel) => {
            const options = { windowClass: "condition-dialog",
                              objectId: absoluteName, limitResize: false };
            const dialog: VSConditionDialog = this.showDialog(
               VSConditionDialog, options,
               (result: VSConditionDialogModel) => {
                  let eventUri: string = "/events/" + CONDITION_URI + "/" + absoluteName;
                  this.viewsheetClient.sendEvent(eventUri, result);
               });
            dialog.model = data;
            dialog.runtimeId = runtimeId;
            dialog.assemblyName = absoluteName;
            dialog.variableValues = variableValues;
         },
         (error: any) => {
            //TODO handle error
            console.error("Failed to load calc table condition model", error);
         }
      );
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

      this.modelService.getModel("../api/" + HIGHLIGHT_URI, params).toPromise().then(
         (data: HighlightDialogModel) => {
            const options = { windowClass: "property-dialog-window",
                              objectId: absoluteName };
            const dialog: HighlightDialog = this.showDialog(
               HighlightDialog, options, (result: HighlightDialogModel) => {
                  const eventUri: string = "/events/" + HIGHLIGHT_URI + "/" + absoluteName;
                  this.viewsheetClient.sendEvent(eventUri, result);
               });
            dialog.model = data;
            dialog.runtimeId = runtimeId;
            dialog.assemblyName = absoluteName;
            dialog.variableValues = variableValues;
         },
         (error: any) => {
            //TODO handle error
            console.error("Failed to load highlight model", error);
         }
      );
   }
}
