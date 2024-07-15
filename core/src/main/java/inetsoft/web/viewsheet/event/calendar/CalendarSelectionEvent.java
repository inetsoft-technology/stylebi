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
package inetsoft.web.viewsheet.event.calendar;

/**
 * Class that encapsulates the parameters for applying a selection.
 *
 * @since 12.3
 */
public class CalendarSelectionEvent {
   public String getCurrentDate1() {
      return currentDate1;
   }

   public void setCurrentDate1(String currentDate1) {
      this.currentDate1 = currentDate1;
   }

   public String getCurrentDate2() {
      return currentDate2;
   }

   public void setCurrentDate2(String currentDate2) {
      this.currentDate2 = currentDate2;
   }

   public String[] getDates() {
      return dates;
   }

   public void setDates(String[] dates) {
      this.dates = dates;
   }

   public String getEventSource() {
      return eventSource;
   }

   public void setEventSource(String eventSource) {
      this.eventSource = eventSource;
   }

   public String currentDate1;
   public String currentDate2;
   public String[] dates;
   public String eventSource;
}

