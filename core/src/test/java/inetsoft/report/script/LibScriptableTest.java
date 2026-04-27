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

package inetsoft.report.script;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.test.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.mozilla.javascript.Scriptable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class LibScriptableTest {
   private LibScriptable libScriptable;
   private Scriptable mockScriptable;

   @Test
   void testGetFun() {
      mockScriptable = mock(Scriptable.class);
      LibManager manager = LibManagerProvider.getInstance().getManager();
      manager.setScript("script1", "function testFunc() { return 'Hello, World!'; }");
      libScriptable = new LibScriptable(mockScriptable);

      assertArrayEquals(new Object[]{}, libScriptable.getIds());
      Scriptable myscript = (Scriptable) libScriptable.get("script1", null);
      assertEquals("testFunc",  myscript.get("name", null));
   }
 }
