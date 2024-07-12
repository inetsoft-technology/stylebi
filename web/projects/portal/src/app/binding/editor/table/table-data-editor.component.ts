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

import {
   ChangeDetectorRef,
   Component,
   ElementRef,
   EventEmitter,
   Input,
   Output,
   QueryList,
   ViewChildren
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { DataRefType } from "../../../common/data/data-ref-type";
import { TableTransfer } from "../../../common/data/dnd-transfer";
import { DragEvent } from "../../../common/data/drag-event";
import {
   DndDropOption,
   DndService,
   OptionLevel,
   OptionType
} from "../../../common/dnd/dnd.service";
import { UIContextService } from "../../../common/services/ui-context.service";
import { ComponentTool } from "../../../common/util/component-tool";
import { TableBindingModel } from "../../data/table/table-binding-model";
import { BindingService } from "../../services/binding.service";
import { DataEditor } from "../data-editor";
import { AssetEntryHelper } from "../../../common/data/asset-entry-helper";
import { Tool } from "../../../../../../shared/util/tool";

@Component({
   selector: "table-data-editor",
   templateUrl: "table-data-editor.component.html",
   styleUrls: ["../data-editor.component.scss"]
})
export class TableDataEditor extends DataEditor {
   @Input() fieldType: string;
   @Input() refs: Object[];
   @Input() groupNum: number;
   @Input() bindingModel: TableBindingModel;
   @ViewChildren("fieldComponents") fieldComponents: QueryList<ElementRef>;
   @Output() onPopUpWarning: EventEmitter<any> = new EventEmitter<any>();

   constructor(protected dservice: DndService,
               protected bindingService: BindingService,
               protected changeRef: ChangeDetectorRef,
               private uiContextService: UIContextService,
               private modalService: NgbModal)
   {
      super(dservice, bindingService, changeRef);
   }

   protected isDropAccept() {
      let transfer: any = this.dservice.getTransfer();

      if(transfer == null) {
         return true;
      }

      let entryValue: string = transfer.column;
      let dropType: string = this.fieldType;
      let dimArr: string[] = ["rows", "cols"];
      let meaArr: string[] = ["aggregates"];

      //For vs table, do not reject dnd.
      if(dropType == "details") {
         return !this.dservice.isCalcAggregate();
      }

      // If drag action is happened on the binding tree.
      if(entryValue != null && entryValue != "") {
         let entry: AssetEntry = transfer.column[0];
         let tableName = transfer.tableName;
         let isCube = tableName != null && tableName.startsWith("___inetsoft_cube_");

         // Feature #4031 vscrosstab accept all type column.
         if(this.objectType == "vscrosstab" && !isCube &&
            !this.isVSAssemblyBinding(tableName) && !this.dservice.isCalcAggregate())
         {
            return true;
         }

         // For vs table, should support add all type columns.
         let isDim: boolean = this.isDimension(entry);

         if(isDim && this.isValueInArray(dropType, dimArr)) {
            return true;
         }

         if(!isDim && this.isValueInArray(dropType, meaArr)) {
            return true;
         }
      }
      // If drag action is happened on the table data editor.
      else {
         let source = !!this.bindingModel && !!this.bindingModel.source ?
            this.bindingModel.source.source : null;

         if(this.objectType == "vscrosstab"  && !this.dservice.isCalcAggregate() &&
            !this.isCalcAggregateTransfer(transfer) && !this.isVSAssemblyBinding(source))
         {
            return true;
         }

         let dsource: TableTransfer = <TableTransfer> transfer.dragSource;
         let dragType: string = dsource.dragType;

         // If drag type and drop type all from dimension array.
         if(this.isValueInArray(dragType, dimArr) &&
            this.isValueInArray(dropType, dimArr))
         {
            return true;
         }
         // Drag type and drop type all from measure array.
         else if(this.isValueInArray(dragType, meaArr) &&
            this.isValueInArray(dropType, meaArr))
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Checks whether the table name starts with "__vs_assembly__"
    */
   private isVSAssemblyBinding(tableName: string): boolean {
      return tableName != null && tableName.startsWith("__vs_assembly__");
   }

   private isValueInArray(val: string, values: string[]) {
      for(let i = 0; i < values.length; i++) {
         if(values[i] == val) {
            return true;
         }
      }

      return false;
   }

   private isDimension(entry: AssetEntry): boolean {
      let refType: string = entry.properties["refType"];
      // 0 means AbstractDataRef.NONE
      let rtype: number = refType == null ? 0 : parseInt(refType, 10);
      // AssetEntry.CUBE_COL_TYPE:String = "cube.column.type";
      let cubeTypeStr: string = entry.properties["cube.column.type"];
      let ctype: number = cubeTypeStr == null ? 0 : parseInt(cubeTypeStr, 10);
      // 1 means AbstractDataRef.DIMENSION
      return (rtype & 1) != 0 || (ctype & 1) == 0;
   }

   private isCalcAggregateTransfer(transfer: any): boolean {
      if(!!transfer.dragSource) {
         let dSource = transfer.dragSource;

         if(dSource.dragType == "aggregates" &&
            dSource.dragIndex < this.bindingModel.aggregates.length)
         {
            return this.bindingModel.aggregates[dSource.dragIndex].refType == DataRefType.AGG_CALC;
         }
      }

      return false;
   }

   public onDrop(event: DragEvent): void {
      event.preventDefault();

      if(!!this.bindingModel && this.bindingModel.embedded &&
         this.dservice.containsCalc())
      {
         this.dragLeave(event);
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
            "_#(js:common.viewsheet.embeddedTable.bindingCalc)");
         this.clearActiveIdx();

         return;
      }
      else if(!!this.bindingModel && this.bindingModel.embedded &&
         !this.dservice.isAllEmbeddedColumn(event))
      {
         this.dragLeave(event);
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
            "_#(js:common.viewsheet.embeddedTable.binding)");
         this.clearActiveIdx();
         return;
      }

      super.onDrop(event);
   }

   /**
    * Get property name from the entity, if the entity is a cube member, should
    * use entity + attribute as the name.
    */
   private getColumnValue(entry: AssetEntry): string {
      if(entry == null) {
         return "";
      }

      let properties: any = entry.properties;
      let cvalue: string = AssetEntryHelper.getEntryName(entry);
      let attribute: string = properties["attribute"];

      // normal chart entry not set entity and attribute properties,
      // cube entry set, the name should use entity + attribute
      if(attribute != null) {
         let entity: string = properties["entity"];
         cvalue = (entity != null ?  entity + "." : "") + attribute;
      }

      return cvalue;
   }

   protected checkDropValid(): boolean {
      return this.isDropAccept();
   }

   protected getFieldComponents(): QueryList<ElementRef> {
      return this.fieldComponents;
   }

   protected getDropType(): string {
      return this.fieldType;
   }
}
