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
import { VisualFrameModel } from "../../../../common/data/visual-frame-model";

export abstract class CategoricalFramePane {
   NUM_EDITORS: number = 8;
   currIndex: number = 0;

   get currentViewIndices(): number[] {
      return Array.from(new Array(this.NUM_EDITORS), (x, i) => i + this.currIndex);
   }

   abstract getNumItems(): number;

   showPrevious() {
      this.currIndex = this.currIndex == 0 ? 0 : this.currIndex - 1;
   }

   showNext() {
      this.currIndex = this.currIndex < this.getNumItems() - this.NUM_EDITORS ?
         this.currIndex + 1 : this.getNumItems() - this.NUM_EDITORS;
   }

   isResetted() {
      return false;
   }

   getResetButtonSrc(): string {
      return this.isResetted() ? "reset-icon icon-disabled" : "reset-icon";
   }

   isPaneVisible(): boolean {
      return this.getFrame() && this.getFrame().clazz.indexOf("Categorical") != -1;
   }

   abstract getFrame(): VisualFrameModel;
}
