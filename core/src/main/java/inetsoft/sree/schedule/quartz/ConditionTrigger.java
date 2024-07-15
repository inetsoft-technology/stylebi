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
package inetsoft.sree.schedule.quartz;

import inetsoft.sree.schedule.ScheduleCondition;
import org.quartz.Trigger;

/**
 * Interface for trigger classes that delegate to a schedule task condition.
 *
 * @since 12.2
 */
public interface ConditionTrigger<T extends ScheduleCondition> extends Trigger {
   /**
    * Indicates that if the triggers condition was missed due to the scheduler
    * being stopped or all execution threads were busy that the schedule task
    * should be executed immediately and then continue with the remaining
    * schedule.
    */
   int MISFIRE_INSTRUCTION_FIRE_ONCE_NOW = 1;

   /**
    * Gets the underlying condition.
    *
    * @return the condition.
    */
   T getCondition();

   /**
    * Sets the underlying condition.
    *
    * @param condition the condition.
    */
   void setCondition(T condition);
}
