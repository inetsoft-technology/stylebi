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
import { Component, Input } from "@angular/core";
import { FacePaneModel } from "../../data/vs/face-pane-model";

@Component({
   selector: "face-pane",
   templateUrl: "face-pane.component.html",
   styleUrls: ["face-pane.component.scss"]
})
export class FacePane {
   @Input() model: FacePaneModel;
   @Input() linkUri: string;

   public getFaceSource(id: string): string {
      return this.linkUri + "getFaceImage/" + this.model.faceType + "/" + encodeURIComponent(id);
   }

   public selectFace(id: string): void {
      this.model.face = Number(id);
   }
}
