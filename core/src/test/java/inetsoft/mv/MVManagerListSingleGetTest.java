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
package inetsoft.mv;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

/**
 * {@link MVManager#list(String[])} reads each MV definition from {@code MVDefMap} once; every
 * {@code MVDefMap.get} costs an {@code IndexedStorage.lastModified} call, and runtime MV lookups
 * call {@code list} once per bound table.
 */
@Tag("core")
class MVManagerListSingleGetTest {
   @Test
   void listFetchesEachDefinitionOnce() throws Exception {
      MVManager manager = mock(MVManager.class, CALLS_REAL_METHODS);
      MVDefMap defMap = mock(MVDefMap.class);
      MVDef mv1 = mock(MVDef.class);
      MVDef mv2 = mock(MVDef.class);

      Set<String> keys = new LinkedHashSet<>();
      keys.add("key1");
      keys.add("key2");
      keys.add("missing");

      when(defMap.keySet("orgA")).thenReturn(keys);
      when(defMap.get("key1", "orgA")).thenReturn(mv1);
      when(defMap.get("key2", "orgA")).thenReturn(mv2);
      when(defMap.get("missing", "orgA")).thenReturn(null);

      setField(manager, "mvs", defMap);
      setField(manager, "lockMap", new ConcurrentHashMap<>());

      List<MVDef> defs = manager.list(new String[] { "orgA" });

      assertEquals(2, defs.size());
      assertSame(mv1, defs.get(0));
      assertSame(mv2, defs.get(1));
      verify(defMap, times(1)).get("key1", "orgA");
      verify(defMap, times(1)).get("key2", "orgA");
      verify(defMap, times(1)).get("missing", "orgA");
   }

   private static void setField(MVManager manager, String name, Object value) throws Exception {
      Field field = MVManager.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(manager, value);
   }
}
