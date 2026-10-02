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

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.util.script.FormulaContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77396: the top level of a script library runs restricted when an engine installs
 * it, so a host type it would keep in a global cannot reach restricted scripts. A library
 * function body still runs under its caller's flag. The tests turn javascript.java.com_org
 * on, so a refusal comes from the restricted mode and not from the com_org default.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScriptLibraryRestrictedTest {
   private static final String COM_ORG = "org.apache.commons.lang3.StringUtils";

   private GraalJavaScriptEngine engine;
   private String oldComOrg;

   @BeforeEach
   void setup() throws Exception {
      FormulaContext.setRestricted(false);
      oldComOrg = SreeEnv.getProperty("javascript.java.com_org");
      SreeEnv.setProperty("javascript.java.com_org", "true");
      Map<String, String> libs = new LinkedHashMap<>();
      libs.put("leak", "var MU = Java.type('" + COM_ORG + "');\n" +
         "function afterLeak(x) { return x + 1; }");
      libs.put("lib2", "function libIsEmpty(s) { return Java.type('" + COM_ORG +
         "').isEmpty(s); }");

      engine = new GraalJavaScriptEngine() {
         @Override
         protected Map<String, String> librarySources() {
            return libs;
         }
      };
      engine.init(new java.util.HashMap<>());
   }

   @AfterEach
   void teardown() {
      engine.close();
      FormulaContext.setRestricted(false);
      SreeEnv.setProperty("javascript.java.com_org", oldComOrg);
   }

   private Object eval(String src, boolean restricted) throws Exception {
      boolean old = FormulaContext.isRestricted();

      try {
         FormulaContext.setRestricted(restricted);
         return engine.exec(engine.compile(src), null, null);
      }
      finally {
         FormulaContext.setRestricted(old);
      }
   }

   @Test
   void libraryTopLevelLookupIsRefusedAtInstall() throws Exception {
      assertFalse(FormulaContext.isRestricted(), "install restores the flag");
      assertEquals("undefined", eval("typeof MU", false));
      assertThrows(Exception.class, () -> eval("MU.isEmpty('')", true));
   }

   @Test
   void failingLibraryDoesNotStopTheNextLibrary() throws Exception {
      // a failing library is logged and skipped, the next one installs
      assertEquals("function", eval("typeof libIsEmpty", false));
   }

   @Test
   void libraryFunctionRunsUnderItsCallersFlag() throws Exception {
      // an unrestricted (e.g. VPM) caller may use com/org in a library function body
      assertEquals(true, eval("libIsEmpty('')", false));
      assertThrows(Exception.class, () -> eval("libIsEmpty('')", true));
   }

   @Test
   void initInsideRestrictedFrameInstallsTheSameWay() throws Exception {
      engine.close();
      FormulaContext.setRestricted(false);
      engine.init(new java.util.HashMap<>());
      Object unrestrictedInit = eval("typeof libIsEmpty", false);

      engine.close();
      FormulaContext.setRestricted(true);
      engine.init(new java.util.HashMap<>());
      assertTrue(FormulaContext.isRestricted(), "install restores the flag");
      FormulaContext.setRestricted(false);
      assertEquals(unrestrictedInit, eval("typeof libIsEmpty", false));
      assertEquals("undefined", eval("typeof MU", false));
   }

   @Test
   void failingTopLevelStatementEndsOnlyItsOwnLibrary() throws Exception {
      // the eval of a library stops at its failing statement, so a function declared
      // after it in the same library is not installed; the failure is logged
      assertEquals("undefined", eval("typeof afterLeak", false));
   }
}
