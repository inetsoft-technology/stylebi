/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.viewsheet.event.calendar;

/**
 * Class that encapsulates the parameters for toggling double calendar.
 *
 * @since 12.3
 */
public class ToggleDoubleCalendarEvent extends CalendarSelectionEvent {
   public boolean isDoubleCalendar() {
      return doubleCalendar;
   }

   public void setDoubleCalendar(boolean doubleCalendar) {
      this.doubleCalendar = doubleCalendar;
   }

   @Override
   public String toString() {
      return "{doubleCalendar:" + doubleCalendar + "}";
   }

   private boolean doubleCalendar;
}
