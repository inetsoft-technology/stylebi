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
package inetsoft.mv.mr.internal;

import inetsoft.mv.fs.*;
import inetsoft.mv.mr.XMapTask;

/**
 * XMapStatus, the status of one map task.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public final class XMapStatus {
   /**
    * Constant indicates that map task is started.
    */
   public static final int STARTED = 1;
   /**
    * Constant indicates that map task is completed.
    */
   public static final int COMPLETED = 8;
   /**
    * Constant indicates that map task is failed.
    */
   public static final int FAILED = 2 | COMPLETED;
   /**
    * Constant indicates that map task is successful.
    */
   public static final int SUCCESSFUL = 4 | COMPLETED;

   /**
    * Create an instance of XMapStatus.
    * @param id the specified job id.
    * @param host the specified host.
    */
   public XMapStatus(String id, String host) {
      super();

      this.id = id;
      this.host = host;
      this.task = null;
   }

   /**
    * Create an instance of XMapStatus.
    * @param task the specified map task.
    */
   public XMapStatus(XMapTask task) {
      super();

      this.host = task.getHost();
      this.id = task.getXBlock();
      this.task = task;
      this.ended = -1;
   }

   /**
    * Start this XMapStatus.
    */
   public void start() {
      this.started = System.currentTimeMillis();
      this.state = STARTED;
   }

   /**
    * Check if the map task is expired.
    */
   public boolean isExpired() {
      if(isCompleted()) {
         return false;
      }

      FSConfig config = FSService.getConfig();
      long period = getPeriod();
      return period > config.getExpired();
   }

   /**
    * Get the host which executed the map task.
    */
   public String getHost() {
      return host;
   }

   /**
    * Get the id of the XBlock to be accessed.
    */
   public String getXBlock() {
      return id;
   }

   /**
    * Get the task of this map status.
    */
   public XMapTask getTask() {
      return task;
   }

   /**
    * Get the period.
    */
   public long getPeriod() {
      if(isCompleted()) {
         return ended - started;
      }

      long ts = System.currentTimeMillis();
      return ts - started;
   }

   /**
    * Get the reason why failed to execute the map task.
    */
   public String getReason() {
      return reason;
   }

   /**
    * Get the state of the map task.
    */
   public int getState() {
      return state;
   }

   /**
    * Check if the task is executed successfully.
    */
   public boolean isSuccessful() {
      return state == SUCCESSFUL;
   }

   /**
    * Check if the map task is completed.
    */
   public boolean isCompleted() {
      return (state & COMPLETED) == COMPLETED;
   }

   /**
    * Mark the map task to be completed.
    */
   public void complete(boolean success, String reason) {
      if(isCompleted()) {
         return;
      }

      this.state = success ? SUCCESSFUL : FAILED;
      this.reason = reason;
      this.ended = System.currentTimeMillis();
   }

   /**
    * Get the hash code value.
    */
   public int hashCode() {
      return host.hashCode() ^ id.hashCode();
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof XMapStatus)) {
         return false;
      }

      XMapStatus status = (XMapStatus) obj;
      return host.equals(status.host) && id.equals(status.id);
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return "MapStatus-" + task + "(state:" + state + ",reason:"+ reason + ')';
   }

   private final String host;
   private final String id;
   private final XMapTask task;
   private volatile int state;
   private String reason;
   private long started; // started moment
   private long ended; // ended monent
}
