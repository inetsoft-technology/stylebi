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

import inetsoft.uql.util.Identity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77990: Permission.writeXML/parseXML must round-trip every grantee type and the
 * per-organization "Grant All edited" flags.
 */
@Tag("core")
class PermissionXmlTest {
   private static final int[] TYPES = {
      Identity.USER, Identity.ROLE, Identity.GROUP, Identity.ORGANIZATION
   };

   @Test
   void roundTrip_allGranteeTypes_multipleGranteesPerType_preserved() throws Exception {
      Permission source = new Permission();
      source.setGrants(ResourceAction.READ, Identity.USER, Set.of(
         new Permission.PermissionIdentity("u1", "orgA"),
         new Permission.PermissionIdentity("u2", "orgB"),
         new Permission.PermissionIdentity("u1", "orgB")));
      source.setGrants(ResourceAction.READ, Identity.ROLE, Set.of(
         new Permission.PermissionIdentity("r1", null),
         new Permission.PermissionIdentity("r2", "orgA"),
         new Permission.PermissionIdentity("r1", "orgA")));
      source.setGrants(ResourceAction.READ, Identity.GROUP, Set.of(
         new Permission.PermissionIdentity("g1", "orgA"),
         new Permission.PermissionIdentity("g2", "orgA")));
      source.setGrants(ResourceAction.WRITE, Identity.ORGANIZATION, Set.of(
         new Permission.PermissionIdentity("orgA", "orgA"),
         new Permission.PermissionIdentity("orgB", "orgB")));
      source.setGrants(ResourceAction.WRITE, Identity.GROUP, Set.of(
         new Permission.PermissionIdentity("g3", null)));
      source.setGrants(ResourceAction.READ, Identity.ORGANIZATION, Set.of(
         new Permission.PermissionIdentity("orgC", "orgC")));

      Permission result = roundTrip(source);

      for(ResourceAction action : new ResourceAction[] { ResourceAction.READ, ResourceAction.WRITE }) {
         for(int type : TYPES) {
            assertEquals(source.getGrants(action, type, null), result.getGrants(action, type, null),
                         "grants for " + action + " type " + type);
         }
      }

      assertEquals(source, result);
   }

   @Test
   void roundTrip_groupGrantIsNotCopiedIntoOrganizationGrants() throws Exception {
      Permission source = new Permission();
      source.setGrants(ResourceAction.READ, Identity.GROUP, Set.of(
         new Permission.PermissionIdentity("g1", "orgA")));

      Permission result = roundTrip(source);

      assertEquals(Set.of(new Permission.PermissionIdentity("g1", "orgA")),
                   result.getGrants(ResourceAction.READ, Identity.GROUP, null));
      assertTrue(result.getGrants(ResourceAction.READ, Identity.ORGANIZATION, null).isEmpty());
   }

   @Test
   void roundTrip_orgEditedFlags_preserved() throws Exception {
      Permission source = new Permission();
      source.setGrants(ResourceAction.READ, Identity.USER, Set.of(
         new Permission.PermissionIdentity("u1", "orgA")));
      source.updateGrantAllByOrg("orgA", true);
      source.updateGrantAllByOrg("orgB", false);

      Permission result = roundTrip(source);

      // equals() ignores the orgEdited map, so assert it directly
      assertEquals(Map.of("orgA", true, "orgB", false), result.getOrgEditedGrantAll());
      assertTrue(result.hasOrgEditedGrantAll("orgA"));
      assertFalse(result.hasOrgEditedGrantAll("orgB"));
   }

   @Test
   void writeXML_nullNamesAndOrganizations_doesNotThrow() {
      Permission source = new Permission();
      source.setGrants(ResourceAction.READ, Identity.ROLE, Set.of(
         new Permission.PermissionIdentity("r1", null),
         new Permission.PermissionIdentity(null, "orgA"),
         new Permission.PermissionIdentity(null, null),
         new Permission.PermissionIdentity("r1", "orgA")));

      assertDoesNotThrow(() -> toXml(source));
   }

   private static Permission roundTrip(Permission source) throws Exception {
      String xml = toXml(source);
      Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new InputSource(new StringReader(xml)));
      Permission result = new Permission();
      result.parseXML(doc.getDocumentElement());
      return result;
   }

   private static String toXml(Permission permission) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         permission.writeXML(writer);
      }

      return buffer.toString();
   }
}
