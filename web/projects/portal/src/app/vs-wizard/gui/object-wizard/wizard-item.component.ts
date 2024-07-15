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
import { Input, Output, EventEmitter, ViewChild, Directive } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { AssetEntryHelper } from "../../../common/data/asset-entry-helper";
import { XSchema } from "../../../common/data/xschema";
import { DataRef } from "../../../common/data/data-ref";
import { FormatInfoModel } from "../../../common/data/format-info-model";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { ConvertColumnEvent } from "../../model/event/convert-column-event";
import { VSWizardConstants } from "../../model/vs-wizard-constants";
import { VSWizardBindingTreeService } from "../../services/vs-wizard-binding-tree.service";

const VS_WIZARD_CONVERT_COLUMN = "/events/vs/wizard/convertColumn";

@Directive()
export abstract class VSWizardItem<T extends DataRef> {
   @Input() dataRef: T;
   @Input() index: number;
   @Input() format: FormatInfoModel;
   @Input() moveUpEnabled: boolean = true;
   @Input() moveDownEnabled: boolean = true;
   @Output() deleteItem = new EventEmitter<number>();
   @Output() onUpdateFormat = new EventEmitter<string>();
   @Output() moveUp = new EventEmitter<null>();
   @Output() moveDown = new EventEmitter<null>();
   @ViewChild(FixedDropdownDirective) formatPane: FixedDropdownDirective;

   constructor(protected modalService: NgbModal,
               protected clientService: ViewsheetClientService,
               protected treeService: VSWizardBindingTreeService)
   {
   }

   get selectedNode(): TreeNodeModel {
      return this.treeService.findSelectedNode(this.dataRef.name);
   }

   get currentEntry(): AssetEntry {
      if(!!!this.selectedNode) {
         return null;
      }

      return this.selectedNode.data;
   }

   deleteColumn(): void {
      this.deleteItem.emit(this.index);
   }

   abstract getFullName(): string;

   abstract getDataType(): string;

   isDate(): boolean {
      return XSchema.isDateType(this.getDataType());
   }

   isNumber(): boolean {
      return XSchema.isNumericType(this.getDataType());
   }

   closeToApplyFormat(open: boolean): void {
      if(!open) {
         this.changeFormat();
      }
   }

   changeFormat(): void {
      this.onUpdateFormat.emit(this.getFullName());
      this.formatPane.close();
   }

   isDimension(): boolean {
      let cubeColumnType: number = this.treeService.getCubeColumnType(this.currentEntry);
      return cubeColumnType != null && (cubeColumnType & 1) == 0;
   }

   isConvertEnabled(): boolean {
      return this.isDimension() ? this.isConvertToMeasureVisible()
         : this.isConvertToDimensionVisible();
   }

   protected isConvertToMeasureVisible(): boolean {
      return this.isDimension();
   }

   protected isConvertToDimensionVisible(): boolean {
      return !this.isDimension() &&
         (this.currentEntry ? this.currentEntry.properties["basedOnDetail"] != "false" : true);
   }

   convert(): void {
      if(this.isDimension() && this.isConvertToMeasureVisible()) {
         this.convertToMeasure();
      }
      else if(!this.isDimension() && this.isConvertToDimensionVisible()){
         this.convertToDimension();
      }
      else {
         this.treeService.unSupportedException(this.modalService);
      }
   }

   convertBtnTitle(): string {
      let title: string = null;

      if(this.isDimension() && this.isConvertToMeasureVisible()) {
         title = "_#(js:Convert To Measure)";
      }
      else if(!this.isDimension() && this.isConvertToDimensionVisible()){
         title = "_#(js:Convert To Dimension)";
      }

      return title;
   }

   protected convertToDimension(): void {
      this.convertRef(VSWizardConstants.CONVERT_TO_DIMENSION);
   }

   protected convertToMeasure(): void {
      this.convertRef(VSWizardConstants.CONVERT_TO_MEASURE);
   }

   protected convertRef(convertType: number): void {
      let table: string = this.treeService.getTableName(this.currentEntry);
      let event: ConvertColumnEvent = new ConvertColumnEvent(this.currentEntry,
         this.getRefNamesForConversion(convertType), convertType, table, null, false);
      this.clientService.sendEvent(VS_WIZARD_CONVERT_COLUMN, event);
   }

   private getRefNamesForConversion(convertType: number): string[] {
      let table: string = this.treeService.getTableName(this.currentEntry);
      let refNames: string[] = [];

      if(AssetEntryHelper.isColumn(this.currentEntry)
         && this.treeService.getTableName(this.currentEntry) === table
         && ((convertType === VSWizardConstants.CONVERT_TO_DIMENSION && this.isConvertToDimensionVisible())
            || (convertType === VSWizardConstants.CONVERT_TO_MEASURE && this.isConvertToMeasureVisible())))
      {
         refNames.push(this.treeService.getColumnValue(this.currentEntry));
      }

      return refNames;
   }
}
