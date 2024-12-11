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
import java.util.List;
import java.util.Objects;

/**
 * SessionRecord is a in memory representation of a record in SR_Session table.
 * It can be used to write record to table.
 *
 * @author InetSoft Technology Corp.
 * @version 8.5, 5/19/2006
 */
public class SessionRecord implements AuditRecord {
   /**
    * Operation type logon.
    */
   public static final String OP_TYPE_LOGON = "logon";
   /**
    * Operation type emlogon.
    */
   public static final String OP_TYPE_EMLOGON = "emlogon";
   /**
    * Operation type emlogon.
    */
   public static final String OP_TYPE_TASKLOGON = "tasklogon";
   /**
    * Operation type logoff.
    */
   public static final String OP_TYPE_LOGOFF = "logoff";

   /**
    * Operation status success.
    */
   public static final String OP_STATUS_SUCCESS = "success";

   /**
    * Operation status failure.
    */
   public static final String OP_STATUS_FAILURE = "failure";

   /**
    * logoff session time out.
    */
   public static final String LOGOFF_SESSION_TIMEOUT = "Session Timeout";

   /**
    * Create an empty instance of SessionRecord.
    */
   public SessionRecord() {
      super();
   }

   /**
    * Create a new instance of SessionRecord and set data.
    * @param userID the specified user ID.
    * @param userHost the specified user host.
    * @param userGroup the specified user group.
    * @param userRole the specified user role.
    * @param userSessionID the specified user session ID.
    * @param opType the specified operation type.
    * @param opTimestamp the specified operation timestamp.
    * @param opStatus the specified operation status.
    * @param opError the specified operation error.
    * @param logoffReason the specified logoff reason.
    */
   public SessionRecord(String userID, String userHost, List<String> userGroup,
                        List<String> userRole, String userSessionID, String opType,
                        Timestamp opTimestamp, String opStatus, String opError, String logoffReason)
   {
      super();

      this.setUserID(userID);
      this.setUserHost(userHost);
      this.setUserGroup(userGroup);
      this.setUserRole(userRole);
      this.setUserSessionID(userSessionID);
      this.setOpType(opType);
      this.setOpTimestamp(opTimestamp);
      this.setOpStatus(opStatus);
      this.setOpError(opError);
      this.setLogoffReason(logoffReason);
   }

   /**
    * Create a new instance of SessionRecord and set data.
    * @param userID the specified user ID.
    * @param userHost the specified user host.
    * @param userGroup the specified user group.
    * @param userRole the specified user role.
    * @param userSessionID the specified user session ID.
    * @param opType the specified operation type.
    * @param opTimestamp the specified operation timestamp.
    * @param opStatus the specified operation status.
    * @param opError the specified operation error.
    */
   public SessionRecord(String userID, String userHost, List<String> userGroup,
                        List<String> userRole, String userSessionID, String opType,
                        Timestamp opTimestamp, String opStatus, String opError)
   {
      this(userID, userHost, userGroup, userRole, userSessionID, opType, opTimestamp, opStatus,
           opError, "");
   }

   /**
    * Check if the Record is a valid one.
    * @return <tt>true</tt> if valid, <tt>false</tt> otherwise.
    */
   @JsonIgnore
   @Override
   public boolean isValid() {
      return !StringUtils.isEmpty(userID) && !StringUtils.isEmpty(userSessionID) &&
         !StringUtils.isEmpty(opType) && !StringUtils.isEmpty(opStatus) && (opTimestamp != null);
   }

   /**
    * Get the operation error.
    * @return the specified operation error.
    */
   @AuditRecordProperty
   public String getOpError() {
      return opError == null ? "" : opError;
   }

   /**
    * Set the operation error.
    * @param opError the specified operation error.
    */
   public void setOpError(String opError) {
      this.opError = opError;
   }

   /**
    * Get the operation status.
    * @return the specified operation status.
    */
   @AuditRecordProperty
   public String getOpStatus() {
      return opStatus;
   }

   /**
    * Set the operation status.
    * @param opStatus the specified operation status.
    */
   public void setOpStatus(String opStatus) {
      this.opStatus = opStatus;
   }

   /**
    * Get the operation Timestamp.
    * @return the specified operation timestamp.
    */
   @AuditRecordProperty
   public Timestamp getOpTimestamp() {
      return opTimestamp;
   }

   /**
    * Set the operation Timestamp.
    * @param opTimestamp the specified operation timestamp.
    */
   public void setOpTimestamp(Timestamp opTimestamp) {
      this.opTimestamp = opTimestamp;
   }

   /**
    * Get the operation type.
    * @return the specified operation type.
    */
   @AuditRecordProperty
   public String getOpType() {
      return opType;
   }

   /**
    * Set the operation type.
    * @param opType the specified operation type.
    */
   public void setOpType(String opType) {
      this.opType = opType;
   }

