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
import { XSchema } from "../../common/data/xschema";
import { GraphTypes } from "../../common/graph-types";
import { DateRangeRef } from "../../common/util/date-range-ref";
import { Tool } from "../../../../../shared/util/tool";
import { AestheticInfo } from "../data/chart/aesthetic-info";
import { ChartAggregateRef } from "../data/chart/chart-aggregate-ref";
import { ChartBindingModel } from "../data/chart/chart-binding-model";
import { ChartAestheticModel } from "../data/chart/chart-binding-model";
import { ChartDimensionRef } from "../data/chart/chart-dimension-ref";
import { ChartGeoRef } from "../data/chart/chart-geo-ref";
import { ChartRef } from "../../common/data/chart-ref";
import { GeographicOptionInfo } from "../data/chart/geographic-option-info";
import { AggregateFormula } from "./aggregate-formula";
import { AssetUtil } from "./asset-util";
import * as Visual from "../../common/data/visual-frame-model";
import { BDimensionRef } from "../data/b-dimension-ref";
import { XConstants } from "../../common/util/xconstants";

export class GraphUtil {
   public static get COLOR_FIELD(): string {
      return "color";
   }

   public static get SHAPE_FIELD(): string {
      return "shape";
   }

   public static get SIZE_FIELD(): string {
      return "size";
   }

   public static get TEXT_FIELD(): string {
      return "text";
   }

   public static get CHART_SHAPE(): string {
      return "shapetype";
   }

   public static get CHART_LINE(): string {
      return "linetype";
   }

   public static get CHART_TEXTURE(): string {
      return "texturetype";
   }

   public static get DIMENSION_REF(): string {
      return "dimension";
   }

   public static get MEASURE_REF(): string {
      return "aggregate";
   }

   public static get GEO_REF(): string {
      return "geo";
   }

   static getAllBindingRefs(bindingModel: ChartBindingModel,
                            includeAesthetic: boolean = true): ChartRef[]
   {
      if(!bindingModel) {
         return new Array<ChartRef>();
      }

      let chartType: number = bindingModel.chartType;
      let arr: ChartRef[] = new Array<ChartRef>();

      if(bindingModel.xfields) {
         arr = arr.concat(bindingModel.xfields);
      }

      if(bindingModel.yfields) {
         arr = arr.concat(bindingModel.yfields);
      }

      if(bindingModel.groupFields) {
         arr = arr.concat(bindingModel.groupFields);
      }

      if(GraphTypes.isGeo(chartType)) {
         arr = arr.concat(bindingModel.geoFields);
      }
      else if(chartType == GraphTypes.CHART_CANDLE || chartType == GraphTypes.CHART_STOCK) {
         arr.push(bindingModel.openField);
         arr.push(bindingModel.closeField);
         arr.push(bindingModel.highField);
         arr.push(bindingModel.lowField);
      }
      else if(GraphTypes.isRelation(chartType)) {
         arr.push(bindingModel.sourceField);
         arr.push(bindingModel.targetField);
      }
      else if(GraphTypes.isGantt(chartType)) {
         arr.push(bindingModel.startField);
         arr.push(bindingModel.endField);
         arr.push(bindingModel.milestoneField);
      }

      return arr.concat(GraphUtil.getAllAestheticRefs(bindingModel)).filter(a => a != null);
   }

   static getAllAestheticRefs(model: ChartBindingModel): ChartRef[] {
      let refs: ChartRef[] = new Array<ChartRef>();

      if(GraphUtil.isMultiAesthetic(model)) {
         let aggrs = GraphUtil.getAestheticAggregateRefs(model, true);

         for(let i = 0; i < aggrs.length; i++) {
            refs = refs.concat(GraphUtil.getAestheticRefs(aggrs[i]));
         }

         return refs;
      }
      else {
         refs = GraphUtil.getAestheticRefs(model);
      }

      return refs;
   }

   /**
    * Get chart aggregate model refs, for no shape aesthetic ref.
    */
   static getModelRefs(bindingModel: ChartBindingModel): ChartRef[] {
      if(bindingModel == null) {
         return new Array<ChartRef>();
      }

      let yrefs = GraphUtil.getModelRefsY(bindingModel);

      if(yrefs && yrefs.length > 0) {
         return yrefs;
      }

      return GraphUtil.getModelRefsX(bindingModel);
   }

