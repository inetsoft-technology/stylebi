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
import { Component, Input, OnInit, HostListener } from "@angular/core";
import { HierarchyPropertyPaneModel } from "../../model/hierarchy-property-pane-model";
import { VSDimensionMemberModel } from "../../model/vs-dimension-member-model";
import { OutputColumnRefModel } from "../../model/output-column-ref-model";
import { DragEvent } from "../../../common/data/drag-event";
import { XSchema } from "../../../common/data/xschema";
import { VSDimensionModel } from "../../model/vs-dimension-model";
import { Tool } from "../../../../../../shared/util/tool";
import { DateRangeRef } from "../../../common/util/date-range-ref";

interface DragSourceEl {
   sourceEl?: any;
   sourceRef?: VSDimensionMemberModel;
   sourceList?: any[];
   sourceIndex?: number; // item index in sourceList
   sourceDimensionIndex?: number; // dimension index of model dimensions if source is model dims
   dropList?: VSDimensionMemberModel[]; // drop into dimension members (temp during drag)
   dropIndex?: number; // drop items in the dropList
}

@Component({
   selector: "hierarchy-property-pane",
   templateUrl: "hierarchy-property-pane.component.html",
   styleUrls: ["./hierarchy-property-pane.component.scss"]
})
export class HierarchyPropertyPane implements OnInit {
   @Input() model: HierarchyPropertyPaneModel;
   localColumnList: OutputColumnRefModel[] = [];
   selectedColumn: OutputColumnRefModel | VSDimensionMemberModel = null;
   dragSourceEl: DragSourceEl = {};
   private inItem: boolean = false;
   private preventDateDrop: boolean = false;
   private isIE = false;

   ngOnInit(): void {
      this.initLocalColumnList();
      this.isIE = window.navigator.userAgent.toLowerCase().indexOf("trident") != -1;
   }

   private initLocalColumnList() {
      this.localColumnList = [];

      for(let column of this.model.columnList) {
         let found: boolean = false;

         if(!this.isDateType(column.dataType)) {
            for(let dim of this.model.dimensions) {
               for(let member of dim.members) {
                  if(member.dataRef.name == column.name) {
                     found = true;
                  }
               }
            }
         }

         if(!found) {
            this.localColumnList.push(column);
         }
      }
   }

   mouseDownSelect(column: OutputColumnRefModel | VSDimensionMemberModel): void {
      this.selectedColumn = column;
   }

   allowDrop(evt: any): void {
      if(!this.preventDateDrop) {
         evt.preventDefault();
      }
   }

   /**
    *  for angular 9. __ngContext__ maybe have circular reference
    */
   ignoreNgContextParseJson(key: any, value: any): any {
      if(key == "__ngContext__") {
         return;
      }

      return value;
   }

   // drag from left column list
   columnDragStart(evt: DragEvent, columnRef: OutputColumnRefModel, index: number): void {
      this.dragSourceEl.sourceEl = evt.target;
      let option: number = DateRangeRef.YEAR_INTERVAL;

      if(columnRef.dataType == XSchema.TIME) {
         option = DateRangeRef.HOUR_INTERVAL;
      }

      this.dragSourceEl.sourceRef = <VSDimensionMemberModel> {
         option: option,
         dataRef: Tool.clone(columnRef)
      };
      this.dragSourceEl.sourceList = this.localColumnList;
      this.dragSourceEl.sourceIndex = index;
      this.dragSourceEl.sourceDimensionIndex = null;
      this.dragSourceEl.dropList = null;
      this.dragSourceEl.dropIndex = null;

      // this is not used by required by FF
      if(!this.isIE) {
         evt.dataTransfer.setData("data",
            JSON.stringify(this.dragSourceEl, this.ignoreNgContextParseJson));
      }
   }

   // if drop back to column list a column dragged from column list, make sure it's
   // not added to the target
   private removeUndroppedColumn() {
      if(this.dragSourceEl && this.dragSourceEl.dropList) {
         const dropIdx = this.dragSourceEl.dropList
            .findIndex(v => v.dataRef.name == this.dragSourceEl.sourceRef.dataRef.name);

         if(dropIdx >= 0 && dropIdx == this.dragSourceEl.dropIndex) {
            this.dragSourceEl.dropList.splice(dropIdx, 1);
         }
      }
   }

