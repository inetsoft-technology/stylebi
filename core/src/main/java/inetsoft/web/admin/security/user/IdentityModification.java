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
package inetsoft.web.admin.security.user;

import inetsoft.sree.security.IdentityID;
import inetsoft.uql.util.Identity;

import java.util.ArrayList;
import java.util.List;

// Used by SystemAdminService to track proposed changes to an identity
// State describes if it is being deleted and what other identities are no longer associated with it
public class IdentityModification {
   public IdentityModification(Identity identity) {
      this.identityID = identity.getIdentityID();
      this.type = identity.getType();
   }

   public IdentityID getIdentityID() {
      return identityID;
   }

   public int getType() {
      return type;
   }

   public boolean isDelete() {
      return delete;
   }

   public void setDelete(boolean delete) {
      this.delete = delete;
   }

   public boolean isSysAdminRemoved() {
      return sysAdminRemoved;
   }

   public void setSysAdminRemoved(boolean sysAdminRemoved) {
      this.sysAdminRemoved = sysAdminRemoved;
   }

   public boolean isOrgAdminRemoved() {
      return orgAdminRemoved;
   }

   public void setOrgAdminRemoved(boolean orgAdminRemoved) {
      this.orgAdminRemoved = orgAdminRemoved;
   }

   public List<IdentityID> getRemovedRoles() {
      return removedRoles;
   }

   public List<IdentityID> getRemovedUsers() {
      return removedUsers;
   }

   public void setRemovedUsers(List<IdentityID> removedUsers) {
      this.removedUsers = removedUsers;
   }

   public List<IdentityID> getRemovedGroups() {
      return removedGroups;
   }

   public void setRemovedGroups(List<IdentityID> removedGroups) {
      this.removedGroups = removedGroups;
   }

   public void setRemovedRoles(List<IdentityID> removedRoles) {
      this.removedRoles = removedRoles;
   }

   public List<String> getRemovedOrganizations() {
      return removedOrganizations;
   }

   public void setRemovedOrganizations(List<String> removedOrganizations) {
      this.removedOrganizations = removedOrganizations;
   }

   private IdentityID identityID;
   private int type;
   private boolean delete = false;
   private boolean sysAdminRemoved = false;
   private boolean orgAdminRemoved = false;
   private List<IdentityID> removedUsers = new ArrayList<>();
   private List<IdentityID> removedGroups = new ArrayList<>();
   private List<IdentityID> removedRoles = new ArrayList<>();
   private List<String> removedOrganizations = new ArrayList<>();
}