   /**
    * Get chart aggregate model X refs, for no shape aesthetic ref.
    */
   static getModelRefsX(bindingModel: ChartBindingModel): ChartRef[] {
      if(bindingModel == null) {
         return new Array<ChartRef>();
      }

      return GraphUtil.getMeasureRefs(bindingModel.xfields);
   }

   // get (non-discrete) aggregate refs.
   private static getMeasureRefs(xfields: ChartRef[]): ChartRef[] {
      let refs = new Array<ChartRef>();
      let names = new Array<string>();

      for(let field of xfields) {
         if(field.measure && field.fullName && names.indexOf(field.fullName) == -1 &&
            !(<ChartAggregateRef> field).discrete)
         {
            refs.push(field);
            names.push(field.fullName);
         }
      }

      return refs;
   }

   /**
    * Get chart aggregate model Y refs, for no shape aesthetic ref.
    */
   static getModelRefsY(bindingModel: ChartBindingModel): ChartRef[] {
      if(bindingModel == null) {
         return new Array<ChartRef>();
      }

      return GraphUtil.getMeasureRefs(bindingModel.yfields);
   }

   static getAestheticInfos(bindable: any): AestheticInfo[] {
      let infos: AestheticInfo[] = new Array<AestheticInfo>();

      if(!bindable) {
         return infos;
      }

      infos.push(bindable.colorField);
      infos.push(bindable.shapeField);
      infos.push(bindable.sizeField);
      infos.push(bindable.textField);

      if(GraphTypes.isRelation(bindable.chartType)) {
         infos.push(bindable.nodeColorField);
         infos.push(bindable.nodeSizeField);
      }

      return infos.filter(i => i != null);
   }

   /**
    * Get all the aggregate refs from xy binding.
    */
   static getXYAggregateRefs(binding: ChartBindingModel,
      onlyXY: boolean = true): Array<ChartAggregateRef> {
      let fields = new Array<ChartRef>();

      if(binding.xfields) {
         fields = fields.concat(binding.xfields);
      }

      if(binding.yfields) {
         fields = fields.concat(binding.yfields);
      }

      let aggs = new Array<ChartAggregateRef>();

      if(GraphUtil.isMultiAesthetic(binding) && binding.allChartAggregate && !onlyXY) {
         aggs[0] = binding.allChartAggregate;
      }

      for(let field of fields) {
         if(field && field.measure) {
            aggs[aggs.length] = <ChartAggregateRef>field;
         }
      }

      return aggs;
   }

   static isMultiAesthetic(binding: ChartBindingModel) {
      return binding.multiStyles || GraphTypes.isGantt(binding.chartType);
   }

   static getAestheticAggregateRefs(binding: ChartBindingModel, onlyXY: boolean = true) {
      const refs = this.getXYAggregateRefs(binding, onlyXY);

      if(GraphTypes.isGantt(binding.chartType)) {
         refs.push(<ChartAggregateRef> binding.startField);
         refs.push(<ChartAggregateRef> binding.milestoneField);
      }

      return refs.filter(a => a != null);
   }

   /**
    * Check if the chart is map.
    */
   static isMap(binding: ChartBindingModel): boolean {
      if(!binding) {
         return false;
      }

      return GraphTypes.isMap(GraphUtil.getChartType(null, binding));
   }

   /**
    * Check if the chart contains map polygon element.
    */
   static containsMapPolygonField(binding: ChartBindingModel): boolean {
      return this.isMap(binding) &&
         this.getPolygonField(binding) != null;
   }

   /**
    * Get polygon field.
    * @param binding binding model of map chart.
    */
   static getPolygonField(binding: ChartBindingModel): ChartGeoRef {
      let arr: ChartGeoRef[] = GraphUtil.getPolygonFields(binding);

      return arr.length > 0 ? arr[0] : null;
   }

   /**
    * Get polygon fields.
    * @param binding binding model of map chart.
    */
   static getPolygonFields(binding: ChartBindingModel): ChartGeoRef[] {
      let arr: ChartGeoRef[] = new Array<ChartGeoRef>();

      for(let ref of binding.geoFields) {
         let geoRef: ChartGeoRef = <ChartGeoRef> ref;
         let option: GeographicOptionInfo = geoRef.option;
         let layer: number = parseInt(option.layerValue, 10);

         if(!this.isPointLayer(layer)) {
            arr.push(geoRef);
         }
      }

      return arr;
   }

