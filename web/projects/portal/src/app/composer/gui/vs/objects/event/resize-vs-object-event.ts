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
import { MoveVSObjectEvent } from "./move-vs-object-event";

/**
 * Event used to resize an object in the composer.
 */
export class ResizeVSObjectEvent extends MoveVSObjectEvent {
   /**
    * The new width of the object.
    */
   public width: number;

   /**
    * The new height of the object.
    */
   public height: number;

   /**
    * The viewsheet scale.
    */
   public scale: number;

   /**
    * Creates a new instance of <tt>ResizeVSObjectEvent</tt>.
    *
    * @param objectName the name of the object.
    * @param width      the new width.
    * @param height     the new height.
    */
   constructor(objectName: string, left: number, top: number, width: number, height: number)
   {
      super(objectName, left, top);
      this.width = width;
      this.height = height;
   }
}