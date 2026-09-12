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

import inetsoft.sree.security.IdentityID;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

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
}