   // drop on left column list
   columnDrop(evt: any): void {
      evt.preventDefault();
      this.removeDuplicateMembers();

      if(this.dragSourceEl.sourceEl.parentNode != evt.target.parentNode) {
         this.dragSourceEl.sourceList.splice(this.dragSourceEl.sourceIndex, 1);

         this.removeUndroppedColumn();
         this.removeEmptyDimensions();

         if(!this.isDateType(this.dragSourceEl.sourceRef.dataRef.dataType) &&
            !this.localColumnList.find(v => v.name == this.dragSourceEl.sourceRef.dataRef.name))
         {
            this.localColumnList.splice(this.dragSourceEl.sourceIndex, 0,
                                        this.dragSourceEl.sourceRef.dataRef);
         }
      }
   }

   private removeEmptyDimensions(): void {
      for(let i: number = this.model.dimensions.length - 1; i >= 0; i--) {
         if(this.model.dimensions[i].members.length == 0) {
            this.model.dimensions.splice(i, 1);
         }
      }
   }

   // drag from right dimension tree
   contentDragStart(evt: any, member: VSDimensionMemberModel, dimension: VSDimensionModel,
                    sourceDimensionIndex: number, sourceIndex: number): void
   {
      this.dragSourceEl.sourceEl = evt.target;
      this.dragSourceEl.sourceRef = Tool.clone(member);
      this.dragSourceEl.sourceList = dimension.members;
      this.dragSourceEl.sourceIndex = sourceIndex;
      this.dragSourceEl.sourceDimensionIndex = sourceDimensionIndex;
      this.dragSourceEl.dropList = null;
      this.dragSourceEl.dropIndex = null;

      if(!this.isIE) {
         evt.dataTransfer.setData("data", JSON.stringify(this.dragSourceEl,
            this.ignoreNgContextParseJson));
      }
   }

   // enter into right dimension tree item
   contentDragEnter(evt: any, dimension: VSDimensionModel, sourceDimensionIndex: number,
                    memberIndex: number): void
   {
      if(this.isDragAccepted(evt, dimension)) {
         this.inItem = true;

         if(this.isDateType(dimension.members[memberIndex].dataRef.dataType)
            && !this.isDateDroppable(this.dragSourceEl.sourceRef, memberIndex, dimension.members)
            && this.dragSourceEl.sourceEl.className.indexOf("hierarchy-content-item") < 0)
         {
            return;
         }

         // if dragged from column to dims, setup the dropList
         // (remove from source and add to dropList)
         if(this.dragSourceEl.dropList == null) {
            this.dragSourceEl.dropList = dimension.members;
            this.dragSourceEl.dropIndex = memberIndex;

            if(this.isDateType(this.dragSourceEl.sourceRef.dataRef.dataType) &&
               this.dragSourceEl.sourceList == this.localColumnList)
            {
               this.setDateLevel(this.dragSourceEl.sourceRef, memberIndex, dimension.members);
            }

            dimension.members.splice(memberIndex, 0, Tool.clone(this.dragSourceEl.sourceRef));
         }

         // dnd to same item, ignore
         if(this.dragSourceEl.dropList == dimension.members &&
            this.dragSourceEl.dropIndex == memberIndex)
         {
            return;
         }

         // drop from source
         const currRef = this.dragSourceEl.dropList[this.dragSourceEl.dropIndex];
         this.dragSourceEl.dropList.splice(this.dragSourceEl.dropIndex, 1);
         let exist: boolean = false;

         if(this.dragSourceEl.sourceList == this.localColumnList) {
            if(this.isDateType(this.dragSourceEl.sourceRef.dataRef.dataType)) {
               this.setDateLevel(this.dragSourceEl.sourceRef, memberIndex, dimension.members);
            }

            exist = dimension.members.some(member =>
               Tool.isEquals(member, this.dragSourceEl.sourceRef));
         }

         // insert to members list
         if(!exist) {
            dimension.members.splice(memberIndex, 0, Tool.clone(this.dragSourceEl.sourceRef));
            this.dragSourceEl.dropList = dimension.members;
            this.dragSourceEl.dropIndex = memberIndex;
         }
         // revert the delete from previous call if not adding the new member
         else {
            this.dragSourceEl.dropList.splice(this.dragSourceEl.dropIndex, 0, currRef);
         }
      }
   }

   // set the dim date level in member list
   private setDateLevel(sourceRef: VSDimensionMemberModel, memberIndex: number,
                        members: VSDimensionMemberModel[])
   {
      if(sourceRef.dataRef.dataType == XSchema.TIME) {
         sourceRef.option = DateRangeRef.HOUR_INTERVAL;
      }
      else {
         sourceRef.option = DateRangeRef.YEAR_INTERVAL;
      }

      for(let i = memberIndex - 1; i >= 0; i--) {
         if(Tool.isEquals(members[i].dataRef, sourceRef.dataRef)) {
            sourceRef.option = DateRangeRef.getNextDateLevel(
               members[i].option, members[i].dataRef.dataType);
            break;
         }
      }
   }

