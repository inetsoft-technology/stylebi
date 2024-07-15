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
package inetsoft.sree.web;

import inetsoft.sree.security.SRPrincipal;
import inetsoft.util.SingletonManager;

import java.util.*;
import java.util.concurrent.*;

/**
 * Class which wraps a SessionLicenseManager for the purpose of managing
 * Viewer Licenses. Licenses are managed by the wrapped class. This class
 * adds a heartbeat management mechanism to recover Viewer Licenses after
 * the Visual Composer is closed.
 */
@SingletonManager.Singleton(SessionLicenseService.ViewerReference.class)
public class ViewerSessionService implements SessionLicenseManager {
   public ViewerSessionService(SessionLicenseManager manager) {
      this.manager = manager;
      this.heartbeats = new ConcurrentHashMap<>();
      this.runner = Executors.newSingleThreadScheduledExecutor();

      this.runner.scheduleAtFixedRate(
         new LicenseCleaner(), 1L, 60L, TimeUnit.SECONDS);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void newSession(SRPrincipal srPrincipal) {
      manager.newSession(srPrincipal);
      heartbeats.put(srPrincipal, System.currentTimeMillis());
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void releaseSession(SRPrincipal srPrincipal) {
      manager.releaseSession(srPrincipal);
      heartbeats.remove(srPrincipal);
   }

   @Override
   public Set<SRPrincipal> getActiveSessions() {
      return manager.getActiveSessions();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void dispose() {
      manager.dispose();
      runner.shutdownNow();
   }

   @Override
   public void close() throws Exception {
      dispose();
   }

   private final SessionLicenseManager manager;
   private final Map<SRPrincipal, Long> heartbeats;
   private final ScheduledExecutorService runner;

   private class LicenseCleaner implements Runnable {

      @Override
      public void run() {
         final Long currentTime = System.currentTimeMillis();
         Iterator<Map.Entry<SRPrincipal, Long>> iterator = heartbeats.entrySet().iterator();

         while(iterator.hasNext()) {
            Map.Entry<SRPrincipal, Long> entry = iterator.next();
            long timestamp = entry.getValue();

            if((currentTime - timestamp) > 120000L) {
               iterator.remove();
               manager.releaseSession(entry.getKey());
            }
         }
      }
   }
}
