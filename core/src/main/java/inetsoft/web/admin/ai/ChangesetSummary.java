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
package inetsoft.web.admin.ai;

import java.sql.Timestamp;

/**
 * Summarizes a single admin change transaction (changeset) for display in the
 * admin chat audit UI.
 */
public class ChangesetSummary {
   public ChangesetSummary(String transactionId, String taskDescription,
                           int changeCount, String lastStatus, Timestamp lastTimestamp)
   {
      this.transactionId = transactionId;
      this.taskDescription = taskDescription;
      this.changeCount = changeCount;
      this.lastStatus = lastStatus;
      this.lastTimestamp = lastTimestamp;
   }

   public String getTransactionId() {
      return transactionId;
   }

   public String getTaskDescription() {
      return taskDescription;
   }

   public int getChangeCount() {
      return changeCount;
   }

   public String getLastStatus() {
      return lastStatus;
   }

   public Timestamp getLastTimestamp() {
      return lastTimestamp;
   }

   private final String transactionId;
   private final String taskDescription;
   private final int changeCount;
   private final String lastStatus;
   private final Timestamp lastTimestamp;
}
