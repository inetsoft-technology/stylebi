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
import { ViewsheetEvent } from "../../../common/viewsheet-client";

export class AddAnnotationEvent implements ViewsheetEvent {
   private content: string;
   private x: number;
   private y: number;
   private parent: string;
   private row: number = -1;
   private col: number = -1;
   private measureName: string;

   public setX(value: number) {
      this.x = value;
   }

   public setY(value: number) {
      this.y = value;
   }

   public setRow(row: number): void {
      this.row = row;
   }

   public setCol(col: number): void {
      this.col = col;
   }

   public setMeasureName(measureName: string): void {
      this.measureName = measureName;
   }

   constructor(content: string, x: number, y: number, parent?: string) {
      this.content = content;
      this.x = x;
      this.y = y;
      this.parent = parent;
   }
}
