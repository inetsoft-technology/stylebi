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
import { Component, Input, NgZone, OnChanges, OnDestroy, OnInit } from "@angular/core";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { GetVSObjectModelEvent } from "../../../vsview/event/get-vs-object-model-event";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ModelService } from "../../../widget/services/model.service";
import { ContextProvider } from "../../context-provider.service";
import { VSInputSelectionEvent } from "../../event/vs-input-selection-event";
import { VSRadioButtonModel } from "../../model/vs-radio-button-model";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { FormInputService } from "../../util/form-input.service";
import { PendingInputSelection } from "../check-box/pending-input-selection";
import { VSCompound } from "../check-box/vs-compound";
import { DataTipService } from "../data-tip/data-tip.service";
import { TooltipIfDirective } from "../../../widget/tooltip/tooltip-if.directive";
import { InteractableDirective } from "../../../widget/interact/interactable.directive";
import { FormsModule } from "@angular/forms";
import { VSTitle } from "../title/vs-title.component";
import { SafeFontDirective } from "../../directives/safe-font.directive";
import { VSPopComponentDirective } from "../data-tip/vs-pop-component.directive";
import { VSDataTipDirective } from "../data-tip/vs-data-tip.directive";


@Component({
    selector: "vs-radio-button",
    templateUrl: "vs-radio-button.component.html",
    styleUrls: ["vs-radio-button.component.scss", "../check-box/vs-compound.scss"],
    imports: [VSDataTipDirective, VSPopComponentDirective, SafeFontDirective, VSTitle, FormsModule, InteractableDirective, TooltipIfDirective]
})
export class VSRadioButton extends VSCompound<VSRadioButtonModel>
   implements OnChanges, OnInit, OnDestroy
{
   selectIndex: number = 0;

   // The latest selection made by the user that has not been acknowledged by the server
   // yet. While it is pending, a model carrying a different value is stale (e.g. the
   // in-flight refresh of the previous apply) and must not revert the displayed choice.
   private readonly pendingSelection: PendingInputSelection<any>;

   constructor(socket: ViewsheetClientService,
               formDataService: CheckFormDataService,
               private formInputService: FormInputService,
               debounceService: DebounceService,
               private zone: NgZone,
               protected context: ContextProvider,
               protected dataTipService: DataTipService,
               protected modelService: ModelService)
   {
      super(socket, formDataService, debounceService, context, modelService, dataTipService, zone);
      this.pendingSelection = new PendingInputSelection<any>(
         zone, (value1, value2) => this.normalizeValue(value1) === this.normalizeValue(value2),
         () => this.releasePendingValue());
   }

   ngOnInit() {
      let objIndex: number = this.getIndex(this.model.selectedObject);
      this.selectIndex = objIndex > 0 ? objIndex : 0;
   }

   ngOnDestroy() {
      super.ngOnDestroy();
      this.pendingSelection.clear();
   }

   @Input() set model(m: VSRadioButtonModel) {
      this._model = m;

      if(!!m && this.pendingSelection.active) {
         this.checkPendingValue();
      }
      else if(!!m) {
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
      // the last click wins, a previous pending selection is replaced
      this.pendingSelection.clear();
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
            // guarded in the viewer, preview and composer edit mode alike, since the same
            // apply and refresh flow runs in all of them
            this.pendingSelection.start(this.model.selectedObject);
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
            // a stale model received while the form data check waited for the user must
            // not replace the protected selection that is sent
            const value = this.pendingSelection.active ?
               this.pendingSelection.value : this.model.selectedObject;
            const event = new VSInputSelectionEvent(this.model.absoluteName, value);
            this.pendingSelection.confirmed(event.value);
            this.debounceService.debounce(
               `InputSelectionEvent.${this.model.absoluteName}`,
               (evt, socket) => {
                  socket.sendEvent("/events/radioButton/applySelection", evt);
                  this.pendingSelection.markSent(evt.value);
               },
               PendingInputSelection.APPLY_DELAY, [event, this.socket]);
         },
         () => {
            // the server model requested below restores the previous value, it must
            // not be ignored as a stale model
            this.pendingSelection.clear();
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

   /**
    * Check an incoming model against the pending selection (Bug #76959).
    */
   private checkPendingValue(): void {
      const pendingIndex = this.getIndex(this.pendingSelection.value);

      // the pending value is no longer an option, so the server value must be shown
      if(pendingIndex < 0) {
         this.releasePendingValue();
         return;
      }

      this.pendingSelection.received(this.model.selectedObject);
      this.selectIndex = pendingIndex;
   }

   /**
    * Stop protecting the pending selection and show the value of the latest model, so a
    * server side change of the value (e.g. by script) is displayed.
    */
   private releasePendingValue(): void {
      this.pendingSelection.clear();

      if(!!this.model) {
         const selectedIndex = this.getIndex(this.model.selectedObject);

         if(selectedIndex >= 0) {
            this.selectIndex = selectedIndex;
         }
      }
   }

   /**
    * Normalize a value the same way as getIndex() does.
    */
   private normalizeValue(value: any): string | null {
      return value == null ? null : value + "";
   }
}