   private isDateDroppable(sourceRef: VSDimensionMemberModel, memberIndex: number,
                           members: VSDimensionMemberModel[]): boolean
   {
      for(let i = memberIndex + 1; i < members.length; i++) {
         if(Tool.isEquals(members[i], sourceRef)) {
            return false;
         }
      }

      return true;
   }

   contentDragEnd(event: DragEvent) {
      this.removeDuplicateMembers();
   }

   private removeDuplicateMembers() {
      if(this.dragSourceEl && this.dragSourceEl.dropList) {
         const sidx = this.dragSourceEl.sourceIndex;

         for(let i = 0; i < this.dragSourceEl.dropList.length; i++) {
            if(i != sidx &&
               Tool.isEquals(this.dragSourceEl.dropList[i], this.dragSourceEl.dropList[sidx]))
            {
               this.dragSourceEl.dropList.splice(i, 1);
               break;
            }
         }

         for(let i = this.dragSourceEl.dropList.length - 1; i > 0; i--) {
            for(let j = i - 1; j >= 0; j--) {
               if(Tool.isEquals(this.dragSourceEl.dropList[i], this.dragSourceEl.dropList[j])) {
                  this.dragSourceEl.dropList.splice(i, 1);
                  break;
               }
            }
         }
      }
   }

   columnDragEnd(event: DragEvent) {
      this.removeUndroppedColumn();
   }

   // leave dim tree, remove from dropList and add back to sourceSource
   contentDragLeave(evt: any) {
      if(evt.target.className && evt.target.className.includes("hierarchy-content-list") &&
         !this.inItem)
      {
         if(this.dragSourceEl && this.dragSourceEl.sourceDimensionIndex >= 0 &&
            this.dragSourceEl.dropList)
         {
            this.dragSourceEl.dropList.splice(this.dragSourceEl.dropIndex, 1);
         }

         this.dragSourceEl.dropList = null;
         this.dragSourceEl.dropIndex = null;
      }
      else if(evt.target.className && (evt.target.className.includes("hierarchy-content-item") ||
         evt.target.className.includes("column-icon")))
      {
         this.inItem = false;
      }
   }

   // drop on dimension list
   contentDrop(evt: any, dimension: VSDimensionModel, sourceDimensionIndex: number,
               sourceIndex: number): void
   {
      // check if it's a duplicate and if so then remove it from the list
      if(this.dragSourceEl.dropIndex != null) {
         if(this.isDateType(this.dragSourceEl.sourceRef.dataRef.dataType) &&
            this.dragSourceEl.sourceList == this.localColumnList)
         {
            for(let i = 0; i < dimension.members.length; i++) {
               if(i != this.dragSourceEl.dropIndex &&
                  Tool.isEquals(dimension.members[i], this.dragSourceEl.sourceRef))
               {
                  dimension.members.splice(this.dragSourceEl.dropIndex, 1);
                  break;
               }
            }
         }

         let sourceItemIndex = this.dragSourceEl.sourceIndex;

         if(this.dragSourceEl.dropList == this.dragSourceEl.sourceList &&
            sourceItemIndex > this.dragSourceEl.dropIndex)
         {
            sourceItemIndex += 1;
         }

         if(!this.isDateType(this.dragSourceEl.sourceRef.dataRef.dataType) ||
            this.dragSourceEl.sourceList != this.localColumnList)
         {
            this.dragSourceEl.sourceList.splice(sourceItemIndex, 1);
         }
      }

      this.removeEmptyDimensions();
      this.dragSourceEl = {};
   }

