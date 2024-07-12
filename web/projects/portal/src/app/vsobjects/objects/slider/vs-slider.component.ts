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
import {
   ChangeDetectorRef,
   Component, ElementRef,
   EventEmitter,
   HostListener,
   Input,
   NgZone,
   OnChanges,
   OnDestroy,
   Output,
   SimpleChanges, ViewChild
} from "@angular/core";
import { Observable ,  Subscription } from "rxjs";
import { GuiTool } from "../../../common/util/gui-tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { GetVSObjectModelEvent } from "../../../vsview/event/get-vs-object-model-event";
import { ContextProvider } from "../../context-provider.service";
import { ChangeVSObjectValueEvent } from "../../event/change-vs-object-value-event";
import { VSSliderModel } from "../../model/vs-slider-model";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { FormInputService } from "../../util/form-input.service";
import { NavigationComponent } from "../abstract-nav-component";
import { NavigationKeys } from "../navigation-keys";
import { DebounceService } from "../../../widget/services/debounce.service";
import { DataTipService } from "../data-tip/data-tip.service";

interface SliderTick {
   left: number;
   label: String;
   labelLeft: number;
}

const VALUE_GAP = 5;
const MAX_MIN_LABELS = 2;
const CHANGE_VALUE_URL: string = "/events/composer/viewsheet/vsSlider/changeValue";
const GET_OBJECT_MODEL_URL: string = "/events/vsview/object/model";

@Component({
   selector: "vs-slider",
   templateUrl: "vs-slider.component.html",
   styleUrls: ["vs-slider.component.scss"]
})
export class VSSlider extends NavigationComponent<VSSliderModel> implements OnChanges, OnDestroy {
   @Input() selected: boolean = false;
   @Input() submitted: Observable<boolean>;
   @Input() viewsheetScale: number = 1;
   @Output() sliderChanged = new EventEmitter();
   @ViewChild("sliderHandle") sliderHandle: ElementRef;
   @ViewChild("sliderContainer") sliderContainer: ElementRef;
   ticks: SliderTick[] = [];
   private mouseDownX: number = NaN;
   isMouseDown: boolean = false;
   private previousLabel: string = "";
   handlePosition: number;
   submittedForm: Subscription;
   private unappliedSelection = false;
   private tickSize: number;
   verticalCenter: number;
   _model: VSSliderModel;
   handleSelected: boolean = false;

   constructor(protected viewsheetClient: ViewsheetClientService,
               private formDataService: CheckFormDataService,
               private formInputService: FormInputService,
               private changeRef: ChangeDetectorRef,
               zone: NgZone,
               protected context: ContextProvider,
               protected dataTipService: DataTipService,
               private debounceService: DebounceService)
   {
      super(viewsheetClient, zone, context, dataTipService);
   }

   @Input()
   set model(model: VSSliderModel) {
      this._model = model;
      this.verticalCenter = Math.ceil(this.model.objectFormat.height / 2);

      // calculate the tick size
      this.tickSize = GuiTool.measureText("|", this.getFont());
   }

   get model(): VSSliderModel {
      return this._model;
   }

   ngOnChanges(changes: SimpleChanges) {
      this.handlePosition = this.getValueX();

      if(this.viewer && changes.submitted && this.submitted) {
         if(this.submittedForm) {
            this.submittedForm.unsubscribe();
            this.submittedForm = null;
         }

         this.submittedForm = this.submitted.subscribe((isSubmitted) => {
            if(isSubmitted && this.unappliedSelection) {
               this.applySelection(true);
            }
         });
      }
   }

   ngOnDestroy() {
      super.ngOnDestroy();

      if(this.submittedForm) {
         this.submittedForm.unsubscribe();
      }
   }

   // get the width of the slider line, used to calculate the various positions.
   getLineWidth(): number {
      return this.model.objectFormat.width;
   }

   // get the current (handle) position
   getValueX(): number {
      let curr = (this.model.value - this.model.min) /
         (this.model.max - this.model.min) * this.getLineWidth();

      return Math.max(0, Math.min(curr, this.getLineWidth()));
   }

   // get the current value label
   getLabel(): string {
      if(this.model.currentLabel != this.previousLabel) {
         return this.model.currentLabel;
      }
      else {
         return this.toLabel(this.model.min + (this.handlePosition / this.getLineWidth()) *
            (this.model.max - this.model.min));
      }
   }

