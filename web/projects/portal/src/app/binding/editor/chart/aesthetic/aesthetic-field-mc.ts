/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import { Input, Output, OnInit, OnChanges, SimpleChanges, ViewChildren, QueryList,
         Directive, EventEmitter } from "@angular/core";
import { ConditionOperation } from "../../../../common/data/condition/condition-operation";
import { DragEvent } from "../../../../common/data/drag-event";
import { StaticShapeModel, VisualFrameModel } from "../../../../common/data/visual-frame-model";
import { DndService } from "../../../../common/dnd/dnd.service";
import { GraphTypes } from "../../../../common/graph-types";
import { Tool } from "../../../../../../../shared/util/tool";
import { XConstants } from "../../../../common/util/xconstants";
import { AestheticInfo } from "../../../data/chart/aesthetic-info";
import { ChartAggregateRef } from "../../../data/chart/chart-aggregate-ref";
import { ChartBindingModel } from "../../../data/chart/chart-binding-model";
import { ChartDimensionRef } from "../../../data/chart/chart-dimension-ref";
import { ChartRef } from "../../../../common/data/chart-ref";
import { ChartAestheticDropTarget } from "../../../data/chart/chart-transfer";
import { ObjectType } from "../../../../common/data/dnd-transfer";
import { ChartEditorService } from "../../../services/chart/chart-editor.service";
import { GraphUtil } from "../../../util/graph-util";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { FixedDropdownDirective } from "../../../../widget/fixed-dropdown/fixed-dropdown.directive";

@Directive()
export abstract class AestheticFieldMc implements OnInit, OnChanges {
   @Input() vsId: string;
   @Input() assetId: string;
   @Input() assemblyName: string;
   @Input() objectType: string;
   @Input() aggr: ChartAggregateRef;
   @Input() bindingModel: ChartBindingModel;
   @Input() grayedOutValues: string[] = [];
   @Input() targetField: string;
   @Input() targetFrame: string;
   @Output() onChange = new EventEmitter<any>();
   @ViewChildren(FixedDropdownDirective) dropdowns: QueryList<FixedDropdownDirective>;

   _isEnabled: boolean;
   _isEditEnabled: boolean;
   _isMixed: boolean;
   field: AestheticInfo;
   frames: VisualFrameModel[];
   framesMap: any[];
   editPaneId: string;
   protected oframes: string;
   dialogOpened: boolean = false;

   get frameColor(): string {
      return !!this.frames && !!this.frames.length && !!this.frames[0] ?
         (this.frames[0] as any).color : null;
   }

   get shapeFrame(): StaticShapeModel {
      return !!this.frames && !!this.frames.length && !!this.frames[0] ?
         (this.frames[0] as StaticShapeModel) : null;
   }

   constructor(public editorService: ChartEditorService, public dservice: DndService,
               protected uiContextService: UIContextService)
   {
   }

   get isVS(): boolean {
      return this.uiContextService.isVS();
   }

   ngOnInit() {
      this.ngOnChanges(null);
   }

   ngOnChanges(changes: SimpleChanges) {
      this._isEnabled = this.isEnabled();
      this._isMixed = this.isMixed();
      this.field = this.getField();
      this.frames = this.getFrames();
      this.framesMap = this.getFramesMap();
      this.editPaneId = this.getEditPaneId();
      this._isEditEnabled = this.isEditEnabled();
   }

   abstract syncAllChartAggregateInfo(): void;
   abstract setAestheticRef(ref: AestheticInfo): void;
   abstract getFieldType(): string;
   protected abstract getField(): AestheticInfo;

   getChartType(): number {
      return GraphUtil.getChartType(this.aggr, this.bindingModel);
   }

   protected getFrames(): VisualFrameModel[] {
      return new Array<VisualFrameModel>();
   }

   protected getBindingRefs(): ChartRef[] {
      return GraphUtil.getAllBindingRefs(this.bindingModel, false);
   }

   /**
    * get name : frame map for combined pane.
    */
   protected getFramesMap(): any[] {
      let propertyName: string = null;

      switch(this.getFieldType()) {
         case GraphUtil.COLOR_FIELD:
            propertyName = "colorFrame"; break;
         case GraphUtil.SHAPE_FIELD:
            propertyName = "shapeFrame"; break;
         case GraphUtil.SIZE_FIELD:
            propertyName = "sizeFrame"; break;
         case GraphUtil.TEXT_FIELD:
            propertyName = "textFrame"; break;
         default:
            propertyName = null; break;
      }

      return GraphUtil.getVisualFrames(propertyName, this.bindingModel, this.aggr);
   }

