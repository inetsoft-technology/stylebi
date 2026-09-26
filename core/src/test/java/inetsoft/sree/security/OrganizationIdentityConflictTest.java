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

import org.junit.jupiter.api.*;

import static inetsoft.sree.security.OrganizationIdentityConflict.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77082: org names and ids share one case-insensitive namespace.
 */
@Tag("core")
class OrganizationIdentityConflictTest {
   @BeforeEach
   void setUp() {
      provider = mock(AuthenticationProvider.class, withSettings().lenient());
      when(provider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "orga", "orgb", null });
      when(provider.getOrganization("host-org")).thenReturn(org("host-org", "Host Organization"));
      when(provider.getOrganization("orga")).thenReturn(orgA = org("orga", "Org A"));
      // no Organization object: the name is resolved through getOrgNameFromID
      when(provider.getOrgNameFromID("orgb")).thenReturn("acme");
   }

   @Test
   void create_checksAllCrossDirections() {
      assertEquals(NAME, find(provider, null, "ORGB", "new"));
      assertEquals(NAME, find(provider, null, "Acme", "new"));
      assertEquals(ID, find(provider, null, "New", "ACME"));
      assertEquals(ID, find(provider, null, "New", "OrgA"));
      assertEquals(NONE, find(provider, null, "New", "new"));
   }

   @Test
   void edit_excludesSelfAndUnchangedFields() {
      assertEquals(NONE, find(provider, orgA, "orga", "orga"));
      assertEquals(NONE, find(provider, orgA, "ORG A", "ORGA"));
      assertEquals(NAME, find(provider, orgA, "orgb", "orga"));
      assertEquals(ID, find(provider, orgA, "Org A", "acme"));
   }

   @Test
   void edit_caseVariantTwinIsStillChecked() {
      // exclusion is by exact id, so an existing "ORGA" twin is not treated as the edited org
      when(provider.getOrganizationIDs()).thenReturn(new String[]{ "orga", "ORGA" });
      when(provider.getOrganization("ORGA")).thenReturn(org("ORGA", "Twin"));
      assertEquals(NAME, find(provider, orgA, "twin", "orga"));
   }

   @Test
   void edit_caseOnlyIdChangeOntoTwin_rejected() {
      // pre-existing case-variant twins: changing "ORGA"'s id to "orga" would merge into "orga"
      when(provider.getOrganizationIDs()).thenReturn(new String[]{ "orga", "ORGA" });
      Organization twin = org("ORGA", "Twin");
      when(provider.getOrganization("ORGA")).thenReturn(twin);
      assertEquals(ID, find(provider, twin, "Twin", "orga"));
   }

   @Test
   void edit_caseOnlyRenameOntoAnotherOrgsName_rejected() {
      Organization orgC = org("orgc", "ORG A");
      when(provider.getOrganizationIDs()).thenReturn(new String[]{ "orga", "orgc" });
      when(provider.getOrganization("orgc")).thenReturn(orgC);
      // "ORG A" -> "Org A" is case-only for orgc, but "Org A" is org a's exact name
      assertEquals(NAME, find(provider, orgC, "Org A", "orgc"));
   }

   @Test
   void edit_caseOnlyChangeWithoutTwin_accepted() {
      // a legitimate case-only rename of the org's own name and id
      assertEquals(NONE, find(provider, orgA, "ORG A", "OrgA"));
      assertEquals(NONE, find(provider, orgA, "Org A", "ORGA"));
      assertEquals(NONE, find(provider, orgA, "org a", "orga"));
   }

   @Test
   void emptyInputsOrProvider_noConflict() {
      assertEquals(NONE, find(provider, null, null, ""));
      assertEquals(NONE, find(null, null, "orgb", "orgb"));
   }

   private static Organization org(String id, String name) {
      FSOrganization org = new FSOrganization(id);
      org.setName(name);
      return org;
   }

   private AuthenticationProvider provider;
   private Organization orgA;
}
