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
import { ViewsheetEvent } from "../../../../../common/viewsheet-client/index";
import { AssetEntry } from "../../../../../../../../shared/data/asset-entry";
/**
 * Event used to add a new viewsheet object in the composer.
 */
export class AddNewVSObjectEvent implements ViewsheetEvent {
   /**
    * The type of object being added.
    */
   public type: number;

   /**
    * The x offset of the object.
    */
   public xOffset: number;

   /**
    * The y offset of the object.
    */
   public yOffset: number;

   /**
    * The viewsheet scale.
    */
   public scale: number;

   /**
    * AssetEntry of embedded viewsheet, if being added.
    */
   public entry: AssetEntry;

   /**
    * Whether to force select/edit mode when returned to front end.
    */
   public forceEditMode: boolean;

   /**
    * the grid row of vs-wizard-pane.
    */
   public wizardCurrentGridRow: number;

   /**
    * the grid col of vs-wizard-pane.
    */
   public wizardCurrentGridCol: number;


   /**
    * Creates a new instance of <tt>AddNewVSObjectEvent</tt>.
    *
    * @param type    the type of object.
    * @param xOffset the x position/offset.
    * @param yOffset the y position/offset.
    */
   constructor(type: number, xOffset: number, yOffset: number, forceEditMode: boolean = false) {
      this.type = type;
      this.xOffset = xOffset;
      this.yOffset = yOffset;
      this.forceEditMode = forceEditMode;
   }
}
