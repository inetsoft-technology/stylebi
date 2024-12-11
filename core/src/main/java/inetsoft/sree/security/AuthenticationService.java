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
package inetsoft.sree.security;

import inetsoft.mv.MVManager;
import inetsoft.report.internal.LicenseException;
import inetsoft.sree.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.web.SessionLicenseManager;
import inetsoft.sree.web.SessionLicenseService;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.ConnectionProcessor;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.*;
import inetsoft.util.audit.Audit;
import inetsoft.util.audit.SessionRecord;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.web.admin.monitoring.MonitorLevelService;
import inetsoft.web.admin.monitoring.StatusMetricsType;
import inetsoft.web.admin.user.FailedLoginModel;
import inetsoft.web.admin.user.UserMetrics;
import inetsoft.web.cluster.ServerClusterClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Class that supplies support for web authentication and session management.
 *
 * @since 12.3
 */
@SingletonManager.ShutdownOrder(after = InetsoftConfig.Reference.class)
public class AuthenticationService {
   /**
    * Creates a new instance of <tt>AuthenticationService</tt>.
    */
   public AuthenticationService() {
   }

   /**
    * Gets the shared instance of <tt>AuthenticationService</tt>.
    *
    * @return the shared instance.
    */
   public static AuthenticationService getInstance() {
      return SingletonManager.getInstance(AuthenticationService.class);
   }

   /**
    * Authenticates a user.
    *
    * @param userId           the log in name of the user.
    * @param loginAsUser      the user name to use for authentication.
    * @param password         the password of the user.
    * @param remoteHost       the host name of the user's machine.
    * @param remoteAddr       the IP address of the user's machine.
    * @param serverName       the name of the authenticating server.
    * @param locale           the name of the locale to use for the user.
    * @param anonymousAllowed <tt>true</tt> if anonymous log ins are allowed.
    * @param userMustExist    <tt>true</tt> if the user must exist in the security provider.
    * @param sessionId        the HTTP session identifier.
    * @param requestedUri     the URI requiring authentication.
    *
    * @return the principal identifying the user.
    *
    * @throws Exception if the user could not be authenticated.
    */
   public Principal authenticate(IdentityID userId, IdentityID loginAsUser, String password,
                                 String remoteHost, String remoteAddr, String serverName,
                                 String locale, Locale clientLocale, boolean anonymousAllowed,
                                 boolean userMustExist, String sessionId,
                                 String requestedUri)
      throws Exception
   {
      boolean anonymous = ClientInfo.ANONYMOUS.equals(userId.name);

      if(anonymous && password == null) {
         password = "";
      }

      Principal principal = null;

      // log action
      String xSessionId = XSessionService.createSessionID(XSessionService.USER, userId.name);
      String opType = SessionRecord.OP_TYPE_LOGON;
      Long time = System.currentTimeMillis();
      Timestamp opTimestamp = new Timestamp(time);
      IdentityID user = SUtil.getUserID(userId, loginAsUser);
      SessionRecord sessionRecord = new SessionRecord(
         user.convertToKey(), remoteAddr, null, null, xSessionId, opType, opTimestamp,
         SessionRecord.OP_STATUS_FAILURE, null);
      boolean isFailedLogin = false;

      try {
         if(userId != null && password != null) {
            try {
               String property = SreeEnv.getProperty("login.loginAs");
               IdentityID userName = userId;

               if("on".equals(property) && loginAsUser != null &&
                  loginAsUser.name.trim().length() > 0)
               {
                  userName = loginAsUser;
               }

               ClientInfo clientInfo = new ClientInfo(userName, remoteAddr, sessionId, clientLocale);
               clientInfo.setLoginUserName(userId);
               DefaultTicket ticket = new DefaultTicket(userId, password);
               principal = authenticate(clientInfo, ticket);

               if(principal instanceof XPrincipal) {
                  ((XPrincipal) principal).setProperty(SUtil.TICKET, userId + ":" + password);
                  ((XPrincipal) principal).setProperty(SUtil.LONGON_TIME, time + "");
               }
            }
            catch(Throwable exc) {
               LOG.error("An error prevented the user from being authenticated: {}", userId, exc);
               throw new Exception(exc.getMessage());
            }

            if(principal != null) {
               updateLocale(principal, userId, locale);

               if(principal instanceof SRPrincipal) {
                  setOlapAuthenticator((SRPrincipal) principal, userId, password);
                  initOrgID(((SRPrincipal) principal).getOrgId(), (SRPrincipal) principal);
               }
            }
         }

         if(principal != null &&
            (principal.getName().equals("") ||
            (!anonymous && principal.getName().equals(ClientInfo.ANONYMOUS))) &&
            SreeEnv.getProperty("security.provider") != null &&
            !SreeEnv.getProperty("security.provider").isEmpty() &&
            (!anonymousAllowed || userMustExist))
         {
            principal = null;
         }

         isFailedLogin = !addLoginRecord(
            principal, userId, loginAsUser, remoteAddr, serverName, requestedUri,
            sessionRecord);
      }
      catch(Exception e) {
         sessionRecord.setOpStatus(SessionRecord.OP_STATUS_FAILURE);
         sessionRecord.setOpError(e.getMessage());
         isFailedLogin = true;
         throw e;
      }
      finally {
         if(isFailedLogin) {
            int monitorLevel = MonitorLevelService.getMonitorLevel();

            if(monitorLevel >= MonitorLevelService.HIGH) {
               FailedLoginModel info = FailedLoginModel.builder()
                  .address(Tool.getRealIP(remoteAddr))
                  .timestamp(System.currentTimeMillis())
                  .user(userId)
                  .build();

               ServerClusterClient client = new ServerClusterClient(false);
               UserMetrics.Builder builder = UserMetrics.builder();
               UserMetrics oldMetrics = client.getMetrics(StatusMetricsType.USER_METRICS, null);

               if(oldMetrics != null) {
                  builder.from(oldMetrics);
               }

               builder.addFailedLogins(info);
               client.setMetrics(StatusMetricsType.USER_METRICS, builder.build());
            }
         }

         // @by jackz, fix bug1236271978588
         // just insert one record in audit when login if the same principal
         if((principal instanceof XPrincipal) &&
            !"true".equals(((XPrincipal) principal).getProperty("__login_audited")))
         {
            ((XPrincipal) principal).setProperty("__login_audited", "true");
            List<String> groups = Arrays.asList(((XPrincipal) principal).getGroups());
            List<String> roles = Arrays.stream(((XPrincipal) principal).getRoles()).map(id -> id.name).toList();

            sessionRecord.setUserGroup(groups);
            sessionRecord.setUserRole(roles);
            Audit.getInstance().auditSession(sessionRecord, principal);
         }
         else if(principal == null && userId != null) {
            User u = SecurityEngine.getSecurity().getSecurityProvider().getUser(userId);

            if(u != null) {
               sessionRecord.setUserGroup(Arrays.asList(u.getGroups()));
               sessionRecord.setUserRole(Arrays.stream(u.getRoles()).map(id -> id.name).toList());
            }

            Audit.getInstance().auditSession(sessionRecord, principal);
         }
      }

      return principal;
   }

