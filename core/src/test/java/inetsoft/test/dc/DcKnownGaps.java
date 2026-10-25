/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.test.dc;

import inetsoft.uql.XConstants;

/** The combinations the model tests leave out until a finding of Bug #79061 is decided. */
public final class DcKnownGaps {
   private DcKnownGaps() {
   }

   /**
    * Combinations where the product differs from the wording of the dialog and the difference is
    * a finding of Bug #79061, so the model test leaves them out until it is decided:
    * <ul>
    *   <li>finding 2: Same Day by Week, End Date Saturday 2021-01-02, current period included;</li>
    *   <li>finding 7: a week that starts in a period and ends in the next one loses its days
    *   there. Same Day by Week of a Year, Quarter or Month period drops the weekday when it
    *   falls in the next period; Week to Date / Same Week of a quarter drops a whole week whose
    *   to-date part crosses the quarter end (Change at 2021-01-02, Monday weeks).</li>
    * </ul>
    */
   public static boolean knownGap(DcShape shape, String weekStart, String end, boolean inclusive,
                                   boolean change)
   {
      boolean sameDayByWeek = shape.intervalUnit() == XConstants.DAY_DATE_GROUP &&
         shape.contextLevel() == XConstants.WEEK_DATE_GROUP;

      return sameDayByWeek && "2021-01-02".equals(end) && inclusive ||
         sameDayByWeek && shape.periodLevel() != XConstants.WEEK_DATE_GROUP ||
         shape.periodLevel() == XConstants.YEAR_DATE_GROUP &&
            shape.intervalUnit() == XConstants.WEEK_DATE_GROUP &&
            shape.contextLevel() == XConstants.QUARTER_DATE_GROUP &&
            "monday".equals(weekStart) && "2021-01-02".equals(end) && change;
   }
}
