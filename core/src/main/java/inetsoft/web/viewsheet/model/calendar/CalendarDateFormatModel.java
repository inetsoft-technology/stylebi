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
package inetsoft.web.viewsheet.model.calendar;

import java.io.Serializable;

public class CalendarDateFormatModel implements Serializable {
   public CalendarDateFormatModel() {
   }

   /**
    * Get the runtime viewsheet id.
    */
   public String getRuntimeId() {
      return runtimeId;
   }

   public void setRuntimeId(String runtimeId) {
      this.runtimeId = runtimeId;
   }

   /**
    * Get the assembly name for the calendar.
    */
   public String getAssemblyName() {
      return assemblyName;
   }

   public void setAssemblyName(String name) {
      this.assemblyName = name;
   }

   /**
    * Get the selected dates string for the calendar.
    */
   public String getDates() {
      return dates;
   }

   public void setDates(String dates) {
      this.dates = dates;
   }

   /**
    * Return if this is a double calendar mode.
    */
   public boolean isDoubleCalendar() {
      return doubleCalendar;
   }

   public void setDoubleCalendar(boolean doubleCalendar) {
      this.doubleCalendar = doubleCalendar;
   }

   /**
    * Return if this calendar is period mode.
    */
   public boolean isPeriod() {
      return period;
   }

   public void setPeriod(boolean period) {
      this.period = period;
   }

   /**
    * Return if this calendar is month view.
    */
   public boolean isMonthView() {
      return monthView;
   }

   public void setMonthView(boolean monthView) {
      this.monthView = monthView;
   }

   private String runtimeId;
   private String assemblyName;
   private String dates;
   private boolean doubleCalendar;
   private boolean period;
   private boolean monthView;
}
