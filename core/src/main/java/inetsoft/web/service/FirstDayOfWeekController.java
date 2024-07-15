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
package inetsoft.web.service;

import inetsoft.util.Tool;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FirstDayOfWeekController {
   @GetMapping("api/first-day-of-week")
   public FirstDayOfWeekModel getFirstDayOfWeek() {
      int javaFirstDay = Tool.getFirstDayOfWeek();
      int isoFirstDay = (javaFirstDay - 1) == 0 ? 7 : (javaFirstDay - 1);
      return FirstDayOfWeekModel.builder()
         .javaFirstDay(javaFirstDay)
         .isoFirstDay(isoFirstDay)
         .build();
   }
}