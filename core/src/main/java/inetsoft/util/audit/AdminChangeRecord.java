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
package inetsoft.util.audit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;

import java.sql.Timestamp;

public class AdminChangeRecord implements AuditRecord {
   public static final String ACTION_APPLY = "apply";
   public static final String ACTION_ROLLBACK = "rollback";
   public static final String ACTION_RESTORE = "restore";
   public static final String STATUS_VERIFIED = "verified";
   public static final String STATUS_FAILED = "failed";
   public static final String RISK_LOW = "low";
   public static final String RISK_HIGH = "high";
   public static final String SCOPE_VALUE = "value";
   public static final String SCOPE_STORAGE = "storage";

   public AdminChangeRecord() {
      super();
   }

   @JsonIgnore
   @Override
   public boolean isValid() {
      try {
         validate();
         return true;
      }
      catch(IllegalStateException ignore) {
         return false;
      }
   }

   private void validate() {
      if(StringUtils.isEmpty(transactionId)) {
         throw new IllegalStateException("Invalid admin change record, transactionId cannot be null");
      }
      if(StringUtils.isEmpty(property)) {
         throw new IllegalStateException("Invalid admin change record, property cannot be null");
      }
      if(StringUtils.isEmpty(action)) {
         throw new IllegalStateException("Invalid admin change record, action cannot be null");
      }
   }

   @AuditRecordProperty
   public String getTransactionId() { return transactionId; }
   public void setTransactionId(String v) { this.transactionId = v; }
   @AuditRecordProperty
   public String getTaskDescription() { return taskDescription; }
   public void setTaskDescription(String v) { this.taskDescription = v; }
   @AuditRecordProperty
   public String getProperty() { return property; }
   public void setProperty(String v) { this.property = v; }
   @AuditRecordProperty
   public String getObjectType() { return objectType; }
   public void setObjectType(String v) { this.objectType = v; }
   @AuditRecordProperty
   public String getBeforeValue() { return beforeValue; }
   public void setBeforeValue(String v) { this.beforeValue = v; }
   @AuditRecordProperty
   public String getAfterValue() { return afterValue; }
   public void setAfterValue(String v) { this.afterValue = v; }
   @AuditRecordProperty
   public String getAction() { return action; }
   public void setAction(String v) { this.action = v; }
   @AuditRecordProperty
   public String getStatus() { return status; }
   public void setStatus(String v) { this.status = v; }
   @AuditRecordProperty
   public String getRiskLevel() { return riskLevel; }
   public void setRiskLevel(String v) { this.riskLevel = v; }
   @AuditRecordProperty
   public String getSnapshotScope() { return snapshotScope; }
   public void setSnapshotScope(String v) { this.snapshotScope = v; }
   @AuditRecordProperty
   public String getBackupRef() { return backupRef; }
   public void setBackupRef(String v) { this.backupRef = v; }
   @AuditRecordProperty
   public String getReviewOutcome() { return reviewOutcome; }
   public void setReviewOutcome(String v) { this.reviewOutcome = v; }
   @AuditRecordProperty
   public String getUserName() { return userName; }
   public void setUserName(String v) { this.userName = v; }
   @AuditRecordProperty
   public String getUserSessionID() { return userSessionID; }
   public void setUserSessionID(String v) { this.userSessionID = v; }
   @AuditRecordProperty
   public Timestamp getActionTimestamp() { return actionTimestamp; }
   public void setActionTimestamp(Timestamp v) { this.actionTimestamp = v; }
   @AuditRecordProperty
   public String getServerHostName() { return serverHostName; }
   public void setServerHostName(String v) { this.serverHostName = v; }
   @AuditRecordProperty
   public String getOrganizationId() { return organizationId; }
   public void setOrganizationId(String v) { this.organizationId = v; }

   private String transactionId;
   private String taskDescription;
   private String property;
   private String objectType;
   private String beforeValue;
   private String afterValue;
   private String action;
   private String status;
   private String riskLevel;
   private String snapshotScope;
   private String backupRef;
   private String reviewOutcome;
   private String userName;
   private String userSessionID;
   private Timestamp actionTimestamp;
   private String serverHostName;
   private String organizationId;
}
