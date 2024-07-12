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
export enum TableDataPathTypes {
   /**
    * Table header row/col/cell.
    */
   HEADER = 0x0100,
   /**
    * Table detail row/col/cell.
    */
   DETAIL = 0x0200, // 512
   /**
    * Table trailer row/col/cell.
    */
   TRAILER = 0x0400,
   /**
    * Table group header row/col/cell.
    */
   GROUP_HEADER = 0x0800,
   /**
    * Table summary row/col/cell.
    */
   SUMMARY = 0x1000,
   /**
    * Table summary header row/col/cell.
    */
   SUMMARY_HEADER = 0x1200,
   /**
    * Table grand total row/col/cell.
    */
   GRAND_TOTAL = TRAILER,
   /**
    * Unknown.
    */
   UNKNOWN = 0x2000,
   /**
    * Title.
    */
   TITLE = 0x4000, // 16384
   /**
    * Calendar title.
    */
   CALENDAR_TITLE = 0x4001,
   /**
    * Year calendar.
    */
   YEAR_CALENDAR = 0x4002,
   /**
    * Month calendar.
    */
   MONTH_CALENDAR = 0x4004,
   /**
    * Object.
    */
   OBJECT = 0x8000
}
