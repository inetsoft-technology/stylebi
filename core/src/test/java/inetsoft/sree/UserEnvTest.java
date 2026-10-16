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
package inetsoft.sree;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class UserEnvTest {
   private static final String USER_DIR = "sreeUserData";

   @Test
   void removeUser_nullIdentity_noDataSpaceAccess() {
      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class)) {
         UserEnv.removeUser(null);
         ds.verify(DataSpace::getDataSpace, never());
      }
   }

   @Test
   void removeUser_deletesExistingFile() {
      IdentityID alice = new IdentityID("alice", "org1");
      String userFile = "alice_org1.xml";
      DataSpace space = mock(DataSpace.class);
      when(space.exists(USER_DIR, userFile)).thenReturn(true);

      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class)) {
         ds.when(DataSpace::getDataSpace).thenReturn(space);
         UserEnv.removeUser(alice);
         verify(space).delete(USER_DIR, userFile);
      }
   }

   @Test
   void removeUser_fileMissing_noDelete() {
      IdentityID alice = new IdentityID("alice", "org1");
      DataSpace space = mock(DataSpace.class);
      when(space.exists(USER_DIR, "alice_org1.xml")).thenReturn(false);

      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class)) {
         ds.when(DataSpace::getDataSpace).thenReturn(space);
         UserEnv.removeUser(alice);
         verify(space, never()).delete(anyString(), anyString());
      }
   }

   @Test
   void removeUser_deleteFails_doesNotThrow() {
      IdentityID alice = new IdentityID("alice", "org1");
      DataSpace space = mock(DataSpace.class);
      when(space.exists(USER_DIR, "alice_org1.xml")).thenReturn(true);
      when(space.delete(USER_DIR, "alice_org1.xml")).thenThrow(new RuntimeException("boom"));

      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class)) {
         ds.when(DataSpace::getDataSpace).thenReturn(space);
         assertDoesNotThrow(() -> UserEnv.removeUser(alice));
      }
   }

   @Test
   void securityOnGuest_keepsValueForSessionWithoutUserFile() throws Exception {
      SRPrincipal guestA = principal(ClientInfo.ANONYMOUS, "guestOrg1", "sessA");
      SRPrincipal guestB = principal(ClientInfo.ANONYMOUS, "guestOrg1", "sessB");
      DataSpace space = mock(DataSpace.class);

      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class);
          MockedStatic<SUtil> sutil = mockStatic(SUtil.class);
          MockedStatic<SreeEnv> ignored = mockStatic(SreeEnv.class))
      {
         ds.when(DataSpace::getDataSpace).thenReturn(space);
         sutil.when(SUtil::isSecurityOn).thenReturn(true);

         UserEnv.setProperty(guestA, "annotation", "false");

         assertEquals("false", UserEnv.getProperty(guestA, "annotation"));
         assertNull(UserEnv.getProperty(guestB, "annotation"));
         verify(space, never()).getInputStream(anyString(), anyString());
         verify(space, never()).beginTransaction();
      }
   }

   @Test
   void securityOffAnonymous_savesUserFile() throws Exception {
      SRPrincipal anonymous = principal(ClientInfo.ANONYMOUS, "anonOrg2", "sessC");
      DataSpace space = mockSpace();

      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class);
          MockedStatic<SUtil> sutil = mockStatic(SUtil.class);
          MockedStatic<SreeEnv> ignored = mockStatic(SreeEnv.class))
      {
         ds.when(DataSpace::getDataSpace).thenReturn(space);
         sutil.when(SUtil::isSecurityOn).thenReturn(false);

         UserEnv.setProperty(anonymous, "annotation", "false");

         assertEquals("false", UserEnv.getProperty(anonymous, "annotation"));
         verify(space).getInputStream(USER_DIR, "anonymous_anonOrg2.xml");
         verify(space.beginTransaction()).newStream(USER_DIR, "anonymous_anonOrg2.xml");
      }
   }

   @Test
   void securityOnNamedUser_savesUserFile() throws Exception {
      SRPrincipal alice = principal("alice", "namedOrg3", "sessD");
      DataSpace space = mockSpace();

      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class);
          MockedStatic<SUtil> sutil = mockStatic(SUtil.class);
          MockedStatic<SreeEnv> ignored = mockStatic(SreeEnv.class))
      {
         ds.when(DataSpace::getDataSpace).thenReturn(space);
         sutil.when(SUtil::isSecurityOn).thenReturn(true);

         UserEnv.setProperty(alice, "annotation", "false");

         assertEquals("false", UserEnv.getProperty(alice, "annotation"));
         verify(space).getInputStream(USER_DIR, "alice_namedOrg3.xml");
         verify(space.beginTransaction()).newStream(USER_DIR, "alice_namedOrg3.xml");
      }
   }

   @Test
   void securityOnGuestWithoutClientInfo_dropsValueWithoutUserFile() throws Exception {
      // a guest principal without a ClientInfo is keyed by name, which all guests share
      Principal guest = mock(Principal.class);
      when(guest.getName()).thenReturn(new IdentityID(ClientInfo.ANONYMOUS, "guestOrg4").convertToKey());
      DataSpace space = mock(DataSpace.class);

      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class);
          MockedStatic<SUtil> sutil = mockStatic(SUtil.class);
          MockedStatic<SreeEnv> ignored = mockStatic(SreeEnv.class))
      {
         ds.when(DataSpace::getDataSpace).thenReturn(space);
         sutil.when(SUtil::isSecurityOn).thenReturn(true);

         UserEnv.setProperty(guest, "annotation", "false");

         assertTrue(UserEnv.supportedUser(guest));
         assertNull(UserEnv.getProperty(guest, "annotation"));
         verify(space, never()).getInputStream(anyString(), anyString());
         verify(space, never()).beginTransaction();
      }
   }

   @Test
   void securityOnGuest_keepsValueWhenUserCacheIsCleared() throws Exception {
      SRPrincipal guest = principal(ClientInfo.ANONYMOUS, "guestOrg5", "sessE");
      List<SRPrincipal> others = new ArrayList<>();
      DataSpace space = mock(DataSpace.class);

      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class);
          MockedStatic<SUtil> sutil = mockStatic(SUtil.class);
          MockedStatic<SreeEnv> ignored = mockStatic(SreeEnv.class))
      {
         ds.when(DataSpace::getDataSpace).thenReturn(space);
         sutil.when(SUtil::isSecurityOn).thenReturn(true);

         UserEnv.setProperty(guest, "annotation", "false");

         // fill the user cache past its size cap so the next lookup clears it
         for(int i = 0; i < 60; i++) {
            SRPrincipal user = principal("user" + i, "cacheOrg5", "sess" + i);
            others.add(user);
            UserEnv.getProperty(user, "annotation");
         }

         assertEquals("false", UserEnv.getProperty(guest, "annotation"));
      }
   }

   @Test
   void securityOnNullPrincipal_dropsValueWithoutUserFile() throws Exception {
      DataSpace space = mock(DataSpace.class);

      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class);
          MockedStatic<SUtil> sutil = mockStatic(SUtil.class);
          MockedStatic<SreeEnv> ignored = mockStatic(SreeEnv.class))
      {
         ds.when(DataSpace::getDataSpace).thenReturn(space);
         sutil.when(SUtil::isSecurityOn).thenReturn(true);

         UserEnv.setProperty(null, "annotation", "false");

         assertFalse(UserEnv.supportedUser(null));
         assertNull(UserEnv.getProperty(null, "annotation"));
         verify(space, never()).getInputStream(anyString(), anyString());
         verify(space, never()).beginTransaction();
      }
   }

   @Test
   void securityOnGuest_equalClientInfoSharesSessionValue() throws Exception {
      // e.g. the STOMP principal and the RuntimeViewsheet's copy of the same session
      SRPrincipal guest = principal(ClientInfo.ANONYMOUS, "guestOrg6", "sessF");
      SRPrincipal sameSession = principal(ClientInfo.ANONYMOUS, "guestOrg6", "sessF");
      assertNotSame(guest.getUser(), sameSession.getUser());
      DataSpace space = mock(DataSpace.class);

      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class);
          MockedStatic<SUtil> sutil = mockStatic(SUtil.class);
          MockedStatic<SreeEnv> ignored = mockStatic(SreeEnv.class))
      {
         ds.when(DataSpace::getDataSpace).thenReturn(space);
         sutil.when(SUtil::isSecurityOn).thenReturn(true);

         UserEnv.setProperty(guest, "annotation", "false");

         assertEquals("false", UserEnv.getProperty(sameSession, "annotation"));
         verify(space, never()).getInputStream(anyString(), anyString());
      }
   }

   // a user file saved on a th_TH server before #77605 holds Buddhist years (Bug #78040)
   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void legacyBuddhistUserFile_readByCompatSetting(boolean on) throws Exception {
      SRPrincipal writer = principal("writer", "compatOrg" + on, "sessW" + on);
      SRPrincipal reader = principal("reader", "compatOrg" + on, "sessR" + on);
      DataSpace space = mock(DataSpace.class);
      DataSpace.Transaction tx = mock(DataSpace.Transaction.class);
      ByteArrayOutputStream saved = new ByteArrayOutputStream();
      when(space.beginTransaction()).thenReturn(tx);
      when(tx.newStream(anyString(), anyString())).thenReturn(saved);

      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class);
          MockedStatic<SUtil> sutil = mockStatic(SUtil.class);
          MockedStatic<SreeEnv> sree = mockStatic(SreeEnv.class))
      {
         ds.when(DataSpace::getDataSpace).thenReturn(space);
         sutil.when(SUtil::isSecurityOn).thenReturn(true);
         sree.when(() -> SreeEnv.getProperty("date.legacy.buddhist.compat"))
            .thenReturn(String.valueOf(on));

         Map<String, Object> prop = new HashMap<>();
         prop.put("d", new java.sql.Date(date(1996, 2, 29)));
         prop.put("arr", new Object[] { new java.sql.Date(date(1996, 2, 29)) });
         prop.put("s", "1996-02-29");
         UserEnv.save(writer, prop);

         String legacy = saved.toString(StandardCharsets.UTF_8).replace("1996-", "2539-");
         assertTrue(legacy.contains("2539-02-29"), legacy);
         when(space.getInputStream(USER_DIR, "reader_compatOrg" + on + ".xml"))
            .thenReturn(new ByteArrayInputStream(legacy.getBytes(StandardCharsets.UTF_8)));

         assertLegacyDate(on, UserEnv.getProperty(reader, "d"));
         assertLegacyDate(on, ((Object[]) UserEnv.getProperty(reader, "arr"))[0]);
         assertEquals("2539-02-29", UserEnv.getProperty(reader, "s"));
      }
   }

   private static void assertLegacyDate(boolean on, Object value) {
      assertInstanceOf(Date.class, value);
      GregorianCalendar cal = new GregorianCalendar();
      cal.setTime((Date) value);

      if(on) {
         assertEquals(date(1996, 2, 29), cal.getTimeInMillis());
      }
      else {
         // CE 2539 has no Feb 29, the lenient parse rolls it
         assertEquals(2539, cal.get(Calendar.YEAR));
      }
   }

   private static long date(int year, int month, int day) {
      GregorianCalendar cal = new GregorianCalendar();
      cal.clear();
      cal.set(year, month - 1, day);
      return cal.getTimeInMillis();
   }

   private static SRPrincipal principal(String name, String orgID, String session) {
      IdentityID identity = new IdentityID(name, orgID);
      SRPrincipal principal = mock(SRPrincipal.class);
      when(principal.getName()).thenReturn(identity.convertToKey());
      when(principal.getUser()).thenReturn(new ClientInfo(identity, "127.0.0.1", session));
      return principal;
   }

   private static DataSpace mockSpace() throws Exception {
      DataSpace space = mock(DataSpace.class);
      DataSpace.Transaction tx = mock(DataSpace.Transaction.class);
      when(space.beginTransaction()).thenReturn(tx);
      when(tx.newStream(anyString(), anyString())).thenReturn(new ByteArrayOutputStream());
      return space;
   }
}
