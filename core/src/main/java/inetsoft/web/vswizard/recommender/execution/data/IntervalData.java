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
package inetsoft.web.vswizard.recommender.execution.data;

import inetsoft.uql.asset.DateRangeRef;

/**
 * @version 13.2
 * @author InetSoft Technology Corp
 */
public class IntervalData implements WizardData {
   public IntervalData() {
   }

   public IntervalData(int datalevel, double startD, double endD) {
      this.datalevel = datalevel;
      this.start = startD;
      this.end = endD;
   }

   public double getMin() {
      return start;
   }

   public double getMax() {
      return end;
   }

   public int getDatalevel() {
      return datalevel;
   }

   public long getStartDate() {
      return (long) start;
   }

   public long getEndDate() {
      return (long) end;
   }

   private double start;
   private double end;
   private int datalevel = DateRangeRef.NONE_INTERVAL;
}
