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
import org.mockito.MockedStatic;

import java.io.ByteArrayOutputStream;

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
