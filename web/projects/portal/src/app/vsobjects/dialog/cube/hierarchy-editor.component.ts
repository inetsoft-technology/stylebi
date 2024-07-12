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
import { Component, Input, OnInit } from "@angular/core";
import { HierarchyEditorModel } from "../../model/hierarchy-editor-model";
import { VSDimensionMemberModel } from "../../model/vs-dimension-member-model";
import { OutputColumnRefModel } from "../../model/output-column-ref-model";
import { XSchema } from "../../../common/data/xschema";
import { DateRangeRef } from "../../../common/util/date-range-ref";
import { DateLevelExamplesService } from "../../../common/services/date-level-examples.service";

@Component({
   selector: "hierarchy-editor",
   templateUrl: "hierarchy-editor.component.html",
   styleUrls: ["./hierarchy-editor.component.scss"]
})
export class HierarchyEditor implements OnInit {
   @Input() model: HierarchyEditorModel;
   selectedMember: VSDimensionMemberModel = <VSDimensionMemberModel> {};
   disableDate: boolean = true;
   disableTime: boolean = true;
   dateLevels: string[] = [];
   dateLevelExamples: string[] = [];

   @Input()
   set selectedColumn(selectedColumn: any) {
      this.disableTime = true;
      this.disableDate = true;

      // check if it is a VSDimensionMemberModel with date type
      if(selectedColumn && selectedColumn.option) {
         this.selectedMember = selectedColumn;

         if(selectedColumn.dataRef.dataType == XSchema.DATE) {
            this.disableTime = true;
            this.disableDate = false;
         }
         else if(selectedColumn.dataRef.dataType == XSchema.TIME) {
            this.disableDate = true;
            this.disableTime = false;
         }
         else if(selectedColumn.dataRef.dataType == XSchema.TIME_INSTANT) {
            this.disableTime = false;
            this.disableDate = false;
         }
      }
      else {
         this.selectedMember = <VSDimensionMemberModel> {};
      }
   }

   constructor(private examplesService: DateLevelExamplesService) {
   }

   ngOnInit(): void {
      this.dateLevels = [
         DateRangeRef.QUARTER_OF_YEAR_PART + "", DateRangeRef.MONTH_OF_YEAR_PART + "",
         DateRangeRef.WEEK_OF_YEAR_PART + "", DateRangeRef.DAY_OF_MONTH_PART + "",
         DateRangeRef.DAY_OF_WEEK_PART + "", DateRangeRef.HOUR_OF_DAY_PART + "",
         DateRangeRef.MINUTE_OF_HOUR_PART + "", DateRangeRef.SECOND_OF_MINUTE_PART + "",
         DateRangeRef.YEAR_INTERVAL + "", DateRangeRef.QUARTER_INTERVAL + "",
         DateRangeRef.MONTH_INTERVAL + "", DateRangeRef.WEEK_INTERVAL + "",
         DateRangeRef.DAY_INTERVAL + "", DateRangeRef.HOUR_INTERVAL + "",
         DateRangeRef.MINUTE_INTERVAL + "", DateRangeRef.SECOND_INTERVAL + ""
      ];

      this.examplesService.loadDateLevelExamples(this.dateLevels, "timeInstant")
         .subscribe((data: any) => this.dateLevelExamples = data.dateLevelExamples);
   }

   getDateLevelExample(level: number) {
      let findIndex = this.dateLevels.findIndex((dateLevel) => dateLevel == level + "");
      return this.dateLevelExamples[findIndex];
   }

   get yearInterval(): number {
      return DateRangeRef.YEAR_INTERVAL;
   }

   get quarterOfYearPart(): number {
      return DateRangeRef.QUARTER_OF_YEAR_PART;
   }

   get monthOfYearPart(): number {
      return DateRangeRef.MONTH_OF_YEAR_PART;
   }

   get weekOfYearPart(): number {
      return DateRangeRef.WEEK_OF_YEAR_PART;
   }

   get dayOfMonthPart(): number {
      return DateRangeRef.DAY_OF_MONTH_PART;
   }

   get dayOfWeekPart(): number {
      return DateRangeRef.DAY_OF_WEEK_PART;
   }

   get hourOfDayPart(): number {
      return DateRangeRef.HOUR_OF_DAY_PART;
   }

   get minuteOfHourPart(): number {
      return DateRangeRef.MINUTE_OF_HOUR_PART;
   }

   get secondOfMinutePart(): number {
      return DateRangeRef.SECOND_OF_MINUTE_PART;
   }

   get quarterInterval(): number {
      return DateRangeRef.QUARTER_INTERVAL;
   }

   get monthInterval(): number {
      return DateRangeRef.MONTH_INTERVAL;
   }

   get weekInterval(): number {
      return DateRangeRef.WEEK_INTERVAL;
   }

   get dayInterval(): number {
      return DateRangeRef.DAY_INTERVAL;
   }

   get hourInterval(): number {
      return DateRangeRef.HOUR_INTERVAL;
   }

   get minuteInterval(): number {
      return DateRangeRef.MINUTE_INTERVAL;
   }

   get secondInterval(): number {
      return DateRangeRef.SECOND_INTERVAL;
   }
}