   /**
    * Check if the chart contains map point field.
    */
   static containsMapPointField(binding: ChartBindingModel): boolean {
      if(!this.isMap(binding)) {
         return false;
      }

      if(this.getMeasures(binding.xfields).length >= 1 &&
         this.getMeasures(binding.yfields).length >= 1)
      {
         return true;
      }

      return this.getPointFields(binding).length > 0;
   }

   /**
    * Get point fields.
    * @param binding binding model of map chart.
    */
   static getPointFields(binding: ChartBindingModel): ChartGeoRef[] {
      let arr: ChartGeoRef[] = new Array<ChartGeoRef>();

      for(let ref of binding.geoFields) {
         let geoRef: ChartGeoRef = <ChartGeoRef> ref;
         let option: GeographicOptionInfo = geoRef.option;
         let layer: number = parseInt(option.layerValue, 10);

         if(this.isPointLayer(layer)) {
            arr.push(geoRef);
         }
      }

      return arr;
   }

   /**
    * Check if a layer is a point layer.
    */
   static isPointLayer(layer: number): boolean {
      return layer >= 1000;
   }

   static isChartType(binding: ChartBindingModel, func: (number) => boolean): boolean {
      if(!binding) {
         return false;
      }

      if(!binding.multiStyles) {
         let ctype = GraphUtil.getChartType(null, binding);
         return func(ctype);
      }

      let aggrs = GraphUtil.getXYAggregateRefs(binding, false);

      for(let i = 0; i < aggrs.length; i++) {
         let ctype = GraphUtil.getChartType(aggrs[i], binding);

         if(func(ctype)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if is waterfall graph.
    */
   static isWaterfall(binding: ChartBindingModel): boolean {
      return GraphUtil.isChartType(binding, t => GraphTypes.isWaterfall(t));
   }

   /**
    * Check if the chart is polar.
    * @param ispie true to only return true if it's a pie.
    */
   static isPolar(binding: ChartBindingModel, ispie: boolean): boolean {
      return GraphUtil.isChartType(binding,
                                   t => ispie ? GraphTypes.isPie(t) : GraphTypes.isPolar(t));
   }

   /**
    * Return charttype.
    * @param aggr, chart aggregate.
    * @param binding, chart binding model.
    */
   static getChartType(aggr: ChartAggregateRef, binding: ChartBindingModel): number {
      if(binding == null) {
         return GraphTypes.CHART_AUTO;
      }

      if(binding.multiStyles && aggr != null ) {
         return aggr.chartType == GraphTypes.CHART_AUTO ?
            aggr.rtchartType : aggr.chartType;
      }

      return binding.chartType == GraphTypes.CHART_AUTO ?
         binding.rtchartType : binding.chartType;
   }

   /**
    * Check if is merged graph.
    */
   static isMergedGraphType(binding: ChartBindingModel): boolean {
      return GraphUtil.isChartType(binding, t => GraphTypes.isMergedGraphType(t));
   }

   /**
    * Get the measures in the field list.
    */
   static getMeasures(refs: Array<ChartRef>): Array<ChartRef> {
      let ms = new Array<ChartRef>();

      if(!refs) {
         return ms;
      }

      for(let i = 0; i < refs.length; i++) {
         if(refs[i].measure) {
            ms.push(refs[i]);
         }
      }

      return ms;
   }

   /**
    * Set the default formula when measures are changed.
    */
   static setDefaultFormulas(model: any, oxrefs: Array<ChartRef>,
      oyrefs: Array<ChartRef>): void
   {
      let oxmeasures = GraphUtil.getMeasures(oxrefs);
      let oymeasures = GraphUtil.getMeasures(oyrefs);
      let nxmeasures = GraphUtil.getMeasures(model.xfields);
      let nymeasures = GraphUtil.getMeasures(model.yfields);

      let xchanged = !Tool.isEquals(oxmeasures, nxmeasures);
      let ychanged = !Tool.isEquals(oymeasures, nymeasures);

      // if x or y measures changed, check if need to update the default formula
      if(!xchanged && !ychanged) {
         return;
      }

      let nscattered = nxmeasures.length > 0 && nymeasures.length > 0;
      let oscattered = oxmeasures.length > 0 && oymeasures.length > 0;

      // if the binding didn't change between scattered and non-scattered
      // chart, there is no need to change the default formula
      if(nscattered == oscattered) {
         return;
      }

      // if measures swapped, don't change anything
      let nall = nxmeasures.concat(nymeasures);
      let oall = oxmeasures.concat(oymeasures);
      // try if measures on both x/y
      let omxy = oxmeasures.length > 0 && oymeasures.length > 0;
      let nmxy = nxmeasures.length > 0 && nymeasures.length > 0;

      if(Tool.isEquals(nall, oall) && omxy == nmxy) {
         return;
      }

      // check if need to set default formula to None if new measures are added
      if(nscattered) {
         if(xchanged) {
            GraphUtil.setDefaultFormulaNone(nxmeasures, nymeasures);
         }
         else if(ychanged) {
            GraphUtil.setDefaultFormulaNone(nymeasures, nxmeasures);
         }
      }
      else {
         GraphUtil.setDefaultFormula2(nxmeasures, nymeasures, model);
      }
   }

   /**
    * Set the default formula for ref.
    */
   static setDefaultFormulaNone(refs: ChartRef[], others: ChartRef[]): void {
      let found = false;

      for(let i = 0; i < others.length; i++) {
         if((<ChartAggregateRef> others[i]).aggregated) {
            found = true;
            break;
         }
      }

      // default to non-aggregate if there are measures in both x and y
      if(found) {
         for(let i = 0; i < refs.length; i++) {
            if((<ChartAggregateRef> refs[i]).aggregated) {
               const aggregate = refs[i] as ChartAggregateRef;
               aggregate.formula = AggregateFormula.NONE.name;
               aggregate.aggregated = false;
               break;
            }
         }
      }
   }

   /**
    * Set the aggregate formula from none to default if only measures left on
    * one of x or y.
    */
   static setDefaultFormula2(xmeasures: any, ymeasures: any, model: any): void {
      let xmeasure = xmeasures.length > 0;
      let ymeasure = ymeasures.length > 0;

      if(!xmeasure && !ymeasure || xmeasure && ymeasure) {
         return;
      }

      let arr = xmeasure ? xmeasures : ymeasures;

      for(let key in arr) {
         if(arr.hasOwnProperty(key)) {
            let ref = arr[key];

            if(!ref.aggregated && !GraphTypes.isGeo(model.chartType)) {
               ref.formula = AssetUtil.getDefaultFormula(ref.dataRefModel).name;
            }
         }
      }
   }

   /**
    * Check if the specified info supports inverted chart.
    */
   static supportsInvertedChart(model: ChartBindingModel): boolean {
      if(model.multiStyles) {
         let yfs = model.yfields;

         for(let i = 0; i < yfs.length; i++) {
            if(yfs[i].classType ==  "aggregate") {
               let ctype = (<ChartAggregateRef> yfs[i]).chartType;

               if(!GraphTypes.supportsInvertedChart(ctype)) {
                  return false;
               }
            }
         }
      }
      else {
         return GraphTypes.supportsInvertedChart(model.chartType);
      }

      return true;
   }

   static isAllChartAggregate(aggr: ChartAggregateRef): boolean {
      return aggr && aggr.classType == "allaggregate";
   }

   static isAestheticRef(obj: any): boolean {
      return obj && obj.classType == "AestheticInfo";
   }

   static isDimensionRef(obj: any): boolean {
      return obj && obj.classType == "dimension";
   }

   static isDimension(obj: any): boolean {
      if(obj && obj.measure === false) {
         return true;
      }

      return GraphUtil.isDimensionRef(obj);
   }

   static isAggregateRef(obj: any): boolean {
      return obj && obj.classType == "aggregate";
   }

   static isGeoRef(obj: any): boolean {
      return obj && obj.classType == "geo";
   }

   /**
    * Get shape style for shapefieldmc.
    * @param aggr, chart aggregate.
    * @param binding, chart binding model.
    */
   static getShapeType(aggr: ChartAggregateRef, binding: ChartBindingModel): string {
      let chartType = GraphUtil.getChartType(aggr, binding);
      let texture = GraphTypes.supportsTexture(chartType);
      let line = GraphTypes.supportsLine(chartType, binding);
      let shapeType: string;

      if(line) {
         shapeType = GraphUtil.CHART_LINE;
      }
      else if(texture) {
         shapeType = GraphUtil.CHART_TEXTURE;
      }
      else {
         shapeType = GraphUtil.CHART_SHAPE;
      }

      return shapeType;
   }

   /**
    * Do convert chartref for report chart.
    * @param currentAggr current edit aggregate of multistyle chart.
    * @param chartRef the chartref to convert.
    */
   static convertChartRef(bindingModel: ChartBindingModel,
      currentAggr: ChartAggregateRef, chartRef: ChartRef): void
   {
      let oxrefs = Tool.clone(bindingModel.xfields);
      let oyrefs = Tool.clone(bindingModel.yfields);
      let newDataInfo = GraphUtil.reverseRef(chartRef, chartRef.measure);
      GraphUtil.replaceModel(bindingModel, currentAggr, chartRef, newDataInfo);
      GraphUtil.setDefaultFormulas(bindingModel, oxrefs, oyrefs);
   }

   static syncNamedGroup(binding: ChartBindingModel, chartRef: ChartRef): void {
      if(chartRef == null || chartRef.measure) {
         return;
      }

      let dim: ChartDimensionRef = <ChartDimensionRef> chartRef;

      if(dim.namedGroupInfo == null) {
         return;
      }

      this.processDims(binding.xfields, dim);
      this.processDims(binding.yfields, dim);
      this.processDims(binding.groupFields, dim);
      this.processDims(this.getAestheticRefs(binding), dim);
   }

   static processDims(refs: ChartRef[], ndim: ChartDimensionRef): void {
      for(let i = 0; i < refs.length; i++) {
         if(refs[i].measure) {
            continue;
         }

         let dim: ChartDimensionRef = <ChartDimensionRef> refs[i];

         if(dim == ndim) {
            continue;
         }

         if(dim.fullName == ndim.fullName) {
            let order = dim.order;

            if((order & XConstants.SORT_SPECIFIC) != XConstants.SORT_SPECIFIC) {
               dim.order = dim.order + XConstants.SORT_SPECIFIC;
            }

            dim.others = ndim.others;
            dim.namedGroupInfo = ndim.namedGroupInfo;
            dim.manualOrder = null;
            dim.customNamedGroupInfo = null;
         }
      }
   }

   /**
    * Convert ref to opposite ref.
    * @prame dataInfo the target ref to convert.
    * @prame toDimension if convert to dimension.
    * @return the result of convert.
    */
   private static reverseRef(dataInfo: ChartRef, toDimension: boolean): ChartRef {
      let newDataInfo: ChartRef;

      if(toDimension) {
         newDataInfo = GraphUtil.reverseToDimRef(<ChartAggregateRef> dataInfo);
         newDataInfo.classType = GraphUtil.DIMENSION_REF;
      }
      else {
         newDataInfo = GraphUtil.reverseToAggRef(<ChartDimensionRef> dataInfo);
         newDataInfo.classType = GraphUtil.MEASURE_REF;
      }

      //newDataInfo.original = null;

      return newDataInfo;
   }

   /**
    * Reverse aggregate ref to dimension ref.
    */
   private static reverseToDimRef(aggrInfo: ChartAggregateRef): ChartDimensionRef {
      let dimInfo = new ChartDimensionRef();
      dimInfo.measure = false;
      dimInfo.dataRefModel = aggrInfo.dataRefModel;

      if(aggrInfo.dataRefModel && XSchema.isDateType(aggrInfo.dataRefModel.dataType)) {
         dimInfo.dateLevel =
            DateRangeRef.getNextDateLevel(-1, aggrInfo.dataRefModel.dataType) + "";
      }

      return dimInfo;
   }

   /**
    * Reverse dimension ref to aggregate ref.
    */
   private static reverseToAggRef(dimInfo: ChartDimensionRef): ChartAggregateRef {
      let aggrInfo = new ChartAggregateRef();
      aggrInfo.measure = true;
      aggrInfo.aggregated = true;
      aggrInfo.dataRefModel = dimInfo.dataRefModel;
      aggrInfo.formula = AssetUtil.getDefaultFormula(dimInfo.dataRefModel).name;

      return aggrInfo;
   }

   /**
    * After convert dimension to measure or conver measure to dimensure,
    * we should use the new dataInfo of convert result replace old in bindingModel.
    */
   private static replaceModel(bindingModel: ChartBindingModel,
      currentAggr: ChartAggregateRef, oldInfo: ChartRef, newInfo: ChartRef): void
   {
      let fields = [bindingModel.xfields, bindingModel.yfields, bindingModel.groupFields];

      for(let i = 0; i < fields.length; i++) {
         let fieldsTemp = fields[i];

         for(let j = 0; j < fieldsTemp.length; j++) {
            if(Tool.isEquals(fieldsTemp[j], oldInfo)) {
               fieldsTemp[j] = newInfo;
               break;
            }
         }
      }

      if(Tool.isEquals(bindingModel.pathField, oldInfo)) {
         bindingModel.pathField = newInfo;

         return;
      }

      GraphUtil.replaceChartAestheticModel(bindingModel, oldInfo, newInfo);

      if(GraphTypes.isMultiAesthetic(bindingModel)) {
         GraphUtil.replaceAggChartAestheticModel(currentAggr, oldInfo, newInfo);
      }
   }

   private static replaceChartAestheticModel(
      chartModel: ChartBindingModel,
      oldInfo: ChartRef,
      newInfo: ChartRef): void
   {
      if(!chartModel) {
         return;
      }

      let aestheticRefs: AestheticInfo[] = [chartModel.colorField, chartModel.shapeField,
         chartModel.sizeField, chartModel.textField, chartModel.nodeSizeField, chartModel.nodeColorField];

      this.replaceAestheticModel(aestheticRefs, oldInfo, newInfo);
   }

   private static replaceAggChartAestheticModel(
      aesModel: ChartAestheticModel,
      oldInfo: ChartRef,
      newInfo: ChartRef): void
   {
      if(!aesModel) {
         return;
      }

      let aestheticRefs: AestheticInfo[] = [aesModel.colorField, aesModel.shapeField,
         aesModel.sizeField, aesModel.textField];

      this.replaceAestheticModel(aestheticRefs, oldInfo, newInfo);
   }

   private static replaceAestheticModel(aestheticRefs: AestheticInfo[], oldInfo: ChartRef,
                                        newInfo: ChartRef): void
   {
      if(!aestheticRefs) {
         return;
      }

      for(let i = 0; i < aestheticRefs.length; i++) {
         let aestheticRef: AestheticInfo = aestheticRefs[i];

         if(!!aestheticRef && Tool.isEquals(aestheticRef.dataInfo, oldInfo)) {
            aestheticRef.dataInfo = newInfo;
         }
         else if (!!aestheticRef && aestheticRef.dataInfo.name == oldInfo.name) {
            aestheticRef = null;
         }
      }
   }

   static isRefConvertEnabled(chartType: number, field: ChartRef, fieldType: string): boolean {
      if(fieldType === "geofields") {
         return false;
      }
      else if(GraphTypes.isRadar(chartType)) {
         if(fieldType === "xfields") {
            return GraphUtil.isAggregateRef(field);
         }
      }
      else if(GraphTypes.isCandle(chartType) || GraphTypes.isStock(chartType)) {
         if(fieldType === "xfields" || fieldType === "yfields") {
            return GraphUtil.isAggregateRef(field);
         }
         else if(fieldType === "high" || fieldType === "close" ||
            fieldType === "open" || fieldType === "low" ||
            fieldType === "HighLowField")
         {
            return false;
         }

         return true;
      }
      else if(fieldType == "source" || fieldType == "target" ||
         fieldType == "start" || fieldType == "end" || fieldType == "milestone")
      {
         return false;
      }
      else if(GraphTypes.isRelation(chartType) || GraphTypes.isGantt(chartType)) {
         if(fieldType === "xfields" || fieldType === "yfields") {
            return false;
         }
      }
      else if(GraphTypes.isTreemap(chartType) || GraphTypes.isMekko(chartType)) {
         if(fieldType === "xfields" || fieldType === "yfields" || fieldType === "groupfields") {
            return false;
         }
      }
      else if(GraphTypes.isFunnel(chartType)) {
         if(fieldType == "yfields" && GraphUtil.isDimensionRef(field)) {
            return false;
         }
      }
      else if(GraphTypes.isInterval(chartType) && fieldType == "size") {
         return !GraphUtil.isAggregateRef(field);
      }

      if(GraphTypes.isMekko(chartType) && fieldType == "gfields") {
         return false;
      }

      if(fieldType === "xfields") {
         return GraphUtil.isAggregateRef(field) || field.refConvertEnabled;
      }
      else if(fieldType === "yfields") {
         return true;
      }

      return true;
   }

   static isSecondaryAxisSupported(binding: ChartBindingModel, aref: ChartAggregateRef): boolean {
      if(binding.separated) {
         return false;
      }

      let chartType: number = binding.rtchartType;

      if(!binding.multiStyles && (chartType == GraphTypes.CHART_3D_BAR ||
         chartType == GraphTypes.CHART_3D_BAR_STACK || chartType == GraphTypes.CHART_3D_PIE))
      {
         return false;
      }

      // ignore aesthetic binding
      if(!GraphUtil.isYFields(binding, aref)) {
         return false;
      }

      chartType = binding.multiStyles ? (<ChartAggregateRef> aref).rtchartType : chartType;

      return GraphTypes.supportsSecondaryAxis(chartType);
   }

   static isYFields(binding: ChartBindingModel, aref: ChartAggregateRef): boolean {
      const allFields = [binding.yfields, binding.xfields];

      for(let i = 0; i < allFields.length; i++) {
         for(let j = 0; j < allFields[i].length; j++) {
            if(allFields[i][j] == aref) {
               return true;
            }
         }
      }

      return false;
   }

   static getAestheticRefs(bindable: any): ChartRef[] {
      let infos: AestheticInfo[] = GraphUtil.getAestheticInfos(bindable);
      let refs: ChartRef[] = new Array<ChartRef>();

      for(let i = 0; i < infos.length; i++) {
         refs.push(infos[i].dataInfo);
      }

      return refs;
   }

   static isNilSupported(aggr: ChartAggregateRef,
                         bindingModel: ChartBindingModel): boolean
   {
      if(bindingModel == null) {
         return false;
      }

      let shapeField = aggr ? aggr.shapeField : bindingModel.shapeField;
      let sizeField = aggr ? aggr.sizeField : bindingModel.sizeField;
      let textField = aggr ? aggr.textField : bindingModel.textField;
      let sizeFrame = aggr ? aggr.sizeFrame : bindingModel.sizeFrame;

      if(GraphUtil.isMap(bindingModel)) {
         return GraphUtil.containsMapPolygonField(bindingModel) &&
            !GraphUtil.containsMapPointField(bindingModel) &&
            shapeField == null && !sizeFrame.changed;
      }

      // point with text as mark (no shape) for table like chart
      // point with color but not size for heatmap
      return shapeField == null && (textField != null || sizeField == null);
   }

   /**
    * Return visualframe array for no aesthetic ref.
    * @param propertyName, 'colorFrame' or 'shapeFrame' or ...
    * @param bindingModel current chart binding model.
    * @param aggr, current edit aggregate, only use for multistyle chart.
    */
   static getVisualFrames(propertyName: string, bindingModel: ChartBindingModel,
      aggr?: ChartAggregateRef, global?: boolean): any[]
   {
      if(propertyName == "shapeFrame") {
         return GraphUtil.getShapeFrames(bindingModel, aggr, global);
      }

      let frameMap: any[] = new Array<any>();

      // map frame not tied to aggregate
      if(GraphUtil.isMap(bindingModel)) {
         return frameMap;
      }

      let arefs: ChartRef[] = GraphUtil.getModelRefs(bindingModel);
      // get visualframes from right aggregate for multistyle waterfall chart.
      let visualForAllAggr: boolean = GraphUtil.isAllChartAggregate(aggr) &&
         GraphUtil.isWaterfall(bindingModel);
      const hasMeasure: boolean = arefs.some(ref => !(<ChartAggregateRef> ref).discrete);

      for(let ref of arefs) {
         let aref: ChartAggregateRef = <ChartAggregateRef>ref;

         // @by gregm discrete measures do not need a color frame
         if(!aref.discrete && aref[propertyName]) {
            frameMap.push({name: aref.view,
               frame: visualForAllAggr ? aggr[propertyName] : aref[propertyName]});

            if(propertyName == "colorFrame" && GraphUtil.isWaterfall(bindingModel) && hasMeasure) {
               frameMap.push({name: "Summary",
                              summary: true,
                              frame: visualForAllAggr ? aggr.summaryColorFrame
                              : aref.summaryColorFrame});
            }
         }
      }

      return frameMap;
   }

   /**
    * Return visualframe array for no aesthetic ref.
    * @param bmodel current chart binding model.
    * @param aggr, current edit aggregate, only use for multistyle chart.
    */
   static getShapeFrames(bmodel: ChartBindingModel, aggr?: ChartAggregateRef,
      global?: boolean): any[]
   {
      let frameMap: any[] = new Array<any>();
      let arefs: ChartRef[] = GraphUtil.getModelRefs(bmodel);
            // get visualframes from right aggregate for multistyle waterfall chart.
      let visualForAllAggr: boolean = GraphUtil.isAllChartAggregate(aggr) &&
         GraphUtil.isWaterfall(bmodel);
      const hasMeasure: boolean = arefs.some(ref => !(<ChartAggregateRef> ref).discrete);

      for(let ref of arefs) {
         let aref: ChartAggregateRef = <ChartAggregateRef>ref;

         if(GraphUtil.checkChartType(bmodel, GraphTypes.CHART_BAR, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_BAR_STACK, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_3D_BAR, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_3D_BAR_STACK, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_PIE, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_DONUT, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_SUNBURST, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_TREEMAP, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_CIRCLE_PACKING, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_ICICLE, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_3D_PIE, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_WATERFALL, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_PARETO, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_CANDLE, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_BOXPLOT, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_MEKKO, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_FUNNEL, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_INTERVAL, aref, global))
         {
            let tmodel = new Visual.StaticTextureModel();

            if(!aref.textureFrame || aref.textureFrame.clazz != tmodel.clazz) {
               aref.textureFrame = tmodel;
            }

            if(!aref.discrete) {
               frameMap.push({name: aref.view,
                 frame: visualForAllAggr ? aggr.textureFrame : aref.textureFrame});

               if(GraphUtil.isWaterfall(bmodel) && hasMeasure) {
                  frameMap.push({name: "Summary",
                                 summary: true,
                    frame: visualForAllAggr ? aggr.summaryTextureFrame : aref.summaryTextureFrame});
               }
            }
         }
         else if(GraphUtil.checkChartType(bmodel, GraphTypes.CHART_POINT, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_POINT_STACK, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_RADAR, aref, global) && bmodel.pointLine)
         {
            let smodel = new Visual.StaticShapeModel();

            if(!aref.shapeFrame || aref.shapeFrame.clazz != smodel.clazz) {
               aref.shapeFrame = smodel;
            }

            if(!aref.discrete) {
               frameMap.push({name: aref.view, frame: aref.shapeFrame});
            }
         }
         else if(GraphUtil.checkChartType(bmodel, GraphTypes.CHART_LINE, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_LINE_STACK, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_STEP, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_STEP_STACK, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_JUMP, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_STEP_AREA, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_STEP_AREA_STACK, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_AREA, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_AREA_STACK, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_RADAR, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_FILL_RADAR, aref, global) ||
            GraphUtil.checkChartType(bmodel, GraphTypes.CHART_MAP, aref, global) &&
            bmodel.pathField)
         {
            let lmodel = new Visual.StaticLineModel();

            if(!aref.lineFrame || aref.lineFrame.clazz != lmodel.clazz) {
               aref.lineFrame = lmodel;
            }

            if(!aref.discrete) {
               frameMap.push({name: aref.view, frame: aref.lineFrame});
            }
         }
         else if(GraphUtil.checkChartType(bmodel, GraphTypes.CHART_STOCK, aref, global)) {
            // does not support shape model
         }
      }

      return frameMap;
   }

   private static checkChartType(bindingModel: ChartBindingModel,
      chartType: number, aref: ChartAggregateRef, global: boolean = true): boolean
   {
      if(!aref || global && !bindingModel) {
         return false;
      }

      let cType0: number = global ? bindingModel.chartType : aref.chartType;
      let rType0: number = global ? bindingModel.rtchartType : aref.rtchartType;

      if(cType0 == GraphTypes.CHART_AUTO) {
         return rType0 == chartType;
      }

      return cType0 == chartType;
   }

   static isAestheticType(type: string): boolean {
      return type == GraphUtil.COLOR_FIELD || type == GraphUtil.SHAPE_FIELD
         || type == GraphUtil.SIZE_FIELD || type == GraphUtil.TEXT_FIELD;
   }
}
