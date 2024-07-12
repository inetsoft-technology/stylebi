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
package inetsoft.sree.web;

import inetsoft.report.internal.LicenseException;
import inetsoft.report.internal.UnlicensedUserNameException;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.util.Catalog;
import inetsoft.util.SingletonManager;
import inetsoft.util.profile.Profile;

import java.util.*;
import java.util.function.Supplier;

public abstract class SessionLicenseService implements SessionLicenseManager {
   /**
    * Gets an instance of a session license service, depending on the server
    * runtime license key. Returns null if the license is not a session license.
    * @return an instance of a session license service, or null if the service
    * cannot be provided.
    */
   public static SessionLicenseManager getSessionLicenseService() {
      return SingletonManager.getInstance(SessionLicenseManager.class);
   }

   /**
    * Gets a LicenseManager for managing Exploratory Analyzer sessions. Returns
    * null if the server does not have a viewer 'W' key.
    */
   public static SessionLicenseManager getViewerLicenseService() {
      return SingletonManager.getInstance(ViewerSessionService.class);
   }

   public static void resetServices() {
      SingletonManager.reset(SessionLicenseManager.class);
      SingletonManager.reset(ViewerSessionService.class);
   }

   /**
    * Concurrent session LicenseManager. A pool of licenses is used. No more
    * than pool.size() sessions may be active at one time.
    */
   private static class ConcurrentSessionService extends AbstractSessionService {

      private ConcurrentSessionService(Supplier<Integer> maxSessions) {
         this(maxSessions, true);
      }

