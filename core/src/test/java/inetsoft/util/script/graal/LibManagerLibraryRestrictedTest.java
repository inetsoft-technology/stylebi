/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.util.script.graal;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.test.*;
import inetsoft.uql.script.VpmScope;
import inetsoft.util.script.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77396: a script library saved in the LibManager is installed with its top level
 * restricted, through the env a real surface gets from {@link ScriptEnvRepository}, and a
 * VPM script reached from a restricted frame can still use com/org in a library function.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LibManagerLibraryRestrictedTest {
   private static final String COM_ORG = "org.apache.commons.lang3.StringUtils";

   @BeforeAll
   static void addLibraries() {
      LibManager mgr = LibManagerProvider.getInstance().getManager();
      mgr.setScript("leakLib", "var MU = Java.type('" + COM_ORG + "');\n" +
         "function leakLib(x) { return x; }");
      mgr.setScript("isEmptyLib", "function isEmptyLib(s) { return Java.type('" +
         COM_ORG + "').isEmpty(s); }");
   }

   @AfterAll
   static void removeLibraries() {
      LibManager mgr = LibManagerProvider.getInstance().getManager();
      mgr.removeScript("leakLib");
      mgr.removeScript("isEmptyLib");
   }

   @AfterEach
   void teardown() {
      FormulaContext.setRestricted(false);
   }

   private static Object exec(String src, boolean restricted) throws Exception {
      ScriptEnv env = ScriptEnvRepository.getScriptEnv();
      env.init();
      assertFalse(FormulaContext.isRestricted(), "install restores the flag");
      FormulaContext.setRestricted(restricted);

      try {
         return env.exec(env.compile(src), new VpmScope(), null, null);
      }
      finally {
         FormulaContext.setRestricted(false);
      }
   }

   @Test
   void libraryTopLevelGlobalIsNotReachable() throws Exception {
      assertEquals("undefined", exec("typeof MU", false));
      assertThrows(Exception.class, () -> exec("MU.isEmpty('')", true));
      assertEquals("undefined", VpmScope.execute("typeof MU", new VpmScope()));
   }

   @Test
   void libraryFunctionRunsUnderItsCallersFlag() throws Exception {
      assertEquals(true, exec("isEmptyLib('')", false));
      assertThrows(Exception.class, () -> exec("isEmptyLib('')", true));
   }

   @Test
   void vpmInsideRestrictedFrameCanUseLibraryFunction() throws Exception {
      FormulaContext.setRestricted(true);
      assertEquals(true, VpmScope.execute("isEmptyLib('')", new VpmScope()));
      assertTrue(FormulaContext.isRestricted());
   }
}
