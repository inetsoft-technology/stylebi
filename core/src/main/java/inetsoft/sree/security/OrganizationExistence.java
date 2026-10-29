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

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.db.DatabaseAuthenticationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Decides whether an organization that a session still refers to has been deleted (Bug #79542).
 * Deleting an organization from StyleBI logs its sessions out through an event, but an
 * organization removed in an external provider (a database organization table) or whose event
 * was missed leaves its sessions behind.
 * <p>
 * "Not listed by the provider" is not the same as "deleted": a provider that is loading, failed
 * a query or is empty returns the same answer. So this answers true only when the chain holds an
 * organization-storing provider (file or database) and every such provider is healthy and does
 * not list the organization. In every other case the answer is false (unknown, keep the
 * session). The host organization and the self organization are never deleted, and nothing is
 * deleted outside a multi-tenant installation.
 */
public final class OrganizationExistence {
   private OrganizationExistence() {
   }

   /**
    * Determines whether the organization was deleted.
    *
    * @param orgId the organization id.
    *
    * @return true if the organization is known to be deleted.
    */
   public static boolean isDeleted(String orgId) {
      return orgId != null && findDeleted(List.of(orgId)).contains(orgId);
   }

   /**
    * Finds the organizations that were deleted, reading the providers once.
    *
    * @param orgIds the organization ids to check.
    *
    * @return the ids, as passed in, of the organizations that are known to be deleted.
    */
   public static Set<String> findDeleted(Collection<String> orgIds) {
      try {
         if(orgIds == null || orgIds.isEmpty() || !SUtil.isMultiTenant()) {
            return new HashSet<>();
         }

         SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
         return findDeleted(provider == null ? null : provider.getAuthenticationProvider(), orgIds);
      }
      catch(RuntimeException e) {
         LOG.debug("Unable to determine whether the organizations were deleted", e);
         return new HashSet<>();
      }
   }

   /**
    * Finds the organizations that were deleted in the given authentication provider.
    *
    * @param auth   the authentication provider or chain.
    * @param orgIds the organization ids to check.
    *
    * @return the ids, as passed in, of the organizations that are known to be deleted.
    */
   public static Set<String> findDeleted(AuthenticationProvider auth, Collection<String> orgIds) {
      Set<String> result = new HashSet<>();

      if(auth == null || orgIds == null) {
         return result;
      }

      List<String> candidates = orgIds.stream()
         .filter(id -> id != null && !id.isEmpty() && !isReserved(id))
         .distinct()
         .toList();

      if(candidates.isEmpty()) {
         return result;
      }

      try {
         List<AuthenticationProvider> providers =
            auth instanceof AuthenticationChain chain ? chain.getProviders() : List.of(auth);

         if(providers == null || providers.isEmpty() || auth.isLoading()) {
            return result;
         }

         // only a provider that stores organizations can tell that one is gone; LDAP and
         // virtual providers know the default organization only
         List<AuthenticationProvider> storing = providers.stream()
            .filter(p -> p instanceof FileAuthenticationProvider ||
               p instanceof DatabaseAuthenticationProvider)
            .toList();

         if(storing.isEmpty()) {
            return result;
         }

         Set<String> listed = new HashSet<>();

         for(AuthenticationProvider provider : storing) {
            if(provider.isLoading()) {
               return result;
            }

            String[] ids = provider.getOrganizationIDs();

            // a failed query and an empty store look the same
            if(ids == null || ids.length == 0) {
               return result;
            }

            Set<String> providerIds = new HashSet<>();

            for(String id : ids) {
               if(id != null) {
                  providerIds.add(id.toLowerCase(Locale.ROOT));
               }
            }

            // a healthy file store always holds the host organization
            if(provider instanceof FileAuthenticationProvider &&
               !providerIds.contains(
                  Organization.getDefaultOrganizationID().toLowerCase(Locale.ROOT)))
            {
               return result;
            }

            listed.addAll(providerIds);
         }

         for(String id : candidates) {
            if(!listed.contains(id.toLowerCase(Locale.ROOT)) && auth.getOrganization(id) == null) {
               result.add(id);
            }
         }
      }
      catch(RuntimeException e) {
         LOG.debug("Unable to determine whether the organizations were deleted", e);
         return new HashSet<>();
      }

      return result;
   }

   private static boolean isReserved(String orgId) {
      return orgId.equalsIgnoreCase(Organization.getDefaultOrganizationID()) ||
         orgId.equalsIgnoreCase(Organization.getSelfOrganizationID());
   }

   private static final Logger LOG = LoggerFactory.getLogger(OrganizationExistence.class);
}
