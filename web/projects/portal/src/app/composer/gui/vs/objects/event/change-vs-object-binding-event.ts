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
import { AssetEntry } from "../../../../../../../../shared/data/asset-entry";
import { TableTransfer } from "../../../../../common/data/dnd-transfer";

/**
 * Event used to change the binding of an object.
 */
export class ChangeVSObjectBindingEvent extends VSObjectEvent {
   /**
    * The AssetEntry of the datasource to bind to;
    */
   public binding: AssetEntry[];

   /**
    * The information for binding dropped from a component (tables only)
    */
   public componentBinding: TableTransfer;

   /**
    * The new x position of item if creating new one;
    */
   public x: number;

   /**
    * The new y position of item if creating new one;
    */
   public y: number;

   /**
    * Whether or not to place new object into tab.
    */
   public tab: boolean;

   /**
    * Creates a new instance of <tt>ChangeVSObjectBindingEvent</tt>.
    *
    * @param objectName the name of the object.
    */
   constructor(objectName: string)
   {
      super(objectName);
   }
}