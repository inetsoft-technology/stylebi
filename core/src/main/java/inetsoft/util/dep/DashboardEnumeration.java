/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util.dep;

import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardRegistry;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DashboardEnumeration implements the XAssetEnumeration interface,
 * generates a series of DashboardAssets, one at a time.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class DashboardEnumeration implements XAssetEnumeration<DashboardAsset> {
   /**
    * Constructor.
    */
   public DashboardEnumeration() {
      try {
         SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
         IdentityID[] susers = provider != null ? provider.getUsers() :
            new IdentityID[] { new IdentityID("anonymous", OrganizationManager.getCurrentOrgName())};
         IdentityID[] users = new IdentityID[susers.length + 1];
         System.arraycopy(susers, 0, users, 1, susers.length);
         List<Enumeration<DashboardAsset>> denums = Arrays.stream(users)
            .map(DashboardRegistryEnumeration::new)
            .collect(Collectors.toList());
         denum = new EnumEnumeration<>(denums);
      }
      catch(Exception e) {
         LOG.error("Failed to enumeration dashboard registries", e);
      }
   }

   /**
    * Tests if this enumeration contains more elements.
    * @return <code>true</code> if and only if this enumeration object contains
    * at least one more element to provide; <code>false</code> otherwise.
    */
   @Override
   public boolean hasMoreElements() {
      return denum != null && denum.hasMoreElements();
   }

   /**
    * Returns the next element of this enumeration if this enumeration object
    * has at least one more element to provide.
    * @return the next element of this enumeration.
    * @throws NoSuchElementException if no more elements exist.
    */
   @Override
   public DashboardAsset nextElement() {
      return denum.nextElement();
   }

   /**
    * Dashboard registry enumeration enumerates a dashboard registry.
    */
   private static class DashboardRegistryEnumeration
      implements DisposableEnumeration<DashboardAsset>
   {
      /**
       * Constructor.
       */
      DashboardRegistryEnumeration(IdentityID user) {
         this.user = user;
         DashboardRegistry registry = DashboardRegistry.getRegistry(user);
         names = registry.getDashboardNames();
      }

      /**
       * Tests if this enumeration contains more elements.
       * @return <code>true</code> if and only if this enumeration object
       * contains at least one more element to provide,
       * <code>false</code> otherwise.
       */
      @Override
      public boolean hasMoreElements() {
         return names != null && currentIndex < names.length;
      }

      /**
       * Returns the next element of this enumeration if this enumeration object
       * has at least one more element to provide.
       * @return the next element of this enumeration.
       */
      @Override
      public DashboardAsset nextElement() {
         String dashboard = names[currentIndex++];
         return new DashboardAsset(dashboard, user);
      }

      /**
       * Dispose the enumeration.
       */
      @Override
      public void dispose() {
         names = null;
      }

      private IdentityID user;
      private String[] names;
      private int currentIndex;
   }

   private Enumeration<DashboardAsset> denum;
   private static final Logger LOG = LoggerFactory.getLogger(DashboardEnumeration.class);
}
