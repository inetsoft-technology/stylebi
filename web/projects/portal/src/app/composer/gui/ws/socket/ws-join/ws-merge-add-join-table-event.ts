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
export class WSMergeAddJoinTableEvent {
   private mergeTableName: string;
   private newTableName: string;
   private insertionIndex: number;

   public setMergeTableName(name: string) {
      this.mergeTableName = name;
   }

   public setNewTableName(name: string) {
      this.newTableName = name;
   }

   public setInsertionIndex(index: number) {
      this.insertionIndex = index;
   }
}