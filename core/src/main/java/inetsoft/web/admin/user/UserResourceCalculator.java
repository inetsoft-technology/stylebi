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
package inetsoft.web.admin.user;

import inetsoft.sree.security.*;
import inetsoft.web.admin.monitoring.MonitorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * The UserResourceCalculator is used to calculate the resource owned by users.
 *
 * @version 10.2
 * @author InetSoft Technology Corp.
 */
public class UserResourceCalculator {
   public UserResourceCalculator(SecurityProvider securityProvider, SecurityEngine securityEngine) {
      this.securityProvider = securityProvider;
      this.securityEngine = securityEngine;
   }

   /**
    * Add active report count.
    * @param user the user.
    */
   public void addActiveReportCount(IdentityID user) {
      Integer cnt = actReportMap.get(user);

      if(cnt == null) {
         actReportMap.put(user, 0);
         cnt = actReportMap.get(user);
      }

      actReportMap.put(user, cnt + 1);
      addTotalCount(user);
   }

   /**
    * Add executing report count.
    * @param user the user.
    */
   public void addExecutingReportCount(IdentityID user) {
     Integer cnt = exeReportMap.get(user);

      if(cnt == null) {
         exeReportMap.put(user, 0);
         cnt = exeReportMap.get(user);
      }

      exeReportMap.put(user, cnt + 1);
      addTotalCount(user);
   }

   /**
    * Add active viewsheet count.
    * @param user the user.
    */
   public void addActiveVSCount(IdentityID user) {
      Integer cnt = actVSMap.get(user);

      if(cnt == null) {
         actVSMap.put(user, 0);
         cnt = actVSMap.get(user);
      }

      actVSMap.put(user, cnt + 1);
      addTotalCount(user);
   }

   /**
    * Add executing viewsheet count.
    * @param user the user.
    */
   public void addExecutingVSCount(IdentityID user) {
      Integer cnt = exeVSMap.get(user);

      if(cnt == null) {
         exeVSMap.put(user, 0);
         cnt = exeVSMap.get(user);
      }

      exeVSMap.put(user, cnt + 1);
      addTotalCount(user);
   }

   /**
    * Set the age of the user.
    * @param user the user.
    * @param age the session age of the user.
    */
   public void setUserAge(IdentityID user, Long age) {
      ageMap.put(user, age);
   }

   /**
    * Get the top 5 users.
    * @return all the qualified users.
    */
   public IdentityID[] getTop5Users() {
      return getTop5List().toArray(new IdentityID[0]);
   }

   /**
    * Add the total count.
    * @param user the user.
    */
   private void addTotalCount(IdentityID user) {
      Integer cnt = totalMap.get(user);

      if(cnt == null) {
         totalMap.put(user, 0);
         cnt = totalMap.get(user);
      }

      totalMap.put(user, cnt + 1);
   }

   public List<TopUser> getTopNUserResources() {
      return getTop5List().stream()
         .filter(ageMap::containsKey)
         .map(name -> TopUser.builder()
            .name(name)
            .activeReports(actReportMap.getOrDefault(name, 0))
            .executingReports(exeReportMap.getOrDefault(name, 0))
            .activeViewsheets(actVSMap.getOrDefault(name, 0))
            .executingViewsheets(exeVSMap.getOrDefault(name, 0))
            .age(ageMap.getOrDefault(name, 0L))
            .build())
         .collect(Collectors.toList());
   }

   /**
    * Get the user resource info. Only uses the user, dateCreated, and
    * numActiveViewsheets fields of sessionInfo.
    * Rewrite of getUserResourceGrid @by OliverYeung, for 12.2 Agile / Big Data.
    */
   public List<SessionModel> getUserResourceInfo() {
      List<IdentityID> list = getTop5List();
      ArrayList<SessionModel> infos = new ArrayList<>();
      SecurityProvider provider = null;

      try {
         provider = SecurityEngine.getSecurity().getSecurityProvider();
      }
      catch(Exception e) {
         // ignore
      }

      for(int i = 0; i < Math.min(5, list.size()); i++) {
         IdentityID key = list.get(i);

         if(ageMap.get(key) == null && provider != null) {
            continue;
         }

         SessionModel.Builder info = SessionModel.builder();
         info.user(key);
         Integer active = actVSMap.get(key);
         active = active == null ? 0 : active;
         Integer executing = exeVSMap.get(key);
         executing = executing == null ? 0 : executing;
         info.activeViewsheets(active + executing);

         if(ageMap.size() > 0) {
            Long age = 0L;

            if(provider != null) {
               age = ageMap.get(key);
               age = age == null ? 0 : age;
            }

            info.dateCreated(age);
         }

         infos.add(info.build());
      }

      return infos;
   }

   /**
    * Get the top 5 list.
    */
   private List<IdentityID> getTop5List() {
      List<Entry<IdentityID, Integer>> list = new ArrayList<>(totalMap.entrySet());
      list.sort((entry1, entry2) -> {
         int val1 = entry1.getValue();
         int val2 = entry2.getValue();
         return MonitorUtil.compareUser(val1, val2);
      });

      List<IdentityID> top5List = totalMap.entrySet().stream()
         .sorted((e1, e2) -> MonitorUtil.compareUser(e1.getValue(), e2.getValue()))
         .map(Map.Entry::getKey)
         .limit(5)
         .collect(Collectors.toList());

      if(top5List.size() == 5) {
         return top5List;
      }

      try {
         if(securityProvider  == null) {
            return top5List;
         }

         List<SRPrincipal> principals = securityEngine.getActivePrincipalList();

         for(SRPrincipal principal : principals) {
            IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
            if(!top5List.contains(pId)) {
               top5List.add(pId);
            }
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to remove active users from top 5 user list", e);
      }

      return top5List;
   }

   private Map<IdentityID, Integer> actReportMap = new HashMap<>();
   private Map<IdentityID, Integer> exeReportMap = new HashMap<>();
   private Map<IdentityID, Integer> actVSMap = new HashMap<>();
   private Map<IdentityID, Integer> exeVSMap = new HashMap<>();
   private Map<IdentityID, Integer> totalMap = new HashMap<>();
   private HashMap<IdentityID, Long> ageMap = new HashMap<>();

   private final SecurityProvider securityProvider;
   private final SecurityEngine securityEngine;

   private static final Logger LOG =
      LoggerFactory.getLogger(UserResourceCalculator.class);
}
