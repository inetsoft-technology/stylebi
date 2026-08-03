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

public class AdminChangeRequest {
   public String getTransactionId() { return transactionId; }
   public void setTransactionId(String v) { this.transactionId = v; }
   public String getTaskDescription() { return taskDescription; }
   public void setTaskDescription(String v) { this.taskDescription = v; }
   public String getProperty() { return property; }
   public void setProperty(String v) { this.property = v; }
   public String getValue() { return value; }
   public void setValue(String v) { this.value = v; }
   public String getAction() { return action; }
   public void setAction(String v) { this.action = v; }
   public String getRiskLevel() { return riskLevel; }
   public void setRiskLevel(String v) { this.riskLevel = v; }
   public String getSnapshotScope() { return snapshotScope; }
   public void setSnapshotScope(String v) { this.snapshotScope = v; }
   public String getBackupRef() { return backupRef; }
   public void setBackupRef(String v) { this.backupRef = v; }
   public String getReviewOutcome() { return reviewOutcome; }
   public void setReviewOutcome(String v) { this.reviewOutcome = v; }

   private String transactionId, taskDescription, property, value, action,
                  riskLevel, snapshotScope, backupRef, reviewOutcome;
}