      /**
       * Creates a Concurrent Session License Manager.
       * @param maxSessions Maximum sessions that can be active
       * @param logoutAfterFailure Whether to log the user out after a
       *                           LicenseException
       */
      private ConcurrentSessionService(Supplier<Integer> maxSessions,
                                       boolean logoutAfterFailure) {
         this.maxSessions = maxSessions;
         this.principals = new HashSet<>();
         this.logoutAfterFailure = logoutAfterFailure;
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public synchronized void newSession(SRPrincipal srPrincipal) {
         // @by stephenwebster, For California Leg. Counsel
         // After a lot of testing for an issue from the customer, we were not
         // able to figure out how the security manager list of users and the
         // license manager list of users became out of sync.  In general, the
         // user timeout and session listener should properly logout inactive
         // users.  However, to ensure we do not orphan users in the license
         // manager and lock out users, we will use the security manager's list
         // of active users as a master list.
         try {
            List<SRPrincipal> activeUsers =
               SecurityEngine.getSecurity().getActivePrincipalList();

            for(SRPrincipal principal : principals) {
               if(!activeUsers.contains(principal)) {
                  releaseSession(principal);
               }
            }
         }
         catch(Exception e) {
            //ignore logging from license manager
         }

         if(principals.add(srPrincipal)) {
            if(principals.size() > getMaxSessions()) {
               principals.remove(srPrincipal);

               if(logoutAfterFailure) {
                  SUtil.logout(srPrincipal);
               }

               LicenseManager.getInstance().addKeyViolation(
                  "Concurrent users exceeded - " + srPrincipal.getName(), null);
               final Catalog catalog = Catalog.getCatalog(srPrincipal);
               final String msg = String.format("%s %s", catalog.getString("common.sessionsExceed"),
                                                catalog.getString("common.contactAdmin"));

               throw new LicenseException(msg);
            }
         }
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public synchronized void releaseSession(SRPrincipal srPrincipal) {
         principals.remove(srPrincipal);
         Profile.getInstance().removeProfileInfo();
      }

      @Override
      public synchronized Set<SRPrincipal> getActiveSessions() {
         return Collections.unmodifiableSet(new HashSet<>(principals));
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public synchronized void dispose() {
         super.dispose();
         principals.clear();
      }

      private int getMaxSessions() {
         return maxSessions.get();
      }

      private final Supplier<Integer> maxSessions;
      private final boolean logoutAfterFailure;
      private final Set<SRPrincipal> principals;
   }

   /**
    * Named user LicenseManager. This manager uses a concurrent session pool
    * internally, but will only allow new sessions if the user principal is a
    * certain name. The allowed names are determined by a property setting.
    *
    * Additionally, only one session with a given name may be active (logged in)
    * at any one time. If a second user with the same name and different machine
    * tries to log in, it is a license error.
    *
    * If both these conditions pass, the manager delegates to a
    * ConcurrentSessionService.
    */
   private static class NamedSessionService implements SessionLicenseManager {
      private NamedSessionService() {
         logoutAfterFailure = true;

         // The named users are limited in the implementation of newSession() in this class. Make
         // the underlying session service unbounded so that someone creating multiple sessions
         // with the same user from one machine only takes one seat.
         if(SUtil.isCluster()) {
            sessionService = new ConcurrentSessionClusterService(() -> Integer.MAX_VALUE);
         }
         else {
            sessionService = new ConcurrentSessionService(() -> Integer.MAX_VALUE);
         }
      }

      private NamedSessionService(Supplier<Integer> maxSessions, boolean logoutAfterFailure) {
         this.logoutAfterFailure = logoutAfterFailure;

         if(SUtil.isCluster()) {
            sessionService = new ConcurrentSessionClusterService(maxSessions);
         }
         else {
            sessionService = new ConcurrentSessionService(maxSessions, logoutAfterFailure);
         }
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public void newSession(SRPrincipal srPrincipal) {
         Set<String> namedUsers = LicenseManager.getInstance().getNamedUsers();
         ClientInfo info = srPrincipal.getUser();
         // Feature #36409, the admin user's session should be taken when using log in as
         IdentityID loginUser = info.getLoginUserID();

         if(loginUser.equalsIgnoreCase(info.getUserIdentity())) {
            // LDAP allows case-insensitive login, use the actual user name in this case
            loginUser = info.getUserIdentity();
         }

         if(namedUsers != null &&!namedUsers.contains(loginUser)) {
            if(logoutAfterFailure) {
               SUtil.logout(srPrincipal);
            }

            LicenseManager.getInstance().addKeyViolation(
               "Named user exception - " + loginUser, null);
            throw new UnlicensedUserNameException(Catalog.getCatalog(srPrincipal).
               getString("Named User Not Allowed", loginUser));
         }

         Set<SRPrincipal> activeSessions = sessionService.getActiveSessions();
         Set<SRPrincipal> invalidSessions = new HashSet<>();

         for(SRPrincipal session : activeSessions) {
            ClientInfo activeInfo = session.getUser();
            IdentityID activeLoginUser = activeInfo.getLoginUserID();

            if(activeLoginUser.equals(loginUser)) {
               if(!activeInfo.getIPAddress().equals(info.getIPAddress())) {
                  invalidSessions.add(session);
               }
            }
         }

         // Feature #36409, invalidate old sessions for a named user when a new session is created
         for(SRPrincipal session : invalidSessions) {
            // this will also remove the session from the license service
            SUtil.logout(session, true);
         }

         try {
            sessionService.newSession(srPrincipal);
         }
         catch(LicenseException le) {
            sessionError(srPrincipal, le);
         }

      }

      /**
       * {@inheritDoc}
       */
      @Override
      public void releaseSession(SRPrincipal srPrincipal) {
         sessionService.releaseSession(srPrincipal);
      }

      @Override
      public Set<SRPrincipal> getActiveSessions() {
         return sessionService.getActiveSessions();
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public void dispose() {
         sessionService.dispose();
      }

      @Override
      public void close() throws Exception {
         dispose();
      }

      private void sessionError(SRPrincipal srPrincipal, LicenseException le) {
         if(logoutAfterFailure) {
            SUtil.logout(srPrincipal);
         }

         releaseSession(srPrincipal);

         throw le;
      }

      private final SessionLicenseManager sessionService;
      private final boolean logoutAfterFailure;
   }

   public static final class Reference
      extends SingletonManager.Reference<SessionLicenseManager>
   {
      @Override
      public synchronized SessionLicenseManager get(Object ... parameters) {
         if(manager == null) {
            LicenseManager licenseManager = LicenseManager.getInstance();
            final int sessions =
               licenseManager.getConcurrentSessionCount() + licenseManager.getViewerSessionCount();
            final Set<String> namedUsers = licenseManager.getNamedUsers();

            if(sessions > 0) {
               if(SUtil.isCluster()) {
                  manager =
                     new ConcurrentSessionClusterService(Reference::getAllowedViewerInstances);
               }
               else {
                  manager = new ConcurrentSessionService(Reference::getAllowedViewerInstances);
               }
            }
            else if(namedUsers != null) {
               manager = new NamedSessionService();
            }
         }

         return manager;
      }

      @Override
      public synchronized void dispose() {
         if(manager != null) {
            manager.dispose();
            manager = null;
         }
      }

      private static int getAllowedViewerInstances() {
         LicenseManager licenseManager = LicenseManager.getInstance();
         return licenseManager.getConcurrentSessionCount() + licenseManager.getViewerSessionCount()
                 + licenseManager.getNamedUserViewerSessionCount();
      }

      private SessionLicenseManager manager;
   }

   public static final class ViewerReference
      extends SingletonManager.Reference<ViewerSessionService>
   {
      @Override
      public synchronized ViewerSessionService get(Object ... parameters) {
         if(service == null) {
            if(getAllowedVCInstances() > 0 || isViewerOnlyLicense()) {
               SessionLicenseManager manager;

               if(SUtil.isCluster()) {
                  manager = new ConcurrentSessionClusterService(
                     ViewerReference::getAllowedVCInstances, false, "viewer_licenses.dat");
               }
               else {
                  manager = new ConcurrentSessionService(
                     ViewerReference::getAllowedVCInstances, false);
               }

               service = new ViewerSessionService(manager);
            }
            else if(LicenseManager.getInstance().getNamedUserViewerSessionCount() > 0) {
               SessionLicenseManager manager = new NamedSessionService(
                  ViewerReference::getAllowedVCNamedUsers, false);
               service = new ViewerSessionService(manager);
            }
         }

         return service;
      }

      @Override
      public synchronized void dispose() {
         if(service != null) {
            service.dispose();
            service = null;
         }
      }

      private static int getAllowedVCInstances() {
         return LicenseManager.getInstance().getConcurrentSessionCount();
      }

      private static int getAllowedVCNamedUsers() {
         return LicenseManager.getInstance().getNamedUserCount();
      }

      private static boolean isViewerOnlyLicense() {
         LicenseManager licenseManager = LicenseManager.getInstance();
         int sessions = licenseManager.getConcurrentSessionCount();
         int viewerSessions = licenseManager.getViewerSessionCount();

         if(sessions == 0 && viewerSessions == 0) {
            sessions = licenseManager.getNamedUserCount();
            viewerSessions = licenseManager.getNamedUserViewerSessionCount();
         }

         return sessions == 0 && viewerSessions > 0;
      }

      private ViewerSessionService service;
   }
}
