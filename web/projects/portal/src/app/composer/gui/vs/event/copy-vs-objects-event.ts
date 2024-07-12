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
import { ViewsheetEvent } from "../../../../common/viewsheet-client/viewsheet-event";

/**
 * Event used to copy objects in the composer.
 */
export class CopyVSObjectEvent implements ViewsheetEvent {
   /**
    * List of object names to be copied.
    */
   public objects: string[];

   /**
    * Whether this object is copy or cut.
    */
   public cut: boolean;

   /**
    * The new x offset of the object.
    */
   public xOffset: number;

   /**
    * The new y offset of the object.
    */
   public yOffset: number;

   // offset relative to the first object.
   public relative;

   /**
    * Creates a new instance of <tt>RemoveVSObjectEvent</tt>.
    *
    * @param objects the names of the objects being copied.
    * @param isCut   whether the objects are being copied or pasted.
    */
   constructor(objects: string[], isCut: boolean, xOffset: number = 0, yOffset: number = 0,
               relative: boolean = false)
   {
      this.objects = objects;
      this.cut = isCut;
      this.xOffset = xOffset;
      this.yOffset = yOffset;
      this.relative = relative;
   }
}
