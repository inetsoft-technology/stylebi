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
export class WSConcatAddSubtableEvent {
   private concatTableName: string;
   private newTableName: string;
   private index: number;
   private concatenateWithLeftTable: boolean;
   private operator: number;

   setConcatTableName(value: string) {
      this.concatTableName = value;
   }

   setNewTableName(value: string) {
      this.newTableName = value;
   }

   setIndex(value: number) {
      this.index = value;
   }

   setConcatenateWithLeftTable(value: boolean) {
      this.concatenateWithLeftTable = value;
   }

   setOperator(value: number) {
      this.operator = value;
   }
}