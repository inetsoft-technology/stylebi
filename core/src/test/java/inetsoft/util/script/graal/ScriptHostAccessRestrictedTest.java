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

import inetsoft.test.*;
import inetsoft.util.script.FormulaContext;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77396: a script that runs restricted (an end-user script surface) must not
 * reach com.* / org.* classes or the admin class grants, by any lookup path, while
 * an unrestricted (admin) script keeps them.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScriptHostAccessRestrictedTest {
   private static final String COM_ORG = "org.apache.commons.lang3.StringUtils";

   private GraalJavaScriptEngine engine;

   @BeforeEach
   void setup() throws Exception {
      FormulaContext.setRestricted(false);
      engine = new GraalJavaScriptEngine();
      engine.init(new java.util.HashMap<>());
   }

   @AfterEach
   void teardown() {
      engine.close();
      FormulaContext.setRestricted(false);
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
   void restrictedScriptCannotLookUpComOrgClass() {
      assertThrows(Exception.class,
                   () -> eval("Java.type('" + COM_ORG + "').isEmpty('')", true));
   }

   @Test
   void restrictedScriptStillReachesJavaBasicsAndOurApi() throws Exception {
      assertEquals(5.0, ((Number) eval("Java.type('java.lang.Math').max(2, 5)", true))
         .doubleValue());
      assertEquals(3.0, ((Number) eval("java.awt.Color(0x010203).getBlue()", true))
         .doubleValue());
      assertNotNull(eval("Java.type('inetsoft.report.StyleConstants').CENTER", true));
   }

   @Test
   void restrictedScriptKeepsJavaLangAndMath() throws Exception {
      assertEquals(42, ((Number) eval("java.lang.Integer.parseInt('42')", true)).intValue());
      assertEquals(42, ((Number) eval(
         "Java.type('java.lang.Integer').parseInt('42')", true)).intValue());
      assertEquals("3.000", eval(
         "'' + new java.math.BigDecimal('1.5').multiply(new java.math.BigDecimal('2.00'))", true));
      assertEquals("3", eval(
         "'' + Java.type('java.math.BigInteger').valueOf(3)", true));
   }

   @Test
   void restrictedScriptStillDeniesDangerousJavaLangClasses() {
      for(String cls : new String[] {
         "java.lang.System", "java.lang.Runtime", "java.lang.Class", "java.lang.ClassLoader",
         "java.lang.Thread", "java.lang.ProcessBuilder", "java.lang.reflect.Method",
         "java.lang.invoke.MethodHandles" })
      {
         assertThrows(Exception.class, () -> eval("Java.type('" + cls + "')", true), cls);
         assertThrows(Exception.class, () -> eval("'' + " + cls, true), cls);
      }
   }

   @Test
   void unrestrictedScriptStillReachesComOrgClass() throws Exception {
      assertEquals(true, eval("Java.type('" + COM_ORG + "').isEmpty('')", false));
      assertEquals(true, eval(COM_ORG + ".isEmpty('')", false));
   }

   @Test
   void restrictedScriptCannotNavigateToComOrgClass() {
      assertThrows(Exception.class, () -> eval(COM_ORG + ".isEmpty('')", true));
      assertThrows(Exception.class, () -> eval("Packages." + COM_ORG + ".isEmpty('')", true));
      assertThrows(Exception.class,
         () -> eval("importClass(" + COM_ORG + "); StringUtils.isEmpty('')", true));
   }

   /**
    * GraalJS tests the class filter only on a context's first lookup of a class, so
    * a class found by an unrestricted script must still be refused to a restricted
    * script that runs later in the same context.
    */
   @Test
   void classFoundByUnrestrictedScriptIsRefusedToRestrictedScript() throws Exception {
      assertEquals(true, eval("Java.type('" + COM_ORG + "').isEmpty('')", false));
      assertThrows(Exception.class,
                   () -> eval("Java.type('" + COM_ORG + "').isEmpty('')", true));
      assertThrows(Exception.class,
                   () -> eval("Java.to([], '" + COM_ORG + "[]')", true));
      assertEquals(true, eval("Java.type('" + COM_ORG + "').isEmpty('')", false));
   }

   @Test
   void typeLookupCheckCannotBeReplacedOrBypassed() throws Exception {
      eval("try { Java.type = function() { return null; }; } catch(e) {}", true);
      assertThrows(Exception.class,
                   () -> eval("Java.type('" + COM_ORG + "').isEmpty('')", true));
      assertThrows(Exception.class, () -> eval(
         "Java.type({ toString: function() { return '" + COM_ORG + "'; } })", true));
      assertThrows(Exception.class, () -> eval(
         "Java.to([], { toString: function() { return '" + COM_ORG + "[]'; } })", true));
   }

   @Test
   void typeLookupCheckIgnoresReplacedBuiltIns() {
      // the check does not use built-ins a script can replace
      assertThrows(Exception.class, () -> eval(
         "Object.prototype.hasOwnProperty = function() { return true; };" +
         "String.prototype.replace = function() { return 'java.lang.Math'; };" +
         "String.prototype.endsWith = function() { return false; };" +
         "Function.prototype.call = function() { return true; };" +
         "Java.type('" + COM_ORG + "')", true));
   }

   /**
    * A restricted script loses com/org and script.java.allowed.classes, but keeps the
    * packages an administrator listed in javascript.java.packages, as in Rhino.
    */
   @Test
   void restrictedScriptGetsOnlyThePackageGrant() {
      Predicate<String> filter = ScriptHostAccess.classFilter(
         Set.of("org.apache.commons.lang3.StringUtils"),
         new String[] { "org.apache.commons.text" }, true);

      try(Context ctx = Context.newBuilder("js")
         .allowHostAccess(ScriptHostAccess.hostAccess())
         .allowHostClassLookup(filter)
         .build())
      {
         ScriptHostAccess.installTypeLookupCheck(ctx, filter);
         String script = "Java.type('" + COM_ORG + "');" +
            "Java.type('org.apache.commons.text.WordUtils');" +
            "Java.type('com.fasterxml.jackson.databind.ObjectMapper')";

         FormulaContext.setRestricted(false);
         ctx.eval("js", script);

         FormulaContext.setRestricted(true);
         assertThrows(PolyglotException.class, () -> ctx.eval("js", "Java.type('" + COM_ORG + "')"));
         // javascript.java.packages is the one grant a restricted script keeps
         ctx.eval("js", "Java.type('org.apache.commons.text.WordUtils')");
         assertThrows(PolyglotException.class, () -> ctx.eval(
            "js", "Java.type('com.fasterxml.jackson.databind.ObjectMapper')"));
      }
      finally {
         FormulaContext.setRestricted(false);
      }
   }
}
