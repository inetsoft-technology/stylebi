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

@SingletonManager.Singleton(ElasticLicenseService.Reference.class)
public interface ElasticLicenseService {
   long getRemainingHours(License license);

   long getGracePeriodHours(License license);

   void removeLicense(String licenseKey);

   void startElasticPolling(License license);

   static ElasticLicenseService getInstance() {
      return SingletonManager.getInstance(ElasticLicenseService.class);
   }

   final class Reference extends SingletonManager.Reference<ElasticLicenseService> {
      @Override
      public ElasticLicenseService get(Object... parameters) {
         if(instance == null) {
            try {
               instance = ServiceLoader.load(ElasticLicenseService.class).iterator().next();
            }
            catch(Exception e) {
               instance = new NoopElasticLicenseService();
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

      private ElasticLicenseService instance;
   }
}
