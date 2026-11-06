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

import inetsoft.report.script.graal.ReportGraalJavaScriptEngine;
import inetsoft.test.*;
import inetsoft.util.script.FormulaContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79860: a restricted script must not change the JVM-wide default time zone or locale
 * ({@code TimeZone.setDefault}, both {@code Locale.setDefault} overloads, and the static
 * inherited by {@code SimpleTimeZone}) by any lookup route, while instances and constants of
 * those types stay usable. Runs on both production engines.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScriptJvmDefaultSetterTest {
   private static final String TZ = "java.util.TimeZone";
   private static final String LOC = "java.util.Locale";
   private static final String NEW_TZ = "TZX.getTimeZone('Pacific/Kiritimati')";
   private static final String NEW_LOC = "LX.forLanguageTag('ja-JP')";

   private TimeZone oldZone;
   private Locale oldDefault;
   private Locale oldFormat;
   private Locale oldDisplay;
   private GraalJavaScriptEngine engine;

   @BeforeEach
   void setup() {
      oldZone = TimeZone.getDefault();
      oldDefault = Locale.getDefault();
      oldFormat = Locale.getDefault(Locale.Category.FORMAT);
      oldDisplay = Locale.getDefault(Locale.Category.DISPLAY);
      FormulaContext.setRestricted(true);
   }

   @AfterEach
   void teardown() {
      if(engine != null) {
         engine.close();
      }

      FormulaContext.setRestricted(false);
      TimeZone.setDefault(oldZone);
      Locale.setDefault(oldDefault);
      Locale.setDefault(Locale.Category.FORMAT, oldFormat);
      Locale.setDefault(Locale.Category.DISPLAY, oldDisplay);
   }

   private void open(String kind) throws Exception {
      engine = "report".equals(kind) ? new ReportGraalJavaScriptEngine()
         : new GraalJavaScriptEngine();
      engine.init(new HashMap<>());
   }

   /** Runs a script that may throw; the outcome is judged by the JVM defaults only. */
   private void attempt(String script) throws Exception {
      String body = "(function(){ try { " + script + " } catch(e) {} return 1; })()";
      engine.exec(engine.compile(body), null, null);
   }

   private Object eval(String src) throws Exception {
      return engine.exec(engine.compile(src), null, null);
   }

   private void assertDefaultsUnchanged() {
      assertEquals(oldZone, TimeZone.getDefault());
      assertEquals(oldDefault, Locale.getDefault());
      assertEquals(oldFormat, Locale.getDefault(Locale.Category.FORMAT));
      assertEquals(oldDisplay, Locale.getDefault(Locale.Category.DISPLAY));
   }

   /** Script bodies; TZX/LX stand for the route to the TimeZone/Locale static view. */
   private static final String[] ROUTES = {
      "var TZX = Java.type('%1$s'); var LX = Java.type('%2$s'); %3$s",
      "var TZX = %1$s; var LX = %2$s; %3$s",
      "var TZX = Packages.%1$s; var LX = Packages.%2$s; %3$s",
      "var TZX = Java.type('%1$s')['class'].static; "
         + "var LX = Java.type('%2$s')['class'].static; %3$s",
      "var TZX = Java.type('%1$s').class.static; var LX = Java.type('%2$s').class.static; %3$s",
      "importClass(%1$s); importClass(%2$s); var TZX = TimeZone; var LX = Locale; %3$s",
      "var TZX = TimeZone; var LX = Locale; %3$s",
   };

   private static final String[] ACTIONS = {
      "TZX.setDefault(" + NEW_TZ + ");",
      "LX.setDefault(" + NEW_LOC + ");",
      "LX.setDefault(Java.type('java.util.Locale$Category').FORMAT, " + NEW_LOC + ");",
      "LX.setDefault(Java.type('java.util.Locale$Category').DISPLAY, " + NEW_LOC + ");",
      "var f = TZX.setDefault; f(" + NEW_TZ + ");",
      "var f = TZX.setDefault; f.call(null, " + NEW_TZ + ");",
      "var f = LX.setDefault; f(" + NEW_LOC + ");",
      "TZX['setDefault'](" + NEW_TZ + ");",
      "LX['setDefault'](" + NEW_LOC + ");",
      "with(java.util){ TimeZone.setDefault(TimeZone.getTimeZone('Pacific/Kiritimati')); "
         + "Locale.setDefault(Locale.forLanguageTag('ja-JP')); }",
      "Java.type('java.util.SimpleTimeZone').setDefault(" + NEW_TZ + ");",
      "java.util.SimpleTimeZone.setDefault(" + NEW_TZ + ");",
      "Java.type('java.util.SimpleTimeZone')['class'].static.setDefault(" + NEW_TZ + ");",
      "var st = new (Java.type('java.util.SimpleTimeZone'))(0, 'UTC'); "
         + "st.constructor.setDefault(st);",
      "var st = new (Java.type('java.util.SimpleTimeZone'))(0, 'UTC'); "
         + "Object.getPrototypeOf(st).constructor.setDefault(st);",
      "var t = " + NEW_TZ + "; t.getClass().setDefault(t);",
      "var t = " + NEW_TZ + "; t['class'].static.setDefault(t);",
   };

   @ParameterizedTest
   @ValueSource(strings = { "graal", "report" })
   void everyRouteLeavesJvmDefaultsUnchanged(String kind) throws Exception {
      open(kind);
      int ran = 0;

      for(String route : ROUTES) {
         for(String action : ACTIONS) {
            attempt(String.format(route, TZ, LOC, action));
            ran++;
            assertDefaultsUnchanged();
         }
      }

      assertTrue(ran > 100);
   }

   /** Guards the probe: the setters are absent from every route's view, not just ignored. */
   @ParameterizedTest
   @ValueSource(strings = { "graal", "report" })
   void settersAreAbsentFromEveryView(String kind) throws Exception {
      open(kind);

      for(String view : new String[] {
         "Java.type('java.util.TimeZone')", "java.util.TimeZone", "Packages.java.util.TimeZone",
         "Java.type('java.util.SimpleTimeZone')", "java.util.SimpleTimeZone",
         "Java.type('java.util.Locale')", "java.util.Locale" })
      {
         assertEquals("undefined", eval("typeof " + view + ".setDefault"), view);
         assertEquals("undefined", eval("typeof " + view + "['setDefault']"), view);
         assertEquals("undefined", eval("typeof " + view + "['class']"), view);
         assertEquals(Boolean.FALSE, eval("'setDefault' in " + view), view);
         assertEquals("function", eval("typeof (" + view + ".getAvailableIDs || "
            + view + ".getAvailableLocales)"), view);
      }

      assertDefaultsUnchanged();
   }

   /**
    * The stand-in is context-wide (a class looked up by an unrestricted script stays cached
    * for a restricted one that runs later in the same context), so it applies in both modes.
    */
   @ParameterizedTest
   @ValueSource(strings = { "graal", "report" })
   void unrestrictedScriptsAreGuardedToo(String kind) throws Exception {
      open(kind);
      FormulaContext.setRestricted(false);
      attempt("Java.type('java.util.TimeZone').setDefault(java.util.TimeZone.getTimeZone('UTC'));");
      attempt("java.util.Locale.setDefault(java.util.Locale.FRANCE);");
      assertDefaultsUnchanged();
      assertEquals("UTC", eval("java.util.TimeZone.getTimeZone('UTC').getID()"));
   }

   private static final String[] TAMPER_TARGETS = {
      "Reflect.apply", "Reflect.construct", "Object.defineProperty", "Object.create",
      "Function.prototype.apply", "Function.prototype.call", "Function.prototype.bind",
      "Array.prototype.push", "Array.prototype.slice", "String.prototype.charAt",
      "String.prototype.substring", "Array.from", "Object.keys",
   };

   /**
    * A script that replaces a built-in the stand-in code might use must not get the host class
    * out of it: every receiver and argument the replacement sees is recorded and then tried for
    * the setters. One built-in per script, since a replacement that breaks the stand-in's
    * construction would end the attack early.
    */
   @ParameterizedTest
   @CsvSource({ "graal,true", "graal,false", "report,true", "report,false" })
   void patchedBuiltinsDoNotLeakTheHostClass(String kind, boolean restricted) throws Exception {
      for(String target : TAMPER_TARGETS) {
         if(engine != null) {
            engine.close();
         }

         open(kind);
         FormulaContext.setRestricted(restricted);
         attempt(
            "var seen = [], n = 0, orig = " + target + ";"
            + "var spy = function() { seen[n++] = this;"
            + "   for(var i = 0; i < arguments.length; i++) { seen[n++] = arguments[i]; }"
            + "   return orig.apply(this, arguments); };"
            + target + " = spy;"
            + "try { var T = Java.type('java.util.TimeZone'), L = Java.type('java.util.Locale');"
            + "   T.getTimeZone('UTC'); L.forLanguageTag('de'); L.US; new T('x');"
            + "   Java.type('java.util.SimpleTimeZone').getAvailableIDs();"
            + "   java.util.TimeZone.getTimeZone('UTC'); java.util.Locale.getDefault();"
            + "   var q = {} instanceof T; } catch(e) {}"
            + target + " = orig;"
            + "this.__seen = n;"
            + "for(var i = 0; i < n; i++) { var o = seen[i];"
            + "   try { o.setDefault(java.util.TimeZone.getTimeZone('Pacific/Kiritimati')); }"
            + "   catch(e) {}"
            + "   try { o['setDefault'](java.util.Locale.FRANCE); } catch(e) {}"
            + "   try { o.class.static.setDefault(java.util.Locale.FRANCE); } catch(e) {}"
            + "   try { o.getTimeZone('UTC'); o.constructor.setDefault(o); } catch(e) {} }"
         );
         assertDefaultsUnchanged();
      }
   }

   @ParameterizedTest
   @ValueSource(strings = { "graal", "report" })
   void standInIsSharedAndCheap(String kind) throws Exception {
      open(kind);
      assertEquals(Boolean.TRUE, eval(
         "Java.type('java.util.TimeZone') === Java.type('java.util.TimeZone') && "
            + "Java.type('java.util.Locale') === Java.type('java.util.Locale')"));
   }

   /** The stand-in is cached per context, so a script must not be able to leave state on it. */
   @ParameterizedTest
   @ValueSource(strings = { "graal", "report" })
   void standInCannotCarryScriptState(String kind) throws Exception {
      for(boolean restricted : new boolean[] { true, false }) {
         FormulaContext.setRestricted(restricted);
         open(kind);

         try {
            for(String type : new String[] { "java.util.TimeZone", "java.util.Locale",
                                             "java.util.SimpleTimeZone" })
            {
               String t = "Java.type('" + type + "')";
               // separate attempts, so one that throws cannot skip the others
               attempt(t + ".mine = 5;");
               attempt(t + ".prototype.x = 1;");
               attempt("Object.defineProperty(" + t + ", 'late', {value: 1});");
               // a later exec in the same context must see nothing the first one tried to add
               assertEquals(Boolean.TRUE, eval(
                  "(function(){ var t = " + t + "; return t.mine === undefined && "
                     + "t.late === undefined && t.prototype.x === undefined && "
                     + "Object.isFrozen(t) && Object.isFrozen(t.prototype); })()"),
                  type + " restricted=" + restricted);
            }

            // the guarded view still works after freezing
            assertEquals(Boolean.TRUE, eval(
               "Java.type('java.util.TimeZone') === Java.type('java.util.TimeZone') && "
                  + "Java.type('java.util.TimeZone').getTimeZone('UTC').getID() === 'UTC'"));
            assertDefaultsUnchanged();
         }
         finally {
            engine.close();
            engine = null;
         }
      }
   }

   @ParameterizedTest
   @ValueSource(strings = { "graal", "report" })
   void instanceofIsFalseForNonInstances(String kind) throws Exception {
      open(kind);

      for(String type : new String[] { "java.util.TimeZone", "java.util.Locale",
                                       "java.util.SimpleTimeZone", "java.util.Date" })
      {
         String t = "Java.type('" + type + "')";

         for(String value : new String[] { "null", "undefined", "0", "'x'", "true", "({})",
                                           "[]", "function(){}", "new java.util.ArrayList()" })
         {
            assertEquals(Boolean.FALSE, eval("(" + value + ") instanceof " + t), value + " " + type);
         }
      }

      assertEquals(Boolean.TRUE, eval("java.util.Locale.US instanceof Java.type('java.util.Locale')"));
      assertEquals(Boolean.FALSE, eval("java.util.Locale.US instanceof Java.type('java.util.TimeZone')"));
      assertEquals(Boolean.TRUE, eval(
         "java.util.TimeZone.getTimeZone('UTC') instanceof Java.type('java.util.TimeZone')"));
      assertEquals(Boolean.TRUE, eval(
         "new (Java.type('java.util.SimpleTimeZone'))(0, 'U') instanceof "
            + "Java.type('java.util.SimpleTimeZone')"));
   }

   @ParameterizedTest
   @ValueSource(strings = { "graal", "report" })
   void constructorsAcceptEveryArity(String kind) throws Exception {
      open(kind);
      // SimpleTimeZone has 2, 4, 10 and 11 argument constructors
      assertEquals("A", eval("new (Java.type('java.util.SimpleTimeZone'))(0, 'A').getID()"));
      assertEquals("B", eval("new (Java.type('java.util.SimpleTimeZone'))(0, 'B', 3, 1, 1, 0, "
         + "9, 1, 1, 0).getID()"));
      assertEquals("C", eval("new (Java.type('java.util.SimpleTimeZone'))(0, 'C', 3, 1, 1, 0, "
         + "9, 1, 1, 0, 3600000).getID()"));
      assertEquals("D", eval("new java.util.SimpleTimeZone(0, 'D', 3, 1, 1, 0, 9, 1, 1, 0, "
         + "3600000).getID()"));
      assertEquals("fr", eval("new (Java.type('java.util.Locale'))('fr').getLanguage()"));
      assertEquals("FR", eval("new (Java.type('java.util.Locale'))('fr', 'FR', 'x')"
         + ".getCountry()"));
   }

   private static final String[] REALM_ROUTES = {
      "var g2 = loadWithNewGlobal({name: 'x', script: 'this'});"
         + "g2.Java.type('java.util.TimeZone').setDefault(java.util.TimeZone.getTimeZone('Pacific/Kiritimati'));",
      "var g2 = loadWithNewGlobal({name: 'x', script: 'this'});"
         + "g2.java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone('Pacific/Kiritimati'));",
      "var g2 = loadWithNewGlobal({name: 'x', script: 'this'});"
         + "g2.java.util.Locale.setDefault(java.util.Locale.FRANCE);",
      "load({name: 'x', script: 'java.util.Locale.setDefault(java.util.Locale.FRANCE)'});",
      "loadWithNewGlobal('x.js');",
      "Polyglot.eval('js', \"Java.type('java.util.Locale').setDefault(java.util.Locale.FRANCE)\");",
      "Debug.createRealm().eval('js', 'java.util.Locale.setDefault(java.util.Locale.FRANCE)');",
      "var r = Realm.create(); Realm.eval(r, 'java.util.Locale.setDefault(java.util.Locale.FRANCE)');",
      "$262.createRealm().global.java.util.Locale.setDefault(java.util.Locale.FRANCE);",
      "new (Java.type('org.graalvm.polyglot.Context'))();",
      "var c = Packages.org.graalvm.polyglot.Context.create('js');"
         + "c.eval('js', \"java.util.Locale.setDefault(java.util.Locale.FRANCE)\");",
      "var m = new (Java.type('javax.script.ScriptEngineManager'))();"
         + "m.getEngineByName('js').eval('java.util.Locale.setDefault(java.util.Locale.FRANCE)');",
      "Function('return this')().Java.type('java.util.Locale').setDefault(java.util.Locale.FRANCE);",
      "(0, eval)('Java.type(\"java.util.Locale\")').setDefault(java.util.Locale.FRANCE);",
   };

   /**
    * Every way a script might reach a global or realm that has no guard: a new global from
    * load/loadWithNewGlobal (which are not available), other realm builtins, nested contexts and
    * script engines (classes the filter refuses), and the same realm by other names.
    */
   @ParameterizedTest
   @CsvSource({ "graal,true", "graal,false", "report,true", "report,false" })
   void everyWayToAnotherGlobalIsClosed(String kind, boolean restricted) throws Exception {
      open(kind);
      FormulaContext.setRestricted(restricted);

      for(String route : REALM_ROUTES) {
         attempt(route);
         assertDefaultsUnchanged();
      }

      assertEquals("undefined", eval("typeof loadWithNewGlobal"));
      assertEquals("undefined", eval("typeof load"));
      assertEquals("undefined", eval("typeof Debug"));
      assertEquals("undefined", eval("typeof Realm"));
      assertEquals("undefined", eval("typeof $262"));

      // a nested polyglot context or a script engine is not a class a script can name
      for(String cls : new String[] { "org.graalvm.polyglot.Context", "javax.script.ScriptEngineManager",
                                      "org.graalvm.polyglot.Engine" })
      {
         assertEquals("refused", eval("(function() { try { Java.type('" + cls
            + "'); return 'found'; } catch(e) { return 'refused'; } })()"), cls);
      }

      // the same realm by other names still has the guarded Java.type
      assertEquals("undefined", eval("typeof Function('return this')().Java.type('java.util.Locale')"
         + ".setDefault"));
      assertEquals("undefined", eval("typeof (0, eval)('Java').type('java.util.Locale').setDefault"));
   }

   @ParameterizedTest
   @ValueSource(strings = { "graal", "report" })
   void legitimateUseStillWorks(String kind) throws Exception {
      open(kind);
      assertEquals("UTC", eval("java.util.TimeZone.getTimeZone('UTC').getID()"));
      assertEquals("UTC", eval("Java.type('java.util.TimeZone').getTimeZone('UTC').getID()"));
      assertEquals("Z1", eval("new (Java.type('java.util.SimpleTimeZone'))(3600000, 'Z1')"
                              + ".getID()"));
      assertEquals(3600000.0, ((Number) eval(
         "new (Java.type('java.util.SimpleTimeZone'))(3600000, 'Z1').getRawOffset()"))
         .doubleValue());
      assertEquals("US", eval("java.util.Locale.US.getCountry()"));
      assertEquals("fr", eval("new (Java.type('java.util.Locale'))('fr', 'FR').getLanguage()"));
      assertEquals("de", eval("java.util.Locale.forLanguageTag('de-DE').getLanguage()"));
      assertEquals(oldZone.getID(), eval("java.util.TimeZone.getDefault().getID()"));
      assertEquals(oldDefault.getLanguage(), eval("java.util.Locale.getDefault().getLanguage()"));
      assertEquals(Boolean.TRUE, eval(
         "new (Java.type('java.util.SimpleTimeZone'))(0, 'U') instanceof "
            + "Java.type('java.util.TimeZone')"));
      assertEquals(Boolean.TRUE, eval(
         "java.util.Locale.US instanceof Java.type('java.util.Locale')"));
      assertEquals("2024-01-02 03:04", eval(
         "var f = new java.text.SimpleDateFormat('yyyy-MM-dd HH:mm', java.util.Locale.US);"
            + "f.setTimeZone(java.util.TimeZone.getTimeZone('UTC'));"
            + "f.format(new java.util.Date(1704164640000))"));
      assertEquals("UTC", eval(
         "java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone('UTC'))"
            + ".getTimeZone().getID()"));
      assertEquals("1,234.5", eval(
         "java.text.NumberFormat.getInstance(java.util.Locale.US).format(1234.5)"));
      assertDefaultsUnchanged();
   }
}
