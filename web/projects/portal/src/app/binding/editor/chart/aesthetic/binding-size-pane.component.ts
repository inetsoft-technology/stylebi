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
import { Component, Input, OnChanges, OnInit, Output, EventEmitter } from "@angular/core";
import { SizeFrameModel } from "../../../../common/data/visual-frame-model";
import { RangeSliderOptions } from "../../../widget/range-slider-options";
import { SliderOptions } from "../../../widget/slider-options";

@Component({
   selector: "binding-size-pane",
   templateUrl: "binding-size-pane.component.html",
   styleUrls: ["binding-size-pane.component.scss"]
})
export class BindingSizePane implements OnChanges, OnInit {
   @Input() frameModel: SizeFrameModel;
   @Input() singleValue: boolean;
   @Output() apply = new EventEmitter<boolean>();
   sliderOptions = new RangeSliderOptions();
   slider2Options: SliderOptions = new SliderOptions();

   /**
    * when reload bindingModel should update options value.
    */
   ngOnChanges(changes: any) {
      if(this.frameModel) {
         this.initOptionsValue();
      }
   }

   ngOnInit() {
      this.initOptionsValue();
   }

   private initOptionsValue() {
      this.sliderOptions.selectStart = this.frameModel.smallest;
      this.sliderOptions.selectEnd = this.frameModel.largest;
      this.slider2Options.value = this.frameModel.largest;
   }

   sliderChanged(values: Array<number>) {
      this.frameModel.smallest = values[0];
      this.frameModel.largest = values[1];
      this.frameModel.changed = true;
   }

   slider2Changed(value: number) {
      this.frameModel.smallest = this.frameModel.largest = value;
      this.frameModel.changed = true;
   }

   applyClick() {
      this.apply.emit(false);
   }
}
