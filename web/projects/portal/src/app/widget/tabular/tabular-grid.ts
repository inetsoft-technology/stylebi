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
import { TabularView } from "../../common/data/tabular/tabular-view";
import { TabularGridCell } from "./tabular-grid-cell";

export class TabularGrid {
   private readonly _rows: number[] = [];
   private readonly _cols: number[] = [];
   // key = "row,col"
   private readonly cells: Map<string, TabularGridCell> = new Map();

   constructor(rootView: TabularView) {
      for(let i = 0; i < rootView.views.length; i++) {
         const view = rootView.views[i];

         if(!this.isVisible(view)) {
            continue;
         }

         if(view.type === "EDITOR") {
            this.addView(view, view.editor.row, view.editor.col, "EDITOR");
         }
         else if(view.type === "COMPONENT") {
            this.addView(view, view.row, view.col, "COMPONENT");
         }
         else {
            this.addView(view, view.row, view.col, view.type);
         }
      }

      const compareFn = (a: number, b: number) => a - b;
      this._rows.sort(compareFn);
      this._cols.sort(compareFn);
   }

   private isVisible(view: TabularView): boolean {
      return view.visible || (view.editor != null && view.editor.visible);
   }

   // update enabled status of cell editors
   copyValid(grid0: TabularGrid) {
      for(let r = 0; r < this._rows.length; r++) {
         for(let c = 0; c < this._cols.length; c++) {
            const cell = this.getCell(this._rows[r], this._cols[c]);
            const cell0 = grid0.getCell(this._rows[r], this._cols[c]);

            if(cell && cell0) {
               cell.valid = cell0.valid;
            }
         }
      }
   }

   private addView(view: TabularView, row: number, col: number, type: string) {
      if(this._rows.indexOf(row) < 0) {
         this._rows.push(row);
      }

      if(this._cols.indexOf(col) < 0) {
         this._cols.push(col);
      }

      this.cells.set(this.getKey(row, col), new TabularGridCell(view, type));
      const colspan = view.type === "COMPONENT" ? view.colspan + 1 : view.colspan;

      for(let i = col + 1; i < col + colspan; i++) {
         this.cells.set(this.getKey(row, i), new TabularGridCell(null, null, true));
      }
   }

   get rows(): number[] {
      return this._rows;
   }

   get cols(): number[] {
      return this._cols;
   }

   public getCell(row: number, col: number): TabularGridCell {
      return this.cells.get(this.getKey(row, col));
   }

   public isValid(): boolean {
      let valid = true;

      this.cells.forEach((cell) => {
         valid = valid && cell.valid;
      });

      return valid;
   }

   private getKey(row: number, col: number): string {
      return row + "," + col;
   }
}
