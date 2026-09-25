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
package inetsoft.util.script.graal.pool;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class InitSnapshotTest {
   @Test
   void keepsOrderAndIsImmutable() {
      Map<String, String> lib = new LinkedHashMap<>();
      lib.put("b", "function b(){}");
      lib.put("a", "function a(){}");
      InitSnapshot snapshot = new InitSnapshot("org1", lib);
      lib.put("c", "function c(){}");

      assertEquals("org1", snapshot.getOrgId());
      assertEquals(java.util.List.of("b", "a"), java.util.List.copyOf(snapshot.getLibrary().keySet()));
      assertThrows(UnsupportedOperationException.class, () -> snapshot.getLibrary().put("x", "y"));
   }

   @Test
   void captureNeverThrowsWithoutAServer() {
      assertNotNull(InitSnapshot.capture().getLibrary());
   }
}
