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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CurrentDateModel {
   public CurrentDateModel() {
   }

   public CurrentDateModel(String dateString) {
      // parse calendar 'currentDate' variables to year and month
      Date date = new Date();
      LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
      year = localDate.getYear();
      // getMonthValue() goes from 1-12
      month = localDate.getMonthValue() - 1;

      if(dateString != null && dateString.length() > 0) {
         List<Integer> datesArr = Stream.of(dateString.split("-"))
            .map(Integer::valueOf).collect(Collectors.toList());
         year = datesArr.get(0);

         if(datesArr.size() > 1) {
            month = datesArr.get(1);
         }
      }
   }

   public int getYear() {
      return year;
   }

   public void setYear(int year) {
      this.year = year;
   }

   public int getMonth() {
      return month;
   }

   public void setMonth(int month) {
      this.month = month;
   }

   private int year;
   private int month = 0;
}
