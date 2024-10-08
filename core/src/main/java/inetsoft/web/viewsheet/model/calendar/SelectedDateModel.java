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

public class SelectedDateModel extends CurrentDateModel {
   public SelectedDateModel() {
   }

   public SelectedDateModel(String dateString) {
      // parse dateString from 'dates' variables to selectedDateModel
      dateType = dateString.charAt(0);
      String[] dateArr = dateString.split("-");
      setYear(Integer.parseInt(dateArr[0].substring(1)));

      if(dateType != 'y' && dateArr.length > 1) {
         setMonth(Integer.parseInt(dateArr[1]));

         if(dateType != 'm' && dateArr.length > 2) {
            value = Integer.parseInt(dateArr[2]);
         }
      }
   }

   public char getDateType() {
      return dateType;
   }

   public void setDateType(char dateType) {
      this.dateType = dateType;
   }

   public int getValue() {
      return value;
   }

   public void setValue(int value) {
      this.value = value;
   }

   private char dateType;
   private int value;
}
