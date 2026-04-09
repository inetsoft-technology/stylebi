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

package inetsoft.sree.web;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Service
public class SessionLicenseServiceProvider {
   @Autowired
   public SessionLicenseServiceProvider(LicenseManager licenseManager) {
      this.licenseManager = licenseManager;
   }

   /**
    * Gets an instance of a session license service, depending on the server
    * runtime license key. Returns null if the license is not a session license.
    * @return an instance of a session license service, or null if the service
    * cannot be provided.
    */
   public SessionLicenseManager getSessionLicenseManager() {
      SessionLicenseManager manager = sessionLicenseManager;

      if(manager == null) {
         lock.lock();

         try {
            manager = sessionLicenseManager;

            if(manager == null) {
               if(licenseManager.isHostedLicense()) {
                  manager = new SessionLicenseService.HostedSessionService();
               }
               else {
                  final int sessions = licenseManager.getConcurrentSessionCount() +
                     licenseManager.getViewerSessionCount();
                  final Set<IdentityID> namedUsers = licenseManager.getNamedUsers();

                  if(sessions > 0) {
                     Supplier<Integer> maxSession = () -> licenseManager.getConcurrentSessionCount() +
                        licenseManager.getViewerSessionCount() +
                        licenseManager.getNamedUserViewerSessionCount();

                     if(SUtil.isCluster()) {
                        manager = new ConcurrentSessionClusterService(maxSession);
                     }
                     else {
                        manager = new SessionLicenseService.ConcurrentSessionService(maxSession);
                     }
                  }
                  else if(namedUsers != null) {
                     manager = new SessionLicenseService.NamedSessionService();
                  }
               }

               sessionLicenseManager = manager;
            }
         }
         finally {
            lock.unlock();
         }
      }

      return manager;
   }

   /**
    * Gets a LicenseManager for managing Exploratory Analyzer sessions. Returns
    * null if the server does not have a viewer 'W' key.
    */
   public SessionLicenseManager getViewerLicenseService() {
      ViewerSessionService service = viewerSessionService;

      if(service == null) {
         lock.lock();

         try {
            service = viewerSessionService;

            if(service == null) {
               Supplier<Integer> allowedVCInstances = licenseManager::getConcurrentSessionCount;
               Supplier<Integer> allowedVCNamedUsers = licenseManager::getNamedUserCount;

               if(allowedVCInstances.get() > 0 || isViewerOnlyLicense()) {
                  SessionLicenseManager m;

                  if(SUtil.isCluster()) {
                     m = new ConcurrentSessionClusterService(
                        allowedVCInstances, false,
                        ViewerSessionService.class.getName() + ".licenseMap");
                  }
                  else {
                     m = new SessionLicenseService.ConcurrentSessionService(
                        allowedVCInstances, false);
                  }

                  service = new ViewerSessionService(m);
               }
               else if(licenseManager.getNamedUserViewerSessionCount() > 0) {
                  SessionLicenseManager m = new SessionLicenseService.NamedSessionService(
                     allowedVCNamedUsers, false);
                  service = new ViewerSessionService(m);
               }
               viewerSessionService = service;
            }
         }
         finally {
            lock.unlock();
         }
      }

      return service;
   }

   @PreDestroy
   public void resetServices() {
      lock.lock();

      try {
         SessionLicenseManager manager = sessionLicenseManager;
         sessionLicenseManager = null;

         if(manager != null) {
            manager.dispose();
         }

         ViewerSessionService service = viewerSessionService;
         viewerSessionService = null;

         if(service != null) {
            service.dispose();
         }
      }
      finally {
         lock.unlock();
      }
   }

   @EventListener(AuthenticationResetEvent.class)
   public void onAuthenticationReset(AuthenticationResetEvent event) {
      resetServices();
   }

   @EventListener(SessionLoggedOutEvent.class)
   public void onSessionLoggedOut(SessionLoggedOutEvent event) {
      lock.lock();

      try {
         SessionLicenseManager manager = sessionLicenseManager;

         if(manager instanceof AbstractSessionService s) {
            s.loggedOut(event);
         }
      }
      finally {
         lock.unlock();
      }
   }

   private boolean isViewerOnlyLicense() {
      int sessions = licenseManager.getConcurrentSessionCount();
      int viewerSessions = licenseManager.getViewerSessionCount();

      if(sessions == 0 && viewerSessions == 0) {
         sessions = licenseManager.getNamedUserCount();
         viewerSessions = licenseManager.getNamedUserViewerSessionCount();
      }

      return sessions == 0 && viewerSessions > 0;
   }

   private final LicenseManager licenseManager;
   private volatile SessionLicenseManager sessionLicenseManager;
   private volatile ViewerSessionService viewerSessionService;
   private final Lock lock = new ReentrantLock();
}
