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
import org.quartz.Calendar;
import org.quartz.impl.triggers.AbstractTrigger;
import org.quartz.impl.triggers.CoreTrigger;

import java.util.Date;

/**
 * Base class for trigger classes that fire jobs when a schedule task condition
 * is satisfied.
 *
 * @param <T> the type of the underlying condition.
 *
 * @since 12.2
 */
public abstract class AbstractConditionTrigger<T extends ScheduleCondition,
                                               I extends ConditionTrigger<T>>
   extends AbstractTrigger<I> implements ConditionTrigger<T>, CoreTrigger
{
   @Override
   public T getCondition() {
      return condition;
   }

   @Override
   public void setCondition(T condition) {
      this.condition = condition;
   }

   @Override
   public void triggered(Calendar calendar) {
      this.previousFireTime = this.nextFireTime;
      this.nextFireTime = getFireTimeAfter(this.nextFireTime);

      while(this.nextFireTime != null && calendar != null &&
         !calendar.isTimeIncluded(this.nextFireTime.getTime()))
      {
         this.nextFireTime = getFireTimeAfter(this.nextFireTime);

         if(this.nextFireTime == null) {
            break;
         }
      }
   }

   @Override
   public Date computeFirstFireTime(Calendar calendar) {
      this.nextFireTime = getStartTime();

      while(this.nextFireTime != null && calendar != null &&
            !calendar.isTimeIncluded(this.nextFireTime.getTime()))
      {
         this.nextFireTime = getFireTimeAfter(this.nextFireTime);

         if(this.nextFireTime == null) {
            break;
         }
      }

      return this.nextFireTime;
   }

   @Override
   public boolean mayFireAgain() {
      return true;
   }

   @Override
   public Date getStartTime() {
      return startTime;
   }

   @Override
   public void setStartTime(Date startTime) {
      this.startTime = startTime;
   }

   @Override
   public void setEndTime(Date endTime) {
      this.endTime = endTime;
   }

   @Override
   public Date getEndTime() {
      return endTime;
   }

   @Override
   public Date getNextFireTime() {
      if(nextFireTime != null) {
         return nextFireTime;
      }

      return this.nextFireTime = getFireTimeAfter(new Date());
   }

   @Override
   public Date getPreviousFireTime() {
      return previousFireTime;
   }

   @Override
   public Date getFireTimeAfter(Date afterTime) {
      Date result = null;

      if(condition != null && afterTime != null) {
         long ts = condition.getRetryTime(afterTime.getTime() + 30000);

         if(ts >= 0) {
            result = new Date(ts);
         }
      }

      return result;
   }

   @Override
   public Date getFinalFireTime() {
      return null;
   }

   @Override
   protected boolean validateMisfireInstruction(int candidateMisfireInstruction) {
      return candidateMisfireInstruction >= -1 && candidateMisfireInstruction <= 1;
   }

   @Override
   public void updateAfterMisfire(Calendar cal) {
      int misfireInstruction = getMisfireInstruction();

      if(misfireInstruction != MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY) {
         if(misfireInstruction == MISFIRE_INSTRUCTION_SMART_POLICY) {
            misfireInstruction = MISFIRE_INSTRUCTION_FIRE_ONCE_NOW;
         }
      }

      if(misfireInstruction == MISFIRE_INSTRUCTION_FIRE_ONCE_NOW) {
         setNextFireTime(new Date());
      }
      else {
         Date newFireTime = this.getFireTimeAfter(new Date());

         while(newFireTime != null && cal != null &&
            !cal.isTimeIncluded(newFireTime.getTime()))
         {
            newFireTime = this.getFireTimeAfter(newFireTime);

            if(newFireTime == null) {
               break;
            }
         }

         setNextFireTime(newFireTime);
      }
   }

   @Override
   public void updateWithNewCalendar(Calendar calendar, long misfireThreshold) {
      this.nextFireTime = getFireTimeAfter(this.previousFireTime);

      if(this.nextFireTime != null && calendar != null) {
         Date now = new Date();

         while(this.nextFireTime != null &&
            !calendar.isTimeIncluded(this.nextFireTime.getTime()))
         {
            this.nextFireTime = getFireTimeAfter(this.nextFireTime);

            if(this.nextFireTime == null) {
               break;
            }

            if(this.nextFireTime.before(now)) {
               long diff = now.getTime() - this.nextFireTime.getTime();

               if(diff >= misfireThreshold) {
                  this.nextFireTime = getFireTimeAfter(this.nextFireTime);
               }
            }
         }
      }
   }

   @Override
   public void setNextFireTime(Date nextFireTime) {
      this.nextFireTime = nextFireTime;
   }

   @Override
   public void setPreviousFireTime(Date previousFireTime) {
      this.previousFireTime = previousFireTime;
   }

   @Override
   public boolean hasAdditionalProperties() {
      return false;
   }

   @Override
   public String toString() {
      return "AbstractConditionTrigger{" +
         "condition=" + condition +
         ", startTime=" + startTime +
         ", endTime=" + endTime +
         ", previousFireTime=" + previousFireTime +
         ", nextFireTime=" + nextFireTime +
         "} " + super.toString();
   }

   private T condition;
   private Date startTime;
   private Date endTime;
   private Date previousFireTime;
   private Date nextFireTime;
   private static final long serialVersionUID = 1L;
}