   protected getEditPaneId(): string {
      return "";
   }

   protected isEditEnabled(): boolean {
      if(!this.isEnabled() || this.isMixedField()) {
         return false;
      }

      if(!this.field && this.getBindingRefs().length == 0) {
         return false;
      }

      return true;
   }

   /**
    * Check if the pane is enabled.
    */
   protected isEnabled(): boolean {
      if(!this.bindingModel) {
         return false;
      }

      let chartType: number = this.getChartType();

      if(GraphUtil.isMultiAesthetic(this.bindingModel) &&
         GraphUtil.isAllChartAggregate(this.aggr) &&
         GraphUtil.getAestheticAggregateRefs(this.bindingModel, true).length == 0)
      {
         return false;
      }

      if(chartType == GraphTypes.CHART_MEKKO) {
         return this.getFieldType() != GraphUtil.SIZE_FIELD;
      }

      if(chartType == GraphTypes.CHART_STOCK) {
         return this.getFieldType() == GraphUtil.COLOR_FIELD ||
            this.getFieldType() == GraphUtil.TEXT_FIELD;
      }

      return true;
   }

   protected isMixed(): boolean {
      return this.isMixedField();
   }

   protected isMixedField(): boolean {
      if(!GraphUtil.isAllChartAggregate(this.aggr)) {
         return false;
      }

      switch(this.getFieldType()) {
         case GraphUtil.COLOR_FIELD:
            return this.isMixedValue("colorField");
         case GraphUtil.SHAPE_FIELD:
            return this.isMixedValue("shapeField");
         case GraphUtil.SIZE_FIELD:
            return this.isMixedValue("sizeField");
         case GraphUtil.TEXT_FIELD:
            return this.isMixedValue("textField");
         default:
            return false;
      }
   }

