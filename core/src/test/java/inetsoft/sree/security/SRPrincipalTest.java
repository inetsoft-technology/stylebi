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

/*
 * SRPrincipal scenario table
 *
 * [CreateUser: sso-user]         principal has SSO groups/roles                                      -> createUser() returns a populated User
 * [CreateUser: internal]         principal marked as internal                                        -> createUser() returns null
 * [CreateUser: no-membership]    principal has no groups and no roles                                -> createUser() returns null
 * [Copy: constructor]            copy constructor receives a fully populated principal               -> all copied fields are reproduced in the new instance
 * [Equals: non-fake]             non-fake principals with same identity, client, and secureID        -> equal; differing secureID or null argument → not equal
 * [Locale: property-sync]        setLocale writes LOCALE property when absent, skips when present   -> getProperty(LOCALE) reflects the first locale set; later calls do not overwrite it
 * [Session: valid-states]        sref null (no session set) or sref pointing to a live object       -> isValid() is true in both cases; getSession() returns the live object
 * [Identifier: internal]         principal marked as __internal__                                   -> toIdentifier() returns plain name with no SSO encoding
 * [Externalizable: round-trip]   principal has arrays, properties, params, locale                   -> readExternal() restores all persisted fields
 * [XML: round-trip]              principal written as XML                                            -> parseXML() restores all XML fields
 * [XML: missing-required-tags]   XML omits any one of clientInfo/secureID/age/accessed/properties   -> parseXML() throws IOException naming the missing element
 *
 */

