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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { VSRecommendType } from "../../model/recommender/vs-recommend-type";
import { VSObjectRecommendation } from "../../model/recommender/vs-object-recommendation";

@Component({
   selector: "object-type-pane",
   templateUrl: "object-type-pane.component.html",
   styleUrls: ["object-type-pane.component.scss"]
})
export class ObjectTypePane implements OnInit {
   @Input() recommendations: VSObjectRecommendation[];
   @Input() originalType: VSRecommendType;
   @Input() set selectedType(type: VSRecommendType) {
      if(type == null && (!this.recommendations || this.recommendations?.length == 0) &&
         this.originalType != null)
      {
         this._selectedType = this.originalType;
         this.onSelect.emit(this.originalType);
      }
      else {
         this._selectedType = type;
      }
   }

   get selectedType() {
      return this._selectedType;
   }

   @Output() onSelect = new EventEmitter<VSRecommendType>();
   _selectedType: VSRecommendType;

   allTypes: VSRecommendType[] = [ VSRecommendType.CHART,
                                   VSRecommendType.CROSSTAB,
                                   VSRecommendType.TABLE,
                                   VSRecommendType.GAUGE,
                                   VSRecommendType.FILTER,
                                   VSRecommendType.TEXT];

   ngOnInit() {
   }

   selectObjectType(type: VSRecommendType) {
      if(this.isRecommended(type) || type == VSRecommendType.ORIGINAL_TYPE) {
         this.selectedType = type;
         this.onSelect.emit(type);
      }
   }

   getObjectTypeIcon(type: VSRecommendType): string {
      let src: string = "";

      switch(type) {
         case VSRecommendType.CHART:
            src = "chart-icon";
            break;
         case VSRecommendType.TABLE:
            src = "table-icon";
            break;
         case VSRecommendType.CROSSTAB:
            src = "crosstab-icon";
            break;
         case VSRecommendType.GAUGE:
            src = "gauge-icon";
            break;
         case VSRecommendType.FILTER:
            src = "condition-icon";
            break;
         case VSRecommendType.TEXT:
            src = "text-box-icon";
            break;
         case VSRecommendType.ORIGINAL_TYPE:
            src = "star-icon";
            break;
         default:
      }

      return src;
   }

   getTooltip(type: VSRecommendType): string {
      switch(type) {
         case VSRecommendType.CHART:
            return "_#(js:Chart)";
         case VSRecommendType.TABLE:
            return "_#(js:Table)";
         case VSRecommendType.CROSSTAB:
            return "_#(js:Crosstab)";
         case VSRecommendType.GAUGE:
            return "_#(js:Gauge)";
         case VSRecommendType.FILTER:
           return "_#(js:Filter)";
         case VSRecommendType.TEXT:
           return "_#(js:Text)";
         default:
            return null;
      }
   }

   hasObjectTypes(): boolean {
      return !!this.recommendations && this.recommendations.length != 0;
   }

   isRecommended(rtype: VSRecommendType): boolean {
      return this.recommendations && !!this.recommendations.find(r => r.type == rtype);
   }
}
