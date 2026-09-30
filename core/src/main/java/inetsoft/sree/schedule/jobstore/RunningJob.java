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
package inetsoft.sree.schedule.jobstore;

import org.quartz.TriggerKey;

import java.io.Serializable;

/**
 * Bug #77202, the run of a job that disallows concurrent execution, recorded when one of its
 * triggers fires. While it is honoured (see
 * {@link ClusterJobStore#isRunning(RunningJob, java.util.Set, boolean, long, long)}), no other
 * trigger of the job fires. Only the completion of the same fire instance removes it.
 */
public final class RunningJob implements Serializable {
   private static final long serialVersionUID = 1L;

   public RunningJob(String fireInstanceId, TriggerKey triggerKey, String ownerNode,
                     String ownerMember, long startTime)
   {
      this.fireInstanceId = fireInstanceId;
      this.triggerKey = triggerKey;
      this.ownerNode = ownerNode;
      this.ownerMember = ownerMember;
      this.startTime = startTime;
   }

   public String getFireInstanceId() {
      return fireInstanceId;
   }

   public TriggerKey getTriggerKey() {
      return triggerKey;
   }

   public String getOwnerNode() {
      return ownerNode;
   }

   public String getOwnerMember() {
      return ownerMember;
   }

   public long getStartTime() {
      return startTime;
   }

   @Override
   public String toString() {
      return "RunningJob{"
         + "fireInstanceId=" + fireInstanceId
         + ", triggerKey=" + triggerKey
         + ", ownerNode=" + ownerNode
         + ", ownerMember=" + ownerMember
         + ", startTime=" + startTime
         + '}';
   }

   private final String fireInstanceId;
   private final TriggerKey triggerKey;
   // the id of the cluster node running the job (a new id every time a node joins), and its
   // member name, for logging only
   private final String ownerNode;
   private final String ownerMember;
   private final long startTime;
}
