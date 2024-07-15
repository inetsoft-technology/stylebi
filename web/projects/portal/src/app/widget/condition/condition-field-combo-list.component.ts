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
import {
   AfterContentInit,
   Component,
   EventEmitter,
   Input,
   OnInit,
   OnChanges,
   Output,
   SimpleChanges
} from "@angular/core";
import { Tool } from "../../../../../shared/util/tool";
import { ColumnRef } from "../../binding/data/column-ref";
import { DataRef } from "../../common/data/data-ref";
import { GuiTool } from "../../common/util/gui-tool";
import { DataRefWrapper } from "../../common/data/data-ref-wrapper";
import { GroupRef } from "../../common/data/group-ref";
import { Range } from "../../common/data/range";
import { AggregateRef } from "../../common/data/aggregate-ref";

@Component({
   selector: "condition-field-combo-list",
   templateUrl: "condition-field-combo-list.component.html",
   styleUrls: ["condition-field-combo-list.component.scss"]
})
export class ConditionFieldComboListComponent implements OnInit, OnChanges, AfterContentInit {
   @Input() field: DataRef;
   @Input() listModel: DataRef[];
   @Input() grayedOutFields: DataRef[];
   @Input() showOriginalName: boolean;
   @Input() noneItem: DataRef;
   @Input() scrollTop: number;
   @Output() onSelectField = new EventEmitter<DataRef>();
   visibleIndexRange: Range;
   listContainerHeight = 0;
   private readonly LIST_CONTAINER_MAX_HEIGHT = 300;
   // LIST_ENTRY_HEIGHT = fontSize(--inet-font-size-base) * lineHeight(1.5)
   private readonly LIST_ENTRY_HEIGHT = 13 * 1.5;
   minWidth: number = 0;

   ngOnInit() {
      this.updateMinWidth();
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes["listModel"] != null) {
         this.updateMinWidth();
      }

      if(changes["scrollTop"] != null || changes["listModel"] != null) {
         this.listContainerHeight = this.calculateListContainerHeight();
         this.updateVisibleIndexRange();
      }
   }

   ngAfterContentInit(): void {
      this.updateVisibleIndexRange();
   }

   // set min width to avoid the dropdown width shifting while scrolling
   private updateMinWidth() {
      if(this.listModel) {
         const minWidth = this.listModel
            .map(ref => GuiTool.measureText(ref.view, "13px Roboto"))
            .reduce((a, b) => Math.max(a, b), 0);
         this.minWidth = Math.min(minWidth, 500);
      }
   }

   getTooltip(fld: DataRef): string {
      if(fld == null) {
         return "";
      }

      let tooltip: string = "";

      if(fld.classType == "GroupRef") {
         let columnRef: DataRef = (<GroupRef> fld).ref;
         tooltip = columnRef == null ? "" : this.showOriginalName ?
            ColumnRef.getTooltip(<GroupRef> fld) : columnRef.description;
      }
      else if(fld.classType == "AggregateRef") {
         let columnRef: DataRef = (<AggregateRef> fld).ref;
         tooltip = columnRef == null ? "" : this.showOriginalName ?
            ColumnRef.getTooltip(<AggregateRef> fld) : columnRef.description;
      }
      else if(fld.classType == "ColumnRef") {
         let columnDesc: string = (<ColumnRef> fld).description;
         tooltip = columnDesc == null ? "" : this.showOriginalName ?
            ColumnRef.getTooltip(<ColumnRef> fld) : columnDesc;
      }
      else if(fld.description) {
         tooltip = fld.description;
      }
      else {
         tooltip = fld.view;
      }

      return !tooltip ? "" : tooltip;
   }

   isGrayedOut(fld: DataRef): boolean {
      if(!this.grayedOutFields || this.grayedOutFields.length == 0
         || Tool.isEquals(fld, this.noneItem))
      {
         return false;
      }

      let name: string = fld.name.replace(/[:^]/g, ".");

      return this.grayedOutFields.find((field) => fld.name == field.name) != null
         || this.grayedOutFields.find((field) => name == field.name) != null;
   }

   isSelectedField(fld: DataRef): boolean {
      return Tool.isEquals(this.field, fld) ||
         fld && this.isSelectedField((fld as DataRefWrapper).dataRefModel);
   }

   selectField(fld: DataRef) {
      let nfld: DataRef = Tool.isEquals(fld, this.noneItem) ? null : fld;
      this.onSelectField.emit(nfld);
   }

   private calculateListContainerHeight(): number {
      if(this.listModel == null) {
         return 0;
      }

      return this.listModel.length * this.LIST_ENTRY_HEIGHT;
   }

   private updateVisibleIndexRange(): void {
      if(this.listModel == null) {
         return;
      }

      const maxVisibleEntries = this.LIST_CONTAINER_MAX_HEIGHT / this.LIST_ENTRY_HEIGHT;
      const beginIndexExact = this.scrollTop / this.LIST_ENTRY_HEIGHT;
      const beginIndexLowerBound = Math.floor(beginIndexExact);
      const endRowUpperBound = Math.ceil(beginIndexExact + maxVisibleEntries);
      const endIndex = Math.min(endRowUpperBound, this.listModel.length - 1);

      this.visibleIndexRange = new Range(beginIndexLowerBound, endIndex);
   }

   getVisibleListTop(): number {
      if(this.visibleIndexRange == null) {
         return 0;
      }

      return this.visibleIndexRange.start * this.LIST_ENTRY_HEIGHT;
   }
}
