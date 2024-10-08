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
import { BaseVSLayoutEvent } from "./base-vs-layout-event";

/**
 * Event used to move/resize a layout object
 */
export class MoveResizeLayoutObjectsEvent extends BaseVSLayoutEvent {
   /**
    * The name of the object being moved.
    */
   public objectNames: String[];

   /**
    * The x position;
    */
   public left: number[];

   /**
    * The y position;
    */
   public top: number[];

   /**
    * The width.
    */
   public width: number[];

   /**
    * The height;
    */
   public height: number[];

   /**
    * Creates a new instance of <tt>MoveResizeLayoutObjectsEvent</tt>.
    *
    * @param layoutName  the name of the layout.
    * @param objectName  the name of the object being changed.
    * @param x           the new x position.
    * @param y           the new y position.
    * @param width       the new width.
    * @param height.     the new height.
    */
   constructor(layoutName: string, objectNames: string[], left: number[], top: number[],
               width: number[], height: number[])
   {
      super();

      this.layoutName = layoutName;
      this.objectNames = objectNames;
      this.left = left;
      this.top = top;
      this.width = width;
      this.height = height;
   }
}