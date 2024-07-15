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
import { Component, Input, NgZone, OnChanges, OnInit } from "@angular/core";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { GetVSObjectModelEvent } from "../../../vsview/event/get-vs-object-model-event";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ModelService } from "../../../widget/services/model.service";
import { ContextProvider } from "../../context-provider.service";
import { VSInputSelectionEvent } from "../../event/vs-input-selection-event";
import { VSRadioButtonModel } from "../../model/vs-radio-button-model";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { FormInputService } from "../../util/form-input.service";
import { VSCompound } from "../check-box/vs-compound";
import { DataTipService } from "../data-tip/data-tip.service";

@Component({
   selector: "vs-radio-button",
   templateUrl: "vs-radio-button.component.html",
   styleUrls: ["vs-radio-button.component.scss", "../check-box/vs-compound.scss"]
})
export class VSRadioButton extends VSCompound<VSRadioButtonModel> implements OnChanges, OnInit {
   selectIndex: number = 0;

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

   ngOnInit() {
      let objIndex: number = this.getIndex(this.model.selectedObject);
      this.selectIndex = objIndex > 0 ? objIndex : 0;
   }

   @Input() set model(m: VSRadioButtonModel) {
      this._model = m;

      if(!!m) {
         let selectedIndex = this.getIndex(m.selectedObject);

         if(selectedIndex >= 0) {
            this.selectIndex = selectedIndex;
         }
      }
   }

   get model(): VSRadioButtonModel {
      return this._model;
   }

   onChange(index: number) {
      this.unappliedSelection = true;
      this.model.selectedLabel = this.model.labels[index];
      this.selectIndex = index;
      this.model.selectedObject = this.model.values[index];

      if(this.model.writeBackDirectly) {
         this.applySelection();
      }
      else if(this.model.refresh) {
         if(this.ctrlDown) {
            this.pendingChange = true;
         }
         else {
            this.applySelection();
         }
      }
      else {
         this.formInputService.addPendingValue(this.model.absoluteName,
                                               this.model.selectedObject);
      }
   }

   protected applySelection(): void {
      this.unappliedSelection = false;
      this.formDataService.checkFormData(
         this.socket.runtimeId, this.model.absoluteName, null,
         () => {
            const event =
               new VSInputSelectionEvent(this.model.absoluteName, this.model.selectedObject);
            this.debounceService.debounce(
               `InputSelectionEvent.${this.model.absoluteName}`,
               (evt, socket) => socket.sendEvent("/events/radioButton/applySelection", evt),
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
    * Perform selection when space is pressed.
    * @param {number} index
    */
   protected onSpace(index: number): void {
      this.onChange(index);
   }
}
