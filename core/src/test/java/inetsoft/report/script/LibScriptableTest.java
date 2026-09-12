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
import inetsoft.util.script.graal.ScriptScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class LibScriptableTest {
   private LibScriptable libScriptable;
   private ScriptScope mockScope;

   /**
    * Verify that a registered library function is exposed as a member.
    * {@code LibScriptable} retains the function source string for member
    * enumeration (autocomplete); callability from formulas is covered
    * separately by {@code LibFunctionCallableTest}, which exercises the
    * engine-global function installed at engine init.
    */
   @Test
   void testGetFunSource() {
      mockScope = mock(ScriptScope.class);
      LibManager manager = LibManagerProvider.getInstance().getManager();
      manager.setScript("script1", "function testFunc() { return 'Hello, World!'; }");
      libScriptable = new LibScriptable(mockScope);

      assertTrue(libScriptable.hasMember("script1"));
      Object member = libScriptable.getMember("script1");
      assertEquals("function testFunc() { return 'Hello, World!'; }", member);
   }
}
