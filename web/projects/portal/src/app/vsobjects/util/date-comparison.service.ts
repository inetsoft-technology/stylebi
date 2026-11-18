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
import { Injectable } from "@angular/core";
import { Tool } from "../../../../../shared/util/tool";
import { AbstractBindingRef } from "../../binding/data/abstract-binding-ref";
import { ComboMode } from "../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { DatePeriodModel } from "../model/date-period-model";
import { DynamicValueModel, ValueTypes } from "../model/dynamic-value-model";
import { BAggregateRef } from "../../binding/data/b-aggregate-ref";
import { XSchema } from "../../common/data/xschema";
import { BDimensionRef } from "../../binding/data/b-dimension-ref";

@Injectable({
   providedIn: "root"
})
export class DateComparisonService {
   private pattern = new RegExp(/^(?:(?!0000)[0-9]{4}(-?)(?:(?:0?[1-9]|1[0-2])\1(?:0?[1-9]|1[0-9]|2[0-8])|(?:0?[13-9]|1[0-2])\1(?:29|30)|(?:0?[13578]|1[02])\1(?:31))|(?:[0-9]{2}(?:0[48]|[2468][048]|[13579][26])|(?:0[48]|[2468][048]|[13579][26])00)(-?)0?2\2(?:29))$/);

   public getDateComparisonValueTypeNumber(type: string): number {
      if(type == ValueTypes.VALUE) {
         return ComboMode.VALUE;
      }
      if(type == ValueTypes.VARIABLE) {
         return ComboMode.VARIABLE;
      }
      else if(type == ValueTypes.EXPRESSION) {
         return ComboMode.EXPRESSION;
      }
      else {
         return ComboMode.VALUE;
      }
   }

   public getDateComparisonValueTypeStr(type: number): string {
      if(type == ComboMode.VALUE) {
         return ValueTypes.VALUE;
      }
      else if(type == ComboMode.VARIABLE) {
         return ValueTypes.VARIABLE;
      }
      else if(type == ComboMode.EXPRESSION) {
         return ValueTypes.EXPRESSION;
      }
      else {
         return ValueTypes.VALUE;
      }
   }

   public isValidDate(dateStr: string): boolean {
      return this.pattern.test(dateStr) || Tool.isDynamic(dateStr);
   }

   /**
    * Parse a fixed (non-variable, non-script) date value into a sortable day number
    * (yyyymmdd), or null if the value is dynamic or not a valid date.
    */
   private toFixedDay(value: DynamicValueModel): number | null {
      if(!value || value.type != ValueTypes.VALUE || value.value == null) {
         return null;
      }

      const str = String(value.value);

      if(!this.pattern.test(str)) {
         return null;
      }

      const match = /^(\d{4})-?(\d{1,2})-?(\d{1,2})$/.exec(str);

      return match ? Number(match[1]) * 10000 + Number(match[2]) * 100 + Number(match[3]) : null;
   }

   /**
    * Check whether any two custom periods whose start and end are both fixed dates share a
    * calendar day (a period ending on the day another starts is an overlap). Periods defined
    * with a variable or script are not checked. Independent of list order.
    */
   public hasOverlappingFixedPeriods(periods: DatePeriodModel[]): boolean {
      const ranges: [number, number][] = [];

      for(let period of periods || []) {
         const a = this.toFixedDay(period.start);
         const b = this.toFixedDay(period.end);

         if(a != null && b != null) {
            ranges.push([Math.min(a, b), Math.max(a, b)]);
         }
      }

      for(let i = 0; i < ranges.length; i++) {
         for(let j = i + 1; j < ranges.length; j++) {
            if(ranges[i][0] <= ranges[j][1] && ranges[j][0] <= ranges[i][1]) {
               return true;
            }
         }
      }

      return false;
   }

   public checkBindingField(nref: AbstractBindingRef, oref: AbstractBindingRef,
                             isAesthetic: boolean = false): boolean
   {
      if(nref.classType == "aggregate" || nref.classType == "BAggregateRefModel") {
         return isAesthetic ? false : JSON.stringify((<BAggregateRef> nref).calculateInfo) !=
            JSON.stringify((<BAggregateRef> oref).calculateInfo);
      }

      if(nref.dataType != XSchema.DATE && nref.dataType != XSchema.TIME_INSTANT) {
         return false;
      }

      const nfield = <BDimensionRef> nref;
      const ofield = <BDimensionRef> oref;

      return nfield.order != ofield.order || nfield.rankingN != ofield.rankingN ||
         nfield.rankingCol != ofield.rankingCol || nfield.rankingOption != ofield.rankingOption ||
         nfield.groupOthers != ofield.groupOthers || nfield.dateLevel != ofield.dateLevel;
   }
}