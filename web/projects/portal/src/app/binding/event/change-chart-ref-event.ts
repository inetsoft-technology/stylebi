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
import { OriginalDescriptor } from "../../common/data/original-descriptor";
import { ViewsheetEvent } from "../../common/viewsheet-client";
import { ChartBindingModel } from "../data/chart/chart-binding-model";

/**
 * Event for common parameters for composer object events.
 */
export class ChangeChartRefEvent implements ViewsheetEvent {
   public model: any;

   /**
    * Creates a new instance of <tt>ChangeChartRefEvent</tt>.
    *
    * @param name the name of the chart.
    * @param refOriginalDescriptor the OriginalDescriptor of current edit chartref.
    * @param model the binding model of the chart.
    */
   constructor(public name: string, public refOriginalDescriptor: OriginalDescriptor,
               model: ChartBindingModel, public fieldType?: string)
   {
      // remove fields that are not used on the server side to reduce the transmission size
      this.model = (
         ({source, sqlMergeable, type, chartType, rtchartType, colorField, shapeField, sizeField, textField, colorFrame, shapeFrame, lineFrame, textureFrame, sizeFrame, waterfall, multiStyles, separated, mapType, xfields, yfields, geoFields, groupFields, supportsGroupFields, openField, closeField, highField, lowField, pathField, sourceField, targetField, startField, endField, milestoneField, supportsPathField, geoCols, nodeColorField, nodeColorFrame, nodeSizeField, nodeSizeFrame}) =>
            ({source, sqlMergeable, type, chartType, rtchartType, colorField, shapeField, sizeField, textField, colorFrame, shapeFrame, lineFrame, textureFrame, sizeFrame, waterfall, multiStyles, separated, mapType, xfields, yfields, geoFields, groupFields, supportsGroupFields, openField, closeField, highField, lowField, pathField, sourceField, targetField, startField, endField, milestoneField, supportsPathField, geoCols, nodeColorField, nodeColorFrame, nodeSizeField, nodeSizeFrame}))
        // eslint-disable-next-line no-unexpected-multiline
         (model);
   }
}
