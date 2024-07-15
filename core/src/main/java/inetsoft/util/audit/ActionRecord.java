/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util.audit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import inetsoft.sree.security.*;
import inetsoft.util.Tool;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;

/**
 * ActionRecord is a in memory representation of a record in SR_Action table.
 * It can be used to write record to table.
 *
 * +──────────────────────────────+───────+
 * | DataBase field length limit  |       |
 * +──────────────────────────────+───────+
 * | ACTION_NAME                  | 16    |
 * | OBJECT_NAME                  | 1024  |
 * | OBJECT_TYPE                  | 20    |
 * | ACTION_TIMESTAMP             | 15    |
 * | ACTION_STATUS                | 16    |
 * | ACTION_ERROR                 | 2048  |
 * | SERVER_HOST_NAME             | 255   |
 * +──────────────────────────────+───────+
 *
 * @author InetSoft Technology Corp.
 * @version 8.5, 5/19/2006
 */
public class ActionRecord implements AuditRecord {
   /**
    * Action name edit.
    */
   public static final String ACTION_NAME_EDIT = "edit";
   /**
    * Action name create.
    */
   public static final String ACTION_NAME_CREATE = "create";
   /**
    * Action name delete.
    */
   public static final String ACTION_NAME_DELETE = "delete";
   /**
    * Action name finish.
    */
   public static final String ACTION_NAME_FINISH = "finish";
   /**
    * Action name rename.
    */
   public static final String ACTION_NAME_RENAME = "rename";
   /**
    * Action name move.
    */
   public static final String ACTION_NAME_MOVE = "move";
   /**
    * Aciton name run.
    */
   public static final String ACTION_NAME_RUN = "run";
   /**
    * Aciton name import.
    */
   public static final String ACTION_NAME_IMPORT = "import";
   /**
    * Object type report.
    */
   public static final String OBJECT_TYPE_REPORT = "report";
   /**
    * Object type asset.
    */
   public static final String OBJECT_TYPE_ASSET = "asset";
   /**
    * Object type file.
    */
   public static final String OBJECT_TYPE_FILE = "file";
   /**
    * Object type script.
    */
   public static final String OBJECT_TYPE_SCRIPT = "script";
   /**
    * Object type parameter sheet.
    */
   public static final String OBJECT_TYPE_PROTOTYPE = "prototype";
   /**
    * Object type table style.
    */
   public static final String OBJECT_TYPE_TABLE_STYLE = "table style";
   /**
    * Object type query.
    */
   public static final String OBJECT_TYPE_QUERY = "query";
   /**
    * Object type datasource.
    */
   public static final String OBJECT_TYPE_DATASOURCE = "datasource";
   /**
    * Object type view.
    */
   public static final String OBJECT_TYPE_VIEW = "viewsheet";
   /**
    * Object type dashboard.
    */
   public static final String OBJECT_TYPE_DASHBOARD = "dashboard";
   /**
    * Object type scheduler task.
    */
   public static final String OBJECT_TYPE_TASK = "task";
   /**
    * Object type scheduler cycle.
    */
   public static final String OBJECT_TYPE_CYCLE = "cycle";
   /**
    * Object type snapshot.
    */
   public static final String OBJECT_TYPE_SNAPSHOT = "snapshot";
   /**
    * Object type password.
    */
   public static final String OBJECT_TYPE_PASSWORD = "password";
   /**
    * Object type user security.
    */
   public static final String OBJECT_TYPE_EMPROPERTY = "properties";
   /**
    * Object type EM Property.
    */
   public static final String OBJECT_TYPE_USERPERMISSION = "user";
   /**
    * Object type object permission.
    */
   public static final String OBJECT_TYPE_OBJECTPERMISSION = "permission";
   /**
    * Object type object plug.
    */
   public static final String OBJECT_TYPE_PLUG = "plug";
   /**
    * Object type object worksheet.
    */
   public static final String OBJECT_TYPE_WORKSHEET = "worksheet";
   /**
    * Object type folder
    */
   public static final String OBJECT_TYPE_FOLDER = "folder";
   /**
    * Object type security provider
    */
   public static final String OBJECT_TYPE_SECURITY_PROVIDER = "security provider";
   /**
    * Object type physical view.
    */
   public static final String OBJECT_TYPE_PHYSICAL_VIEW = "physical view";
   /**
    * Object type logical model.
    */
   public static final String OBJECT_TYPE_LOGICAL_MODEL = "logical model";
   /**
    * Object type vpm.
    */
   public static final String OBJECT_TYPE_VIRTUAL_PRIVATE_MODEL = "vpm";
   /**
    * Action status success.
    */
   public static final String ACTION_STATUS_SUCCESS = "success";
   /**
    * Action status failure.
    */
   public static final String ACTION_STATUS_FAILURE = "failure";

   /**
    * Create an empty instance of ActionRecord.
    */
   public ActionRecord() {
      super();
   }

