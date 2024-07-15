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
import { Tool } from "../../../../../../../shared/util/tool";
import { EmbeddedTableAssembly } from "../../../data/ws/embedded-table-assembly";

export class EditCellPosition {
   private _row: number;
   private editableColIndex: number;
   private editableColumns: number[];

   constructor(private table: EmbeddedTableAssembly) {
      this.populateEditableColumns();
   }

   private get rowCount(): number {
      return this.table.totalRows;
   }

   private get editableColCount(): number {
      return this.editableColumns.length;
   }

   public get row(): number {
      return this._row;
   }

   public set row(value: number) {
      this._row = value;
   }

   public get col(): number {
      return this.editableColumns[this.editableColIndex];
   }

   setCurrentPosition(row: number, col: number): void {
      let colIndex = this.editableColumns.findIndex((el) => el === col);

      if(colIndex !== -1) {
         this._row = row;
         this.editableColIndex = colIndex;
      }
   }

   offsetRow(offset: 1 | -1, initialCall: boolean = true): void {
      let row = this._row + offset;
      this._row = Tool.mod(row, this.rowCount);

      if(initialCall) {
         if(row < 0) {
            this.offsetCol(-1, false);
         }
         else if(row >= this.rowCount) {
            this.offsetCol(1, false);
         }
      }
   }

   offsetCol(offset: 1 | -1, initialCall: boolean = true): void {
      let newColIndex = this.editableColIndex + offset;
      this.editableColIndex = Tool.mod(newColIndex, this.editableColCount);

      if(initialCall) {
         if(newColIndex < 0) {
            this.offsetRow(-1, false);
         }
         else if(newColIndex >= this.editableColCount) {
            this.offsetRow(1, false);
         }
      }
   }

   private populateEditableColumns(): void {
      this.editableColumns = [];

      for(let i = 0; i < this.table.colCount; i++) {
         if(!this.table.colInfos[i].ref.expression) {
            this.editableColumns.push(i);
         }
      }
   }
}