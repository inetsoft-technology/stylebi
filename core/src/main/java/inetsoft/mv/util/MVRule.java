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
package inetsoft.mv.util;

import inetsoft.mv.MVDef;
import inetsoft.mv.MVManager;
import inetsoft.mv.fs.internal.ClusterUtil;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.util.ThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.time.Duration;
import java.util.concurrent.Callable;

public class MVRule {
   /**
    * Check if the freshness exceeded.
    */
   public static boolean isStale(long ts) {
      return isExpired(ts, "mv.freshness");
   }

   /**
    * Check if the max age exceeded.
    */
   public static boolean isExpired(long ts) {
      return isExpired(ts, "mv.maxAge");
   }

   /**
    * Check if mv should be generated automatically.
    */
   public static boolean isAuto() {
      return SreeEnv.getProperty("mv.freshness") != null ||
         SreeEnv.getProperty("mv.maxAge") != null;
   }

   // check if ts passed threshold
   private static boolean isExpired(long ts, String durationProp) {
      long duration = parseDuration(SreeEnv.getProperty(durationProp));

      if(duration <= 0) {
         return false;
      }

      long now = System.currentTimeMillis();
      return ts < now - duration;
   }

   // parse duration spec to long (ms)
   public static long parseDuration(String str) {
      if(str != null) {
         try {
            return Long.parseLong(str);
         }
         catch(Exception ex) {
            return Duration.parse(str).toMillis();
         }
      }

      return 0;
   }

   /**
    * Remove expired MVs of every organization. The current organization is cleaned in place;
    * every other organization is cleaned in its own organization scope, since the MV listing,
    * removal and file deletion all resolve the organization from the current context.
    */
   public static void cleanup() {
      MVManager mgr = MVManager.getManager();
      String current = OrganizationManager.getInstance().getCurrentOrgID();

      for(MVDef mv : mgr.list(false)) {
         removeIfExpired(mgr, mv);
      }

      String[] orgIDs = SecurityEngine.getSecurity().getOrganizations();

      for(String orgID : orgIDs) {
         if(orgID == null || orgID.equalsIgnoreCase(current)) {
            continue;
         }

         try {
            runInOrg(orgID, () -> {
               for(MVDef mv : mgr.list(new String[] { orgID })) {
                  removeIfExpired(mgr, mv);
               }

               return null;
            });
         }
         catch(Exception ex) {
            LOG.warn("Failed to clean up expired MVs of organization: {}", orgID, ex);
         }
      }
   }

   private static void removeIfExpired(MVManager mgr, MVDef mv) {
      if(mv != null && isExpired(mv.getLastUpdateTime())) {
         ClusterUtil.deleteClusterMV(mv.getName());
         mgr.remove(mv);

         if(LOG.isDebugEnabled()) {
            LOG.debug("MV expired and deleted: " + mv.getName());
         }
      }
   }

   // the principal's organization takes precedence over the context holder, so the principal
   // of the triggering user is suspended while working in another organization
   private static void runInOrg(String orgID, Callable<Void> task) throws Exception {
      Principal principal = ThreadContext.getContextPrincipal();

      try {
         ThreadContext.setContextPrincipal(null);
         OrganizationManager.runInOrgScope(orgID, task);
      }
      finally {
         ThreadContext.setContextPrincipal(principal);
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(MVRule.class);
}
