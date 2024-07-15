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
import { Component, Input, Output, EventEmitter } from "@angular/core";
import { AestheticInfo } from "../../../data/chart/aesthetic-info";
import { ChartAggregateRef } from "../../../data/chart/chart-aggregate-ref";

@Component({
   selector: "chart-aesthetic-mc",
   templateUrl: "chart-aesthetic-mc.component.html",
   styleUrls: ["../../data-editor.component.scss"]
})
export class ChartAestheticMc {
   @Input() isMixed: boolean;
   @Input() isEnabled: boolean;
   @Input() isEditEnabled: boolean;
   @Input() fieldType: string;
   @Input() field: AestheticInfo;
   @Input() dragComplete: Function;
   @Input() currentAggr: ChartAggregateRef;
   @Input() grayedOutValues: string[] = [];
   @Input() isPrimaryField: boolean = false;
   @Input() hint: string;
   @Input() targetField: string;
   @Output() onChangeAesthetic: EventEmitter<any> = new EventEmitter<any>();
   @Output() onConvert: EventEmitter<any> = new EventEmitter<any>();

   changeAesthetic(): void {
      this.onChangeAesthetic.emit();
   }

   getHint(): String {
      if(this.hint) {
         return this.hint;
      }

      return this.isEnabled ? (this.isMixed ? "_#(js:Multiple Styles)"
         : "_#(js:drag.column.here)") : "";
   }
}
