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
package inetsoft.sree.security;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.db.DatabaseAuthenticationProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79542: an organization is "deleted" only when every organization-storing provider is
 * healthy and none lists it. Anything unknown keeps the session.
 */
@Tag("core")
class OrganizationExistenceTest {
   private static final String HOST = Organization.getDefaultOrganizationID();
   private static final String SELF = Organization.getSelfOrganizationID();

   @Test
   void orgMissingFromHealthyFileProviderIsDeleted() {
      AuthenticationChain chain = chainOf(file(HOST, "org1"));

      assertEquals(Set.of("gone"), OrganizationExistence.findDeleted(chain, List.of("gone", "org1")));
   }

   // the only tenant org is deleted: nothing may block it
   @Test
   void onlyNonHostOrgDeletedIsDeleted() {
      AuthenticationChain chain = chainOf(file(HOST));

      assertEquals(Set.of("gone"), OrganizationExistence.findDeleted(chain, List.of("gone")));
   }

   @Test
   void everyNonHostOrgDeletedAreAllDeleted() {
      AuthenticationChain chain = chainOf(file(HOST));

      assertEquals(Set.of("a", "b"), OrganizationExistence.findDeleted(chain, List.of("a", "b")));
   }

   @Test
   void listedOrgIsKeptAndComparisonIgnoresCase() {
      AuthenticationChain chain = chainOf(file(HOST, "Org1"));

      assertTrue(OrganizationExistence.findDeleted(chain, List.of("org1", "ORG1")).isEmpty());
   }

   @Test
   void orgTheChainStillResolvesIsKept() {
      AuthenticationChain chain = chainOf(file(HOST));
      when(chain.getOrganization("odd")).thenReturn(new Organization("odd"));

      assertTrue(OrganizationExistence.findDeleted(chain, List.of("odd")).isEmpty());
   }

   @Test
   void hostAndSelfOrgsAreNeverDeleted() {
      AuthenticationChain chain = chainOf(db("other"));

      assertTrue(OrganizationExistence.findDeleted(chain, List.of(HOST, SELF, HOST.toUpperCase()))
                    .isEmpty());
   }

   @Test
   void nullAndEmptyOrgsAreKept() {
      AuthenticationChain chain = chainOf(file(HOST));

      assertTrue(OrganizationExistence.findDeleted(chain, java.util.Arrays.asList(null, "")).isEmpty());
      assertTrue(OrganizationExistence.findDeleted(null, List.of("gone")).isEmpty());
   }

   @Test
   void providerThatThrowsKeepsEverything() {
      FileAuthenticationProvider file = file(HOST);
      when(file.getOrganizationIDs()).thenThrow(new RuntimeException("storage closed"));

      assertTrue(OrganizationExistence.findDeleted(chainOf(file), List.of("gone")).isEmpty());
   }

   @Test
   void emptyChainKeepsEverything() {
      assertTrue(OrganizationExistence.findDeleted(chainOf(), List.of("gone")).isEmpty());
   }

   @Test
   void loadingChainKeepsEverything() {
      AuthenticationChain chain = chainOf(file(HOST));
      when(chain.isLoading()).thenReturn(true);

      assertTrue(OrganizationExistence.findDeleted(chain, List.of("gone")).isEmpty());
   }

   @Test
   void loadingProviderKeepsEverything() {
      FileAuthenticationProvider file = file(HOST);
      when(file.isLoading()).thenReturn(true);

      assertTrue(OrganizationExistence.findDeleted(chainOf(file), List.of("gone")).isEmpty());
   }

   @Test
   void emptyOrganizationListKeepsEverything() {
      AuthenticationChain chain = chainOf(db());

      assertTrue(OrganizationExistence.findDeleted(chain, List.of("gone")).isEmpty());
   }

   @Test
   void fileStoreWithoutHostOrgIsUnhealthy() {
      AuthenticationChain chain = chainOf(file("org1"));

      assertTrue(OrganizationExistence.findDeleted(chain, List.of("gone")).isEmpty());
   }

   // File + Database: the org lives only in the database and its query returned nothing; the
   // union is non-empty, so only the per-provider health test protects the session
   @Test
   void emptyDatabaseVetoesAFileAndDatabaseChain() {
      AuthenticationChain chain = chainOf(file(HOST), db());

      assertTrue(OrganizationExistence.findDeleted(chain, List.of("dbOrg")).isEmpty());
   }

