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
import { Component, EventEmitter, Input, NgZone, OnInit, OnChanges, Output } from "@angular/core";
import { GraphTypes } from "../../../common/graph-types";
import { VSFilterType } from "../../model/recommender/vs-filter-type";
import { VSSubType } from "../../model/recommender/vs-sub-type";
import { VSObjectRecommendation } from "../../model/recommender/vs-object-recommendation";
import { ChartSubType } from "../../model/recommender/chart-sub-type";
import { Tool } from "../../../../../../shared/util/tool";
import { GaugeFaceType } from "../../model/recommender/gauge-face-type";

@Component({
   selector: "object-sub-type-pane",
   templateUrl: "object-sub-type-pane.component.html",
   styleUrls: ["object-sub-type-pane.component.scss"]
})
export class ObjectSubTypePane implements OnInit, OnChanges {
   @Input() objectRecommendation: VSObjectRecommendation = null;
   @Output() onSelectSubTypeIdx: EventEmitter<number> = new EventEmitter<number>();
   readonly LIMIT = 12;
   private currentLimit: number = this.LIMIT;

   constructor() {
   }

   ngOnInit() {
   }

   ngOnChanges() {
      this.currentLimit = this.LIMIT;
   }

   selectObjectSubType(index: number) {
      this.objectRecommendation.selectedIndex = index;
      this.onSelectSubTypeIdx.emit(index);
   }

   isSelected(index: number): boolean {
      return Tool.isEquals(this.objectRecommendation.selectedIndex, index);
   }

   hasSubTypes(): boolean {
      return !!this.objectRecommendation && !!this.objectRecommendation.subTypes &&
         this.objectRecommendation.subTypes.length != 0;
   }

   get displayedSubTypes(): VSSubType[] {
      const subTypes = this.objectRecommendation?.subTypes;
      return subTypes ? subTypes.slice(0, this.currentLimit) : [];
   }

   get limited(): boolean {
      const subTypes = this.objectRecommendation?.subTypes;
      return subTypes && subTypes.length > this.currentLimit;
   }

   showAll() {
      this.currentLimit = 1000;
   }
}
