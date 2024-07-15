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
package inetsoft.sree.schedule.quartz;

import inetsoft.sree.schedule.ScheduleCondition;
import org.quartz.ScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.spi.MutableTrigger;

/**
 * Schedule builder for condition-based triggers.
 *
 * @since 12.2
 */
public abstract class AbstractConditionScheduleBuilder<T extends ScheduleCondition, I extends ConditionTrigger<T>>
   extends ScheduleBuilder<I>
{
   @Override
   protected MutableTrigger build() {
      AbstractConditionTrigger<T, I> trigger = createTrigger();
      trigger.setMisfireInstruction(misfireInstruction);
      trigger.setCondition(condition);
      return trigger;
   }

   /**
    * Sets the condition for the trigger.
    *
    * @param condition the condition.
    *
    * @return this builder.
    */
   public AbstractConditionScheduleBuilder<T, I> condition(T condition) {
      this.condition = condition;
      return this;
   }

   /**
    * Sets the misfire instruction to
    * {@link Trigger#MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY}.
    *
    * @return this builder.
    */
   public AbstractConditionScheduleBuilder<T, I> withMisfireHandlingInstructionIgnoreMisfires() {
      this.misfireInstruction = -1;
      return this;
   }

   /**
    * Sets the misfire instruction to
    * {@link ConditionTrigger#MISFIRE_INSTRUCTION_FIRE_ONCE_NOW}
    *
    * @return this builder.
    */
   public AbstractConditionScheduleBuilder<T, I> withMisfireHandlingInstructionFireOnceNow() {
      this.misfireInstruction = 1;
      return this;
   }

   /**
    * Creates a new instance of the trigger class.
    *
    * @return a new trigger instance.
    */
   protected abstract AbstractConditionTrigger<T, I> createTrigger();

   private int misfireInstruction = 0;
   private T condition;
}
