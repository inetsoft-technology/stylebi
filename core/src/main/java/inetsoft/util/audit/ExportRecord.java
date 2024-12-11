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
import java.util.ArrayList;
import java.util.List;

/**
 * ExportRecord is a in memory representation of a record in SR_Export table.
 * It can be used to write record to table.
 *
 * @author InetSoft Technology Corp.
 * @version 12.0, 10/15/2013
 */
public class ExportRecord implements AuditRecord {
   /**
    * Object type viewsheet.
    */
   public static final String OBJECT_TYPE_VIEWSHEET = "dashboard";

   /**
    * Create an empty instance of ExportRecord.
    */
   public ExportRecord() {
      super();
   }

   /**
    * Create a new instance of ExportRecord and set data.
    * @param userName the specified user name.
    * @param objectName the specified object name.
    * @param exportType the specified object type.
    * @param exportTimestamp the specified action timestamp.
    */
   public ExportRecord(String userName, String objectName, String objectType,
                       String exportType, Timestamp exportTimestamp, String serverHostName)
   {
      super();

      this.setUserName(userName);
      this.setObjectName(objectName);
      this.setObjectType(objectType);
      this.setExportType(exportType);
      this.setExportTimestamp(exportTimestamp);
      this.setServerHostName(serverHostName);
   }

   /**
    * Check if the record is a valid one.
    * @return <tt>true</tt> if valid, <tt>false</tt> otherwise.
    */
   @JsonIgnore
   @Override
   public boolean isValid() {
      return !StringUtils.isEmpty(userName) && !StringUtils.isEmpty(objectName) &&
         !StringUtils.isEmpty(objectType) &&
         !StringUtils.isEmpty(exportType) && (exportTimestamp != null);
   }

   /**
    * Get the user name.
    * @return the specified user name.
    */
   @AuditRecordProperty
   public String getUserName(){
      return this.userName;
   }

   /**
    * Set the user name.
    * @param userName the specified user name.
    */
   public void setUserName(String userName) {
      this.userName = userName;
   }

   /**
    * Get the object name.
    * @return the specified object name.
    */
   @AuditRecordProperty
   public String getObjectName() {
      return this.objectName;
   }

   /**
    * Set the object name.
    * @param objectName the specified object name.
    */
   public void setObjectName(String objectName) {
      this.objectName = objectName;
      this.objectFolders = null;

      if(objectName != null && objectName.length() > 1) {
         int index = objectName.indexOf('/', 1);

         if(index >= 0) {
            this.objectFolders = new ArrayList<>();

            while(index >= 0) {
               this.objectFolders.add(objectName.substring(0, index));
               index = objectName.indexOf('/', index + 1);
            }
         }
      }
   }

   @AuditRecordProperty
   public List<String> getObjectFolders() {
      return objectFolders;
   }

   public void setObjectFolders(List<String> objectFolders) {
      this.objectFolders = objectFolders;
   }

   /**
    * Get the object type.
    * @return the specified object type.
    */
   @AuditRecordProperty
   public String getObjectType() {
      return this.objectType;
   }

   /**
    * Set the  object type.
    * @param objectType the specified object type.
    */
   public void setObjectType(String objectType) {
      this.objectType = objectType;
   }

   /**
    * Get the export type.
    * @return the specified export type.
    */
   @AuditRecordProperty
   public String getExportType() {
      return this.exportType;
   }

   /**
    * Set the  export type.
    * @param exportType the specified export type.
    */
   public void setExportType(String exportType) {
      this.exportType = exportType;
   }

   /**
    * Get the export timestamp.
    * @return the specified export timestamp.
    */
   @AuditRecordProperty
   public Timestamp getExportTimestamp() {
      return this.exportTimestamp;
   }

   /**
    * Set the export timestamp.
    * @param exportTimestamp the specified export timestamp.
    */
   public void setExportTimestamp(Timestamp exportTimestamp) {
      this.exportTimestamp = exportTimestamp;
   }

   @AuditRecordProperty
   public String getServerHostName() {
      return serverHostName;
   }

   public void setServerHostName(String serverHostName) {
      this.serverHostName = serverHostName;
   }

   @AuditRecordProperty
   public String getOrganizationId() {
      return organizationId;
   }

   public void setOrganizationId(String organizationId) {
      this.organizationId = organizationId;
   }

   private String userName;
   private String objectName;
   private List<String> objectFolders;
   private String objectType;
   private String exportType;
   private Timestamp exportTimestamp;
   private String serverHostName;
   private String organizationId;
}