   // drop a the bottom of the dim tree to create new dimension
   newDimensionDrop(evt: any): void {
      evt.preventDefault();
      let isDate: boolean = this.isDateType(this.dragSourceEl.sourceRef.dataRef.dataType);
      let newDim: VSDimensionModel = <VSDimensionModel> {
         members: [this.dragSourceEl.sourceRef]
      };

      if(this.dragSourceEl.sourceList == this.localColumnList) {
         if(isDate && this.getDateDimension()) {
            return;
         }

         this.model.dimensions.push(newDim);
         this.selectedColumn = this.dragSourceEl.sourceRef;

         if(!isDate) {
            this.dragSourceEl.sourceList.splice(this.dragSourceEl.sourceIndex, 1);
         }
      }
      else {
         let dateMembers: number = 0;

         // if date, don't remove from source
         for(let member of this.dragSourceEl.sourceList) {
            if(Tool.isEquals(member.dataRef, this.dragSourceEl.sourceRef.dataRef)) {
               dateMembers++;

               if(dateMembers > 1) {
                  return;
               }
            }
         }

         this.model.dimensions.push(newDim);
         this.selectedColumn = this.dragSourceEl.sourceRef;
         this.dragSourceEl.sourceList.splice(this.dragSourceEl.sourceIndex, 1);
         this.removeEmptyDimensions();
      }

      this.dragSourceEl = {};
   }

   isDateType(dataType: string): boolean {
      return dataType == XSchema.DATE || dataType == XSchema.TIME_INSTANT || dataType == XSchema.TIME;
   }

   getDateDimension(): VSDimensionModel {
      for(let dim of this.model.dimensions) {
         for(let member of dim.members) {
            if(Tool.isEquals(member.dataRef, this.dragSourceEl.sourceRef.dataRef)) {
               return dim;
            }
         }
      }

      return null;
   }

   getMemberName(member: VSDimensionMemberModel): string {
      if(!this.isDateType(member.dataRef.dataType)) {
         return member.dataRef.name;
      }
      else {
         let name: string = member.dataRef.name;

         switch(member.option) {
            case DateRangeRef.YEAR_INTERVAL:
               name = "_#(js:Year)(" + name + ")";
               break;
            case DateRangeRef.QUARTER_OF_YEAR_PART:
               name = "_#(js:QuarterOfYear)(" + name + ")";
               break;
            case DateRangeRef.MONTH_OF_YEAR_PART:
               name = "_#(js:MonthOfYear)(" + name + ")";
               break;
            case DateRangeRef.WEEK_OF_YEAR_PART:
               name = "_#(js:WeekOfYear)(" + name + ")";
               break;
            case DateRangeRef.DAY_OF_WEEK_PART:
               name = "_#(js:DayOfWeek)(" + name + ")";
               break;
            case DateRangeRef.DAY_OF_MONTH_PART:
               name = "_#(js:DayOfMonth)(" + name + ")";
               break;
            case DateRangeRef.HOUR_OF_DAY_PART:
               name = "_#(js:HourOfDay)(" + name + ")";
               break;
            case DateRangeRef.MINUTE_OF_HOUR_PART:
               name = "_#(js:MinuteOfHour)(" + name + ")";
               break;
            case DateRangeRef.SECOND_OF_MINUTE_PART:
               name = "_#(js:SecondOfMinute)(" + name + ")";
               break;
            case DateRangeRef.QUARTER_INTERVAL:
               name = "_#(js:Quarter)(" + name + ")";
               break;
            case DateRangeRef.MONTH_INTERVAL:
               name = "_#(js:Month)(" + name + ")";
               break;
            case DateRangeRef.WEEK_INTERVAL:
               name = "_#(js:Week)(" + name + ")";
               break;
            case DateRangeRef.DAY_INTERVAL:
               name = "_#(js:Day)(" + name + ")";
               break;
            case DateRangeRef.HOUR_INTERVAL:
               name = "_#(js:Hour)(" + name + ")";
               break;
            case DateRangeRef.MINUTE_INTERVAL:
               name = "_#(js:Minute)(" + name + ")";
               break;
            case DateRangeRef.SECOND_INTERVAL:
               name = "_#(js:Second)(" + name + ")";
               break;
            default:
               name = member.dataRef.name;
         }

         return name;
      }
   }

   clear(): void {
      this.model.dimensions = [];
      this.initLocalColumnList();
      this.selectedColumn = null;
   }

   // disallow drop if a date column alreaday exists in other hierarchy dimensions
   isDragAccepted(evt: any, dimension: VSDimensionModel): boolean {
      let sourceDataRef = this.dragSourceEl.sourceRef.dataRef;

      if(this.isDateType(sourceDataRef.dataType)) {
         for(let dim of this.model.dimensions) {
            if(dimension == null || !Tool.isEquals(dimension, dim)) {
               for(let member of dim.members) {
                  if(Tool.isEquals(member.dataRef, sourceDataRef)) {
                     this.preventDateDrop = true;
                     return false;
                  }
               }
            }
         }
      }

      this.preventDateDrop = false;
      return true;
   }
}
