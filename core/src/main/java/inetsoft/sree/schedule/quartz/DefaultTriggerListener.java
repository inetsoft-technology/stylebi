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

import org.quartz.JobExecutionContext;
import org.quartz.Trigger;
import org.quartz.listeners.TriggerListenerSupport;

/**
 * Quartz trigger listener providing support for adding logic within the trigger
 * lifecycle.
 *
 * @since 12.2
 */
public class DefaultTriggerListener extends TriggerListenerSupport {
   public DefaultTriggerListener(String name) {
      if(name == null) {
         throw new IllegalArgumentException("The listener name cannot be null");
      }

      this.name = name;
   }

   @Override
   public String getName() {
      return name;
   }

   @Override
   public boolean vetoJobExecution(Trigger trigger, JobExecutionContext context) {
      return super.vetoJobExecution(trigger, context);
   }

   private final String name;
}