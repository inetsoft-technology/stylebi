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
package inetsoft.web.admin.security.user;

/*
 * Bug #76995: org ids are case-insensitive system-wide (lowercased storage buckets and
 * properties, case-insensitive org-boundary and ACL identity checks), but the explicit-id
 * branch of UserTreeService.createOrganization() (reached from the REST/Shell clone path) only
 * did an exact getOrganization(orgID) lookup, so an org "HOST-ORG" could be created alongside
 * "host-org". It must now reject ids equal ignoring case to any existing org id.
 */

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.security.AuthenticationProviderService;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class UserTreeServiceCreateOrganizationTest {
   @BeforeEach
   void setUp() {
      sUtilStatic = mockStatic(SUtil.class, withSettings().strictness(Strictness.LENIENT));
      sUtilStatic.when(() -> SUtil.getActionRecord(any(Principal.class), anyString(), any(), anyString()))
         .thenReturn(mock(ActionRecord.class));
      auditStatic = mockStatic(Audit.class, withSettings().strictness(Strictness.LENIENT));
      auditStatic.when(Audit::getInstance).thenReturn(mock(Audit.class));

      editProvider = mock(EditableAuthenticationProvider.class, withSettings().lenient());
      AuthenticationProviderService providerService =
         mock(AuthenticationProviderService.class, withSettings().lenient());
      when(providerService.getProviderByName("Primary")).thenReturn(editProvider);

      securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "org1" });
      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);

      principal = mock(Principal.class, withSettings().lenient());
      when(principal.getName()).thenReturn(new IdentityID("admin", "host-org").convertToKey());

      service = new UserTreeService(
         providerService, null, null, null, securityEngine, null, null, null, null, null, null,
         null, null, null, null, null, null);
   }

   @AfterEach
   void tearDown() {
      sUtilStatic.close();
      auditStatic.close();
   }

   @Test
   void explicitId_caseVariantOfExistingOrgId_rejectedAsDuplicateId() {
      // exact lookup misses (File provider map is case-sensitive)
      when(editProvider.getOrganization("HOST-ORG")).thenReturn(null);

      MessageException thrown = assertThrows(MessageException.class, () ->
         service.createOrganization("org1", "Primary", "New Org", "HOST-ORG", principal, "Str0ng!Passw0rd"));

      assertEquals(Catalog.getCatalog().getString("em.duplicateOrganizationID"), thrown.getMessage());
      // clone request: the source org must not be copied
      verify(editProvider, never()).copyOrganization(any(), any(), any(), any(), any(), any(), any(),
                                                     anyBoolean(), any());
      verify(editProvider, never()).addOrganization(any());
   }

   @Test
   void explicitId_mixedCaseStoredIdRequestedWithDifferentCase_rejectedAsDuplicateId() {
      // stored id is mixed-case, so a check that only lowercases the requested id would miss it
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "Acme" });
      when(editProvider.getOrganization("ACME")).thenReturn(null);

      MessageException thrown = assertThrows(MessageException.class, () ->
         service.createOrganization(null, "Primary", "New Org", "ACME", principal, null));

      assertEquals(Catalog.getCatalog().getString("em.duplicateOrganizationID"), thrown.getMessage());
      verify(editProvider, never()).addOrganization(any());
   }

   @Test
   void explicitId_exactExistingOrgId_rejectedAsDuplicateId() {
      when(editProvider.getOrganization("org1")).thenReturn(new FSOrganization("org1"));

      MessageException thrown = assertThrows(MessageException.class, () ->
         service.createOrganization(null, "Primary", "New Org", "org1", principal, null));

      assertEquals(Catalog.getCatalog().getString("em.duplicateOrganizationID"), thrown.getMessage());
      verify(editProvider, never()).addOrganization(any());
   }

   @Test
   void explicitId_distinctOrgId_passesIdCheck() {
      // control: a genuinely new id must not trip the id check; use a duplicate name so the
      // method stops at the (next) name check instead of running the full create flow.
      when(editProvider.getOrganization("org2")).thenReturn(null);
      when(editProvider.getOrgIdFromName("Taken Name")).thenReturn("org1");

      MessageException thrown = assertThrows(MessageException.class, () ->
         service.createOrganization(null, "Primary", "Taken Name", "org2", principal, null));

      assertEquals(Catalog.getCatalog().getString("em.duplicateOrganizationName"), thrown.getMessage());
   }

   // Bug #77082: org names and ids share one case-insensitive namespace, so an explicit-id
   // create must also reject a name equal to another org's id and an id equal to another
   // org's name (and a name equal to another org's name ignoring case).

   @Test
   void explicitId_nameEqualToAnotherOrgIdIgnoringCase_rejectedAsDuplicateName() {
      stubOrgOne();

      MessageException thrown = assertThrows(MessageException.class, () ->
         service.createOrganization(null, "Primary", "ORG1", "org2", principal, null));

      assertEquals(Catalog.getCatalog().getString("em.duplicateOrganizationName"), thrown.getMessage());
      verify(editProvider, never()).addOrganization(any());
   }

   @Test
   void explicitId_idEqualToAnotherOrgNameIgnoringCase_rejectedAsDuplicateId() {
      stubOrgOne();

      MessageException thrown = assertThrows(MessageException.class, () ->
         service.createOrganization(null, "Primary", "New Org", "ACME", principal, null));

      assertEquals(Catalog.getCatalog().getString("em.duplicateOrganizationID"), thrown.getMessage());
      verify(editProvider, never()).addOrganization(any());
   }

   @Test
   void explicitId_nameEqualToAnotherOrgNameDifferentCase_rejectedAsDuplicateName() {
      stubOrgOne();

      MessageException thrown = assertThrows(MessageException.class, () ->
         service.createOrganization(null, "Primary", "ACME", "org2", principal, null));

      assertEquals(Catalog.getCatalog().getString("em.duplicateOrganizationName"), thrown.getMessage());
      verify(editProvider, never()).addOrganization(any());
   }

   private void stubOrgOne() {
      FSOrganization org1 = new FSOrganization("org1");
      org1.setName("acme");
      when(securityProvider.getOrganization("org1")).thenReturn(org1);
      when(securityProvider.getOrganizationNames()).thenReturn(new String[]{ "Host Organization", "acme" });
   }

   private EditableAuthenticationProvider editProvider;
   private SecurityProvider securityProvider;
   private Principal principal;
   private UserTreeService service;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<Audit> auditStatic;
}
