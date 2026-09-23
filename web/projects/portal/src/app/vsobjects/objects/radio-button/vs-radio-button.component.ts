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
   // delay before the selection is sent to the server, see applySelection()
   private static readonly APPLY_DELAY = 500;
   // How long a sent selection is protected after the send or after the last stale model,
   // before the latest server model is shown regardless of its value. Stale models keep
   // arriving while the server is still processing the previous apply, so each of them
   // restarts this deadline. It is also how late a server side override (e.g. by script)
   // of the selection is shown.
   private static readonly PENDING_TIMEOUT = 2000;
   // Absolute limit for protecting a sent selection, measured from the send, so a stream of
   // non-matching models (e.g. a server override refreshed again and again) is eventually
   // shown. It also limits waiting for the form data check before the selection is sent.
   private static readonly PENDING_MAX_TIMEOUT = 10000;

   selectIndex: number = 0;

   // The latest selection made by the user that has not been acknowledged by the server
   // yet. While it is pending, a model carrying a different value is stale (e.g. the
   // in-flight refresh of the previous apply) and must not revert the displayed choice.
   private hasPendingValue: boolean = false;
   private pendingValue: any;
   // true once the pending selection has actually been sent to the server
   private pendingSent: boolean = false;
   private pendingTimer: any = null;
   private pendingMaxTimer: any = null;

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
   }

   ngOnInit() {
      let objIndex: number = this.getIndex(this.model.selectedObject);
      this.selectIndex = objIndex > 0 ? objIndex : 0;
   }

   ngOnDestroy() {
      super.ngOnDestroy();
      this.clearPendingValue();
   }

   @Input() set model(m: VSRadioButtonModel) {
      this._model = m;

      if(!!m && this.hasPendingValue) {
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
      this.clearPendingValue();
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
            if(this.viewer) {
               this.startPendingValue(this.model.selectedObject);
            }

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
            const value = this.hasPendingValue ? this.pendingValue : this.model.selectedObject;
            const event = new VSInputSelectionEvent(this.model.absoluteName, value);
            this.pendingValueConfirmed(event.value);
            this.debounceService.debounce(
               `InputSelectionEvent.${this.model.absoluteName}`,
               (evt, socket) => {
                  socket.sendEvent("/events/radioButton/applySelection", evt);
                  this.pendingValueSent(evt.value);
               },
               VSRadioButton.APPLY_DELAY, [event, this.socket]);
         },
         () => {
            // the server model requested below restores the previous value, it must
            // not be ignored as a stale model
            this.clearPendingValue();
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
    * Protect a locally applied selection from being reverted by stale models until the
    * server acknowledges it (Bug #76959).
    */
   private startPendingValue(value: any): void {
      this.hasPendingValue = true;
      this.pendingValue = value;
      this.pendingSent = false;
      // safety net in case the form data check never confirms the selection (e.g. the
      // dialog is dismissed or the check fails), replaced once it is confirmed
      this.pendingTimer = this.schedulePendingTimeout(
         this.pendingTimer, VSRadioButton.PENDING_MAX_TIMEOUT);
   }

   /**
    * Called when the form data check has confirmed the selection and its send is debounced.
    */
   private pendingValueConfirmed(value: any): void {
      if(this.isPendingValue(value) && !this.pendingSent) {
         // safety net in case the selection is never sent
         this.pendingTimer = this.schedulePendingTimeout(
            this.pendingTimer, VSRadioButton.APPLY_DELAY + VSRadioButton.PENDING_TIMEOUT);
      }
   }

   /**
    * Called when the (debounced) selection event has been sent to the server.
    */
   private pendingValueSent(value: any): void {
      if(this.isPendingValue(value)) {
         this.pendingSent = true;
         this.pendingTimer = this.schedulePendingTimeout(
            this.pendingTimer, VSRadioButton.PENDING_TIMEOUT);
         this.pendingMaxTimer = this.schedulePendingTimeout(
            this.pendingMaxTimer, VSRadioButton.PENDING_MAX_TIMEOUT);
      }
   }

   private isPendingValue(value: any): boolean {
      return this.hasPendingValue &&
         this.normalizeValue(value) === this.normalizeValue(this.pendingValue);
   }

   /**
    * Check an incoming model against the pending selection.
    */
   private checkPendingValue(): void {
      const pendingIndex = this.getIndex(this.pendingValue);

      // the pending value is no longer an option, so the server value must be shown
      if(pendingIndex < 0) {
         this.releasePendingValue();
         return;
      }

      if(this.pendingSent) {
         // the server has applied the pending selection
         if(this.isPendingValue(this.model.selectedObject)) {
            this.clearPendingValue();
         }
         // the server is still sending models built before the selection was applied, so
         // keep protecting it (up to PENDING_MAX_TIMEOUT from the send)
         else {
            this.pendingTimer = this.schedulePendingTimeout(
               this.pendingTimer, VSRadioButton.PENDING_TIMEOUT);
         }
      }

      this.selectIndex = pendingIndex;
   }

   /**
    * Stop protecting the pending selection and show the value of the latest model, so a
    * server side change of the value (e.g. by script) is displayed.
    */
   private releasePendingValue(): void {
      this.clearPendingValue();

      if(!!this.model) {
         const selectedIndex = this.getIndex(this.model.selectedObject);

         if(selectedIndex >= 0) {
            this.selectIndex = selectedIndex;
         }
      }
   }

   private clearPendingValue(): void {
      this.hasPendingValue = false;
      this.pendingValue = undefined;
      this.pendingSent = false;

      if(this.pendingTimer != null) {
         clearTimeout(this.pendingTimer);
         this.pendingTimer = null;
      }

      if(this.pendingMaxTimer != null) {
         clearTimeout(this.pendingMaxTimer);
         this.pendingMaxTimer = null;
      }
   }

   /**
    * Replace the given timer with one that releases the pending selection after the delay.
    */
   private schedulePendingTimeout(timer: any, delay: number): any {
      if(timer != null) {
         clearTimeout(timer);
      }

      // the timer should not cause an extra change detection, only the release does
      return this.zone.runOutsideAngular(() => setTimeout(() => {
         this.zone.run(() => this.releasePendingValue());
      }, delay));
   }

   /**
    * Normalize a value the same way as getIndex() does.
    */
   private normalizeValue(value: any): string | null {
      return value == null ? null : value + "";
   }
}