   /**
    * Authenticates a user.
    *
    * @param userId           the log in name of the user.
    * @param password         the password of the user.
    * @param remoteAddr       the IP address of the user's machine.
    * @param locale           the name of the locale to use for the user.
    * @param anonymousAllowed <tt>true</tt> if anonymous log ins are allowed.
    *
    * @return the principal identifying the user.
    *
    * @throws Exception if the user could not be authenticated.
    */
   public Principal authenticate(IdentityID userId, String password, String locale,
                                 String remoteAddr, boolean anonymousAllowed)
      throws Exception
   {
      Principal principal = null;

      if(userId != null && password != null) {
         try {
            principal = authenticate(
               new ClientInfo(userId, remoteAddr), new DefaultTicket(userId, password));
         }
         catch(Throwable exc) {
            LOG.error("An error prevented the user from being authenticated: " +
                  userId, exc);
            throw new Exception("Failed to authentication user: " + userId, exc);
         }

         if(principal != null) {
            String loc = LocaleService.getInstance().getLocale(locale, principal);

            if(principal instanceof SRPrincipal) {
               ((SRPrincipal) principal).setProperty(SRPrincipal.LOCALE, loc);
               UserEnv.setProperty(principal, "locale", loc);
            }
         }
      }

      if(principal != null &&
         (principal.getName().equals("") ||
            principal.getName().equals(ClientInfo.ANONYMOUS)) &&
         SreeEnv.getProperty("security.provider") != null &&
         !anonymousAllowed)
      {
         principal = null;
      }

      return principal;
   }

