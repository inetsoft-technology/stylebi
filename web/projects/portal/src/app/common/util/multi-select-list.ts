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
import { Range } from "../data/range";

/**
 * Class which manages the index selection state of a list with ctrl- and shift-select capabilities.
 */
export class MultiSelectList {
   private selectedIndices: boolean[];
   private selectCursor: number | null = null;

   constructor(size: number = 0) {
      this.selectedIndices = new Array<boolean>(size);
   }

   private reset(size: number) {
      if(size < 0) {
         throw new Error(`Negative size not allowed: ${size}`);
      }

      this.selectedIndices = new Array<boolean>(size);
      this.selectCursor = null;
   }

   size(): number {
      return this.selectedIndices.length;
   }

   clear(): void {
      this.reset(this.size());
   }

   isSelected(index: number): boolean {
      return this.selectedIndices[index] === true;
   }

   hasSelection(): boolean {
      return this.selectedIndices.indexOf(true) >= 0;
   }

   getSelectedIndices(): number[] {
      const indices = new Array<number>();

      for(let i = 0; i < this.selectedIndices.length; i++) {
         if(this.selectedIndices[i] === true) {
            indices.push(i);
         }
      }

      return indices;
   }

   select(index: number): void {
      this.checkBounds(index);

      this.clear();
      this.selectedIndices[index] = true;
      this.selectCursor = index;
   }

   ctrlSelect(index: number): void {
      this.checkBounds(index);

      if(this.isSelected(index)) {
         this.deselect(index);
      }
      else {
         this.selectedIndices[index] = true;
         this.selectCursor = index;
      }
   }

   shiftSelect(index: number): void {
      this.checkBounds(index);

      if(this.selectCursor == null || this.selectCursor === index) {
         this.select(index);
      }
      else {
         this.selectedIndices = new Array<boolean>(this.size());
         const start = Math.min(index, this.selectCursor);
         const end = Math.max(index, this.selectCursor);

         this.selectedIndices.fill(true, start, end + 1);
      }
   }

   selectWithEvent(index: number, event: MouseEvent): void {
      if(event.ctrlKey) {
         this.ctrlSelect(index);
      }
      else if(event.shiftKey) {
         this.shiftSelect(index);
      }
      else {
         this.select(index);
      }
   }

   selectRange(range: Range) {
      for(let index of Array.from(range)) {
         this.checkBounds(index);
         this.selectedIndices[index] = true;
      }

      this.selectCursor = range.start;
   }

   deselect(index: number): void {
      this.checkBounds(index);

      this.selectedIndices[index] = false;
      this.selectCursor = null;
   }

   setSize(size: number) {
      this.reset(size);
   }

   private checkBounds(index: number): void {
      if(index < 0 || index >= this.size()) {
         throw new Error(`Index out of bounds: ${index} in array of length ${this.size()}`);
      }
   }
}
