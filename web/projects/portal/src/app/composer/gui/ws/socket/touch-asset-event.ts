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
export class TouchAssetEvent {
   private design: boolean;
   private changed: boolean;
   private update: boolean;
   private wallboard = false;
   private width: number = 0;
   private height: number = 0;

   public setDesign(value: boolean) {
      this.design = value;
   }

   public setChanged(value: boolean) {
      this.changed = value;
   }

   public setUpdate(value: boolean) {
      this.update = value;
   }

   public setWallboard(value: boolean) {
      this.wallboard = value;
   }

   public setWidth(width: number) {
      this.width = width;
   }

   public setHeight(height: number) {
      this.height = height;
   }
}