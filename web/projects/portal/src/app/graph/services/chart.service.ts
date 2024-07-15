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
import { Injectable } from "@angular/core";
import { Rectangle } from "../../common/data/rectangle";

@Injectable()
export class ChartService {
   clearCanvas(context: CanvasRenderingContext2D): void {
      context.clearRect(0, 0, context.canvas.width, context.canvas.height);
   }

   /**
    * Draw a rectangle.
    *
    * @param context the 2d canvas rendering context on which to draw the shape.
    * @param box the rectangle.
    */
   drawRectangle(context: CanvasRenderingContext2D, box: Rectangle): void {
      this.clearCanvas(context);
      context.lineWidth = 2;
      context.beginPath();
      context.strokeStyle = "#66DD66";
      context.strokeRect(box.x + 1, box.y + 1, box.width, box.height);
      context.closePath();
      context.stroke();
   }
}
