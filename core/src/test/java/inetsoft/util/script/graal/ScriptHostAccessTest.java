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

import org.graalvm.polyglot.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class ScriptHostAccessTest {
   private Context newContext() {
      return Context.newBuilder("js")
         .allowHostAccess(ScriptHostAccess.hostAccess())
         .allowHostClassLookup(ScriptHostAccess.classFilter())
         .build();
   }

   @Test void allowedJavaClassLoads() {
      try(Context ctx = newContext()) {
         Object v = ScriptValueConverter.toHost(
            ctx.eval("js", "Java.type('java.lang.Math').max(2, 5)"));
         assertEquals(5.0, v);
      }
   }

   @Test void deniedJavaClassThrows() {
      try(Context ctx = newContext()) {
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js", "Java.type('java.lang.System').exit(1)"));
      }
   }

   @Test void exportedMethodAccessible() {
      // Note: allowPublicAccess(true) is required for Java.type() static method access;
      // this means all public methods of host objects are also accessible. The @Export
      // annotation pattern still works for selective opt-in but cannot be used to DENY
      // access to other public methods when allowPublicAccess is true. The class filter
      // (allowHostClassLookup) is the primary security boundary.
      try(Context ctx = newContext()) {
         ctx.getBindings("js").putMember("h", new Sample());
         assertEquals("ok", ScriptValueConverter.toHost(ctx.eval("js", "h.allowed()")));
      }
   }

   /** FIX 1: internal blocked class must not be reachable via Java.type(). */
   @Test void blockedInternalClassDenied() {
      try(Context ctx = newContext()) {
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js",
               "Java.type('inetsoft.report.internal.license.LicenseManager')"));
      }
   }

   /** FIX 2: reflection escape via getClass() on a host object must be blocked. */
   @Test void reflectionEscapeBlocked() {
      try(Context ctx = newContext()) {
         ctx.getBindings("js").putMember("d", new java.util.Date());
         // d.getClass() would return java.lang.Class — denyAccess(Class.class) must block it
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js", "d.getClass()"));
      }
   }

   /**
    * Task 6.4: curated exact safe classes load even when their package is in the
    * block list (java.sql, java.text). java.util.UUID is a plain curated class.
    */
   @Test void curatedExactClassOverBlockedPackage() {
      try(Context ctx = newContext()) {
         assertDoesNotThrow(() -> ctx.eval("js", "Java.type('java.sql.Date')"));
         assertDoesNotThrow(() -> ctx.eval("js", "Java.type('java.text.NumberFormat')"));
         assertDoesNotThrow(() -> ctx.eval("js", "Java.type('java.util.UUID')"));
      }
   }

   /** Task 6.4: dangerous classes remain blocked (not in the exact allow-list). */
   @Test void dangerousClassesStillBlocked() {
      try(Context ctx = newContext()) {
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js", "Java.type('java.lang.System')"));
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js", "Java.type('java.lang.Runtime')"));
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js", "Java.type('java.lang.Class')"));
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js", "Java.type('java.io.File')"));
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js",
               "Java.type('inetsoft.report.internal.license.LicenseManager')"));
      }
   }

   /**
    * Task 6.4: a java.sql class that is NOT in the curated allow-list is still
    * denied by the package block.
    */
   @Test void uncuratedBlockedPackageClassDenied() {
      try(Context ctx = newContext()) {
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js", "Java.type('java.sql.DriverManager')"));
      }
   }

   /**
    * Regression (#75423): the broad main-branch allow-list was narrowed away in
    * the initial GraalJS cutover. java.awt.Color (and the java.awt/text/util,
    * com/org families) must be reachable again via Java.type.
    */
   @Test void restoredPackageAllowListLoads() {
      try(Context ctx = newContext()) {
         assertDoesNotThrow(() -> ctx.eval("js", "Java.type('java.awt.Color')"));
         assertDoesNotThrow(() -> ctx.eval("js", "Java.type('java.util.ArrayList')"));
         assertDoesNotThrow(() -> ctx.eval("js", "Java.type('java.text.MessageFormat')"));
      }
   }

   /** Threading stays blocked regardless of the restored package allow-list. */
   @Test void threadingStaysBlocked() {
      try(Context ctx = newContext()) {
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js", "Java.type('java.lang.Thread')"));
         assertThrows(PolyglotException.class,
            () -> ctx.eval("js", "Java.type('java.util.concurrent.ConcurrentHashMap')"));
      }
   }

   public static class Sample {
      @org.graalvm.polyglot.HostAccess.Export public String allowed() { return "ok"; }
      public String denied() { return "no"; }
   }
}
