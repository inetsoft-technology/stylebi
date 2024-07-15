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

import inetsoft.util.SingletonManager;

import java.util.ServiceLoader;

@SingletonManager.Singleton(OrganizationCache.Reference.class)
public interface OrganizationCache {
   Organization getOrganization();

   void clear();

   static OrganizationCache getInstance() {
      return SingletonManager.getInstance(OrganizationCache.class);
   }

   final class Reference extends SingletonManager.Reference<OrganizationCache> {
      @Override
      public OrganizationCache get(Object... parameters) {
         if(cache == null) {
            cache = ServiceLoader.load(OrganizationCache.class).findFirst().orElse(null);
         }

         return cache;
      }

      @Override
      public void dispose() {
         if(cache instanceof AutoCloseable closeable) {
            try {
               closeable.close();
            }
            catch(Exception ignore) {
            }
         }
      }

      private OrganizationCache cache;
   }
}
