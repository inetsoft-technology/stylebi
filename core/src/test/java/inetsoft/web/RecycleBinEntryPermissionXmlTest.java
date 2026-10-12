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
package inetsoft.web;

import inetsoft.sree.security.Permission;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.util.Identity;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77990: RecycleBin.Entry is the only caller of Permission.writeXML/parseXML. The entry's
 * XML must carry every grantee type and the per-organization "Grant All edited" flags, and an
 * entry document whose permission holds orgEdited at the permission level (the only place the
 * writer has ever put it) must parse those flags back.
 */
@Tag("core")
class RecycleBinEntryPermissionXmlTest {
   @Test
   void entryRoundTrip_preservesAllGranteeTypesAndOrgEditedFlags() throws Exception {
      Permission permission = new Permission();
      permission.setGrants(ResourceAction.READ, Identity.USER, Set.of(
         new Permission.PermissionIdentity("u1", "orgA"),
         new Permission.PermissionIdentity("u2", "orgA")));
      permission.setGrants(ResourceAction.READ, Identity.ROLE, Set.of(
         new Permission.PermissionIdentity("r1", null),
         new Permission.PermissionIdentity("r2", "orgA")));
      permission.setGrants(ResourceAction.READ, Identity.GROUP, Set.of(
         new Permission.PermissionIdentity("g1", "orgA"),
         new Permission.PermissionIdentity("g2", "orgA")));
      permission.setGrants(ResourceAction.WRITE, Identity.ORGANIZATION, Set.of(
         new Permission.PermissionIdentity("orgA", "orgA")));
      permission.updateGrantAllByOrg("orgA", true);

      RecycleBin.Entry entry = new RecycleBin.Entry();
      entry.setPath("Recycle Bin/sheet");
      entry.setName("sheet");
      entry.setPermission(permission);

      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      entry.writeXML(writer);
      writer.flush();

      RecycleBin.Entry back = parse(buf.toString());
      Permission result = back.getPermission();

      assertNotNull(result);
      assertEquals("sheet", back.getName());

      for(ResourceAction action : new ResourceAction[] { ResourceAction.READ, ResourceAction.WRITE }) {
         for(int type : new int[] { Identity.USER, Identity.ROLE, Identity.GROUP, Identity.ORGANIZATION }) {
            assertEquals(permission.getGrants(action, type, null), result.getGrants(action, type, null),
                         "grants for " + action + " type " + type);
         }
      }

      assertEquals(Map.of("orgA", true), result.getOrgEditedGrantAll());
   }

   @Test
   void parsesPermissionLevelOrgEditedFromFixedDocument() throws Exception {
      String xml =
         "<entry>\n" +
         "<name><![CDATA[sheet]]></name>\n" +
         "<permission>\n" +
         "  <grant action=\"READ\">\n" +
         "<user><name><![CDATA[u1]]></name><organization><![CDATA[orgA]]></organization></user>\n" +
         "<organization><name><![CDATA[orgA]]></name><organization><![CDATA[orgA]]></organization></organization>\n" +
         "  </grant>\n" +
         "<orgEdited><orgEditedElement><name><![CDATA[orgA]]></name>" +
         "<organization><![CDATA[true]]></organization></orgEditedElement></orgEdited>\n" +
         "</permission>\n" +
         "</entry>\n";

      Permission result = parse(xml).getPermission();

      assertEquals(Set.of(new Permission.PermissionIdentity("u1", "orgA")),
                   result.getGrants(ResourceAction.READ, Identity.USER, null));
      assertEquals(Set.of(new Permission.PermissionIdentity("orgA", "orgA")),
                   result.getGrants(ResourceAction.READ, Identity.ORGANIZATION, null));
      assertTrue(result.getGrants(ResourceAction.READ, Identity.GROUP, null).isEmpty());
      assertEquals(Map.of("orgA", true), result.getOrgEditedGrantAll());
   }

   private static RecycleBin.Entry parse(String xml) throws Exception {
      RecycleBin.Entry entry = new RecycleBin.Entry();
      entry.parseXML(Tool.parseXML(new ByteArrayInputStream(
         xml.getBytes(StandardCharsets.UTF_8))).getDocumentElement());
      return entry;
   }
}
