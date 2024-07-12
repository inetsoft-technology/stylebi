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
import { VSObjectEvent } from "../../../../../vsobjects/event/vs-object-event";

/**
 * Event used to change the z index of a viewsheet object.
 */
export class ChangeVSObjectLayerEvent extends VSObjectEvent {
   /**
    * The object to switch z indexes with.
    */
   public switchWithObject: string;

   /**
    * The new z index of object;
    */
   public zIndex: number;

   /**
    * Creates a new instance of <tt>ChangeVSObjectLayerEvent</tt>.
    *
    * @param objectName the name of the object.
    * @param zIndex     the new z index.
    */
   constructor(objectName: string, zIndex: number, switchWithObject?: string)
   {
      super(objectName);
      this.zIndex = zIndex;
       this.switchWithObject = switchWithObject;
   }
}