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
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";

export class DynamicDate {
   /**
    * beginning of this year
    */
   public static readonly BEGINNING_OF_THIS_YEAR = "_BEGINNING_OF_THIS_YEAR";
   /**
    * beginning of this quarter
    */
   public static readonly BEGINNING_OF_THIS_QUARTER = "_BEGINNING_OF_THIS_QUARTER";
   /**
    * beginning of this month
    */
   public static readonly BEGINNING_OF_THIS_MONTH = "_BEGINNING_OF_THIS_MONTH";
   /**
    * beginning of this week
    */
   public static readonly BEGINNING_OF_THIS_WEEK = "_BEGINNING_OF_THIS_WEEK";
   /**
    * end of this year
    */
   public static readonly END_OF_THIS_YEAR = "_END_OF_THIS_YEAR";
   /**
    * end of this quarter
    */
   public static readonly END_OF_THIS_QUARTER = "_END_OF_THIS_QUARTER";
   /**
    * end of this month
    */
   public static readonly END_OF_THIS_MONTH = "_END_OF_THIS_MONTH";
   /**
    * end of this week
    */
   public static readonly END_OF_THIS_WEEK = "_END_OF_THIS_WEEK";

   /**
    * now
    */
   public static readonly NOW = "_NOW";

   /**
    * this quarter
    */
   public static readonly THIS_QUARTER = "_THIS_QUARTER";
   /**
    * today
    */
   public static readonly TODAY = "_TODAY";
   /**
    * last year
    */
   public static readonly LAST_YEAR = "_LAST_YEAR";
   /**
    * last quarter
    */
   public static readonly LAST_QUARTER = "_LAST_QUARTER";
   /**
    * last month
    */
   public static readonly LAST_MONTH = "_LAST_MONTH";
   /**
    * last week
    */
   public static readonly LAST_WEEK = "_LAST_WEEK";
   /**
    * last day
    */
   public static readonly LAST_DAY = "_LAST_DAY";
   /**
    * last hour
    */
   public static readonly LAST_HOUR = "_LAST_HOUR";
   /**
    * last minute
    */
   public static readonly LAST_MINUTE = "_LAST_MINUTE";
   /**
    * next year
    */
   public static readonly NEXT_YEAR = "_NEXT_YEAR";
   /**
    * next quarter
    */
   public static readonly NEXT_QUARTER = "_NEXT_QUARTER";
   /**
    * next month
    */
   public static readonly NEXT_MONTH = "_NEXT_MONTH";
   /**
    * next week
    */
   public static readonly NEXT_WEEK = "_NEXT_WEEK";
   /**
    * next day
    */
   public static readonly NEXT_DAY = "_NEXT_DAY";
   /**
    * next hour
    */
   public static readonly NEXT_HOUR = "_NEXT_HOUR";
   /**
    * next minute
    */
   public static readonly NEXT_MINUTE = "_NEXT_MINUTE";

   public static readonly DYNAMIC_DATES = "Dynamic Dates";
}