   /**
    * Authenticates a remote user.
    *
    * @param user       identifying information for the remote user.
    * @param credential the authentication credential.
    *
    * @return a principal identifying the authenticated user or <tt>null</tt> if
    *         authentication failed.
    */
   public Principal authenticate(ClientInfo user, Object credential) {
      Principal principal = null;

      try {
         SecurityEngine engine = SecurityEngine.getSecurity();
         SecurityProvider provider = engine.getSecurityProvider();
         principal =
            SecurityEngine.getSecurity().authenticate(user, credential, provider);
      }
      catch(Exception e) {
         LOG.error("An error prevented user from being authenticated: " + user, e);
      }

      return principal;
   }

   /**
    * Adds a session for the specified user.
    *
    * @param principal the principal that identifies the user.
    *
    * @throws LicenseException if the session could not be added due to licensing
    *                          restrictions.
    */
   public void addSession(Principal principal) throws LicenseException {
      SessionLicenseManager sessionLicenseManager =
         SessionLicenseService.getSessionLicenseService();

      try {
         if(sessionLicenseManager != null) {
            sessionLicenseManager.newSession((SRPrincipal) principal);
         }
      }
      catch(LicenseException e) {
         boolean failed = true;

         if(SUtil.checkUserSessionTimeout()) {
            try {
               sessionLicenseManager.newSession((SRPrincipal) principal);
               failed = false;

               SessionEvent event = null;

               for(SessionListener listener : listeners) {
                  if(event == null) {
                     event = new SessionEvent(this, principal);
                  }

                  listener.loggedIn(event);
               }
            }
            catch(LicenseException ignore) {
            }
         }

         if(failed) {
            throw e;
         }
      }
   }

   /**
    * Signs a user out of the application and invalidates their session. Adds audit record.
    *
    * @param principal  the principal that identifies the user to log out.
    * @param remoteHost the host name of the user's machine.
    */
   public void logout(Principal principal, String remoteHost, String logoffReason) {
      logout(principal, remoteHost, logoffReason, false);
   }

   /**
    * Signs a user out of the application and invalidates their session. Adds audit record.
    *
    * @param principal  the principal that identifies the user to log out.
    * @param remoteHost the host name of the user's machine.
    */
   public void logout(Principal principal, String remoteHost, String logoffReason,
                      boolean invalidateSession)
   {
      String opType = SessionRecord.OP_TYPE_LOGOFF;
      List<String> userGroup = Arrays.asList(((XPrincipal) principal).getGroups());
      IdentityID[] roles = ((XPrincipal) principal).getRoles();
      List<String> userRole = roles != null
         ? Arrays.stream(roles).map(IdentityID::getName).collect(Collectors.toList())
         : new ArrayList<>();
      String sessionId = ((XPrincipal) principal).getSessionID();
      Timestamp opTimestamp = new Timestamp(System.currentTimeMillis());
      IdentityID principalID = IdentityID.getIdentityIDFromKey(principal.getName());
      SessionRecord sessionRecord = new SessionRecord(
         principalID.convertToKey(), remoteHost, userGroup, userRole,
         sessionId, opType, opTimestamp, SessionRecord.OP_STATUS_SUCCESS, null, logoffReason);
      String logonTime = ((XPrincipal) principal).getProperty(SUtil.LONGON_TIME);
      sessionRecord.setLogonTime(logonTime == null || logonTime.isEmpty() ?
                          new Timestamp(System.currentTimeMillis()) : new Timestamp(Long.parseLong(logonTime)));
      sessionRecord.setServerHostName(Tool.getHost());
      Timestamp diffMillis = new Timestamp(sessionRecord.getOpTimestamp().getTime() -
                                              sessionRecord.getLogonTime().getTime());
      sessionRecord.setDuration(diffMillis);

      try {
         logout(principal, invalidateSession);
      }
      catch(Exception e) {
         LOG.warn("Failed to log out user", e);
         sessionRecord.setOpStatus(SessionRecord.OP_STATUS_FAILURE);
         sessionRecord.setOpError(e.getMessage());
      }

      Audit.getInstance().auditSession(sessionRecord, principal);
   }

   /**
    * Signs a user out of the application and invalidates their session.
    *
    * @param principal the principal that identifies the user to log out.
    */
   public void logout(Principal principal) {
      logout(principal, false);
   }

   public void logout(Principal principal, boolean invalidateSession) {
      if(principal != null) {
         SessionEvent event = null;
         // Create a copy of the listeners before iterating because DashboardRegistry removes itself
         // as a listener when loggedOut() is called on it, causing a concurrent modification
         // exception.
         List<SessionListener> copy = new ArrayList<>(listeners);

         for(SessionListener listener : copy) {
            if(event == null) {
               event = new SessionEvent(this, principal, invalidateSession);
            }

            listener.loggedOut(event);
         }
      }
   }

