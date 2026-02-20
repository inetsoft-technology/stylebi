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

import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationContextHolder;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Quartz job implementation that delegates the execution to a schedule task.
 *
 * @since 12.2
 */
@DisallowConcurrentExecution
public class ScheduleTaskJob implements InterruptableJob {
   @Override
   public void execute(JobExecutionContext context) throws JobExecutionException
   {
      String taskName = context.getJobDetail().getKey().getName();
      ScheduleTask task = (ScheduleTask) context.getJobDetail().getJobDataMap()
         .get(ScheduleTask.class.getName());

      if(task != null) {
         executionLock.lock();

         try {
            executionThread = Thread.currentThread();
            executionTask = task;
         }
         finally {
            executionLock.unlock();
         }

         String previousOrgId = OrganizationContextHolder.getCurrentOrgId();
         IdentityID owner = task.getOwner();
         boolean orgChanged = owner != null && !Tool.isEmptyString(owner.getOrgID());

         try {
            Identity identity = task.getIdentity();
            String addr = Tool.getIP();
            Principal principal = null;

            // Bug #40798, don't audit logins for internal tasks
            if(!ScheduleManager.isInternalTask(taskName)) {
               if(identity == null) {
                  principal = SUtil.getPrincipal(task.getOwner(), addr, true);
               }
               else {
                  principal = SUtil.getPrincipal(identity, addr, true);
               }
            }

            if(principal != null) {
               ThreadContext.setContextPrincipal(principal);
            }

            if(orgChanged) {
               OrganizationContextHolder.setCurrentOrgId(owner.getOrgID());
            }

            if(principal == null) {
               LOG.error("User is null", new Exception("User is null"));
            }

            SUtil.runTask(principal, task, addr);
         }
         catch(InterruptedException e) {
            if(!interrupted) {
               throw new JobExecutionException(
                  "Scheduled task '" + SUtil.getTaskNameWithoutOrg(taskName) +
                  "' was interrupted unexpectedly", e);
            }
         }
         catch(Throwable e) {
            throw new JobExecutionException(
               "Scheduled task '" + SUtil.getTaskNameWithoutOrg(taskName) + "' failed to complete", e);
         }
         finally {
            executionLock.lock();

            try {
               if(interrupted) {
                  context.getJobDetail().getJobDataMap()
                     .put("inetsoft.cancelled", true);
               }

               executionThread = null;
               executionTask = null;
               interrupted = false;
            }
            finally {
               executionLock.unlock();
            }

            if(orgChanged) {
               OrganizationContextHolder.setCurrentOrgId(previousOrgId);
            }
         }
      }
   }

   @Override
   public void interrupt() {
      executionLock.lock();

      try {
         if(executionTask != null && executionThread != null) {
            interrupted = true;
            executionTask.cancel();
            executionThread.interrupt();
         }
      }
      finally {
         executionLock.unlock();
      }
   }

   private boolean interrupted = false;
   private Thread executionThread = null;
   private ScheduleTask executionTask = null;
   private final Lock executionLock = new ReentrantLock();
   private static final Logger LOG = LoggerFactory.getLogger(ScheduleTaskJob.class);
}