export const dynamicDates: TreeNodeModel = {
   label: "_#(js:Dynamic Dates)",
   data: {
      data: DynamicDate.DYNAMIC_DATES,
   },
   children: [
      {
         label: DynamicDate.BEGINNING_OF_THIS_YEAR,
         data: {
            data: DynamicDate.BEGINNING_OF_THIS_YEAR,
            parentName: DynamicDate.DYNAMIC_DATES,
            parentLabel: DynamicDate.DYNAMIC_DATES,
         },
         icon: "worksheet-icon",
         leaf: true
      },
      {
         label: DynamicDate.BEGINNING_OF_THIS_QUARTER,
         data: {
            data: DynamicDate.BEGINNING_OF_THIS_QUARTER,
            parentName: DynamicDate.DYNAMIC_DATES,
            parentLabel: DynamicDate.DYNAMIC_DATES,
         },
         icon: "worksheet-icon",
         leaf: true
      },
      {
         label: DynamicDate.BEGINNING_OF_THIS_MONTH,
         data: {
            data: DynamicDate.BEGINNING_OF_THIS_MONTH,
            parentName: DynamicDate.DYNAMIC_DATES,
            parentLabel: DynamicDate.DYNAMIC_DATES,
         },
         icon: "worksheet-icon",
         leaf: true
      },
      {
         label: DynamicDate.BEGINNING_OF_THIS_WEEK,
         data: {
            data: DynamicDate.BEGINNING_OF_THIS_WEEK,
            parentName: DynamicDate.DYNAMIC_DATES,
            parentLabel: DynamicDate.DYNAMIC_DATES,
         },
         icon: "worksheet-icon",
         leaf: true
      },
      {
         label: DynamicDate.END_OF_THIS_YEAR,
         data: {
            data: DynamicDate.END_OF_THIS_YEAR,
            parentName: DynamicDate.DYNAMIC_DATES,
            parentLabel: DynamicDate.DYNAMIC_DATES,
         },
         icon: "worksheet-icon",
         leaf: true
      },
      {
         label: DynamicDate.END_OF_THIS_QUARTER,
         data: {
            data: DynamicDate.END_OF_THIS_QUARTER,
            parentName: DynamicDate.DYNAMIC_DATES,
            parentLabel: DynamicDate.DYNAMIC_DATES,
         },
         icon: "worksheet-icon",
         leaf: true
      },
      {
         label: DynamicDate.END_OF_THIS_MONTH,
         data: {
            data: DynamicDate.END_OF_THIS_MONTH,
            parentName: DynamicDate.DYNAMIC_DATES,
            parentLabel: DynamicDate.DYNAMIC_DATES,
         },
         icon: "worksheet-icon",
         leaf: true
      },
      {
         label: DynamicDate.END_OF_THIS_WEEK,
         data: {
            data: DynamicDate.END_OF_THIS_WEEK,
            parentName: DynamicDate.DYNAMIC_DATES,
            parentLabel: DynamicDate.DYNAMIC_DATES,
         },
         icon: "worksheet-icon",
         leaf: true
      },
      {
         label: DynamicDate.NOW,
         data: {
            data: DynamicDate.NOW,
            parentName: DynamicDate.DYNAMIC_DATES,
            parentLabel: DynamicDate.DYNAMIC_DATES,
         },
         icon: "worksheet-icon",
         leaf: true
      },
      {
         label: DynamicDate.THIS_QUARTER,
         data: {
            data: DynamicDate.THIS_QUARTER,
            parentName: DynamicDate.DYNAMIC_DATES,
            parentLabel: DynamicDate.DYNAMIC_DATES,
         },
         icon: "worksheet-icon",
         leaf: true
      },
      {
         label: DynamicDate.TODAY,
         data: {
            data: DynamicDate.TODAY,
            parentName: DynamicDate.DYNAMIC_DATES,
            parentLabel: DynamicDate.DYNAMIC_DATES,
         },
         icon: "worksheet-icon",
         leaf: true
      },
      {
         label: DynamicDate.LAST_YEAR,
         data: {
            data: DynamicDate.LAST_YEAR,
            parentName: DynamicDate.DYNAMIC_DATES,
            parentLabel: DynamicDate.DYNAMIC_DATES,
         },
         icon: "worksheet-icon",
         leaf: true
      },
      {
         label: DynamicDate.LAST_QUARTER,
         data: {
            data: DynamicDate.LAST_QUARTER,
            parentName: DynamicDate.DYNAMIC_DATES,
            parentLabel: DynamicDate.DYNAMIC_DATES,
         },
         icon: "worksheet-icon",
         leaf: true
      },
      {
         label: DynamicDate.LAST_MONTH,
         data: {
            data: DynamicDate.LAST_MONTH,
            parentName: DynamicDate.DYNAMIC_DATES,
            parentLabel: DynamicDate.DYNAMIC_DATES,
         },
         icon: "worksheet-icon",
         leaf: true
      },
      {
         label: DynamicDate.LAST_WEEK,
         data: {
            data: DynamicDate.LAST_WEEK,
            parentName: DynamicDate.DYNAMIC_DATES,
            parentLabel: DynamicDate.DYNAMIC_DATES,
         },
         icon: "worksheet-icon",
         leaf: true
      },
      {
         label: DynamicDate.LAST_DAY,
         data: {
            data: DynamicDate.LAST_DAY,
            parentName: DynamicDate.DYNAMIC_DATES,
            parentLabel: DynamicDate.DYNAMIC_DATES,
         },
         icon: "worksheet-icon",
         leaf: true
      },
      {
         label: DynamicDate.LAST_HOUR,
         data: {
            data: DynamicDate.LAST_HOUR,
            parentName: DynamicDate.DYNAMIC_DATES,
            parentLabel: DynamicDate.DYNAMIC_DATES,
         },
         icon: "worksheet-icon",
         leaf: true
      },
      {
         label: DynamicDate.LAST_MINUTE,
         data: {
            data: DynamicDate.LAST_MINUTE,
            parentName: DynamicDate.DYNAMIC_DATES,
            parentLabel: DynamicDate.DYNAMIC_DATES,
         },
         icon: "worksheet-icon",
         leaf: true
      },
      {
         label: DynamicDate.NEXT_YEAR,
         data: {
            data: DynamicDate.NEXT_YEAR,
            parentName: DynamicDate.DYNAMIC_DATES,
            parentLabel: DynamicDate.DYNAMIC_DATES,
         },
         icon: "worksheet-icon",
         leaf: true
      },
      {
         label: DynamicDate.NEXT_QUARTER,
         data: {
            data: DynamicDate.NEXT_QUARTER,
            parentName: DynamicDate.DYNAMIC_DATES,
            parentLabel: DynamicDate.DYNAMIC_DATES,
         },
         icon: "worksheet-icon",
         leaf: true
      },
      {
         label: DynamicDate.NEXT_MONTH,
         data: {
            data: DynamicDate.NEXT_MONTH,
            parentName: DynamicDate.DYNAMIC_DATES,
            parentLabel: DynamicDate.DYNAMIC_DATES,
         },
         icon: "worksheet-icon",
         leaf: true
      },
      {
         label: DynamicDate.NEXT_WEEK,
         data: {
            data: DynamicDate.NEXT_WEEK,
            parentName: DynamicDate.DYNAMIC_DATES,
            parentLabel: DynamicDate.DYNAMIC_DATES,
         },
         icon: "worksheet-icon",
         leaf: true
      },
      {
         label: DynamicDate.NEXT_DAY,
         data: {
            data: DynamicDate.NEXT_DAY,
            parentName: DynamicDate.DYNAMIC_DATES,
            parentLabel: DynamicDate.DYNAMIC_DATES,
         },
         icon: "worksheet-icon",
         leaf: true
      },
      {
         label: DynamicDate.NEXT_HOUR,
         data: {
            data: DynamicDate.NEXT_HOUR,
            parentName: DynamicDate.DYNAMIC_DATES,
            parentLabel: DynamicDate.DYNAMIC_DATES,
         },
         icon: "worksheet-icon",
         leaf: true
      },
      {
         label: DynamicDate.NEXT_MINUTE,
         data: {
            data: DynamicDate.NEXT_MINUTE,
            parentName: DynamicDate.DYNAMIC_DATES,
            parentLabel: DynamicDate.DYNAMIC_DATES,
         },
         icon: "worksheet-icon",
         leaf: true
      }
   ],
   icon: "folder-icon",
   leaf: false
};
