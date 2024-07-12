/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.sree.schedule;

import java.io.Serializable;

/**
 * This interface defines the API for the condition used to check
 * to see if a scheduled task is qualified to run.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public interface ScheduleCondition extends Serializable {
   /**
    * Check the condition.
    * @param curr current time.
    * @return true if the condition is met.
    */
   boolean check(long curr);

   /**
    * Get the next time to retry the condition.
    * @param curr current time.
    * @return the next time to retry. Negative value to stop retry.
    */
   long getRetryTime(long curr);
}

