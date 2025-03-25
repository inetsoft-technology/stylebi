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

import org.immutables.serial.Serial;
import org.immutables.value.Value;

@Value.Immutable
@Serial.Structural
public interface CalendarDateFormatModel {
   /**
    * Get the runtime viewsheet id.
    */
   String getRuntimeId();

   /**
    * Get the assembly name for the calendar.
    */
   String getAssemblyName();

   /**
    * Get the selected dates string for the calendar.
    */
   String getDates();

   /**
    * Return if this is a double calendar mode.
    */
   boolean isDoubleCalendar();

   /**
    * Return if this calendar is period mode.
    */
   boolean isPeriod();

   /**
    * Return if this calendar is month view.
    */
   boolean isMonthView();
}