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
export class WSPasteAssembliesEvent {
   private assemblies: string[];
   private columnIndices: number[];
   private sourceRuntimeId: string;
   private top: number = 0;
   private left: number = 0;
   private cut: boolean;
   private dragColumn: boolean;

   public setAssemblies(value: string[]) {
      this.assemblies = value;
   }

   public setSourceRuntimeId(value: string) {
      this.sourceRuntimeId = value;
   }

   public setTop(value: number) {
      this.top = value;
   }

   public setLeft(value: number) {
      this.left = value;
   }

   public setCut(value: boolean) {
      this.cut = value;
   }

   public setDragColumn(value: boolean) {
      this.dragColumn = value;
   }

   public setColumnIndices(value: number[]) {
      this.columnIndices = value;
   }
}