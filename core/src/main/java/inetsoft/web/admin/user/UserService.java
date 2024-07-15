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
package inetsoft.web.admin.user;

import inetsoft.sree.RepletRepository;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.MapSession;
import inetsoft.web.MapSessionRepository;
import inetsoft.web.admin.monitoring.*;
import inetsoft.web.admin.viewsheet.ViewsheetService;
import inetsoft.web.cluster.ServerClusterClient;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Lazy(false)
public class UserService
   extends MonitorLevelService
   implements MessageListener, StatusUpdater, MapSessionRepository.PrincipalChangeListener
{
   @Autowired
   public UserService(ViewsheetService viewsheetService,
                      ServerClusterClient client, SecurityEngine securityEngine,
                      SecurityProvider securityProvider,
                      MonitoringDataService monitoringDataService,
                      MapSessionRepository sessionRepository)
   {
      super(lowAttrs, medAttrs, highAttrs);
      this.viewsheetService = viewsheetService;
      this.client = client;
      this.securityEngine = securityEngine;
      this.securityProvider = securityProvider;
      this.monitoringDataService = monitoringDataService;
      this.sessionRepository = sessionRepository;
   }

   @PostConstruct
   public void addListener() {
      cluster = Cluster.getInstance();
      cluster.addMessageListener(this);
      sessionRepository.addPrincipalChangeListener(this);
   }

   @PreDestroy
   public void removeListener() {
      if(cluster != null) {
         cluster.removeMessageListener(this);
      }

      sessionRepository.removePrincipalChangeListener(this);
   }

   @Override
   public void updateStatus(long timestamp) {
      updateMetrics();
   }

   private void updateMetrics() {
      ServerClusterClient.getDebouncer().debounce(
         "UserService.updateMetrics", 2, TimeUnit.SECONDS, this::updateMetrics0);
   }

   private void updateMetrics0() {
      List<SessionModel> sessions;
      List<TopUser> topUsers;

      try {
         sessions = getSessionInfo();
      }
      catch(Exception e) {
         LOG.warn("Failed to update user status", e);
         sessions = Collections.emptyList();
      }

      try {
         topUsers = calculateTopNUsers();
      }
      catch(Exception e) {
         LOG.warn("Failed to update user status", e);
         topUsers = Collections.emptyList();
      }

      UserMetrics.Builder builder = UserMetrics.builder();
      UserMetrics oldMetrics = client.getMetrics(StatusMetricsType.USER_METRICS, null);

      if(oldMetrics != null) {
         builder.from(oldMetrics);
      }

      builder
         .sessions(sessions)
         .topUsers(topUsers);
      client.setMetrics(StatusMetricsType.USER_METRICS, builder.build(),
         metrics -> monitoringDataService.update());
   }

   private void updateStatus() {
      updateMetrics();
   }

   @Override
   public void messageReceived(MessageEvent event) {
      String sender = event.getSender();

      if(event.getMessage() instanceof LogoutSessionMessage) {
         handleLogoutSessionMessage(sender, (LogoutSessionMessage) event.getMessage());
      }
   }

   private void handleLogoutSessionMessage(String sender, LogoutSessionMessage message) {
      String[] sessionIds = message.getSessionIds();

      try {
         logout(sessionIds);
         cluster.sendMessage(sender, new LogoutSessionCompleteMessage());
      }
      catch(Exception e) {
         LOG.warn("Failed to logout sessions", e);
      }
   }

   List<TopUser> getTopUsers(String address) {
      return getModelData(address, UserMetrics::topUsers);
   }

   List<TopUser> getTopUsers(String address, int count) {
      return getTopUsers(address).stream()
         .limit(count)
         .collect(Collectors.toList());
   }

   void logoutSession(String address, String[] sessionIds) {
      try {
         if(StringUtils.isEmpty(address)) {
            logout(sessionIds);
         }
         else {
            LogoutSessionMessage message = new LogoutSessionMessage();
            message.setSessionIds(sessionIds);
            cluster.exchangeMessages(address, message, LogoutSessionCompleteMessage.class);
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to logout session from cluster", e);
      }
   }

   List<UserSessionMonitoringTableModel> getServerSessionModel(String address, Principal principal)
   {
      boolean lastAccessEnabled = isLevelQualified("lastAccess");
      Catalog catalog = Catalog.getCatalog(principal);
      String orgName = OrganizationManager.getInstance().getCurrentOrgName(principal);
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();

      return getModelData(address, u -> u.sessions().stream()
         .filter(i -> i.user() != null)
         .filter(i -> Objects.equals(orgName, i.user().getOrganization()))
         .filter(user -> provider.checkPermission(principal, ResourceType.SECURITY_USER,
                                                  user.user().convertToKey(), ResourceAction.ADMIN))
         .map(s -> UserSessionMonitoringTableModel.builder()
            .from(s, lastAccessEnabled, catalog)
            .build())
         .collect(Collectors.toList()));
   }

   List<UserFailedLoginMonitoringTableModel> getServerFailedModel(String address) {
      List<IdentityID> orgUsers = getOrgUsers();
      return getModelData(address, u -> u.failedLogins().stream()
         .filter(i -> orgUsers.contains(i.user()))
         .map(i -> UserFailedLoginMonitoringTableModel.builder()
            .from(i)
            .build())
         .collect(Collectors.toList()));
   }

   List<TopUsersMonitoringTableModel> getTopNUsersModel(String address) {
      return getTopUsers(address).stream()
         .map(u -> TopUsersMonitoringTableModel.builder()
            .from(u)
            .build())
         .collect(Collectors.toList());
   }

   List<TopUsersMonitoringTableModel> getTopNUsersModel(String address, int count) {
      return getTopUsers(address, count).stream()
         .map(u -> TopUsersMonitoringTableModel.builder()
            .from(u)
            .build())
         .collect(Collectors.toList());
   }

   private <T> List<T> getModelData(String address, Function<UserMetrics, List<T>> fn) {
      UserMetrics metrics = client.getMetrics(StatusMetricsType.USER_METRICS, address);

      if(metrics == null) {
         return Collections.emptyList();
      }

      return fn.apply(metrics);
   }

   private boolean isIdentityMising(IdentityID name, Function<SecurityProvider, IdentityID[]> fn) {
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();

      if(provider != null) {
         for(IdentityID identity : fn.apply(provider)) {
            if(identity.equals(name)) {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Get the FailedLoginHistory from the monitor service.
    * @return a array of FailedLoginInfo.
    */
   List<FailedLoginModel> getFailedLoginHistory() {
      UserMetrics metrics = client.getMetrics(StatusMetricsType.USER_METRICS, null);

      if(metrics == null) {
         return Collections.emptyList();
      }

      return metrics.failedLogins();
   }

   private SecurityEngine getSecurityEngine() {
      return SecurityEngine.getSecurity();
   }

   /**
    * Get the user ages.
    */
   private void getUserAge(UserResourceCalculator cal) {
      if(!isLevelQualified("sessionInfo")) {
         return;
      }

      List<SRPrincipal> principals = sessionRepository.getActiveSessions();
      HashMap<IdentityID, Long> userMap = new HashMap<>();

      for(SRPrincipal principal : principals) {
         if(principal == null) {
            continue;
         }

         IdentityID userName = principal.getClientUserID();
         Long age = userMap.get(userName);

         if(age == null || principal.getAge() > age) {
            userMap.put(userName, principal.getAge());
         }
      }

      IdentityID[] top5Users = cal.getTop5Users();

      for(IdentityID user : top5Users) {
         cal.setUserAge(user, userMap.get(user) == null ? 0L :
            userMap.get(user));
      }
   }

   /**
    * Get the number of active user sessions that currently exist on the server.
    */
   int getSessionCount() {
      if(!isLevelQualified("sessionCount")) {
         return 0;
      }

      return sessionRepository.getActiveSessions().size();
   }

   /**
    * Get SessionInfo from the monitor service.
    */
   List<SessionModel> getSessionInfo() {
      if(!isLevelQualified("sessionInfo")) {
         return Collections.emptyList();
      }

      List<SessionModel> infos = new ArrayList<>();
      List<SRPrincipal> principals = sessionRepository.getActiveSessions();

      for(SRPrincipal principal : principals) {
         if(principal == null) {
            continue;
         }

         IdentityID userID = principal.getClientUserID();

         SessionModel.Builder info = SessionModel.builder();
         info.address(Tool.getRealIP(principal.getUser().getIPAddress()));
         info.dateCreated(principal.getAge());
         info.dateAccessed(!isLevelQualified("lastAccess") ? 0L :
                                 principal.getLastAccess());
         info.user(userID);
         info.id(principal.getSessionID());
         info.activeViewsheets(0);

         if(principal.getRoles() != null) {
            info.roles(Arrays.asList(principal.getRoles()));
         }

         if(principal.getGroups() != null) {
            info.groups(Arrays.asList(principal.getGroups()));
         }

         info.organization(principal.getOrgId());
         infos.add(info.build());
      }

      return infos;
   }

   /**
    * Logout.
    */
   public void logout(String sessionId) {
      if(sessionId == null || sessionId.trim().isEmpty()) {
         throw new IllegalArgumentException("The session ID is required");
      }

      for(SRPrincipal principal : sessionRepository.getActiveSessions()) {
         if(principal != null && sessionId.equals(principal.getSessionID())) {
            Map<String, MapSession> map = sessionRepository.findByIndexNameAndIndexValue(
               FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principal.getName());

            for(Map.Entry<String, MapSession> e : map.entrySet()) {
               SRPrincipal sessionPrincipal =
                  e.getValue().getAttribute(RepletRepository.PRINCIPAL_COOKIE);

               if(sessionPrincipal != null && sessionId.equals(sessionPrincipal.getSessionID())) {
                  sessionRepository.invalidate(e.getKey());
                  this.updateStatus();
                  return;
               }
            }
         }
      }

      updateStatus(); // make sure that the client has the updated session list
      LOG.warn("Cannot log out, session does not exist: {}", sessionId);
   }

   /**
    * Logout some session ids.
    */
   public void logout(String[] sessionIds) {
      if(sessionIds == null || sessionIds.length == 0) {
         throw new IllegalArgumentException("One or more session IDs is required");
      }

      for(String sessionId : sessionIds) {
         logout(sessionId);
      }
   }

   private List<TopUser> calculateTopNUsers() {
      UserResourceCalculator cal = new UserResourceCalculator(securityProvider, securityEngine);

      viewsheetService.calculateUserResource(cal);
      getUserAge(cal);
      return cal.getTopNUserResources();
   }

   @Override
   public void principalChanged(MapSessionRepository.PrincipalChangeEvent event) {
      updateStatus();
   }

   private final ViewsheetService viewsheetService;
   private final ServerClusterClient client;
   private final SecurityEngine securityEngine;
   private final SecurityProvider securityProvider;
   private final MonitoringDataService monitoringDataService;
   private final MapSessionRepository sessionRepository;
   private Cluster cluster;

   private static final String[] lowAttrs = {"sessionCount", "sessionInfo", "quotaInfo"};
   private static final String[] medAttrs = {"lastAccess"};
   private static final String[] highAttrs = {"failedLoginInfo"};
   private static final Logger LOG = LoggerFactory.getLogger(UserService.class);
}