   // css left for value label
   getLabelLeft(): number {
      // label width is set to 80 in css
      return (this.handlePosition - 40);
   }

   getTicks(): SliderTick[] {
      let startX: number = 0;
      let labelX: number;
      const values: Array<string> = this.getValueLabels();
      const numLong: number = this.model.max - this.model.min;
      const incCount: number = Math.ceil(numLong / this.model.increment);
      const incWidth = (this.model.increment / numLong) * this.getLineWidth();
      const jump = this.getJump(values);
      const tickjump = (incCount / jump < 4) ? Math.floor(jump / 2) : jump;
      const ticks: SliderTick[] = [];

      for(let i = 0; i < incCount + 1; i++, startX += incWidth) {
         startX = Math.min(startX, this.getLineWidth() - this.tickSize);

         if((this.model.labelVisible || this.model.minVisible) && i === 0 ||
            (this.model.labelVisible || this.model.maxVisible) && i === incCount ||
            tickjump === 0 || tickjump !== 0 && i % tickjump === 0 && i !== incCount && i != 0)
         {
            //Do not display last tick if it overlaps with the maximum value
            const paintCount = incCount % jump == 0 ?
               Math.floor(incCount / jump) - 1 : Math.floor(incCount / jump);

            if(i != 0 && i != incCount && paintCount == i / jump) {
               const finalPos = this.getLineWidth() + VALUE_GAP - this.tickSize;

               if(startX + this.getDefaultLabelWidth(values[i]) > finalPos) {
                  continue;
               }
            }

            labelX = startX - (GuiTool.measureText(values[i], this.getFont()) * 0.5);

            ticks.push({label: values[i], left: startX, labelLeft: labelX});
         }
      }

      return ticks;
   }

   // get the tick labels
   private getValueLabels(): Array<string> {
      const calculatedNTicks = Math.floor((this.model.max - this.model.min) / this.model.increment);
      const nticks = calculatedNTicks == 0
         ? (!this.model.labels ? 0 : this.model.labels.length) : calculatedNTicks + MAX_MIN_LABELS;
      const values: Array<string> = [];
      const labels = this.model.labels;

      for(let i = 0; i < nticks; i++) {
         values.push((labels && labels[i]) ? labels[i]
                     : this.toLabel(this.model.min + i * this.model.increment));
      }

      return values;
   }

   // get the number of tick labels to skip
   private getJump(values: Array<string>): number {
      for(let i = 1; i < values.length; i ++) {
         if(!this.isValuesOverlapped(values, i)) {
            return i;
         }
      }

      return values.length;
   }

   // check if the labels will overlap if we skip number (jump - 1) of items in between
   private isValuesOverlapped(values: Array<string>, jump: number) {
      if(jump >= values.length) {
         return true;
      }

      const incWidthDelta = this.getLineWidth() / values.length;

      for(let i = 0; i + jump < values.length; i += jump) {
         const length1 = this.getDefaultLabelWidth(values[i]);
         const length2 = this.getDefaultLabelWidth(values[i + jump]);

         if((length1 + length2) / 2 + VALUE_GAP > incWidthDelta * jump) {
            return true;
         }
      }

      return false;
   }

   // get the width for the label
   private getDefaultLabelWidth(value: string) {
      return GuiTool.measureText(value, "10px arial") + 4;
   }

   // nice number string
   private toLabel(v: number): string {
      return isNaN(v) ? "" : this.fixNum(v);
   }

   // round number to the number of significant digits
   private fixNum(num: number): string {
      return num.toFixed(this.getFractionDigits());
   }

   // find the number of digits after decimal point
   private getFractionDigits(): number {
      let incstr = this.model.increment + "";
      let incidx = incstr.indexOf(".") < 0 ? 0 :
         incstr.length - incstr.indexOf(".") - 1;

      if(incidx == 1 && incstr.charAt(incstr.indexOf(".") + 1) == "0") {
         return 0;
      }

      return incidx;
   }

   onClick(event: MouseEvent) {
      this.sliderChanged.emit(this.model.absoluteName);
   }

