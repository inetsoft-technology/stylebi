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
import { ViewsheetEvent } from "../../../common/viewsheet-client/index";

/**
 * Class that encapsulates an event sent to the server to instruct it to apply
 * calendar selection.
 */
export class CalendarSelectionEvent implements ViewsheetEvent {
   /**
    * The current date string of first calendar.
    */
   public currentDate1: string;

   /**
    * The current date string of second calendar.
    */
   public currentDate2: string;

   /**
    * The dates array of the vs calendar.
    */
   public dates: string[];

   public eventSource: string;

   /**
    * Creates a new instance of <tt>CalendarSelectionEvent</tt>.
    *
    * @param dates         the dates array
    * @param currentDate1  the current date of calendar 1
    * @param currentDate2  the current date of calendar 2
    */
   constructor(dates: string[], currentDate1: string, currentDate2: string, eventSource?: string)
   {
      this.dates = dates;
      this.currentDate1 = currentDate1;
      this.currentDate2 = currentDate2;
      this.eventSource = eventSource;
   }
}
