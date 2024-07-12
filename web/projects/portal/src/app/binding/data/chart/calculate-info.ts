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
export const enum CalcConstant {
   /**
    * Compute for inner row dimension of crosstab.
    */
   ROW_INNER = "0",

   /**
    * Compute for inner column dimension of crosstab.
    */
   COLUMN_INNER = "1"
}

export class CalculateInfo {
   type: number;
   alias: string;
   name: string;
   prefix: string;
   prefixView: string;
   view: string;
   supportSortByValue: boolean;
   classType: string;

   public equals(obj: CalculateInfo): boolean {
      if(obj == null || this.classType != obj.classType) {
         return false;
      }

      return true;
   }
}

export class CustomCalcInfo extends CalculateInfo {
}

export class ValueOfCalcInfo extends CalculateInfo{
   columnName: string;
   from: number;

   public equals(obj: ValueOfCalcInfo): boolean {
      return super.equals(obj) && this.columnName == obj.columnName && this.from == obj.from;
   }
}

export class ChangeCalcInfo extends ValueOfCalcInfo {
   asPercent: boolean;

   public equals(obj: ChangeCalcInfo): boolean {
      return super.equals(obj) && this.asPercent == obj.asPercent;
   }
}

export interface AggregateCalcInfo {
   aggregate: string;
}

export class MovingCalcInfo extends CalculateInfo implements AggregateCalcInfo {
   previous: number;
   next: number;
   includeCurrentValue: boolean;
   nullIfNoEnoughValue: boolean;
   aggregate: string;
   innerDim: string;

   public equals(obj: MovingCalcInfo): boolean {
      return this.aggregate == obj.aggregate &&
         this.innerDim == obj.innerDim &&
         this.previous == obj.previous &&
         this.next == obj.next &&
         this.includeCurrentValue == obj.includeCurrentValue &&
         this.nullIfNoEnoughValue == obj.nullIfNoEnoughValue;
   }
}

export class PercentCalcInfo extends CalculateInfo {
   level: number;
   columnName: string;
   byRow: boolean = false;
   byColumn: boolean = false;

   public equals(obj: PercentCalcInfo, checkDefault = true): boolean {
      if(checkDefault && (this.byRow != obj.byRow || this.byColumn != obj.byColumn)) {
         return false;
      }

      return this.level == obj.level && this.columnName == obj.columnName;
   }
}

export class RunningTotalCalcInfo extends CalculateInfo implements AggregateCalcInfo {
   aggregate: string;
   resetLevel: number;
   breakBy: string;

   public equals(obj: RunningTotalCalcInfo): boolean {
      return this.aggregate == obj.aggregate &&
         this.resetLevel == obj.resetLevel &&
         this.breakBy == obj.breakBy;
   }
}

export enum RunningTotalCalcRestLevel {
   NONE = -1,
   YEAR = 0,
   QUARTER = 1,
   MONTH = 2,
   WEEK = 3,
   DAY = 4,
   HOUR = 5,
   MINUTE = 6,
   SECOND = 7
}

export class CompoundGrowthCalcInfo extends RunningTotalCalcInfo {
   public equals(obj: CompoundGrowthCalcInfo): boolean {
      return obj != null && this.classType == obj.classType && super.equals(obj);
   }
}
