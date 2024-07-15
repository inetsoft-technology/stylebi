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
export interface Rectangular {
   x: number;
   y: number;
   width: number;
   height: number;
}

export class Rectangle implements Rectangular {
   constructor(public x: number, public y: number, public width: number, public height: number) {
   }

   /**
    * Check if point contains to this rect.
    */
   public contains(x: number, y: number): boolean {
      return x >= this.x && x <= this.x + this.width &&
         y >= this.y && y <= this.y + this.height;
   }

   /**
    * Create a Rectangle from a client bounding rectangle
    */
   public static fromClientRect(clientRect: ClientRect): Rectangle {
      return new Rectangle(clientRect.left, clientRect.top, clientRect.width, clientRect.height);
   }

   public intersects(rect: Rectangle | ClientRect): boolean {
      if(!rect) {
         return false;
      }

      if(!(rect instanceof Rectangle)) {
         rect = Rectangle.fromClientRect(rect);
      }

      const rLeft = rect.x;
      const rRight = rect.x + rect.width;
      const rTop = rect.y;
      const rBot = rect.y + rect.height;

      const left = this.x;
      const right = this.x + this.width;
      const top = this.y;
      const bot = this.y + this.height;

      return left < rRight && right > rLeft && top < rBot && bot > rTop;
   }

   /**
    * True if this rectangle encloses an area
    */
   public isEmpty(): boolean {
      return this.width <= 0 || this.height <= 0;
   }
}
