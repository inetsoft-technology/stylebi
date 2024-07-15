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
import { AlignmentInfo } from "../../common/data/format-info-model";

@Component({
   selector: "binding-alignment-pane",
   styleUrls: ["binding-alignment-pane.component.scss"],
   templateUrl: "binding-alignment-pane.component.html",
})
export class BindingAlignmentPane {
   @Input() enableHAlign: boolean;
   @Input() enableVAlign: boolean;
   @Input() alignmentInfo: AlignmentInfo;
   @Output() onAlignmentChange: EventEmitter<boolean> = new EventEmitter<boolean>();
   @Output() onApply: EventEmitter<boolean> = new EventEmitter<boolean>();

   constructor() {
   }

   changeHalign(align: string) {
      this.alignmentInfo.halign = align;
      this.onAlignmentChange.emit(true);
   }

   changeValign(align: string) {
      this.alignmentInfo.valign = align;
      this.onAlignmentChange.emit(true);
   }

   setAuto() {
      if(this.enableHAlign) {
         this.alignmentInfo.halign = null;
      }

      if(this.enableVAlign) {
         this.alignmentInfo.valign = null;
      }

      this.onAlignmentChange.emit(true);
   }
}