   mouseDown(event: MouseEvent|TouchEvent) {
      if(!GuiTool.isButton1(event)) {
         return;
      }

      this.isMouseDown = true;
      this.mouseDownX = GuiTool.pageX(event) * (1 / this.viewsheetScale) - this.handlePosition;

      event.stopPropagation();
   }

   mouseMove(event: MouseEvent|TouchEvent) {
      if(this.isMouseDown) {
         event.preventDefault();
         this.moveHandlePosition(GuiTool.pageX(event) * (1 / this.viewsheetScale) - this.mouseDownX);
      }
   }

   private moveHandlePosition(pos: number): void {
      this.previousLabel = this.model.currentLabel;
      this.handlePosition = this.snap(pos);

      if(this.handlePosition < 0) {
         this.handlePosition = 0;
      }
      else if(this.handlePosition > this.getLineWidth()) {
         this.handlePosition = this.getLineWidth();
      }

      this.changeRef.detectChanges();
   }

   @HostListener("document: mouseup", ["$event"])
   @HostListener("document: touchend", ["$event"])
   mouseUp(event: any): void {
      if(this.isMouseDown) {
         this.unappliedSelection = true;
         this.isMouseDown = false;
         this.applySelection();
      }
   }

   moveHandleHere(event: MouseEvent) {
      this.handlePosition = this.snap(event.offsetX);
      this.model.value = this.getModelValueFromXPosition(this.handlePosition);
      this.previousLabel = this.model.currentLabel;
      this.isMouseDown = true;
      this.mouseUp(event);
   }

   private snap(pos: number): number {
      if(this.model.snap && this.model.increment) {
         const val = this.getModelValueFromXPosition(pos);
         const val2 = Math.round((val - this.model.min) / this.model.increment)
            * this.model.increment + this.model.min;
         return (val2 - this.model.min) * this.getLineWidth() / (this.model.max - this.model.min);
      }

      return pos;
   }

   private getModelValueFromXPosition(xPosition: number) {
      return this.model.min + (xPosition / this.getLineWidth()) *
         (this.model.max - this.model.min);
   }

   private applySelection(force: boolean = false): void {
      this.unappliedSelection = false;
      this.model.value = +this.getLabel();

      let changeValueEvent: ChangeVSObjectValueEvent = new ChangeVSObjectValueEvent(
         this.model.absoluteName, this.model.value);

      this.formDataService.checkFormData(
         this.viewsheetClient.runtimeId, this.model.absoluteName, null,
         () => {
            if(force || this.model.refresh || this.model.writeBackDirectly) {
               this.viewsheetClient.sendEvent(CHANGE_VALUE_URL, changeValueEvent);
            }
            else {
               this.formInputService.addPendingValue(this.model.absoluteName, this.model.value);
            }
         },
         () => {
            let event: GetVSObjectModelEvent =
               new GetVSObjectModelEvent(this.model.absoluteName);
            this.viewsheetClient.sendEvent(GET_OBJECT_MODEL_URL, event);
         }
      );
   }

   private getFont(): string {
      const fontFormat = /\d+px\s\w+/.exec(this.model.objectFormat.font);
      return fontFormat != null ? fontFormat[0] : "10px normal";
   }

   /**
    * Keyboard navigation for this component.
    * @param {NavigationKeys} key
    */
   protected navigate(key: NavigationKeys): void {
      this.handleSelected = true;

      if(key != NavigationKeys.LEFT && key != NavigationKeys.RIGHT) {
         this.sliderContainer.nativeElement.focus();
         return;
      }

      let incr = 5;

      if(this.model.snap && this.model.increment) {
         incr = this.model.increment * this.getLineWidth() / (this.model.max - this.model.min);
      }

      if(key == NavigationKeys.LEFT) {
         incr = -incr;
      }

      this.moveHandlePosition(this.handlePosition + incr);
      this.unappliedSelection = true;

      if(this.model.refresh) {
         const debounceKey: string = `VSSlider.ApplyEvent.${this.model.absoluteName}`;
         const callback: () => void = () => {
            this.applySelection();
            this.sliderHandle.nativeElement.blur();
            this.sliderHandle.nativeElement.focus();
         };
         this.debounceService.debounce(debounceKey, callback, 300, null);
      }
   }

   /**
    * Clear selection made by navigating.
    */
   protected clearNavSelection(): void {
      this.handleSelected = false;
   }
}
