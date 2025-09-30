/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import { ChartBindingModel } from "../../data/chart/chart-binding-model";
import {
   convertDataRef,
   convertDataRefs,
   getChartLabel,
   removeNullProps
} from "./binding-tool";
import { ChartBindingFields, ChartBindingInfo } from "./types/chart-binding-info";

export function getChartBindingContext(bindingModel: ChartBindingModel): string {
   let bindingFields: ChartBindingFields = {};
   const multiStyle = bindingModel.multiStyles;

   if(bindingModel.xfields && bindingModel.xfields.length != 0) {
      bindingFields.x_axis_fields = convertDataRefs(bindingModel.xfields, multiStyle, true);
   }

   if(bindingModel.yfields && bindingModel.yfields.length != 0) {
      bindingFields.y_axis_fields = convertDataRefs(bindingModel.yfields, multiStyle, true);
   }

   if(bindingModel.geoFields && bindingModel.geoFields.length != 0) {
      bindingFields.geo_fields = convertDataRefs(bindingModel.geoFields);
   }

   if(bindingModel.groupFields && bindingModel.groupFields.length != 0) {
      bindingFields.group_fields = convertDataRefs(bindingModel.groupFields);
   }

   if(bindingModel.colorField?.dataInfo) {
      bindingFields.color = convertDataRef(bindingModel.colorField?.dataInfo);
   }

   if(bindingModel.shapeField?.dataInfo) {
      bindingFields.shape = convertDataRef(bindingModel.shapeField?.dataInfo);
   }

   if(bindingModel.sizeField?.dataInfo) {
      bindingFields.size = convertDataRef(bindingModel.sizeField?.dataInfo);
   }

   if(bindingModel.textField?.dataInfo) {
      bindingFields.text = convertDataRef(bindingModel.textField?.dataInfo);
   }

   if(bindingModel.openField) {
      bindingFields.open = convertDataRef(bindingModel.openField);
   }

   if(bindingModel.closeField) {
      bindingFields.close = convertDataRef(bindingModel.closeField);
   }

   if(bindingModel.highField) {
      bindingFields.high = convertDataRef(bindingModel.highField);
   }

   if(bindingModel.lowField) {
      bindingFields.low = convertDataRef(bindingModel.lowField);
   }

   if(bindingModel.pathField) {
      bindingFields.path = convertDataRef(bindingModel.pathField);
   }

   if(bindingModel.sourceField) {
      bindingFields.source = convertDataRef(bindingModel.sourceField);
   }

   if(bindingModel.targetField) {
      bindingFields.target = convertDataRef(bindingModel.targetField);
   }

   if(bindingModel.startField) {
      bindingFields.start = convertDataRef(bindingModel.startField);
   }

   if(bindingModel.endField) {
      bindingFields.end = convertDataRef(bindingModel.endField);
   }

   if(bindingModel.milestoneField) {
      bindingFields.milestone = convertDataRef(bindingModel.milestoneField);
   }

   if(bindingModel.nodeColorField) {
      bindingFields.node_color = convertDataRef(bindingModel.nodeColorField?.dataInfo);
   }

   if(bindingModel.nodeSizeField) {
      bindingFields.node_size = convertDataRef(bindingModel.nodeSizeField?.dataInfo);
   }

   let chartInfo: ChartBindingInfo = {
      chartType : getChartLabel(bindingModel.chartType),
      bindingFields: bindingFields
   }

   return JSON.stringify(removeNullProps(chartInfo));
}
