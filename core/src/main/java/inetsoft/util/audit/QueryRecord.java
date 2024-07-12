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
import inetsoft.util.Tool;
import org.apache.commons.lang3.StringUtils;

import java.sql.Timestamp;

/**
 * QueryRecord is a in memory representation of a record in SR_Query table.
 * It can be used to write record to table.
 *
 * @author InetSoft Technology Corp.
 * @version 8.5, 5/19/2006
 */
public class QueryRecord implements AuditRecord {
   /**
    * Object type query.
    */
   public static final String OBJECT_TYPE_QUERY = "query";
   /**
    * Object type model.
    */
   public static final String OBJECT_TYPE_MODEL = "model";
   /**
    * Object type worksheet.
    */
   public static final String OBJECT_TYPE_WORKSHEET = "worksheet";
   /**
    * Execution type start.
    */
   public static final String EXEC_TYPE_START = "start";
   /**
    * Execution type finish.
    */
   public static final String EXEC_TYPE_FINISH = "finish";
   /**
    * Execution status success.
    */
   public static final String EXEC_STATUS_SUCCESS = "success";
   /**
    * Execution status failure.
    */
   public static final String EXEC_STATUS_FAILURE = "failure";

   /**
    * Create an empty instance of QueryRecord.
    */
   public QueryRecord() {
      super();
   }

   /**
    * Create a new instance of QueryRecord and set data.
    * @param execSessionID the specified execution session ID.
    * @param userSessionID the specified user session ID.
    * @param objectName the specified object name.
    * @param objectType the specified object type.
    * @param execType the specified execution type.
    * @param execTimestamp the specified execution timestamp.
    * @param execStatus the specified execution status.
    * @param execError the specified execution error.
    */
   public QueryRecord(String execSessionID, String userSessionID,
                      String objectName, String objectType,
                      String execType, Timestamp execTimestamp,
                      String execStatus, String execError) {
      super();

      this.setExecSessionID(execSessionID);
      this.setUserSessionID(userSessionID);
      this.setObjectName(objectName);
      this.setObjectType(objectType);
      this.setExecType(execType);
      this.setExecTimestamp(execTimestamp);
      this.setExecStatus(execStatus);
      this.setExecError(execError);
      this.setServerHostName(Tool.getHost());
   }

   /**
    * Check if the record is a valid one.
    * @return <tt>true</tt> if valid, <tt>false</tt> otherwise.
    */
   @JsonIgnore
   @Override
   public boolean isValid() {
      return !StringUtils.isEmpty(execSessionID) && !StringUtils.isEmpty(userSessionID) &&
         !StringUtils.isEmpty(objectName) &&
         !StringUtils.isEmpty(objectType) && !StringUtils.isEmpty(execType) &&
         !StringUtils.isEmpty(execStatus) && (execTimestamp != null);
   }

   /**
    * Get the execution session ID.
    * @return the specified execution session ID.
    */
   public String getExecSessionID() {
      return this.execSessionID;
   }

   /**
    * Set the execution session ID.
    * @param execSessionID the specified execution session ID.
    */
   public void setExecSessionID(String execSessionID) {
      this.execSessionID = execSessionID;
   }

   /**
    * Get the user session ID.
    * @return the specified user session ID.
    */
   public String getUserSessionID() {
      return this.userSessionID;
   }

   /**
    * Set the user session ID.
    * @param userSessionID the specified user session ID.
    */
   public void setUserSessionID(String userSessionID) {
      this.userSessionID = userSessionID;
   }

   /**
    * Get the object name.
    * @return the specified object name.
    */
   public String getObjectName() {
      return this.objectName;
   }

   /**
    * Set the object name.
    * @param objectName the specified object name.
    */
   public void setObjectName(String objectName) {
      this.objectName = objectName;
   }

   /**
    * Get the object type.
    * @return the specified object type.
    */
   public String getObjectType() {
      return this.objectType;
   }

   /**
    * Set the object type.
    * @param objectType the specified object type.
    */
   public void setObjectType(String objectType) {
      this.objectType = objectType;
   }

   /**
    * Get the execution type.
    * @return the specified execution type.
    */
   public String getExecType() {
      return this.execType;
   }

   /**
    * Set the execution type.
    * @param execType the specified execution type.
    */
   public void setExecType(String execType) {
      this.execType = execType;
   }

   /**
    * Get the execution timestamp.
    * @return the specified execution timestamp.
    */
   public Timestamp getExecTimestamp() {
      return this.execTimestamp;
   }

   /**
    * Set the execution timestamp.
    * @param execTimestamp the specified timestamp.
    */
   public void setExecTimestamp(Timestamp execTimestamp) {
      this.execTimestamp = execTimestamp;
   }

   /**
    * Get the execution status.
    * @return the specified execution status.
    */
   public String getExecStatus() {
      return this.execStatus;
   }

   /**
    * Set the execution status.
    * @param execStatus the specified execution status.
    */
   public void setExecStatus(String execStatus) {
      this.execStatus = execStatus;
   }

   /**
    * Get the execution error.
    * @return the specified execution error.
    */
   public String getExecError() {
      return this.execError;
   }

   /**
    * Set the execution error.
    * @param execError the specified execution error.
    */
   public void setExecError(String execError) {
      this.execError = execError;
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
   /**
    * Determines if this object is equivilent to another.
    * @param obj the object to compare.
    * @return <code>true</code> if the objects are equivilent.
    */
   public boolean equals(Object obj) {
      if(obj == this) {
         return true;
      }

      if(!(obj instanceof QueryRecord)) {
         return false;
      }

      QueryRecord record = (QueryRecord) obj;

      return Tool.equals(objectType, record.objectType) &&
         Tool.equals(execType, record.execType);
   }

   /**
    * Calculates a hash code for this object.
    * @return the hash code.
    */
   public int hashCode() {
      int hash = 0;

      if(objectType != null) {
         hash += objectType.hashCode();
      }

      if(execType != null) {
         hash += 17 * execType.hashCode();
      }

      return hash;
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return getObjectType() + ":" + getObjectName() + "::" + getExecType();
   }

   private String execSessionID;
   private String userSessionID;
   private String objectName;
   private String objectType;
   private String execType;
   private Timestamp execTimestamp;
   private String execStatus;
   private String execError;
   private String serverHostName;
   private String organizationId;
}
