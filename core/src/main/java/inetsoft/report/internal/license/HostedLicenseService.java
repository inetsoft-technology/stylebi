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

package inetsoft.report.internal.license;

import inetsoft.util.SingletonManager;

import java.util.ServiceLoader;

@SingletonManager.Singleton(HostedLicenseService.Reference.class)
public interface HostedLicenseService {
   long getRemainingHours(String licenseKey, String orgId, String user);

   long getGracePeriodHours(String licenseKey, String orgId, String user);

   boolean startSession(License license, String orgId, String user);

   void stopSession(String licenseKey, String orgId, String user);

   void removeLicense(String licenseKey);

   static HostedLicenseService getInstance() {
      return SingletonManager.getInstance(HostedLicenseService.class);
   }

   final class Reference extends SingletonManager.Reference<HostedLicenseService> {
      @Override
      public HostedLicenseService get(Object... parameters) {
         if(instance == null) {
            try {
               instance = ServiceLoader.load(HostedLicenseService.class).iterator().next();
            }
            catch(Exception e) {
               instance = new NoopHostedLicenseService();
            }
         }

         return instance;
      }

      @Override
      public void dispose() {
         if(instance instanceof AutoCloseable closeable) {
            try {
               closeable.close();
            }
            catch(Exception ignore) {
            }
         }

         instance = null;
      }

      private HostedLicenseService instance;
   }
}
