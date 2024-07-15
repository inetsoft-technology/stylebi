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
package inetsoft.web.composer.model.vs;

import inetsoft.uql.viewsheet.internal.DatePeriod;

public class DatePeriodModel {
   public DatePeriodModel() {
      super();
   }

   public DatePeriodModel(DatePeriod datePeriod) {
      super();

      if(datePeriod != null) {
         setStart(new DynamicValueModel(datePeriod.getStartValue()));
         setEnd(new DynamicValueModel(datePeriod.getEndValue()));
      }
   }

   public DynamicValueModel getStart() {
      return start;
   }

   public void setStart(DynamicValueModel start) {
      this.start = start;
   }

   public DynamicValueModel getEnd() {
      return end;
   }

   public void setEnd(DynamicValueModel end) {
      this.end = end;
   }

   private DynamicValueModel start;
   private DynamicValueModel end;
}
