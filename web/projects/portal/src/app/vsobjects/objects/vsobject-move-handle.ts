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
import { VSObjectType } from "../../common/data/vs-object-type";

export namespace VSObjectMoveHandle {
   /**
    * Gets the css selector for a VSObject's move handle.
    *
    * @param type the VSObjectType to get the move handle of.
    *
    * @return the move handle, or null the VSObject doesn't have one.
    */
   export function getMoveHandle(type: VSObjectType): string | null {
      let moveHandle: string | null;

      switch(type) {
         case "VSTable":
         case "VSCalcTable":
         case "VSCrosstab":
         case "VSCalendar":
         case "VSCheckBox":
         case "VSRadioButton":
         case "VSSelectionList":
         case "VSSelectionTree":
         case "VSSelectionContainer":
            moveHandle = ".title-move-zone";
            break;
         case "VSChart":
            moveHandle = ".title-move-zone,chart-title-area,chart-axis-area";
            break;
         case "VSGauge":
            moveHandle = ".vs-gauge__image";
            break;
         case "VSImage":
            moveHandle = ".image-container";
            break;
         case "VSLine":
            moveHandle = ".line-resize-container";
            break;
         case "VSOval":
            moveHandle = ".vs-oval";
            break;
         case "VSRangeSlider":
            moveHandle = "vs-range-slider";
            break;
         case "VSRectangle":
            moveHandle = ".vs-rectangle";
            break;
         case "VSSlider":
            moveHandle = ".slider";
            break;
         case "VSSubmit":
            moveHandle = ".submit-button";
            break;
         case "VSText":
            moveHandle = ".text-content";
            break;
         case "VSGroupContainer":
            moveHandle = ".vs-group-container";
            break;
         case "VSAnnotation":
         case "VSComboBox":
         case "VSCylinder":
         case "VSSlidingScale":
         case "VSSpinner":
         case "VSTab":
         case "VSTextInput":
         case "VSThermometer":
         case "VSUpload":
         case "VSViewsheet":
            moveHandle = null;
            break;
         default:
            throw new Error(`Unknown VSObjectType: ${type}`);
      }

      return moveHandle;
   }
}