   /**
    * Get the User Group (dilimited).
    * @return the specified user group.
    */
   @AuditRecordProperty
   public List<String> getUserGroup() {
      return userGroup;
   }

   /**
    * Set the User Group (dilimited).
    * @param userGroup the specified userGroup.
    */
   public void setUserGroup(List<String> userGroup) {
      this.userGroup = userGroup;
   }

   /**
    * Get the user host.
    * @return the specified user host.
    */
   @AuditRecordProperty
   public String getUserHost() {
      return userHost;
   }

   /**
    * Set the user host.
    * @param userHost the specified user host.
    */
   public void setUserHost(String userHost) {
      if("0:0:0:0:0:0:0:1".equals(userHost) || "[0:0:0:0:0:0:0:1]".equals(userHost)) {
         // @by jasonshobe, Bug #5986, use the IPv4 loopback address instead of
         // the IPv6 loopback address
         this.userHost = "127.0.0.1";
      }
      else {
         this.userHost = userHost;
      }
   }

   /**
    * Get the user id.
    * @return the specified user ID.
    */
   @AuditRecordProperty
   public String getUserID() {
      return userID;
   }

   /**
    * Set the user id.
    * @param userID the specified userID.
    */
   public void setUserID(String userID) {
      this.userID = userID;
   }

   /**
    * Get the User Role.
    * @return the specified user role.
    */
   @AuditRecordProperty
   public List<String> getUserRole() {
      return userRole;
   }

   /**
    * Set the User Role.
    * @param userRole the specified user role.
    */
   public void setUserRole(List<String> userRole) {
      this.userRole = userRole;
   }

   /**
    * Get the User SessionID.
    * @return the specified user session ID.
    */
   @AuditRecordProperty
   public String getUserSessionID() {
      return userSessionID;
   }

   /**
    * Set the User SessionID.
    * @param userSessionID the specified user session ID.
    */
   public void setUserSessionID(String userSessionID) {
      this.userSessionID = userSessionID;
   }

   /**
    * Get the Logoff reason.
    */
   @AuditRecordProperty
   public String getLogoffReason() {
      return logoffReason;
   }

   /**
    * Set the Logoff reason.
    */
   public void setLogoffReason(String logoffReason) {
      this.logoffReason = logoffReason;
   }

   @AuditRecordProperty
   public String getServerHostName() {
      return serverHostName;
   }

   public void setServerHostName(String serverHostName) {
      this.serverHostName = serverHostName;
   }

   @AuditRecordProperty
   public Timestamp getLogonTime() {
      return logonTime;
   }

   public void setLogonTime(Timestamp logonTime) {
      this.logonTime = logonTime;
   }

   @AuditRecordProperty
   public String getOrganizationId() {
      return organizationId;
   }

   public void setOrganizationId(String organizationId) {
      this.organizationId = organizationId;
   }

   @AuditRecordProperty
   public Timestamp getDuration() {
      return duration;
   }

   public void setDuration(Timestamp duration) {
      this.duration = duration;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      SessionRecord that = (SessionRecord) o;
      return Objects.equals(userID, that.userID) && Objects.equals(userHost, that.userHost) &&
         Objects.equals(userGroup, that.userGroup) && Objects.equals(userRole, that.userRole) &&
         Objects.equals(userSessionID, that.userSessionID) && Objects.equals(opType, that.opType) &&
         Objects.equals(opTimestamp, that.opTimestamp) && Objects.equals(opStatus, that.opStatus) &&
         Objects.equals(opError, that.opError) && Objects.equals(logoffReason, that.logoffReason) &&
         Objects.equals(serverHostName, that.serverHostName) &&
         Objects.equals(organizationId, that.organizationId);
   }

   @Override
   public int hashCode() {
      return Objects.hash(
         userID, userHost, userGroup, userRole, userSessionID, opType, opTimestamp, opStatus,
         opError, logoffReason, serverHostName, organizationId);
   }

   @Override
   public String toString() {
      return "SessionRecord{" +
         "userID='" + userID + '\'' +
         ", userHost='" + userHost + '\'' +
         ", userGroup=" + userGroup +
         ", userRole=" + userRole +
         ", userSessionID='" + userSessionID + '\'' +
         ", opType='" + opType + '\'' +
         ", opTimestamp=" + opTimestamp +
         ", opStatus='" + opStatus + '\'' +
         ", opError='" + opError + '\'' +
         ", logoffReason='" + logoffReason + '\'' +
         ", serverHostName='" + serverHostName + '\'' +
         ", organizationId='" + organizationId + '\'' +
         '}';
   }

   private String userID;
   private String userHost;
   private List<String> userGroup;
   private List<String> userRole;
   private String userSessionID;
   private String opType;
   private Timestamp opTimestamp;
   private String opStatus;
   private String opError;
   private String logoffReason = "";
   private String serverHostName;
   private String organizationId;

   private Timestamp duration;
   private Timestamp logonTime;
}
