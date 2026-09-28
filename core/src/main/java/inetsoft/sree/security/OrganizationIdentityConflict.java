/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.sree.security;

/**
 * Result of checking a new or edited organization's name and id against the other
 * organizations.
 * <p>
 * Organization names and ids share one case-insensitive namespace: bare
 * {@code SECURITY_ORGANIZATION} permission keys are passed sometimes as an org id and sometimes
 * as an org name, so no organization's name or id may equal, ignoring case, <em>another</em>
 * organization's name or id. An organization whose name equals its own id (e.g. a cloned org)
 * is legal.
 * <p>
 * This check is for writes only: a field is only checked when it changes (compared exactly, so
 * a case-only change is checked too), so an organization that already collides (data created
 * before the check existed) stays editable as long as the colliding field is left unchanged.
 * The edited organization is excluded by its exact id, so a pre-existing case-variant twin is
 * still treated as another organization.
 */
public enum OrganizationIdentityConflict {
   /** No conflict. */
   NONE,
   /** The name equals another organization's name or id, ignoring case. */
   NAME,
   /** The id equals another organization's id or name, ignoring case. */
   ID;

   /**
    * Checks a create or rename of an organization against all other organizations known to
    * {@code provider}. This method does not throw, so each caller can report the conflict with
    * its own exception type.
    *
    * @param provider  the provider whose organizations make up the namespace, normally the
    *                  security provider (authentication chain).
    * @param editedOrg the organization being edited, or {@code null} when creating one. It is
    *                  excluded from the check (by exact id), and its fields that are exactly
    *                  unchanged are not checked.
    * @param newName   the requested organization name.
    * @param newId     the requested organization id.
    *
    * @return the kind of conflict, or {@link #NONE}.
    */
   public static OrganizationIdentityConflict find(AuthenticationProvider provider,
                                                   Organization editedOrg,
                                                   String newName, String newId)
   {
      // "changed" is exact: a case-only change must still be checked, because another org may
      // already be a case-variant twin (e.g. "orga" and "ORGA"), and changing "ORGA" to "orga"
      // would merge into it. Without such a twin, a case-only change of the org's own name or
      // id finds no other match and is accepted.
      boolean checkName = !isEmpty(newName) &&
         (editedOrg == null || !newName.equals(editedOrg.getName()));
      boolean checkId = !isEmpty(newId) &&
         (editedOrg == null || !newId.equals(editedOrg.getId()));
      String[] orgIds = provider == null ? null : provider.getOrganizationIDs();

      if((!checkName && !checkId) || orgIds == null) {
         return NONE;
      }

      for(String otherId : orgIds) {
         // exclude the edited org by its exact id, so a case-variant twin is still checked
         if(otherId == null || editedOrg != null && otherId.equals(editedOrg.getId())) {
            continue;
         }

         String otherName = getOrgName(provider, otherId);

         if(checkName && (newName.equalsIgnoreCase(otherName) || newName.equalsIgnoreCase(otherId))) {
            return NAME;
         }

         if(checkId && (newId.equalsIgnoreCase(otherId) || newId.equalsIgnoreCase(otherName))) {
            return ID;
         }
      }

      return NONE;
   }

   private static String getOrgName(AuthenticationProvider provider, String orgId) {
      Organization org = provider.getOrganization(orgId);
      return org != null ? org.getName() : provider.getOrgNameFromID(orgId);
   }

   private static boolean isEmpty(String value) {
      return value == null || value.isEmpty();
   }
}
