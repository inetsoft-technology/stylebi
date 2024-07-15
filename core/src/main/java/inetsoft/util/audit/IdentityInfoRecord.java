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
import inetsoft.sree.security.IdentityID;
import inetsoft.util.Tool;
import org.apache.commons.lang3.StringUtils;

import java.sql.Timestamp;

/**
 * IdentityInfoRecord is a in memory representation of a record in sr_identityinfo table.
 * It can be used to write record to table.
 *
 * @author InetSoft Technology Corp.
 * @version 11.5, 2/4/2013
 */
public class IdentityInfoRecord implements AuditRecord {
   /**
    * Create action.
    */
   public static final String ACTION_TYPE_CREATE = "c";

   /**
    * Delete action.
    */
   public static final String ACTION_TYPE_DELETE = "d";

   /**
    * Modify action.
    */
   public static final String ACTION_TYPE_MODIFY = "m";

   /**
    * Rename action, maybe involve modify action.
    */
   public static final String ACTION_TYPE_RENAME = "r";

   /**
    * User identity.
    */
   public static final String USER_TYPE = "u";

   /**
    * Group identity.
    */
   public static final String GROUP_TYPE = "g";

   /**
    * Role identity.
    */
   public static final String ROLE_TYPE = "r";
   /**
    * Organization identity.
    */
   public static final String ORGANIZATION_TYPE = "o";

   /**
    * Role identity.
    */
   public static final String STATE_ACTIVE = "0";

   /**
    * Role identity.
    */
   public static final String STATE_INACTIVE = "1";

   /**
    * Role identity.
    */
   public static final String STATE_NONE = "2";

   /**
    * Create an empty instance of IdentityInfoRecord.
    */
   public IdentityInfoRecord() {
      super();
   }

   /**
    * Create a new instance of IdentityInfoRecord and set data.
    *
    * @param identityId      the specified identity name.
    * @param identityType    the specified identity type.
    * @param actionType      the specified action type.
    * @param actionTimestamp the specified action timestamp.
    */
   public IdentityInfoRecord(IdentityID identityId, String identityType,
                             String actionType, String actionDesc,
                             Timestamp actionTimestamp, String state) {
      super();

      this.identityName = identityId.name;
      this.identityOrganization = identityId.organization;
      this.identityType = identityType;
      this.actionType = actionType;
      this.actionTimestamp = actionTimestamp;
      this.actionDesc = actionDesc;
      this.state = state;
      this.serverHostName = Tool.getHost();
   }

   /**
    * Check if the Record is a valid one.
    * @return <tt>true</tt> if valid, <tt>false</tt> otherwise.
    */
   @JsonIgnore
   @Override
   public boolean isValid() {
      return !StringUtils.isEmpty(identityName) && !StringUtils.isEmpty(identityOrganization) && !StringUtils.isEmpty(identityType) &&
         !StringUtils.isEmpty(actionType) && (actionTimestamp != null);
   }

   /**
    * Get the action Timestamp.
    * @return the specified action timestamp.
    */
   public Timestamp getActionTimestamp() {
      return actionTimestamp;
   }

   /**
    * Set the action Timestamp.
    * @param actionTimestamp the specified action timestamp.
    */
   public void setActionTimestamp(Timestamp actionTimestamp) {
      this.actionTimestamp = actionTimestamp;
   }

   /**
    * Get the action type.
    * @return the specified action type.
    */
   public String getActionType() {
      return actionType;
   }

   /**
    * Set the action type.
    * @param actionType the specified action type.
    */
   public void setActionType(String actionType) {
      this.actionType = actionType;
   }

   /**
    * Get the identity name.
    *
    * @return the specified identity name.
    */
   public IdentityID getIdentityID() {
      return new IdentityID(identityName, identityOrganization);
   }

   /**
    * Set the identity name.
    *
    * @param identityID the specified identityName.
    */
   public void setIdentityID(IdentityID identityID) {
      this.identityName = identityID.name;
      this.identityOrganization = identityID.organization;
   }

   public String getIdentityName() {
      return identityName;
   }

   public String getIdentityOrganization() {
      return identityOrganization;
   }

   /**
    * Get the identity type.
    * @return the specified identity type.
    */
   public String getIdentityType() {
      return identityType;
   }

   /**
    * Set the identity type.
    * @param identityType the specified identityType.
    */
   public void setIdentityType(String identityType) {
      this.identityType = identityType;
   }

   /**
    * Get the action description.
    * @return the specified action description.
    */
   public String getActionDesc() {
      return actionDesc;
   }

   /**
    * Set the action description.
    * @param actionDesc the specified action description.
    */
   public void setActionDesc(String actionDesc) {
      this.actionDesc = actionDesc;
   }

   /**
    * Get the identity state.
    * @return the specified action description.
    */
   public String getIdentityState() {
      return state;
   }

   /**
    * Set the identity state.
    * @param state the identity state.
    */
   public void setIdentityState(String state) {
      this.state = state;
   }

   public String getState() {
      return state;
   }

   public void setState(String state) {
      this.state = state;
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

   private String identityName;
   private String identityOrganization;
   private String identityType;
   private String actionType;
   private Timestamp actionTimestamp;
   private String actionDesc;
   private String state;
   private String serverHostName;
   private String organizationId;
}
