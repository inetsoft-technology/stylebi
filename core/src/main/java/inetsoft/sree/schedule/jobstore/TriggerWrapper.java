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
package inetsoft.sree.schedule.jobstore;

import org.quartz.*;
import org.quartz.spi.OperableTrigger;

import java.io.Serializable;


public class TriggerWrapper implements Serializable {
   private static final long serialVersionUID = 1L;

   public final TriggerKey key;

   public final JobKey jobKey;

   public final OperableTrigger trigger;

   private final Long acquiredAt;

   private TriggerState state;

   public Long getNextFireTime() {
      return trigger == null || trigger.getNextFireTime() == null
         ? null : trigger.getNextFireTime().getTime();
   }

   public Long getEndTime() {
      return trigger == null || trigger.getEndTime() == null
         ? null : trigger.getEndTime().getTime();
   }

   private TriggerWrapper(OperableTrigger trigger, TriggerState state) {
      if(trigger == null) {
         throw new IllegalArgumentException("Trigger cannot be null!");
      }

      this.trigger = trigger;
      key = trigger.getKey();
      this.jobKey = trigger.getJobKey();
      this.state = state;

      // Change to normal if acquired is not released in 5 seconds
      if(state == TriggerState.ACQUIRED) {
         acquiredAt = DateBuilder.newDate().build().getTime();
      }
      else {
         acquiredAt = null;
      }
   }

   public static TriggerWrapper newTriggerWrapper(OperableTrigger trigger) {
      return newTriggerWrapper(trigger, TriggerState.NORMAL);
   }

   public static TriggerWrapper newTriggerWrapper(TriggerWrapper tw,
                                                  TriggerState state)
   {
      return new TriggerWrapper(tw.trigger, state);
   }

   public static TriggerWrapper newTriggerWrapper(OperableTrigger trigger,
                                                  TriggerState state)
   {
      return new TriggerWrapper(trigger, state);
   }

   @Override
   public boolean equals(Object obj) {
      if(obj instanceof TriggerWrapper) {
         TriggerWrapper tw = (TriggerWrapper) obj;

         if(tw.key.equals(this.key)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public int hashCode() {
      return key.hashCode();
   }

   public OperableTrigger getTrigger() {
      return this.trigger;
   }

   public TriggerState getState() {
      return state;
   }

   public Long getAcquiredAt() {
      return acquiredAt;
   }

   @Override
   public String toString() {
      return "TriggerWrapper{"
         + "trigger=" + trigger
         + ", state=" + state
         + ", nextFireTime=" + getNextFireTime()
         + ", endTime=" + getEndTime()
         + ", acquiredAt=" + getAcquiredAt()
         + '}';
   }
}