   @Test
   void throwingDatabaseVetoesAFileAndDatabaseChain() {
      DatabaseAuthenticationProvider db = db("dbOrg");
      when(db.getOrganizationIDs()).thenThrow(new RuntimeException("connection refused"));
      AuthenticationChain chain = chainOf(file(HOST), db);

      assertTrue(OrganizationExistence.findDeleted(chain, List.of("dbOrg")).isEmpty());
   }

   @Test
   void loadingDatabaseVetoesAFileAndDatabaseChain() {
      DatabaseAuthenticationProvider db = db("dbOrg");
      when(db.isLoading()).thenReturn(true);
      AuthenticationChain chain = chainOf(file(HOST), db);

      assertTrue(OrganizationExistence.findDeleted(chain, List.of("dbOrg")).isEmpty());
   }

   @Test
   void orgListedByTheDatabaseIsKeptInAFileAndDatabaseChain() {
      AuthenticationChain chain = chainOf(file(HOST), db("dbOrg"));

      assertTrue(OrganizationExistence.findDeleted(chain, List.of("dbOrg")).isEmpty());
   }

   @Test
   void orgRemovedFromTheDatabaseIsDeletedInAFileAndDatabaseChain() {
      AuthenticationChain chain = chainOf(file(HOST), db("other"));

      assertEquals(Set.of("dbOrg"), OrganizationExistence.findDeleted(chain, List.of("dbOrg")));
   }

   // an LDAP or virtual chain knows the default org only; an SSO principal of another org stays
   @Test
   void chainWithoutAnOrganizationStoringProviderKeepsEverything() {
      AuthenticationProvider ldap = mock(AuthenticationProvider.class);
      when(ldap.getOrganizationIDs()).thenReturn(new String[] { HOST });
      AuthenticationChain chain = chainOf(ldap, mock(VirtualAuthenticationProvider.class));

      assertTrue(OrganizationExistence.findDeleted(chain, List.of("ssoOrg")).isEmpty());
   }

   @Test
   void isDeletedIsFalseWhenNotMultiTenant() {
      try(MockedStatic<SUtil> util = mockStatic(SUtil.class)) {
         util.when(SUtil::isMultiTenant).thenReturn(false);

         assertFalse(OrganizationExistence.isDeleted("gone"));
         assertTrue(OrganizationExistence.findDeleted(List.of("gone")).isEmpty());
      }
   }

   @Test
   void isDeletedUsesTheSecurityProviderWhenMultiTenant() {
      AuthenticationChain chain = chainOf(file(HOST, "org1"));
      SecurityProvider provider = mock(SecurityProvider.class);
      when(provider.getAuthenticationProvider()).thenReturn(chain);
      SecurityEngine engine = mock(SecurityEngine.class);
      when(engine.getSecurityProvider()).thenReturn(provider);

      try(MockedStatic<SUtil> util = mockStatic(SUtil.class);
          MockedStatic<SecurityEngine> security = mockStatic(SecurityEngine.class))
      {
         util.when(SUtil::isMultiTenant).thenReturn(true);
         security.when(SecurityEngine::getSecurity).thenReturn(engine);

         assertTrue(OrganizationExistence.isDeleted("gone"));
         assertFalse(OrganizationExistence.isDeleted("org1"));
         assertFalse(OrganizationExistence.isDeleted(HOST));
         assertFalse(OrganizationExistence.isDeleted(null));
      }
   }

   private static AuthenticationChain chainOf(AuthenticationProvider... providers) {
      AuthenticationChain chain = mock(AuthenticationChain.class);
      when(chain.getProviders()).thenReturn(List.of(providers));
      return chain;
   }

   private static FileAuthenticationProvider file(String... orgs) {
      FileAuthenticationProvider provider = mock(FileAuthenticationProvider.class);
      when(provider.getOrganizationIDs()).thenReturn(orgs);
      return provider;
   }

   private static DatabaseAuthenticationProvider db(String... orgs) {
      DatabaseAuthenticationProvider provider = mock(DatabaseAuthenticationProvider.class);
      when(provider.getOrganizationIDs()).thenReturn(orgs);
      return provider;
   }
}
