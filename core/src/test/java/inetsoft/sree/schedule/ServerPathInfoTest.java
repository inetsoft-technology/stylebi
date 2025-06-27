/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.sree.schedule;

import inetsoft.web.admin.schedule.model.ServerPathInfoModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ServerPathInfoTest {

   @Test
   void testInit() {
      ServerPathInfo serverPathInfo = new ServerPathInfo("/test/path");

      assertEquals("/test/path", serverPathInfo.getPath());
      assertFalse(serverPathInfo.isFTP());

      serverPathInfo.setPath("/test/path2");
      assertEquals("/test/path2", serverPathInfo.getPath());

      //check sftp
      serverPathInfo = new ServerPathInfo("sftp://test/path");
      assertTrue(serverPathInfo.isFTP());

      //check ftp
      serverPathInfo = new ServerPathInfo("ftp://test/path", "admin", "admin");
      assertEquals("ftp://test/path", serverPathInfo.getPath());
      assertTrue(serverPathInfo.isFTP());
      assertFalse(serverPathInfo.isUseCredential());

      serverPathInfo.setUseCredential(true);
      assertTrue(serverPathInfo.isUseCredential());

      serverPathInfo.setSecretId("abc");
      assertEquals("abc", serverPathInfo.getSecretId());

      serverPathInfo.setUsername("admin");
      assertEquals("admin", serverPathInfo.getUsername());

      serverPathInfo.setPassword("123");
      assertEquals("123", serverPathInfo.getPassword());
   }

   @Test
   void testCreateWithModule() {
      ServerPathInfoModel model = mock(ServerPathInfoModel.class);
      when(model.path()).thenReturn("/test/path");
      when(model.ftp()).thenReturn(true);
      when(model.useCredential()).thenReturn(false);
      when(model.username()).thenReturn("admin");
      when(model.password()).thenReturn("123");

      ServerPathInfo serverPathInfo = new ServerPathInfo(model);

      assertEquals("/test/path", serverPathInfo.getPath());
      assertTrue(serverPathInfo.isFTP());
      assertFalse(serverPathInfo.isUseCredential());
      assertEquals("admin", serverPathInfo.getUsername());
      assertEquals("123", serverPathInfo.getPassword());
   }

   @Test
   void testEqualsAndCompareTo() {
      ServerPathInfo info1 = new ServerPathInfo("/test/path", "user1", "pass1");
      ServerPathInfo info2 = new ServerPathInfo("/test/path", "user1", "pass1");
      ServerPathInfo info3 = new ServerPathInfo("/test/path2", "user2", "pass2");

      assertTrue(info1.equals(info2));   // Test equality for identical objects
      assertFalse(info1.equals(info3));   // Test inequality for different objects
      assertFalse(info1.equals(null)); // Test inequality with null
      assertFalse(info1.equals("some string")); //// Test inequality with a different type

      assertEquals(0, info1.compareTo(info2)); // Test when paths are equal
      assertTrue(info1.compareTo(info3) < 0);  // Test when this.path is lexicographically less than the other
      assertEquals(-1, info1.compareTo("invalid object")); // Test when the object is not an instance of ServerPathInfo
   }
}
