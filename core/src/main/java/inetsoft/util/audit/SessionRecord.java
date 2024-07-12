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
package inetsoft.util.audit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;

import java.sql.Timestamp;

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
   public SessionRecord(String userID, String userHost, String userGroup,
                        String userRole, String userSessionID, String opType,
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
   public SessionRecord(String userID, String userHost, String userGroup,
                        String userRole, String userSessionID, String opType,
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
   public String getOpError() {
      return opError;
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
   public String getUserGroup() {
      return userGroup;
   }

   /**
    * Set the User Group (dilimited).
    * @param userGroup the specified userGroup.
    */
   public void setUserGroup(String userGroup) {
      this.userGroup = userGroup;
   }

   /**
    * Get the user host.
    * @return the specified user host.
    */
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
   public String getUserRole() {
      return userRole;
   }

   /**
    * Set the User Role.
    * @param userRole the specified user role.
    */
   public void setUserRole(String userRole) {
      this.userRole = userRole;
   }

   /**
    * Get the User SessionID.
    * @return the specified user session ID.
    */
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
   public String getLogoffReason() {
      return logoffReason;
   }

   /**
    * Set the Logoff reason.
    */
   public void setLogoffReason(String logoffReason) {
      this.logoffReason = logoffReason;
   }

   public String getServerHostName() {
      return serverHostName;
   }

   public void setServerHostName(String serverHostName) {
      this.serverHostName = serverHostName;
   }

   public String getOrganizationId() {
      return organizationId;
   }

   public void setOrganizationId(String organizationId) {
      this.organizationId = organizationId;
   }

   @Override
   public String toString() {
      return "SessionRecord{" +
         "userID='" + userID + '\'' +
         ", userHost='" + userHost + '\'' +
         ", userGroup='" + userGroup + '\'' +
         ", userRole='" + userRole + '\'' +
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
   private String userGroup;
   private String userRole;
   private String userSessionID;
   private String opType;
   private Timestamp opTimestamp;
   private String opStatus;
   private String opError;
   private String logoffReason = "";
   private String serverHostName;
   private String organizationId;
}
