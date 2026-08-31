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
import {
   Component,
   ElementRef,
   EventEmitter,
   Input,
   NgZone,
   OnChanges,
   OnDestroy,
   OnInit,
   Output,
   SimpleChanges,
   HostListener,
   ViewChild
} from "@angular/core";
import { Observable, Subscription } from "rxjs";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ContextProvider } from "../../context-provider.service";
import { ChangeVSObjectValueEvent } from "../../event/change-vs-object-value-event";
import { VSSpinnerModel } from "../../model/vs-spinner-model";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { FormInputService } from "../../util/form-input.service";
import { NavigationComponent } from "../abstract-nav-component";
import { NavigationKeys } from "../navigation-keys";
import { DataTipService } from "../data-tip/data-tip.service";
import { DebounceService } from "../../../widget/services/debounce.service";
import { Tool } from "../../../../../../shared/util/tool";
import { SafeFontDirective } from "../../directives/safe-font.directive";
import { FormsModule } from "@angular/forms";
import { VSPopComponentDirective } from "../data-tip/vs-pop-component.directive";
import { VSDataTipDirective } from "../data-tip/vs-data-tip.directive";
import { VSInputLabelWrapper } from "../input-label-wrapper/vs-input-label-wrapper.component";


@Component({
    selector: "vs-spinner",
    templateUrl: "vs-spinner.component.html",
    styleUrls: ["vs-spinner.component.scss"],
    imports: [VSInputLabelWrapper, VSDataTipDirective, VSPopComponentDirective, FormsModule, SafeFontDirective]
})
export class VSSpinner extends NavigationComponent<VSSpinnerModel>
implements OnInit, OnChanges, OnDestroy
{
   private _selected: boolean = false;

   @Input() set selected(selected: boolean) {
      this._selected = selected;

      if(!selected && this.model) {
         this.model.editing = false;
         this.model.labelSelected = false;
      }
   }

   get selected(): boolean {
      return this._selected;
   }
   @Input() submitted: Observable<boolean>;
   @Output() spinnerClicked = new EventEmitter();
   @ViewChild("spinnerInput") spinnerInputRef: ElementRef<HTMLInputElement>;
   private _updateOnChange: boolean = true;
   private pendingChange: boolean = false;
   private ovalue: any = null;
   showLabel: boolean = true;
   private holdInitTimer: any = null;
   private holdRepeatTimer: any = null;
   private holdAccelTimer: any = null;
   private holdRepeatInterval: number = 60;

   @Input()
   set updateOnChange(value: boolean) {
      if(!this._updateOnChange && value && this.pendingChange) {
         this.changeValue();
      }

      this._updateOnChange = value;
      this.pendingChange = false;
   }

   get updateOnChange(): boolean {
      return this._updateOnChange;
   }

   submittedForm: Subscription;
   private unappliedChange = false;

   constructor(protected viewsheetClient: ViewsheetClientService,
               private formDataService: CheckFormDataService,
               private formInputService: FormInputService,
               private debounceService: DebounceService,
               zone: NgZone,
               protected context: ContextProvider,
               protected dataTipService: DataTipService)
   {
      super(viewsheetClient, zone, context, dataTipService);
   }

   ngOnInit() {
      this.validate();
      this.ovalue = this.model.value;
   }

   ngOnChanges(changes: SimpleChanges) {
      if(this.viewer && this.submitted) {
         if(this.submittedForm) {
            this.submittedForm.unsubscribe();
            this.submittedForm = null;
         }

         this.submittedForm = this.submitted.subscribe((isSubmitted) => {
            if(isSubmitted && this.unappliedChange) {
               this.changeValue();
            }
         });
      }
   }

   ngOnDestroy() {
      super.ngOnDestroy();

      if(this.submittedForm) {
         this.submittedForm.unsubscribe();
      }

      this.cancelHold();
   }

   enableEditing(): void {
      if(!this.model.vizModern) {
         // legacy spinners have no select-then-edit gate, so there is nothing to enable
         return;
      }

      this.model.editing = !this.viewer;

      if(this.model.editing) {
         this.spinnerInputRef?.nativeElement?.focus();
      }
   }

   isInputDisabled(): boolean {
      return this.model.vizModern && !this.viewer && (!this.selected || !this.model?.editing);
   }

   get isIncrementDisabled(): boolean {
      return !this.model.enabled || (this.model.value != null && this.model.value >= this.model.max);
   }

   get isDecrementDisabled(): boolean {
      return !this.model.enabled || (this.model.value != null && this.model.value <= this.model.min);
   }

   onIncrementClick(): void {
      if(!this.isIncrementDisabled) {
         this.stepValue(this.model.increment);
         this.focusInputIfEditable();
      }
   }

   onDecrementClick(): void {
      if(!this.isDecrementDisabled) {
         this.stepValue(-this.model.increment);
         this.focusInputIfEditable();
      }
   }

   private focusInputIfEditable(): void {
      // stepping is always allowed, but the input itself stays gated behind select-then-edit;
      // focusing it here would grant real keyboard access around that gate (pointer-events:
      // none only blocks pointer interaction, not a programmatic .focus()).
      if(!this.isInputDisabled()) {
         this.spinnerInputRef?.nativeElement?.focus();
      }
   }

   startHold(direction: 1 | -1): void {
      this.cancelHold();
      this.holdRepeatInterval = 60;

      this.holdInitTimer = setTimeout(() => {
         this.holdInitTimer = null;

         const doRepeat = () => {
            if(direction > 0 && this.isIncrementDisabled || direction < 0 && this.isDecrementDisabled) {
               this.cancelHold();
               return;
            }

            this.stepValue(direction * this.model.increment);
            this.holdRepeatTimer = setTimeout(doRepeat, this.holdRepeatInterval);
         };

         doRepeat();

         this.holdAccelTimer = setTimeout(() => {
            this.holdRepeatInterval = 20;
         }, 1500);
      }, 300);
   }

   cancelHold(): void {
      if(this.holdInitTimer != null) {
         clearTimeout(this.holdInitTimer);
         this.holdInitTimer = null;
      }

      if(this.holdRepeatTimer != null) {
         clearTimeout(this.holdRepeatTimer);
         this.holdRepeatTimer = null;
      }

      if(this.holdAccelTimer != null) {
         clearTimeout(this.holdAccelTimer);
         this.holdAccelTimer = null;
      }
   }

   private stepValue(delta: number): void {
      this.model.value = (isNaN(this.model.value) ? 0 : this.model.value) + delta;
      this.commitChange(this.model.writeBackDirectly);
   }

   onMouseDown(event: MouseEvent): void {
      if(!this.model.vizModern) {
         // legacy spinners have no select-then-edit gate, so the input is always directly
         // interactive; stop the mousedown from also selecting/dragging the composer object.
         event.stopPropagation();
      }
   }

   onClick(event: MouseEvent) {
      if(!this.model.vizModern) {
         // legacy spinners have no select-then-edit gate, so the input is always directly
         // interactive; stop the click from also selecting/dragging the composer object.
         event.stopPropagation();
      }

      this.commitChange(this.model.writeBackDirectly);
   }

   onBlur(event: MouseEvent) {
      // unlike onClick/stepValue, onBlur has no writeBackDirectly bypass
      this.commitChange(false);
   }

   private commitChange(bypassRefreshGate: boolean): void {
      this.validate();
      this.spinnerClicked.emit(this.model.absoluteName);
      this.unappliedChange = true;

      if(this.model.refresh && this.updateOnChange || bypassRefreshGate) {
         this.changeValue();
      }
      else {
         this.pendingChange = true;
         this.formInputService.addPendingValue(this.model.absoluteName, this.model.value);
      }
   }

   private validate(): void {
      this.model.value = isNaN(this.model.value) ? 0 : this.model.value;

      if(this.model.value > this.model.max) {
         this.model.value = this.model.max;
      }

      if(this.model.value < this.model.min) {
         this.model.value = this.model.min;
      }
   }

   private changeValue(): void {
      this.debounceService.debounce("change_value" + this.vsInfo?.runtimeId + this.model?.absoluteName, () => {
         this.changeValue0();
      }, 200);
   }

   private changeValue0(): void {
      if(this.ctrlDown) {
         this.pendingChange2 = true;
         return;
      }

      this.unappliedChange = false;

      if(this.ovalue != this.model.value) {
         this.ovalue = this.model.value;
         let event: ChangeVSObjectValueEvent = new ChangeVSObjectValueEvent(
            this.model.absoluteName, this.model.value);

         this.formDataService.checkFormData(
            this.viewsheetClient.runtimeId, this.model.absoluteName, null,
            () => {
               this.viewsheetClient
                  .sendEvent("/events/composer/viewsheet/vsSpinner/changeValue", event);
            }
         );
      }
   }

   /**
    * Keyboard navigation for this component.
    * @param {NavigationKeys} key
    */
   protected navigate(key: NavigationKeys): void {
      if(key == NavigationKeys.DOWN) {
         this.model.value -= this.model.increment;
      }
      else if(key == NavigationKeys.UP) {
         this.model.value += this.model.increment;
      }
   }

   /**
    * Clear selection made by navigating.
    */
   protected clearNavSelection(): void {
      // Do nothing
   }

   private ctrlDown: boolean = false;
   private pendingChange2: boolean = false;

   @HostListener("document: keyup", ["$event"])
   onKeyUp(event: KeyboardEvent) {
      if(event.keyCode == 17) {
         this.ctrlDown = false;

         if(this.pendingChange2) {
            this.changeValue();
            this.pendingChange2 = false;
         }
      }
   }

   @HostListener("document: keydown", ["$event"])
   onKeyDown(event: KeyboardEvent) {
      if(event.keyCode == 17) {
         this.ctrlDown = true;
      }
   }

   getTextVerticalPosition(): string {
      if(!this.model.objectFormat.font || this.model.objectFormat.vAlign == "middle") {
         return "";
      }

      let fontStr = this.model.objectFormat.font;
      fontStr = fontStr.substring(0, fontStr.indexOf("px"));
      fontStr = fontStr.split(" ").pop();
      let padding = this.model.objectFormat.height - Number.parseInt(fontStr, 10) - 3;
      return padding <= 0 ? "" : this.model.objectFormat.vAlign == "top" ?
         "0px 4px " + padding + "px 4px" : padding + "px 4px 0px 4px";
   }

   selectLabel(event: MouseEvent): void {
      if(this.context.preview) {
         return;
      }

      this.model.labelSelected = true;
   }

   clearLabelSelection(): void {
      if(this.model) {
         this.model.labelSelected = false;
      }
   }
}
