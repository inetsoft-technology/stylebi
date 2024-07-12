/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component, NgZone, OnChanges, OnDestroy } from "@angular/core";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { GetVSObjectModelEvent } from "../../../vsview/event/get-vs-object-model-event";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ModelService } from "../../../widget/services/model.service";
import { ContextProvider } from "../../context-provider.service";
import { VSInputSelectionEvent } from "../../event/vs-input-selection-event";
import { VSCheckBoxModel } from "../../model/vs-check-box-model";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { FormInputService } from "../../util/form-input.service";
import { VSCompound } from "./vs-compound";
import { DataTipService } from "../data-tip/data-tip.service";
import { VSFormatModel } from "../../model/vs-format-model";

const CHECKBOX_PADDING = 18;

@Component({
   selector: "vs-check-box",
   templateUrl: "vs-check-box.component.html",
   styleUrls: ["vs-check-box.component.scss", "vs-compound.scss"]
})
export class VSCheckBox extends VSCompound<VSCheckBoxModel> implements OnChanges, OnDestroy {
   constructor(socket: ViewsheetClientService,
               formDataService: CheckFormDataService,
               private formInputService: FormInputService,
               debounceService: DebounceService,
               zone: NgZone,
               protected context: ContextProvider,
               protected dataTipService: DataTipService,
               protected modelService: ModelService)
   {
      super(socket, formDataService, debounceService, context, modelService, dataTipService, zone);
   }

   isSelected(index: any) {
      index = Number(index);
      let values = this.model?.values;

      if(isNaN(index) || index == -1 || !values || index >= values.length) {
         false;
      }

      let value = values[index];
      return this.model.selectedObjects.some(v => v == value);
   }

   onChange(index: any): void {
      index = Number(index);
      let values = this.model?.values;

      if(isNaN(index) || index == -1 || !values || index >= values.length) {
         return;
      }

      this.unappliedSelection = true;
      let option = values[index];

      if(this.isSelected(index)) {
         while((index = this.model.selectedObjects.indexOf(option)) >= 0) {
            this.model.selectedLabels.splice(index, 1);
            this.model.selectedObjects.splice(index, 1);
         }
      }
      else {
         for(let i = 0; i < this.model.values.length; i++) {
            if(this.model.values[i] == option) {
               this.model.selectedLabels.push(this.model.labels[i]);
               this.model.selectedObjects.push(this.model.values[i]);
            }
         }
      }

      if(this.model.refresh) {
         if(this.ctrlDown) {
            this.pendingChange = true;
         }
         else {
            this.applySelection();
         }
      }
      else {
         this.formInputService.addPendingValue(this.model.absoluteName,
                                               this.model.selectedObjects);
      }
   }

   protected applySelection(): void {
      this.unappliedSelection = false;
      this.formDataService.checkFormData(
         this.socket.runtimeId, this.model.absoluteName, null,
         () => {
            const event = new VSInputSelectionEvent(
               this.model.absoluteName, this.model.selectedObjects);
            this.debounceService.debounce(
               `InputSelectionEvent.${this.model.absoluteName}`,
               (evt, socket) => socket.sendEvent("/events/checkBox/applySelection", evt),
               500, [event, this.socket]);
         },
         () => {
            let event: GetVSObjectModelEvent =
               new GetVSObjectModelEvent(this.model.absoluteName);
            this.socket.sendEvent("/events/vsview/object/model", event);
         }
      );
   }

   /**
    * Select when space is pressed.
    * @param {number} index
    */
   protected onSpace(index: number): void {
      this.onChange(this.model.values[index]);
   }
}
