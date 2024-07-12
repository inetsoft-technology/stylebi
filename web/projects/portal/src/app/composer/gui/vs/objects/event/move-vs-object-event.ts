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
 * Event used to move an object in the composer.
 */
export class MoveVSObjectEvent extends VSObjectEvent{
   /**
    * The new x offset of the object.
    */
   public xOffset: number;

   /**
    * The new y offset of the object.
    */
   public yOffset: number;

   /**
    * The vs scale.
    */
   public scale: number;

   /**
    * the viewsheet wizard grid row count
    * */
   public wizardGridRows: number;

   /**
    * the viewsheet wizard grid col count
    * */
   public wizardGridCols: number;

   /**
    * for vs wizard. weather auto layout in horizontal.
    */
   public autoLayoutHorizontal: boolean;

   /**
    * for vs wizard. set move all row or col.
    */
   public moveRowOrCol: boolean;

   /**
    * Creates a new instance of <tt>MoveVSObjectEvent</tt>.
    *
    * @param objectName the name of the object.
    * @param xOffset    the new x offset.
    * @param yOffset    the new y offset.
    */
   constructor(objectName: string, xOffset: number, yOffset: number, wizardGridRows: number = 0,
               wizardGridCols: number = 0)
   {
      super(objectName);
      this.xOffset = xOffset;
      this.yOffset = yOffset;
      this.wizardGridRows = wizardGridRows;
      this.wizardGridCols = wizardGridCols;
   }
}