   /**
    * Create a new instance of ActionRecord and set data.
    * @param userName the specified user session ID.
    * @param actionName the specified action name.
    * @param objectName the specified object name.
    * @param objectType the specified object type.
    * @param actionTimestamp the specified action timestamp.
    * @param actionStatus the specified action status.
    * @param actionError the specified action error.
    */
   public ActionRecord(String userName, String actionName,
                       String objectName, String objectType,
                       Timestamp actionTimestamp, String actionStatus,
                       String actionError) {
      super();

      this.setUserSessionID(userName);
      this.setActionName(actionName);
      this.setObjectName(objectName);
      this.setObjectType(objectType);
      this.setActionTimestamp(actionTimestamp);
      this.setActionStatus(actionStatus);
      this.setActionError(actionError);
      this.setServerHostName(Tool.getHost());
      this.setResourceOrganization(OrganizationManager.getCurrentOrgName());
      IdentityID sessionID = new IdentityID(userSessionID, OrganizationManager.getCurrentOrgName());
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
      String orgId;
      if(provider.getUser(sessionID) != null) {
         orgId = provider.getOrganization(provider.getUser(sessionID).getOrganization()).getId();
      }
      else {
         orgId = OrganizationManager.getInstance().getCurrentOrgID();
      }
      this.setOrganizationId(orgId);
   }

   /**
    * Check if the record is a valid one.
    * @return <tt>true</tt> if valid, <tt>false</tt> otherwise.
    */
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
      if(StringUtils.isEmpty(userSessionID)) {
         throw new IllegalStateException("Invalid action record, userSessionID cannot be null");
      }

      if(StringUtils.isEmpty(actionName)) {
         throw new IllegalStateException("Invalid action record, actionName cannot be null");
      }

      if(StringUtils.isEmpty(objectName)) {
         throw new IllegalStateException("Invalid action record, objectName cannot be null");
      }

      if(StringUtils.isEmpty(objectType)) {
         throw new IllegalStateException("Invalid action record, objectType cannot be null");
      }

      if(StringUtils.isEmpty(actionStatus)) {
         throw new IllegalStateException("Invalid action record, actionStatus cannot be null");
      }

      if(actionTimestamp == null) {
         throw new IllegalStateException("Invalid action record, actionTimestamp cannot be null");
      }
   }

   /**
    * Get the user session ID.
    * @return the specified user session ID.
    */
   public String getUserSessionID(){
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
    * Get the action name.
    * @return the specified action name.
    */
   public String getActionName() {
      return this.actionName;
   }

   /**
    * Set the action name.
    * @param actionName the specified action name.
    */
   public void setActionName(String actionName) {
      this.actionName = actionName;
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
    * Get the action timestamp.
    * @return the specified action timestamp.
    */
   public Timestamp getActionTimestamp() {
      return this.actionTimestamp;
   }

   /**
    * Set the action timestamp.
    * @param actionTimestamp the specified action timestamp.
    */
   public void setActionTimestamp(Timestamp actionTimestamp) {
      this.actionTimestamp = actionTimestamp;
   }

   /**
    * Get the action status.
    * @return the specified action status.
    */
   public String getActionStatus() {
      return this.actionStatus;
   }

   /**
    * Set the action status.
    * @param actionStatus the specified action status.
    */
   public void setActionStatus(String actionStatus) {
      this.actionStatus = actionStatus;
   }

   /**
    * Get the action error.
    * @return the specified action error.
    */
   public String getActionError() {
      return this.actionError;
   }

   /**
    * Set the action error.
    * @param actionError the specified action error.
    */
   public void setActionError(String actionError) {
      this.actionError = actionError;
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

   public String getResourceOrganization() {
      return resourceOrganization;
   }

   public void setResourceOrganization(String resourceOrganization) {
      this.resourceOrganization = resourceOrganization;
   }

   @Override
   public ActionRecord clone() {
      try {
         ActionRecord record = (ActionRecord) super.clone();
         record.userSessionID = this.userSessionID;
         record.actionName = this.actionName;
         record.objectName = this.objectName;
         record.objectType = this.objectType;
         record.actionTimestamp = new Timestamp(this.actionTimestamp.getTime());
         record.actionStatus = this.actionStatus;
         record.actionError = this.actionError;
         record.serverHostName = this.serverHostName;
         record.organizationId = this.organizationId;
         record.resourceOrganization = this.resourceOrganization;

         return record;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone ActionRecord", ex);
      }

      return null;
   }

   @Override
   public String toString() {
      return "ActionRecord{" +
         "userSessionID='" + userSessionID + '\'' +
         ", actionName='" + actionName + '\'' +
         ", objectName='" + objectName + '\'' +
         ", objectType='" + objectType + '\'' +
         ", actionTimestamp=" + actionTimestamp +
         ", actionStatus='" + actionStatus + '\'' +
         ", actionError='" + actionError + '\'' +
         '}';
   }

   private String userSessionID;
   private String actionName;
   private String objectName;
   private String objectType;
   private Timestamp actionTimestamp;
   private String actionStatus;
   private String actionError;
   private String serverHostName;
   private String organizationId;
   private String resourceOrganization;

   private static final Logger LOG = LoggerFactory.getLogger(ActionRecord.class);
}
