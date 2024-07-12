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
import { Point } from "./point";
import { Rectangle } from "./rectangle";

/**
 * A class representing a line.
 */
export class Line {
   constructor(public start: Point, public end: Point) {
   }

   /**
    * Check if line intersects rectangle.
    */
   intersectsRect(rect: Rectangle): boolean {
      const line1: Line = new Line(new Point(rect.x, rect.y),
         new Point(rect.x + rect.width, rect.y));
      const line2: Line = new Line(new Point(rect.x + rect.width, rect.y),
         new Point(rect.x + rect.width, rect.y + rect.height));
      const line3: Line = new Line(new Point(rect.x + rect.width, rect.y + rect.height),
         new Point(rect.x, rect.y + rect.height));
      const line4: Line = new Line(new Point(rect.x, rect.y + rect.height),
         new Point(rect.x, rect.y));

      return this.intersects(line1) || this.intersects(line2) || this.intersects(line3)
         || this.intersects(line4) || (rect.contains(this.start.x, this.start.y)
         && rect.contains(this.end.x, this.end.y));
   }

   /**
    * Check if line (segment) intersects line (segment).
    */
   intersects(line: Line): boolean {
      const x1: number = this.start.x;
      const x2: number = this.end.x;
      const y1: number = this.start.y;
      const y2: number = this.end.y;

      const m1: number = (y2 - y1) / (x2 - x1);
      const b1: number = y1 - m1 * x1;

      const x3: number = line.start.x;
      const x4: number = line.end.x;
      const y3: number = line.start.y;
      const y4: number = line.end.y;

      const m2: number = (y4 - y3) / (x4 - x3);
      const b2: number = y3 - m2 * x3;

      let x: number;
      let y: number;

      // Both are vertical lines
      if(x2 == x1 && x3 == x4) {
         return x1 == x3 && (this.isBetween(y1, y2, y3) || this.isBetween(y1, y2, y4));
      }
      else if(x1 == x2) {
         x = x1;
         y = m2 * x + b2;
      }
      else if(x3 == x4) {
         x = x3;
         y = m1 * x + b1;
      }
      // Parallel lines
      else if(m1 === m2) {
         return b1 == b2 && (this.isBetween(y1, y2, y3) || this.isBetween(y1, y2, y4));
      }
      else {
         x = (b2 - b1) / (m1 - m2);
         y = m1 * x + b1;
      }

      return this.isBetween(x1, x2, x) && this.isBetween(y1, y2, y)
         && this.isBetween(x3, x4, x) && this.isBetween(y3, y4, y);
   }

   /**
    * Check if a number is between two other numbers;
    */
   isBetween(num1: number, num2: number, between: number) {
      return (num1 <= between && num2 >= between)
         || (num1 >= between && num2 <= between);
   }

   /**
    * Get the length of the line.
    */
   getLength(): number {
      const x1: number = this.start.x;
      const x2: number = this.end.x;
      const y1: number = this.start.y;
      const y2: number = this.end.y;
      return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
   }

   /**
    * Get the angle of the line.
    */
   getAngle(): number {
      const angle = Math.atan((this.start.y - this.end.y) / (this.start.x - this.end.x));
      return this.start.x >= this.end.x ? (angle + Math.PI) : angle;
   }
}
