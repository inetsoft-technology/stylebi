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

import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * getInt/getLong/getBoolean must reflect the current property value on every call, not just
 * the first one seen by a given accessor for a given property name (see the now-removed
 * cache.computeIfAbsent memoization that made them stick to their first-parsed value forever).
 */
@Tag("core")
class SreeEnvTest {
   private MockedStatic<PropertiesEngine> propertiesEngineStatic;
   private PropertiesEngine engine;
   private final Map<String, String> properties = new HashMap<>();

   @BeforeEach
   void setUp() {
      properties.clear();
      engine = mock(PropertiesEngine.class);
      when(engine.getProperty(anyString(), eq(false))).thenAnswer(
         invocation -> properties.get(invocation.getArgument(0, String.class)));
      doAnswer(invocation -> properties.put(
         invocation.getArgument(0, String.class), invocation.getArgument(1, String.class)))
         .when(engine).setProperty(anyString(), anyString());

      propertiesEngineStatic = mockStatic(PropertiesEngine.class);
      propertiesEngineStatic.when(PropertiesEngine::getInstance).thenReturn(engine);
   }

   @AfterEach
   void tearDown() {
      propertiesEngineStatic.close();
   }

   @Test
   void getLong_reflectsPropertyChangeAfterFirstRead() {
      SreeEnv.setProperty("some.long.prop", "10");
      assertEquals(10L, SreeEnv.getLong("some.long.prop"));

      SreeEnv.setProperty("some.long.prop", "20");
      assertEquals(20L, SreeEnv.getLong("some.long.prop"));
   }

   @Test
   void getInt_reflectsPropertyChangeAfterFirstRead() {
      SreeEnv.setProperty("some.int.prop", "10");
      assertEquals(10, SreeEnv.getInt("some.int.prop"));

      SreeEnv.setProperty("some.int.prop", "20");
      assertEquals(20, SreeEnv.getInt("some.int.prop"));
   }

   @Test
   void getBoolean_reflectsPropertyChangeAfterFirstRead() {
      SreeEnv.setProperty("some.boolean.prop", "true");
      assertEquals(true, SreeEnv.getBoolean("some.boolean.prop"));

      SreeEnv.setProperty("some.boolean.prop", "false");
      assertEquals(false, SreeEnv.getBoolean("some.boolean.prop"));
   }
}