import inetsoft.sree.ClientInfo;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.XSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class SRPrincipalTest {
   private static final String ORG_A = "orgA";

   // [CreateUser: sso-user] principals with SSO memberships create a lightweight User view
   @Test
   void createUser_withGroupsOrRolesReturnsUser() {
      try(MockedStatic<XSessionService> sessionService = mockSessionService()) {
         SRPrincipal principal = newPrincipal();
         principal.setLocale(Locale.US);

         User user = principal.createUser();

         assertNotNull(user);
         assertEquals(principal.getIdentityID(), user.getIdentityID());
         assertArrayEquals(principal.getGroups(), user.getGroups());
         assertArrayEquals(principal.getRoles(), user.getRoles());
         assertEquals("en_US", user.getLocale());
      }
   }

   // [CreateUser: internal] internal principals are not converted into SSO users
   @Test
   void createUser_internalPrincipalReturnsNull() {
      try(MockedStatic<XSessionService> sessionService = mockSessionService()) {
         SRPrincipal principal = newPrincipal();
         principal.setProperty("__internal__", "true");

         assertNull(principal.createUser());
      }
   }

   // [CreateUser: no-membership] principals without groups or roles do not produce a User
   @Test
   void createUser_withoutGroupsAndRolesReturnsNull() {
      try(MockedStatic<XSessionService> sessionService = mockSessionService()) {
         SRPrincipal principal = new SRPrincipal(
            new ClientInfo(new IdentityID("alice", ORG_A), "10.0.0.1", "session-1"),
            new IdentityID[0], new String[0], ORG_A, 42L);

         assertNull(principal.createUser());
      }
   }

   // [Copy: constructor] copy constructor reproduces all fields from the source principal
   @Test
   void copyConstructor_copiesAllFields() {
      try(MockedStatic<XSessionService> sessionService = mockSessionService()) {
         SRPrincipal original = newPrincipal();
         original.setProperty("badge", "gold");
         original.setParameter("pageSize", 50);
         original.setLastAccess(777000L);
         original.setLocale(Locale.JAPAN);

         SRPrincipal copy = new SRPrincipal(original);

         assertEquals(original.getName(), copy.getName());
         assertEquals(original.getSecureID(), copy.getSecureID());
         assertArrayEquals(original.getRoles(), copy.getRoles());
         assertArrayEquals(original.getGroups(), copy.getGroups());
         assertEquals(original.getOrgId(), copy.getOrgId());
         assertEquals(original.getHost(), copy.getHost());
         assertEquals(original.getLocale(), copy.getLocale());
         assertEquals(original.getLastAccess(), copy.getLastAccess());
         assertEquals(original.getAge(), copy.getAge());
         assertEquals(original.getUser(), copy.getUser());
         assertEquals(50, copy.getParameter("pageSize"));
         assertEquals("gold", copy.getProperty("badge"));
      }
   }

   // [Equals: non-fake] same name + same client + same secureID → equal;
   //                    differing secureID or null argument → not equal
   @Test
   void equals_nonFakePrincipals_equalByIdentityClientAndSecureID() {
      try(MockedStatic<XSessionService> sessionService = mockSessionService()) {
         ClientInfo client = new ClientInfo(new IdentityID("alice", ORG_A), "10.0.0.1", "s1");
         SRPrincipal a = new SRPrincipal(client, new IdentityID[0], new String[0], ORG_A, 42L);
         SRPrincipal b = new SRPrincipal(client, new IdentityID[0], new String[0], ORG_A, 42L);
         SRPrincipal c = new SRPrincipal(client, new IdentityID[0], new String[0], ORG_A, 99L);

         assertEquals(a, b);        // same name, client, secureID → equal
         assertNotEquals(a, c);     // secureID differs → not equal
         assertNotEquals(a, null);  // null → not equal
      }
   }

   // [Locale: property-sync] setLocale writes LOCALE property when absent; a subsequent call
   //                         updates the locale field but leaves an already-set property unchanged
   @Test
   void setLocale_syncsLocalePropertyOnlyWhenAbsent() {
      try(MockedStatic<XSessionService> sessionService = mockSessionService()) {
         SRPrincipal principal = newPrincipal();

         // no existing LOCALE property: first setLocale should write it
         principal.setLocale(Locale.US);
         assertEquals(Locale.US, principal.getLocale());
         assertEquals("en_US", principal.getProperty(XPrincipal.LOCALE));

         // LOCALE property now present: second setLocale updates the field but must not overwrite the property
         principal.setLocale(Locale.GERMANY);
         assertEquals(Locale.GERMANY, principal.getLocale());
         assertEquals("en_US", principal.getProperty(XPrincipal.LOCALE));
      }
   }

   // [Session: valid-states] isValid is true before any session is attached (sref == null),
   //                         and also true while a strongly-held session object is alive
   @Test
   void isValid_trueWhenNoSessionSetAndWhenLiveSession() {
      try(MockedStatic<XSessionService> sessionService = mockSessionService()) {
         SRPrincipal principal = newPrincipal();
         Object session = new Object();

         // before attaching a session: sref is null → valid
         assertTrue(principal.isValid());

         principal.setSession(session);

         assertSame(session, principal.getSession());
         assertTrue(principal.isValid());
      }
   }

   // [Identifier: internal] internal principals bypass SSO encoding; toIdentifier returns just the name
   @Test
   void toIdentifier_internalPrincipalReturnsPlainName() {
      try(MockedStatic<XSessionService> sessionService = mockSessionService()) {
         SRPrincipal principal = newPrincipal();
         principal.setProperty("__internal__", "true");

         assertEquals(principal.getName(), principal.toIdentifier());
      }
   }

   // [Externalizable: round-trip] custom externalization restores persisted state
   @Test
   void externalizableRoundTrip_restoresPrincipalState() throws Exception {
      try(MockedStatic<XSessionService> sessionService = mockSessionService()) {
         SRPrincipal principal = newPrincipal();
         principal.setProperty("theme", "ocean");
         principal.setParameter("limit", 25);
         principal.setIgnoreLogin(true);
         principal.setProfiling(true);
         principal.setLastAccess(123456L);
         principal.setLocale(Locale.CANADA_FRENCH);

         long expectedAge = principal.getAge();
         long expectedParameterTs = principal.getParameterTS("limit");

         SRPrincipal restored = externalize(principal);

         assertEquals(principal.getName(), restored.getName());
         assertEquals(principal.getSecureID(), restored.getSecureID());
         assertEquals(expectedAge, restored.getAge());
         assertEquals(123456L, restored.getLastAccess());
         assertEquals(principal.getHost(), restored.getHost());
         assertEquals(Locale.CANADA_FRENCH, restored.getLocale());
         assertArrayEquals(principal.getRoles(), restored.getRoles());
         assertArrayEquals(principal.getGroups(), restored.getGroups());
         assertEquals(25, restored.getParameter("limit"));
         assertEquals(expectedParameterTs, restored.getParameterTS("limit"));
         assertEquals("ocean", restored.getProperty("theme"));
         assertTrue(restored.isIgnoreLogin());
         assertTrue(restored.isProfiling());
         assertEquals(principal.getUser(), restored.getUser());
      }
   }

   // [XML: round-trip] XML serialization and parsing preserve principal state
   @Test
   void xmlRoundTrip_restoresPrincipalState() throws Exception {
      try(MockedStatic<XSessionService> sessionService = mockSessionService()) {
         SRPrincipal principal = newPrincipal();
         principal.setProperty("theme", "ocean");
         principal.setLastAccess(987654L);
         principal.setLocale(Locale.US);

         SRPrincipal restored = new SRPrincipal();
         restored.parseXML(toElement(principal));

         assertEquals(principal.getName(), restored.getName());
         assertEquals(principal.getSecureID(), restored.getSecureID());
         assertEquals(principal.getAge(), restored.getAge());
         assertEquals(987654L, restored.getLastAccess());
         assertEquals(principal.getHost(), restored.getHost());
         assertArrayEquals(principal.getRoles(), restored.getRoles());
         assertArrayEquals(principal.getGroups(), restored.getGroups());
         assertEquals("ocean", restored.getProperty("theme"));
         assertEquals(principal.getOrgId(), restored.getOrgId());
         assertEquals(principal.getUser(), restored.getUser());
      }
   }

   // [XML: missing-required-tags] parseXML throws IOException for each absent required element:
   //                              clientInfo, secureID, age, accessed, properties
   @Test
   void parseXML_missingRequiredTagThrowsIOException() throws Exception {
      try(MockedStatic<XSessionService> sessionService = mockSessionService()) {
         StringWriter sw = new StringWriter();
         newPrincipal().getUser().writeXML(new PrintWriter(sw));
         String ci = sw.toString();

         // missing clientInfo
         assertMissingTagThrowsIOException("""
            <principal>
              <roles></roles><groups></groups>
              <secureID>42</secureID><age>0</age><accessed>0</accessed>
              <sessionID><![CDATA[]]></sessionID>
              <properties></properties>
            </principal>
            """, "clientInfo");

         // missing secureID
         assertMissingTagThrowsIOException(
            "<principal>" + ci +
            "<roles></roles><groups></groups>" +
            "<age>0</age><accessed>0</accessed>" +
            "<sessionID><![CDATA[]]></sessionID><properties></properties>" +
            "</principal>", "secureID");

         // missing age
         assertMissingTagThrowsIOException(
            "<principal>" + ci +
            "<roles></roles><groups></groups>" +
            "<secureID>42</secureID><accessed>0</accessed>" +
            "<sessionID><![CDATA[]]></sessionID><properties></properties>" +
            "</principal>", "age");

         // missing accessed
         assertMissingTagThrowsIOException(
            "<principal>" + ci +
            "<roles></roles><groups></groups>" +
            "<secureID>42</secureID><age>0</age>" +
            "<sessionID><![CDATA[]]></sessionID><properties></properties>" +
            "</principal>", "accessed");

         // missing properties
         assertMissingTagThrowsIOException(
            "<principal>" + ci +
            "<roles></roles><groups></groups>" +
            "<secureID>42</secureID><age>0</age><accessed>0</accessed>" +
            "<sessionID><![CDATA[]]></sessionID>" +
            "</principal>", "properties");
      }
   }

   // [Suspect 1] fake principals that compare equal should also share the same hashCode
   @Test
   void fakePrincipalsEqualByName_haveSameHashCode() {
      try(MockedStatic<XSessionService> sessionService = mockSessionService()) {
         SRPrincipal first = new SRPrincipal(
            new ClientInfo(new IdentityID("alice", ORG_A), "10.0.0.1", "session-1"),
            new IdentityID[0], new String[0], ORG_A, 1L);
         SRPrincipal second = new SRPrincipal(
            new ClientInfo(new IdentityID("alice", ORG_A), "10.0.0.2", "session-2"),
            new IdentityID[0], new String[0], ORG_A, 2L);
         first.setProperty("__FAKE__", "true");
         second.setProperty("__FAKE__", "true");

         assertEquals(first, second);
         assertEquals(first.hashCode(), second.hashCode());
      }
   }

   // [Suspect 2] identifier round-trip should restore serialized parameters without crashing
   @Test
   void createFromID_withParameters_restoresSerializedParameters() {
      try(MockedStatic<XSessionService> sessionService = mockSessionService()) {
         SRPrincipal principal = newPrincipal();
         principal.setParameter("limit", 25);
         principal.setParameter("enabled", true);

         SRPrincipal restored = SRPrincipal.createFromID(principal.toIdentifier());

         assertEquals(25, restored.getParameter("limit"));
         assertEquals(true, restored.getParameter("enabled"));
      }
   }

   // [Suspect 3] identifier round-trip should restore role identities without delimiter collisions
   @Test
   void createFromID_withRoles_restoresSerializedRoles() {
      try(MockedStatic<XSessionService> sessionService = mockSessionService()) {
         SRPrincipal principal = newPrincipal();

         SRPrincipal restored = SRPrincipal.createFromID(principal.toIdentifier());

         assertArrayEquals(principal.getRoles(), restored.getRoles());
      }
   }

   // [Suspect 4] even a simple identifier should parse back without crashing
   @Test
   void createFromID_roundTripPreservesIdentityPayload() {
      try(MockedStatic<XSessionService> sessionService = mockSessionService()) {
         SRPrincipal principal = new SRPrincipal(
            new ClientInfo(new IdentityID("alice", ORG_A), "10.0.0.1", "session-1"),
            new IdentityID[0], new String[] {"group1", "group2"}, ORG_A, 42L);

         String identifier = principal.toIdentifier();
         SRPrincipal restored = SRPrincipal.createFromID(identifier);

         assertEquals(principal.getName(), restored.getName());
         assertEquals(principal.getSecureID(), restored.getSecureID());
         assertArrayEquals(principal.getGroups(), restored.getGroups());
         assertArrayEquals(new IdentityID[0], restored.getRoles());
         assertEquals(principal.getUser().getIPAddress(), restored.getUser().getIPAddress());
         assertEquals(principal.getUser().getSession(), restored.getUser().getSession());
      }
   }

   @Test
   void getNameFromID_stripsClientInfoSuffixAndDecodesName() {
      // toIdentifier embeds "^secureID^ip^session" after the encoded name
      // getNameFromID must strip that suffix and return only the decoded name
      try(MockedStatic<XSessionService> sessionService = mockSessionService()) {
         SRPrincipal principal = newPrincipal();
         String id = principal.toIdentifier();
         String name = SRPrincipal.getNameFromID(id);
         assertEquals(principal.getName(), name);
      }
   }

   private static SRPrincipal newPrincipal() {
      SRPrincipal principal = new SRPrincipal(
         new ClientInfo(new IdentityID("alice", ORG_A), "10.0.0.1", "session-1"),
         new IdentityID[] {new IdentityID("Administrator", null)},
         new String[] {"group1", "group2"}, ORG_A, 42L);
      principal.setProperty("theme", "ocean");
      return principal;
   }

   private static SRPrincipal externalize(SRPrincipal principal) throws Exception {
      ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(byteStream)) {
         principal.writeExternal(out);
      }

      SRPrincipal restored = new SRPrincipal();

      try(ObjectInputStream in = new ObjectInputStream(
         new ByteArrayInputStream(byteStream.toByteArray())))
      {
         restored.readExternal(in);
      }

      return restored;
   }

   private static Element toElement(SRPrincipal principal) throws Exception {
      StringWriter stringWriter = new StringWriter();

      try(PrintWriter writer = new PrintWriter(stringWriter)) {
         principal.writeXML(writer);
      }

      return parseElement(stringWriter.toString());
   }

   private static void assertMissingTagThrowsIOException(String xml, String tagName)
      throws Exception
   {
      SRPrincipal p = new SRPrincipal();
      Element elem = parseElement(xml);
      IOException thrown = assertThrows(IOException.class, () -> p.parseXML(elem));
      assertTrue(thrown.getMessage().contains(tagName),
         "Expected IOException message to mention '" + tagName + "' but was: " + thrown.getMessage());
   }

   private static Element parseElement(String xml) throws Exception {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(false);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

      try(ByteArrayInputStream input =
              new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
      {
         Document document = factory.newDocumentBuilder().parse(input);
         return document.getDocumentElement();
      }
   }

   private static MockedStatic<XSessionService> mockSessionService() {
      MockedStatic<XSessionService> mocked = Mockito.mockStatic(XSessionService.class);
      mocked.when(XSessionService::getService).thenReturn(new XSessionService());
      return mocked;
   }
}
