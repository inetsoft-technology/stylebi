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
 * Event that changes lock status of ve object.
 */
export class LockVSObjectEvent extends VSObjectEvent {
   /**
    * Whether the object is locked.
    */
   public locked: boolean;

   /**
    * Creates a new instance of <tt>MoveVSObjectEvent</tt>.
    *
    * @param objectName the name of the object.
    * @param locked      whether the object is locked.
    */
   constructor(objectName: string, locked: boolean)
   {
      super(objectName);
      this.locked = locked;
   }
}