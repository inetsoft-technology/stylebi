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

import { ChartRef } from "../../../common/data/chart-ref";
import { StyleConstants } from "../../../common/util/style-constants";
import { XConstants } from "../../../common/util/xconstants";
import { ChartAggregateRef } from "../../data/chart/chart-aggregate-ref";
import { ChartBindingModel } from "../../data/chart/chart-binding-model";
import { ChartDimensionRef } from "../../data/chart/chart-dimension-ref";
import { getChartLabel, getGroupOptionLabel, getOrderDirection } from "./binding-tool";

export function getBindingContext(bindingModel: ChartBindingModel): string {
   let bindingFields: ChartBindingFields = {};

   if(bindingModel.xfields && bindingModel.xfields.length != 0) {
      bindingFields.x_axis_fields = convertChartRefs(bindingModel.xfields);
   }

   if(bindingModel.yfields && bindingModel.yfields.length != 0) {
      bindingFields.y_axis_fields = convertChartRefs(bindingModel.yfields);
   }

   if(bindingModel.geoFields && bindingModel.geoFields.length != 0) {
      bindingFields.geo_fields = convertChartRefs(bindingModel.geoFields);
   }

   if(bindingModel.groupFields && bindingModel.groupFields.length != 0) {
      bindingFields.group_fields = convertChartRefs(bindingModel.groupFields);
   }

   if(bindingModel.colorField?.dataInfo) {
      bindingFields.color = convertChartRef(bindingModel.colorField?.dataInfo);
   }

   if(bindingModel.shapeField?.dataInfo) {
      bindingFields.shape = convertChartRef(bindingModel.shapeField?.dataInfo);
   }

   if(bindingModel.sizeField?.dataInfo) {
      bindingFields.size = convertChartRef(bindingModel.sizeField?.dataInfo);
   }

   if(bindingModel.textField?.dataInfo) {
      bindingFields.text = convertChartRef(bindingModel.textField?.dataInfo);
   }

   if(bindingModel.openField) {
      bindingFields.open = convertChartRef(bindingModel.openField);
   }

   if(bindingModel.closeField) {
      bindingFields.close = convertChartRef(bindingModel.closeField);
   }

   if(bindingModel.highField) {
      bindingFields.high = convertChartRef(bindingModel.highField);
   }

   if(bindingModel.lowField) {
      bindingFields.low = convertChartRef(bindingModel.lowField);
   }

   if(bindingModel.pathField) {
      bindingFields.path = convertChartRef(bindingModel.pathField);
   }

   let chartInfo: ChartBindingInfo = {
      chartType : getChartLabel(bindingModel.chartType),
      bindingFields: bindingFields
   }

   return JSON.stringify(chartInfo);
}

function convertChartRefs(refs: ChartRef[], multiStyle: boolean = false, xy: boolean = false): BindingField[] {
   if(!refs || refs.length == 0) {
      return null;
   }

   let fields: BindingField[] = [];

   for(let i = 0; i < refs.length; i++) {
      let field = convertChartRef(refs[i], multiStyle, xy);
      if(field) {
         fields.push(field);
      }
   }

   if(fields.length == 0) {
      return null;
   }

   return fields;
}

function convertChartRef(ref: ChartRef, multiStyle: boolean = false, xy: boolean = false): DimensionField | AggregateField {
   if(!ref) {
      return null;
   }

   let field_name: string = ref.fullName ?? ref.name;
   let data_type: string = ref.dataType;
   let base_fields: BaseField[] = [];

   if(ref.dataRefModel) {
      base_fields.push({
         field_name: ref.dataRefModel.name,
         data_type: ref.dataRefModel.dataType
      });
   }

   if(ref.classType == "BAggregateRefModel") {
      let aggr = ref as ChartAggregateRef;

      if(aggr.secondaryColumn) {
         base_fields.push({
            field_name: aggr.secondaryColumn?.name,
            data_type: aggr.secondaryColumn?.dataType
         });
      }

      let formula = aggr.formula;

      let aggrInfo: AggregateField = {
         field_name: field_name,
         data_type: data_type,
         base_fields: base_fields,
         aggregation: formula,
      };

      if(multiStyle && xy) {
         aggrInfo.aggregate_chart_type = getChartLabel(aggr.chartType);
      }

      return aggrInfo;
   }

   if(ref.classType == "BDimensionRef") {
      let dim = ref as ChartDimensionRef;

      let dimInfo: DimensionField = {
         field_name: field_name,
         data_type: data_type,
         base_fields: base_fields
      };

      if(dim.order != XConstants.SORT_NONE) {
         dimInfo.sort = {
            direction: getOrderDirection(dim.order),
            by_measure: dim.sortByCol
         };
      }

      if(dim.rankingOption == StyleConstants.TOP_N + "" || dim.rankingOption == StyleConstants.BOTTOM_N + "") {
         dimInfo.topn = {
            enabled: true,
            n: dim.rankingN,
            by_measure: dim.rankingCol,
            reverse: dim.rankingOption == StyleConstants.BOTTOM_N + ""
         };
      }

      if(dim.dateLevel != XConstants.NONE_DATE_GROUP + "") {
         dimInfo.group = getGroupOptionLabel(dim.dateLevel)
      }

      return dimInfo;
   }

   return null;
}