   /**
    * Resets the session management state.
    */
   public void reset() {
      SessionLicenseService.resetServices();
   }

   /**
    * Adds a listener that is notified when a user logs in or logs out.
    *
    * @param l the listener to add.
    */
   public void addSessionListener(SessionListener l) {
      listeners.add(l);
   }

   /**
    * Removes a listener from the notification list.
    *
    * @param l the listener to remove.
    */
   public void removeSessionListener(SessionListener l) {
      listeners.remove(l);
   }

   /**
    * Updates the locale of an authenticated user.
    *
    * @param principal the principal identifying the user.
    * @param userId    the log in name of the user.
    * @param locale    the locale name.
    */
   private void updateLocale(Principal principal, IdentityID userId, String locale) {
      String localeName = LocaleService.getInstance().getLocale(locale, userId, principal);

      if(principal instanceof SRPrincipal) {
         ((SRPrincipal) principal).setProperty(SRPrincipal.LOCALE, localeName);
         UserEnv.setProperty(principal, "locale", localeName);

         if(localeName != null && localeName.length() > 0) {
            try {
               Locale lobj = Catalog.parseLocale(localeName);
               ((SRPrincipal) principal).setLocale(lobj);
            }
            catch(Exception ex) {
               // ignore it
            }
         }

         // @by yuz, fix bug1169115574187
         // update principal if locale changed
         ThreadContext.setPrincipal(principal);
      }
   }

   /**
    * Adds a audit record for an authentication attempt.
    *
    * @param principal      the principal identifying the user.
    * @param userId         the log in name of the user.
    * @param loginAsUser    the user name used for authentication.
    * @param remoteAddr     the remote address of the user.
    * @param serverName     the name of the authenticating server.
    * @param requestedUri   the URI requiring authentication.
    * @param sessionRecord  the audit record to be added.
    *
    * @return <tt>true</tt> if the log in was successful; <tt>false</tt> otherwise.
    */
   private boolean addLoginRecord(Principal principal, IdentityID userId, IdentityID loginAsUser,
                                  String remoteAddr, String serverName,
                                  String requestedUri, SessionRecord sessionRecord)
   {
      boolean result = true;

      if(principal != null) {
         sessionRecord.setUserGroup(Arrays.asList(((XPrincipal) principal).getGroups()));
         List<String> roles = Arrays.stream(((XPrincipal) principal).getRoles())
            .map(IdentityID::getName)
            .collect(Collectors.toList());
         sessionRecord.setUserRole(roles);
         sessionRecord.setUserSessionID(
            ((XPrincipal) principal).getSessionID());
         sessionRecord.setOpStatus(SessionRecord.OP_STATUS_SUCCESS);
         SUtil.loginRecord(userId, loginAsUser, remoteAddr, serverName, null);
      }
      else {
         sessionRecord.setOpStatus(SessionRecord.OP_STATUS_FAILURE);
         sessionRecord.setOpError("Login Failed");
         SUtil.loginRecord(userId, loginAsUser, remoteAddr, serverName, requestedUri);
         result = false;
      }

      return result;
   }

   /**
    * Set OLAP security information if necessary.
    */
   private void setOlapAuthenticator(XPrincipal principal, IdentityID userName,
                                     String password)
   {
      if(!"true".equals(SreeEnv.getProperty("olap.security.enabled"))) {
         return;
      }

      principal.setProperty("__OLAP_LOGIN_USER_NAME__", userName.convertToKey());
      principal.setProperty("__OLAP_LOGIN_USER_PASSWORD__", password);
   }

   /**
    * Initialize singletons that rely on indexed storage's org id
    */
   private void initOrgID(String orgID, XPrincipal principal) throws Exception {
      IndexedStorage indexedStorage = IndexedStorage.getIndexedStorage();

      if(!indexedStorage.isInitialized(orgID)) {
         DataSourceRegistry.getRegistry().init();
         MVManager.getManager().initMVDefMap();

         indexedStorage.setInitialized(orgID);
      }

      ConnectionProcessor.getInstance().setAdditionalDatasource(principal);
   }

   private final List<SessionListener> listeners = new CopyOnWriteArrayList<>();

   private static final Logger LOG = LoggerFactory.getLogger(AuthenticationService.class);
}
