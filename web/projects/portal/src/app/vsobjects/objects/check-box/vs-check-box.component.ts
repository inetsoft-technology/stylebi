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
import { Component, Input, NgZone, OnChanges, OnDestroy } from "@angular/core";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { GetVSObjectModelEvent } from "../../../vsview/event/get-vs-object-model-event";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ModelService } from "../../../widget/services/model.service";
import { ContextProvider } from "../../context-provider.service";
import { VSInputSelectionEvent } from "../../event/vs-input-selection-event";
import { VSCheckBoxModel } from "../../model/vs-check-box-model";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { FormInputService } from "../../util/form-input.service";
import { PendingInputSelection } from "./pending-input-selection";
import { VSCompound } from "./vs-compound";
import { DataTipService } from "../data-tip/data-tip.service";
import { VSFormatModel } from "../../model/vs-format-model";
import { TooltipIfDirective } from "../../../widget/tooltip/tooltip-if.directive";
import { InteractableDirective } from "../../../widget/interact/interactable.directive";
import { VSTitle } from "../title/vs-title.component";
import { SafeFontDirective } from "../../directives/safe-font.directive";
import { VSPopComponentDirective } from "../data-tip/vs-pop-component.directive";
import { VSDataTipDirective } from "../data-tip/vs-data-tip.directive";


const CHECKBOX_PADDING = 18;

@Component({
    selector: "vs-check-box",
    templateUrl: "vs-check-box.component.html",
    styleUrls: ["vs-check-box.component.scss", "vs-compound.scss"],
    imports: [VSDataTipDirective, VSPopComponentDirective, SafeFontDirective, VSTitle, InteractableDirective, TooltipIfDirective]
})
export class VSCheckBox extends VSCompound<VSCheckBoxModel> implements OnChanges, OnDestroy {
   // The latest selection (selected values) made by the user that has not been acknowledged
   // by the server yet. While it is pending, it is displayed instead of the selection of the
   // current model, so a stale model (e.g. the in-flight refresh of the previous apply) does
   // not revert the checked state (Bug #76959).
   private readonly pendingSelection: PendingInputSelection<any[]>;

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
      this.pendingSelection = new PendingInputSelection<any[]>(
         zone, (values1, values2) => this.sameSelection(values1, values2),
         () => this.pendingSelection.clear());
   }

   ngOnDestroy() {
      super.ngOnDestroy();
      this.pendingSelection.clear();
   }

   @Input() set model(m: VSCheckBoxModel) {
      this._model = m;

      if(!!m && this.pendingSelection.active) {
         this.checkPendingValue();
      }
   }

   get model(): VSCheckBoxModel {
      return this._model;
   }

   isSelected(index: any) {
      index = Number(index);
      let values = this.model?.values;

      if(isNaN(index) || index == -1 || !values || index >= values.length) {
         false;
      }

      let value = values[index];
      const selectedObjects = this.pendingSelection.active ?
         this.pendingSelection.value : this.model.selectedObjects;
      return selectedObjects.some(v => v == value);
   }

   onChange(index: any): void {
      index = Number(index);
      let values = this.model?.values;

      if(isNaN(index) || index == -1 || !values || index >= values.length) {
         return;
      }

      // toggle the displayed selection, the current model may be a stale one that is ignored
      if(this.pendingSelection.active) {
         const selectedObjects = this.pendingSelection.value.slice();
         this.model.selectedObjects = selectedObjects;
         this.model.selectedLabels = selectedObjects.map(v => this.model.labels[this.getIndex(v)]);
      }

      // the last click wins, a previous pending selection is replaced
      this.pendingSelection.clear();
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
            // guarded in the viewer, preview and composer edit mode alike, since the same
            // apply and refresh flow runs in all of them
            this.pendingSelection.start(this.model.selectedObjects.slice());
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
            // a stale model received while the form data check waited for the user must
            // not replace the protected selection that is sent
            const value = this.pendingSelection.active ?
               this.pendingSelection.value : this.model.selectedObjects;
            const event = new VSInputSelectionEvent(this.model.absoluteName, value);
            this.pendingSelection.confirmed(event.value);
            this.debounceService.debounce(
               `InputSelectionEvent.${this.model.absoluteName}`,
               (evt, socket) => {
                  socket.sendEvent("/events/checkBox/applySelection", evt);
                  this.pendingSelection.markSent(evt.value);
               },
               PendingInputSelection.APPLY_DELAY, [event, this.socket]);
         },
         () => {
            // the server model requested below restores the previous selection, it must
            // not be ignored as a stale model
            this.pendingSelection.clear();
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

   /**
    * Check an incoming model against the pending selection.
    */
   private checkPendingValue(): void {
      // a pending value is no longer an option, so the server selection must be shown
      if(this.pendingSelection.value.some(v => this.getIndex(v) < 0)) {
         this.pendingSelection.clear();
         return;
      }

      this.pendingSelection.received(this.model.selectedObjects);
   }

   /**
    * Compare two selections regardless of order and duplicates, with the values normalized
    * the same way as getIndex() does.
    */
   private sameSelection(values1: any[], values2: any[]): boolean {
      const set1 = new Set((values1 || []).map(v => v == null ? null : v + ""));
      const set2 = new Set((values2 || []).map(v => v == null ? null : v + ""));
      return set1.size == set2.size && Array.from(set1).every(v => set2.has(v));
   }
}
