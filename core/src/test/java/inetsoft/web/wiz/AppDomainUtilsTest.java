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
package inetsoft.web.wiz;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.XPrincipal;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class AppDomainUtilsTest {
   @Test
   void getAppDomainsUsesOrgSpecificProperty() {
      XPrincipal principal = mock(XPrincipal.class);
      when(principal.getOrgId()).thenReturn("customerOrg");

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("app.domains.customerOrg", false, true))
            .thenReturn("customer.example;sub1.customer.example,sub2.customer.example");

         OrganizationDomains domains = AppDomainUtils.getAppDomains(principal);

         assertNotNull(domains);
         assertEquals("customer.example", domains.getId());
         assertIterableEquals(
            List.of("sub1.customer.example", "sub2.customer.example"),
            domains.getSubDomainIds());
      }
   }

   @Test
   void getAppDomainsReturnsNullForNullPropertyValue() {
      XPrincipal principal = mock(XPrincipal.class);
      when(principal.getOrgId()).thenReturn("customerOrg");

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("app.domains.customerOrg", false, true))
            .thenReturn(null);

         assertNull(AppDomainUtils.getAppDomains(principal));
      }
   }

   @Test
   void getAppDomainsReturnsNullForEmptyPropertyValue() {
      XPrincipal principal = mock(XPrincipal.class);
      when(principal.getOrgId()).thenReturn("customerOrg");

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("app.domains.customerOrg", false, true))
            .thenReturn("");

         assertNull(AppDomainUtils.getAppDomains(principal));
      }
   }

   @Test
   void getAppDomainsUsesFallbackPropertyWhenUserIsNull() {
      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("app.domains", false, true))
            .thenReturn("global.example");

         OrganizationDomains domains = AppDomainUtils.getAppDomains(null);

         assertNotNull(domains);
         assertEquals("global.example", domains.getId());
      }
   }

   @Test
   void setAppDomainsUsesFallbackPropertyWhenOrgIdIsNull() {
      XPrincipal principal = mock(XPrincipal.class);
      when(principal.getOrgId()).thenReturn(null);

      OrganizationDomains domains = new OrganizationDomains();
      domains.setId("global.example");

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class);
          MockedStatic<OrganizationManager> orgManagerStatic = mockStatic(OrganizationManager.class))
      {
         OrganizationManager orgManager = mock(OrganizationManager.class);
         orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
         when(orgManager.isSiteAdmin(principal)).thenReturn(true);

         AppDomainUtils.setAppDomains(domains, principal);

         sreeEnv.verify(() -> SreeEnv.setProperty("app.domains", "global.example", true));
      }
   }

   @Test
   void setAppDomainsThrowsForNonAdminUser() {
      XPrincipal principal = mock(XPrincipal.class);
      when(principal.getOrgId()).thenReturn("customerOrg");

      OrganizationDomains domains = new OrganizationDomains();
      domains.setId("attacker.example");

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class);
          MockedStatic<OrganizationManager> orgManagerStatic = mockStatic(OrganizationManager.class))
      {
         OrganizationManager orgManager = mock(OrganizationManager.class);
         orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
         when(orgManager.isSiteAdmin(principal)).thenReturn(false);
         when(orgManager.isOrgAdmin(principal)).thenReturn(false);

         assertThrows(SecurityException.class,
                      () -> AppDomainUtils.setAppDomains(domains, principal));

         sreeEnv.verifyNoInteractions();
      }
   }

   @Test
   void setAppDomainsAllowsOrgAdminEvenWhenNotSiteAdmin() {
      XPrincipal principal = mock(XPrincipal.class);
      when(principal.getOrgId()).thenReturn("customerOrg");

      OrganizationDomains domains = new OrganizationDomains();
      domains.setId("customer.example");

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class);
          MockedStatic<OrganizationManager> orgManagerStatic = mockStatic(OrganizationManager.class))
      {
         OrganizationManager orgManager = mock(OrganizationManager.class);
         orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
         when(orgManager.isSiteAdmin(principal)).thenReturn(false);
         when(orgManager.isOrgAdmin(principal)).thenReturn(true);

         AppDomainUtils.setAppDomains(domains, principal);

         sreeEnv.verify(() -> SreeEnv.setProperty(
            "app.domains.customerOrg", "customer.example", true));
      }
   }

   @Test
   void getAppDomainsUsesFallbackPropertyForNonXPrincipal() {
      Principal principal = () -> "service-account";

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("app.domains", false, true))
            .thenReturn("fallback.example");

         OrganizationDomains domains = AppDomainUtils.getAppDomains(principal);

         assertNotNull(domains);
         assertEquals("fallback.example", domains.getId());
      }
   }

   @Test
   void getAppDomainReturnsPrimaryDomainId() {
      XPrincipal principal = mock(XPrincipal.class);
      when(principal.getOrgId()).thenReturn("customerOrg");

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("app.domains.customerOrg", false, true))
            .thenReturn("customer.example;sub.customer.example");

         assertEquals("customer.example", AppDomainUtils.getAppDomain(principal));
      }
   }

   @Test
   void setAppDomainsWritesOrgSpecificProperty() {
      XPrincipal principal = mock(XPrincipal.class);
      when(principal.getOrgId()).thenReturn("customerOrg");

      OrganizationDomains domains = new OrganizationDomains();
      domains.setId("customer.example");
      domains.setSubDomainIds(List.of("sub.customer.example"));

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class);
          MockedStatic<OrganizationManager> orgManagerStatic = mockStatic(OrganizationManager.class))
      {
         OrganizationManager orgManager = mock(OrganizationManager.class);
         orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
         when(orgManager.isSiteAdmin(principal)).thenReturn(true);

         AppDomainUtils.setAppDomains(domains, principal);

         sreeEnv.verify(() -> SreeEnv.setProperty(
            "app.domains.customerOrg", "customer.example;sub.customer.example", true));
      }
   }
}
