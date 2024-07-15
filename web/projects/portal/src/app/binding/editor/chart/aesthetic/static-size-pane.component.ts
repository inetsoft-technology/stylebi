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
import { Component, Input, Output, OnChanges, OnInit, EventEmitter } from "@angular/core";
import { StaticSizeModel } from "../../../../common/data/visual-frame-model";
import { ChartAggregateRef } from "../../../data/chart/chart-aggregate-ref";
import { GraphUtil } from "../../../util/graph-util";
import { SliderOptions } from "../../../widget/slider-options";
import { GraphTypes } from "../../../../common/graph-types";

@Component({
   selector: "static-size-pane",
   templateUrl: "static-size-pane.component.html",
   styleUrls: ["static-size-pane.component.scss"]
})
export class StaticSizePane implements OnChanges, OnInit {
   @Input() aggr: ChartAggregateRef;
   @Input() frameModel: StaticSizeModel;
   @Input() autoOnly: boolean = false;
   @Output() sizeChanged: EventEmitter<any> = new EventEmitter<any>();
   @Output() apply: EventEmitter<any> = new EventEmitter<any>();
   sliderOptions: SliderOptions = new SliderOptions();

   ngOnChanges(changes: any) {
      this.sliderOptions.value = this.getFrameSize();
   }

   ngOnInit() {
      this.sliderOptions.value = this.getFrameSize();
      this.sliderOptions.min = 1;
   }

   setFrameSize(val: number) {
      let frame = this.getFrame();

      // for multistyle allchartaggregate.
      if(this.aggr && !frame && GraphUtil.isAllChartAggregate(this.aggr)) {
         let sizeFrame = new StaticSizeModel();
         sizeFrame.size = val;
         sizeFrame.changed = true;
         this.aggr.sizeFrame = sizeFrame;
      }
      else if(frame) {
         frame.size = val;
      }
   }

   getFrameSize(): number {
      let frame = this.getFrame();

      if(frame && frame.clazz.indexOf("StaticSizeModel") != -1) {
         return frame.size;
      }
      else {
         return 1;
      }
   }

   set autoChanged(val: boolean) {
      this.setFrameChanged(!val);
   }

   get autoChanged(): boolean {
      let frame = this.getFrame();
      let changed = frame ? frame.changed : false;

      return !changed || this.autoOnly;
   }

   setFrameChanged(val: boolean) {
      let frame = this.getFrame();

      // for multistyle allchartaggregate.
      if(this.aggr && !frame && GraphUtil.isAllChartAggregate(this.aggr))
      {
         let sizeFrame = new StaticSizeModel();
         sizeFrame.size = 1;
         sizeFrame.changed = val;
         this.aggr.sizeFrame = sizeFrame;
      }
      else if(frame) {
         frame.changed = val;
      }
   }

   getFrame(): StaticSizeModel {
      let frame: StaticSizeModel = null;

      if(this.frameModel) {
         frame = this.frameModel;
      }
      else if(this.aggr) {
         frame = <StaticSizeModel> this.aggr.sizeFrame;
      }

      return frame;
   }

   sliderChanged(val: any) {
      this.setFrameSize(val);
      this.setFrameChanged(true);
   }
}
