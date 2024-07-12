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

public enum TriggerState {
   NONE,
   NORMAL,
   PAUSED,
   COMPLETE,
   ERROR,
   BLOCKED,
   PAUSED_BLOCKED,
   ACQUIRED,
   WAITING,
   STATE_COMPLETED;

   public static org.quartz.Trigger.TriggerState toClassicTriggerState(
      TriggerState state)
   {
      switch (state) {
      case PAUSED:
         return org.quartz.Trigger.TriggerState.PAUSED;
      case COMPLETE:
         return org.quartz.Trigger.TriggerState.COMPLETE;
      case ERROR:
         return org.quartz.Trigger.TriggerState.ERROR;
      case BLOCKED:
      case PAUSED_BLOCKED:
         return org.quartz.Trigger.TriggerState.BLOCKED;
      case NORMAL:
      case ACQUIRED:
      case WAITING:
      case STATE_COMPLETED:
         return org.quartz.Trigger.TriggerState.NORMAL;
      default:
         return org.quartz.Trigger.TriggerState.NORMAL;
      }
   }
}
