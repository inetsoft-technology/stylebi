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
export class WSInsertDataEvent {
   private tableName: string;
   private type: "column" | "row";
   private index: number;
   private insert: boolean;

   public setTableName(tableName: string) {
      this.tableName = tableName;
   }

   public setType(type: "column" | "row") {
      this.type = type;
   }

   public setIndex(index: number) {
      this.index = index;
   }

   public setInsert(insert: boolean) {
      this.insert = insert;
   }
}