   /**
    * Check if the property contains different values from aggregates.
    * @param propertyName, 'colorField', 'colorFrame'...
    */
   protected isMixedValue(propertyName: string): boolean {
      if(!this.bindingModel || !GraphUtil.isMultiAesthetic(this.bindingModel) ||
         !GraphUtil.isAllChartAggregate(this.aggr))
      {
         return false;
      }

      let aggrs: ChartRef[] = GraphUtil.getAestheticAggregateRefs(this.bindingModel)
         .filter(value => !(<ChartAggregateRef> value).discrete);
      let obj: any = null;
      const equals: (v1, v2) => boolean = (v1, v2) => {
         if(propertyName == "rtchartType") {
            // ignore stack difference
            return (v1 | 0x20) == (v2 | 0x20);
         }
         else {
            return Tool.isEquals(v1, v2);
         }
      };

      for(let i in aggrs) {
         if(aggrs.hasOwnProperty(i)) {
            let aggr: ChartAggregateRef = <ChartAggregateRef> aggrs[i];
            let obj2: any = aggr[propertyName];
            let isAestheticRef: boolean =
               GraphUtil.isAestheticRef(obj) && GraphUtil.isAestheticRef(obj2);

            if(parseInt(i, 10) == 0) {
               obj = obj2;
            }
            else if(isAestheticRef &&
                    !this.isEqualAestheticInfo(<AestheticInfo> obj, <AestheticInfo> obj2) ||
                    !isAestheticRef && !equals(obj, obj2))
            {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Check if the two aestheticinfos are considered as equals in aesthetic gui.
    */
   private isEqualAestheticInfo(aref: AestheticInfo, aref2: AestheticInfo): boolean {
      if(!this.isEqualVisualFrame(aref.frame, aref2.frame)) {
         return false;
      }

      let ref1: ChartRef = aref.dataInfo;
      let ref2: ChartRef = aref2.dataInfo;

      if(GraphUtil.isDimensionRef(ref1) && GraphUtil.isDimensionRef(ref2)) {
         let dim1: ChartDimensionRef = <ChartDimensionRef>ref1;
         let dim2: ChartDimensionRef = <ChartDimensionRef>ref2;

         return this.isEqualDimension(dim1, dim2);
      }

      let copyaref: AestheticInfo = Tool.clone(aref);
      let copyaref2: AestheticInfo = Tool.clone(aref2);

      if(copyaref.dataInfo) {
         copyaref.dataInfo["original"] = null;
      }

      if(copyaref2.dataInfo) {
         copyaref2.dataInfo["original"] = null;
      }

      return Tool.isEquals(copyaref, copyaref2);
   }

   /**
    * Check if the two visualframes are considered as equals in aesthetic gui.
    */
   private isEqualVisualFrame(obj1: VisualFrameModel, obj2: VisualFrameModel): boolean {
      return Tool.isEquals(obj1, obj2);
   }

   /**
    * Check if the two dimensioninfos are considered as equals in aesthetic gui.
    */
   private isEqualDimension(obj1: ChartDimensionRef, obj2: ChartDimensionRef): boolean {
      if(obj1 == null && obj2 == null) {
         return true;
      }

      if(obj1 && !obj2 || !obj1 && obj2) {
         return false;
      }

      if(obj1.columnValue != obj2.columnValue ||
         obj1.fullName != obj2.fullName ||
         obj1.timeSeries && obj2.timeSeries &&
         obj1.dateLevel != obj2.dateLevel ||
         obj1.rankingOption != obj2.rankingOption ||
         obj1.order != obj2.order)
      {
         return false;
      }

      if((obj1.rankingOption == ConditionOperation.TOP_N + "" ||
         obj1.rankingOption == ConditionOperation.BOTTOM_N + "") &&
         (obj1.others != obj2.others ||
            obj1.groupOthers != obj2.groupOthers ||
            obj1.rankingN != obj2.rankingN ||
            obj1.rankingCol != obj2.rankingCol)) {
         return false;
      }

      if(!Tool.isEquals(obj1.namedGroupInfo, obj2.namedGroupInfo)) {
         return false;
      }

      if(!Tool.isEquals(obj1.manualOrder, obj2.manualOrder)) {
         return false;
      }

      if(obj1.namedGroupInfo && obj1.others != obj2.others) {
         return false;
      }

      if(obj1.order == XConstants.SORT_VALUE_ASC || obj1.order == XConstants.SORT_VALUE_DESC) {
         return obj1.sortByCol == obj2.sortByCol;
      }

      return true;
   }

   doSubmit(isOpen: boolean) {
      if(!isOpen) {
         if(GraphUtil.isAllChartAggregate(this.aggr)) {
            this.syncAllChartAggregateInfo();
         }

         this.onChange.emit(this.frames);
         this.editorService.changeChartAesthetic(this.getFieldType());
      }
   }

   public dragFieldMCComplete = (index: number) => {
      this.setAestheticRef(null);
   };

   public dragOver(event: DragEvent): void {
      event.preventDefault();
      this.dservice.setDragOverStyle(event, this.isDropPaneAccept());
   }

   public drop(event: any) {
      event.preventDefault();

      if(!this.isDropPaneAccept()) {
         return;
      }

      let dropType: number = this.editorService.getDNDType(this.getFieldType());

      if(dropType < 0) {
         return;
      }

      let dtarget: ChartAestheticDropTarget = new ChartAestheticDropTarget(
         dropType + "", false, this.aggr, Tool.byteEncode(this.assemblyName, false),
            <ObjectType> this.objectType, this.targetField);
      this.dservice.processOnDrop(event, dtarget);
   }

   convert(event: any): void {
      if(GraphUtil.isAllChartAggregate(this.aggr)) {
         this.syncAllChartAggregateInfo();
      }

      this.editorService.convert(event.name, event.type, this.bindingModel);
   }

   openChanged(open: boolean): void {
      if(open) {
         this.oframes = JSON.stringify(this.frames);
      }
      else {
         this.dropdowns.forEach(d => d.close());
         this.submitIfChanged();
      }
   }

   submitIfChanged() {
      if(this.oframes != JSON.stringify(this.frames)) {
         this.oframes = JSON.stringify(this.frames);
         this.doSubmit(false);
      }
   }

   protected isDropPaneAccept(): boolean {
      let accept = this._isEnabled && this.editorService.isDropPaneAccept(
         this.dservice, this.bindingModel, this.getFieldType(), this.getChartType());

      if(accept && GraphUtil.isAllChartAggregate(this.aggr)) {
         accept = this.bindingModel.xfields.concat(this.bindingModel.yfields)
            .filter(f => GraphUtil.isAggregateRef(f))
            .every(f => this.editorService.isDropPaneAccept(this.dservice, this.bindingModel,
               this.getFieldType(), (<ChartAggregateRef> f).chartType));
      }

      return accept;
   }
}
