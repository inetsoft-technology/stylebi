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
import { Component, ElementRef, EventEmitter, Input, Output } from "@angular/core";
import { VSObjectFormatInfoModel } from "../../common/data/vs-object-format-info-model";
import { VSObjectModel } from "../model/vs-object-model";

@Component({
   selector: "viewer-format-pane",
   templateUrl: "viewer-format-pane.component.html",
   styleUrls: ["viewer-format-pane.component.scss"]
})
export class ViewerFormatPane {
   @Input() vsId: string;
   @Input() currentFormat: VSObjectFormatInfoModel;
   @Input() focusedAssemblies: VSObjectModel[] = [];
   @Input() variableValues: string[] = [];
   @Output() onFormatPaneClosed = new EventEmitter();
   @Output() onUpdateData = new EventEmitter<string>();

   closeFormatPane() {
      this.onFormatPaneClosed.emit();
   }

   updateFormat(model: any) {
      if(model) {
         this.onUpdateData.emit("updateFormat");
      }
      else {
         this.onUpdateData.emit("reset");
      }
   }
}
