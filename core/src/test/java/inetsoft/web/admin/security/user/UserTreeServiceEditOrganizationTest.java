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
 * Bug #77082: org names and org ids share one case-insensitive namespace, because bare
 * SECURITY_ORGANIZATION keys are passed sometimes as an id and sometimes as a name (e.g.
 * OrganizationController.editOrganization's @PermissionPath("oldName()")). The EM rename check
 * UserTreeService.checkDuplicateOrgIDs only compared name with name and id with id, so an org
 * could be renamed to another org's id (or have its id changed to another org's name). It also
 * paired getOrganizationNames()[i] with getOrganizationIDs()[i], which the chain de-duplicates
 * independently, so two orgs sharing a name made every rename throw
 * ArrayIndexOutOfBoundsException.
 */

import inetsoft.sree.security.*;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class UserTreeServiceEditOrganizationTest {
   @BeforeEach
   void setUp() {
      orgA = org("orga", "Org A");
      orgB = org("orgb", "acme");
      securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "orga", "orgb" });
      when(securityProvider.getOrganizationNames())
         .thenReturn(new String[]{ "Host Organization", "Org A", "acme" });
      when(securityProvider.getOrganization("host-org")).thenReturn(org("host-org", "Host Organization"));
      when(securityProvider.getOrganization("orga")).thenReturn(orgA);
      when(securityProvider.getOrganization("orgb")).thenReturn(orgB);

      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);

      service = new UserTreeService(
         null, null, null, null, securityEngine, null, null, null, null, null, null,
         null, null, null, null, null, null);
   }

   @Test
   void renameToAnotherOrgsId_rejectedAsDuplicateName() {
      assertDuplicateName(edit(orgA, "orgb", "orga"));
   }

   @Test
   void renameToAnotherOrgsIdDifferentCase_rejectedAsDuplicateName() {
      assertDuplicateName(edit(orgA, "ORGB", "orga"));
   }

   @Test
   void renameToDefaultOrgIdDifferentCase_rejectedAsDuplicateName() {
      assertDuplicateName(edit(orgA, "HOST-ORG", "orga"));
   }

   @Test
   void idChangedToAnotherOrgsName_rejectedAsDuplicateId() {
      assertDuplicateId(edit(orgA, "Org A", "acme"));
   }

   @Test
   void idChangedToAnotherOrgsNameDifferentCase_rejectedAsDuplicateId() {
      assertDuplicateId(edit(orgA, "Org A", "ACME"));
   }

   @Test
   void renameToAnotherOrgsNameDifferentCase_stillRejected() {
      assertDuplicateName(edit(orgA, "ACME", "orga"));
   }

   @Test
   void idChangedToAnotherOrgsIdDifferentCase_stillRejected() {
      assertDuplicateId(edit(orgA, "Org A", "ORGB"));
   }

   @Test
   void nameEqualToOwnId_cloneStyle_accepted() {
      // a cloned org has name == id; renaming to (or keeping) its own id is legal
      assertNull(edit(orgA, "orga", "orga"));
      Organization cloned = org("organization1", "organization1");
      when(securityProvider.getOrganizationIDs())
         .thenReturn(new String[]{ "host-org", "orga", "orgb", "organization1" });
      when(securityProvider.getOrganization("organization1")).thenReturn(cloned);
      assertNull(edit(cloned, "organization1", "neworg1"));
   }

   @Test
   void existingNameCollision_idOnlyChange_accepted() {
      // pre-existing data: org C's name already equals org B's id. Changing only C's id must
      // not be blocked by the collision on the (unchanged) name.
      Organization orgC = org("orgc", "orgb");
      addOrg(orgC);
      assertNull(edit(orgC, "orgb", "orgc2"));
   }

   @Test
   void existingIdCollision_nameOnlyChange_accepted() {
      // pre-existing data: org C's id already equals org B's name. Changing only C's name
      // must not be blocked by the collision on the (unchanged) id.
      Organization orgC = org("acme", "Org C");
      addOrg(orgC);
      assertNull(edit(orgC, "Org C Renamed", "acme"));
   }

   @Test
   void duplicateNamesInChain_noArrayIndexOutOfBounds() {
      // the chain de-duplicates names independently of ids, so the arrays can be misaligned
      Organization orgC = org("orgc", "acme");
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{ "host-org", "orga", "orgb", "orgc" });
      when(securityProvider.getOrganizationNames())
         .thenReturn(new String[]{ "Host Organization", "Org A", "acme" });
      when(securityProvider.getOrganization("orgc")).thenReturn(orgC);

      assertNull(edit(orgA, "Org A Renamed", "orga"));
      assertDuplicateName(edit(orgA, "ACME", "orga"));
   }

   private void addOrg(Organization org) {
      when(securityProvider.getOrganizationIDs())
         .thenReturn(new String[]{ "host-org", "orga", "orgb", org.getId() });
      when(securityProvider.getOrganizationNames())
         .thenReturn(new String[]{ "Host Organization", "Org A", "acme", org.getName() });
      when(securityProvider.getOrganization(org.getId())).thenReturn(org);
   }

   /**
    * Invokes the (private) checkDuplicateOrgIDs for a rename of {@code oldOrg}.
    *
    * @return the thrown exception, or {@code null} if the rename was accepted.
    */
   private Throwable edit(Organization oldOrg, String newName, String newId) {
      EditOrganizationPaneModel model = EditOrganizationPaneModel.builder()
         .name(newName)
         .oldName(oldOrg.getName())
         .id(newId)
         .build();

      try {
         Method method = UserTreeService.class.getDeclaredMethod(
            "checkDuplicateOrgIDs", EditOrganizationPaneModel.class, Organization.class);
         method.setAccessible(true);
         method.invoke(service, model, oldOrg);
         return null;
      }
      catch(InvocationTargetException e) {
         return e.getCause();
      }
      catch(ReflectiveOperationException e) {
         throw new AssertionError(e);
      }
   }

   private static void assertDuplicateName(Throwable thrown) {
      assertInstanceOf(MessageException.class, thrown, "expected rejection, got " + thrown);
      assertEquals(Catalog.getCatalog().getString("em.duplicateOrganizationName"), thrown.getMessage());
   }

   private static void assertDuplicateId(Throwable thrown) {
      assertInstanceOf(MessageException.class, thrown, "expected rejection, got " + thrown);
      assertEquals(Catalog.getCatalog().getString("em.duplicateOrganizationID"), thrown.getMessage());
   }

   private static Organization org(String id, String name) {
      FSOrganization org = new FSOrganization(id);
      org.setName(name);
      return org;
   }

   private Organization orgA;
   private Organization orgB;
   private SecurityProvider securityProvider;
   private UserTreeService service;
}
