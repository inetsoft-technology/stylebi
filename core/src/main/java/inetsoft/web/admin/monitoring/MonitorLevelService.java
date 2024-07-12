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
package inetsoft.web.admin.monitoring;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.ScheduleClient;
import inetsoft.sree.security.*;
import inetsoft.util.Tool;
import inetsoft.web.admin.schedule.ScheduleMetrics;
import inetsoft.web.cluster.ServerClusterClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.*;
import java.util.function.Function;

/**
 * The abstract monitor defines methods for checking if monitoring level is met for an action
 *
 * @version 13.1
 * @author InetSoft Technology Corp
 */
public abstract class MonitorLevelService {
   public MonitorLevelService(String[] lowAttrs, String[] medAttrs, String[] highAttrs) {
      this.lowAttrs = lowAttrs;
      this.medAttrs = medAttrs;
      this.highAttrs = highAttrs;
   }
   /**
    * The high level.
    */
   public static final int HIGH = 10;
   /**
    * The medium level.
    */
   public static final int MEDIUM = 5;
   /**
    * The low level.
    */
   public static final int LOW = 1;
   /**
    * The off level.
    */
   public static final int OFF = 0;

   public static int getMonitorLevel() {
      String monitorLevel = MonitorLevelService.monitorLevel.get().toLowerCase();

      switch(monitorLevel) {
      case "high":
         SreeEnv.setProperty("monitor.level", String.valueOf(MonitorLevelService.HIGH));
         return MonitorLevelService.HIGH;
      case "medium":
         SreeEnv.setProperty("monitor.level", String.valueOf(MonitorLevelService.MEDIUM));
         return MonitorLevelService.MEDIUM;
      case "low":
         SreeEnv.setProperty("monitor.level", String.valueOf(MonitorLevelService.LOW));
         return MonitorLevelService.LOW;
      case "off":
         SreeEnv.setProperty("monitor.level", String.valueOf(MonitorLevelService.OFF));
         return MonitorLevelService.OFF;
      default:
         try {
            return Integer.parseInt(monitorLevel);
         }
         catch(NumberFormatException numberFormatEx) {
            LOG.error(numberFormatEx.getMessage());
            return MonitorLevelService.OFF;
         }
      }
   }

   /**
    * Check if the level of the attribute qualified.
    * @param attr the attribute.
    * @return true if the attribute is qualified at the current monitoring level
    */
   public boolean isLevelQualified(String attr) {
      List<String> qualifiedAttrs = new ArrayList<>();
      int currentLevel = getMonitorLevel();

      switch (currentLevel) {
      case HIGH:
         Collections.addAll(qualifiedAttrs, highAttrs);
      case MEDIUM:
         Collections.addAll(qualifiedAttrs, medAttrs);
      case LOW:
         Collections.addAll(qualifiedAttrs, lowAttrs);
      default:
         break;
      }

      return qualifiedAttrs.contains(attr) && isComponentAvailable();
   }

   /**
    * Check if the component is available.
    */
   public boolean isComponentAvailable() {
      return true;
   }

   protected <T> T getScheduleMetrics(String address, ServerClusterClient clusterClient,
                                      Function<String, T> scheduleGetter,
                                      Function<ScheduleMetrics, T> metricsGetter)
   {
      if(address != null) {
         String[] scheduleServers = ScheduleClient.getScheduleClient().getScheduleServers();

         if(scheduleServers == null) {
            return null;
         }

         for(String server : scheduleServers) {
            if(Tool.equals(server, address)) {
               return scheduleGetter.apply(server);
            }
         }
      }
      else {
         ScheduleMetrics metrics = clusterClient.getMetrics(StatusMetricsType.SCHEDULE_METRICS, null);

         if(metrics != null) {
            return metricsGetter.apply(metrics);
         }
      }

      return null;
   }

   protected List<IdentityID> getOrgUsers(Principal principal) {
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      String orgID = OrganizationManager.getInstance().getCurrentOrgID(principal);

      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
      Organization org = Arrays.stream(provider.getOrganizations())
         .map(provider::getOrganization)
         .filter((o) -> o.getOrganizationID().equals(orgID))
         .findFirst()
         .orElse(null);

      if(org == null) {
         return new ArrayList<>();
      }
      else {
         boolean siteAdmin = OrganizationManager.getInstance().isSiteAdmin(principal);

         return Arrays.stream(provider.getUsers())
            .map(provider::getUser)
            .filter(u -> {
               if((u == null) || !org.getName().equals(u.getOrganization())) {
                  return false;
               }
               if(siteAdmin) {
                  return true;
               }
               IdentityID userID = u.getIdentityID();
               return !OrganizationManager.getInstance().isSiteAdmin(userID);
            })
            .map(User::getIdentityID).toList();
      }
   }

   protected List<IdentityID> getOrgUsers() {
      return getOrgUsers0(OrganizationManager.getInstance().getCurrentOrgID());
   }

   private List<IdentityID> getOrgUsers0(String orgID) {
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
      Organization org = Arrays.stream(provider.getOrganizations())
         .map(provider::getOrganization)
         .filter((o) -> o.getOrganizationID().equals(orgID))
         .findFirst()
         .orElse(null);

      if(org == null) {
         return new ArrayList<>();
      }
      else {
         return Arrays.stream(provider.getUsers())
            .map(provider::getUser)
            .filter(u -> (u != null) && org.getName().equals(u.getOrganization()))
            .map(User::getIdentityID).toList();
      }
   }

   // attribute fields populated/defined in subclasses
   private String[] highAttrs;
   private String[] medAttrs;
   private String[] lowAttrs;
   private static final SreeEnv.Value monitorLevel = new SreeEnv.Value("monitor.level", 10000);
   private static final Logger LOG = LoggerFactory.getLogger(MonitorLevelService.class);
}
