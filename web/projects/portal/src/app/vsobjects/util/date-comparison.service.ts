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
import { Injectable } from "@angular/core";
import { Tool } from "../../../../../shared/util/tool";
import { AbstractBindingRef } from "../../binding/data/abstract-binding-ref";
import { ComboMode } from "../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { ValueTypes } from "../model/dynamic-value-model";
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