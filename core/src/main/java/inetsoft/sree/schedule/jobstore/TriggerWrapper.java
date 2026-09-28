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

   // Bug #77245, the holder of an ACQUIRED or BLOCKED entry: the cluster member, the JVM and the
   // store that acquired or fired it, and when. Null for every other state and for entries written
   // by a version that did not record an owner.
   private final String ownerMember;

   private final String ownerJvm;

   private final String ownerStore;

   private final Long ownedSince;

   public Long getNextFireTime() {
      return trigger == null || trigger.getNextFireTime() == null
         ? null : trigger.getNextFireTime().getTime();
   }

   public Long getEndTime() {
      return trigger == null || trigger.getEndTime() == null
         ? null : trigger.getEndTime().getTime();
   }

   private TriggerWrapper(OperableTrigger trigger, TriggerState state) {
      this(trigger, state, null, null, null);
   }

   private TriggerWrapper(OperableTrigger trigger, TriggerState state, String ownerMember,
                          String ownerJvm, String ownerStore)
   {
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

      this.ownerMember = ownerMember;
      this.ownerJvm = ownerJvm;
      this.ownerStore = ownerStore;
      this.ownedSince = ownerMember == null ? null : System.currentTimeMillis();
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

   /**
    * Creates a wrapper for an ACQUIRED or BLOCKED entry that records which member, JVM and store
    * holds it (Bug #77245). The other factories record no owner, so every other transition clears
    * it.
    */
   public static TriggerWrapper newOwnedTriggerWrapper(OperableTrigger trigger,
                                                       TriggerState state, String ownerMember,
                                                       String ownerJvm, String ownerStore)
   {
      return new TriggerWrapper(trigger, state, ownerMember, ownerJvm, ownerStore);
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

   public String getOwnerMember() {
      return ownerMember;
   }

   public String getOwnerJvm() {
      return ownerJvm;
   }

   public String getOwnerStore() {
      return ownerStore;
   }

   public Long getOwnedSince() {
      return ownedSince;
   }

   @Override
   public String toString() {
      return "TriggerWrapper{"
         + "trigger=" + trigger
         + ", state=" + state
         + ", nextFireTime=" + getNextFireTime()
         + ", endTime=" + getEndTime()
         + ", acquiredAt=" + getAcquiredAt()
         + ", ownerMember=" + ownerMember
         + ", ownerJvm=" + ownerJvm
         + ", ownerStore=" + ownerStore
         + ", ownedSince=" + ownedSince
         + '}';
   }
}
