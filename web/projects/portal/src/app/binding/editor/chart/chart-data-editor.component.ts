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
import {
   ChangeDetectorRef,
   Component,
   ElementRef,
   Input,
   QueryList,
   ViewChildren
} from "@angular/core";
import { DragEvent } from "../../../common/data/drag-event";
import { DndService } from "../../../common/dnd/dnd.service";
import { GraphTypes } from "../../../common/graph-types";
import { ChartBindingModel } from "../../data/chart/chart-binding-model";
import { BindingService } from "../../services/binding.service";
import { ChartEditorService } from "../../services/chart/chart-editor.service";
import { DataEditor } from "../data-editor";

@Component({
   selector: "chart-data-editor",
   templateUrl: "chart-data-editor.component.html",
   styleUrls: ["../data-editor.component.scss"]
})
export class ChartDataEditor extends DataEditor {
   @Input() fieldType: string;
   @Input() refs: Object[];
   @Input() multiStyles: boolean;
   @Input() bindingModel: ChartBindingModel;
   @Input() grayedOutValues: string[] = [];
   @ViewChildren("fieldComponents") fieldComponents: QueryList<ElementRef>;

   constructor(protected dservice: DndService,
               private editorService: ChartEditorService,
               protected bindingService: BindingService,
               protected changeRef: ChangeDetectorRef)
   {
      super(dservice, bindingService, changeRef);
   }

   protected isDropAccept(): boolean {
      return this.editorService.isDropPaneAccept(this.dservice, this.bindingModel, this.fieldType);
   }

   dragOver(event: DragEvent): void {
      event.preventDefault();
      this.dservice.setDragOverStyle(event, this.isDropAccept());
   }

   protected getFieldComponents(): QueryList<ElementRef> {
      return this.fieldComponents;
   }

   protected checkDropValid(): boolean {
      if(!this.isDropAccept()) {
         return false;
      }

      return this.editorService.getDNDType(this.fieldType) >= 0;
   }

   protected getDropType(): string {
      return this.editorService.getDNDType(this.fieldType) + "";
   }

   isPrimaryField(): boolean {
      if(this.bindingModel) {
         if(GraphTypes.isPie(this.bindingModel.chartType)) {
            return this.fieldType == "yfields";
         }
         else if(GraphTypes.isRadar(this.bindingModel.chartType)) {
            return this.fieldType == "yfields";
         }
         else if(GraphTypes.isStock(this.bindingModel.chartType) ||
                 GraphTypes.isCandle(this.bindingModel.chartType))
         {
            return this.fieldType == "xfields";
         }
         else if(GraphTypes.isGeo(this.bindingModel.chartType)) {
            return this.fieldType == "geofields";
         }
         else if(GraphTypes.isTreemap(this.bindingModel.chartType)) {
            return this.fieldType == "groupfields";
         }
         else if(GraphTypes.isMekko(this.bindingModel.chartType)) {
            return this.fieldType == "groupfields" || this.fieldType == "xfields" ||
               this.fieldType == "yfields";
         }
      }

      return this.fieldType == "xfields" || this.fieldType == "yfields";
   }

   get displayLabel(): string {
      if(this.refs && this.refs.length > 0 ||
         (GraphTypes.isGeo(this.bindingModel ? this.bindingModel.chartType : null) &&
            (this.fieldType == "xfields" || this.fieldType == "yfields") &&
            this.bindingModel.geoFields.length > 0))
      {
         return "";
      }

      if((this.fieldType == "xfields" || this.fieldType == "yfields") &&
         (GraphTypes.isTreemap(this.bindingModel.chartType) ||
          GraphTypes.isRelation(this.bindingModel.chartType) ||
          GraphTypes.isGantt(this.bindingModel.chartType)))
      {
         return "_#(js:common.DataEditor.dragDimension)";
      }

      if(this.fieldType == "xfields" && GraphTypes.isRadar(this.bindingModel.chartType)) {
         return "_#(js:common.DataEditor.dragDimension)";
      }

      if(this.fieldType == "yfields" && GraphTypes.isFunnel(this.bindingModel.chartType)) {
         return "_#(js:common.DataEditor.dragDimension)";
      }

      if(GraphTypes.isMekko(this.bindingModel.chartType)) {
         if(this.fieldType == "xfields") {
            return "_#(js:common.DataEditor.dragDimension)";
         }
         else if(this.fieldType == "yfields") {
            return "_#(js:common.DataEditor.dragMeasure)";
         }
      }

      if(GraphTypes.isGeo(this.bindingModel ? this.bindingModel.chartType : null)) {
         if(this.fieldType == "xfields") {
            return "_#(js:common.DataEditor.dragDimensionOrLongitude)";
         }
         else if(this.fieldType == "yfields") {
            return "_#(js:common.DataEditor.dragDimensionOrLatitude)";
         }
      }

      if(this.fieldType == "xfields" || this.fieldType == "yfields") {
         if(GraphTypes.isStock(this.bindingModel.chartType) ||
            GraphTypes.isCandle(this.bindingModel.chartType))
         {
            return "_#(js:common.DataEditor.dragDimension)";
         }

         return "_#(js:common.DataEditor.dragColumns)";
      }

      if(this.fieldType == "geofields") {
         return "_#(js:Geographic)";
      }

      if(this.fieldType == "groupfields") {
         if(GraphTypes.isTreemap(this.bindingModel.chartType)) {
            return "_#(js:common.DataEditor.treeFields)";
         }
         else if(GraphTypes.isMekko(this.bindingModel.chartType)) {
            return "_#(js:common.DataEditor.mekkoFields)";
         }

         return "_#(js:common.DataEditor.groupFields)";
      }

      return "";
   }

   convert(event: any): void {
      this.editorService.convert(event.name, event.type, this.bindingModel);
   }

   public dragOverField(event: DragEvent, idx: number, replaceField: boolean): void {
      // only replace for y field
      if(GraphTypes.isMekko(this.bindingModel.chartType)) {
         if(!replaceField && this.refs.length > 0) {
            replaceField = true;
            idx = 0;
         }
      }

      super.dragOverField(event, idx, replaceField);
   }
}
