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
package inetsoft.report;

import inetsoft.report.lib.logical.NoopLibrarySecurity;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #78735: renaming a library function (EM and Composer tree renames call only
 * {@code LibManager.renameScript}) must rename the function's own declaration, and only that.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  LibManagerRenameDeclarationTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LibManagerRenameDeclarationTest {
   @Configuration
   static class Config {
      @Bean
      RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }
   }

   @Autowired private BlobStorageManager blobStorageManager;

   @Test
   void renameScriptRenamesTheDeclaration() {
      LibManager lib = newLibManager();
      lib.setScript("f", "function f(x) { return x; }");

      lib.renameScript("f", "g");

      assertNull(lib.getScript("f"));
      assertEquals("function g(x) { return x; }", lib.getScript("g"));
   }

   @Test
   void renameScriptChangesOnlyTheDeclaration() {
      LibManager lib = newLibManager();
      String text = "function format(n) {\n" +
         "   // format the value\n" +
         "   var fooBackup = 'format';\n" +
         "   return formatNumber(n) + fooBackup;\n" +
         "}";
      lib.setScript("format", text);

      lib.renameScript("format", "fmt");

      assertEquals(text.replaceFirst("function format\\(", "function fmt("), lib.getScript("fmt"));
   }

   @Test
   void renameFunctionDeclarationLeavesSiblingsLiteralsAndCommentsAlone() {
      String text = "// f is used by g\nvar s = 'function f(';\nfunction fooBackup(x) { return f; }\n" +
         "function f (x) { return 'f'; }";

      String renamed = LibManager.renameFunctionDeclaration("f", "g2", text);

      assertEquals("// f is used by g\nvar s = 'function f(';\nfunction fooBackup(x) { return g2; }\n" +
                   "function g2 (x) { return 'f'; }", renamed);
   }

   @Test
   void renameFunctionDeclarationIgnoresAPrefixOfALongerName() {
      String text = "function formatNumber(n) { return n; }";

      assertEquals(text, LibManager.renameFunctionDeclaration("format", "fmt", text));
   }

   @Test
   void renameFunctionDeclarationHandlesRegexAndReplacementMetacharacters() {
      String text = "function a$b(x) { return x; }";

      assertEquals("function c$1(x) { return x; }",
                   LibManager.renameFunctionDeclaration("a$b", "c$1", text));
   }

   @Test
   void renameFunctionDeclarationHandlesVarLetConstAndGeneratorForms() {
      assertEquals("var g = function(x) { return g(x); };\nvar fBackup = 1;",
                   LibManager.renameFunctionDeclaration(
                      "f", "g", "var f = function(x) { return f(x); };\nvar fBackup = 1;"));
      assertEquals("const g = 0.1;", LibManager.renameFunctionDeclaration("f", "g", "const f = 0.1;"));
      assertEquals("let g = 2;", LibManager.renameFunctionDeclaration("f", "g", "let f = 2;"));
      assertEquals("function* g(x) { yield x; }",
                   LibManager.renameFunctionDeclaration("f", "g", "function* f(x) { yield x; }"));
      assertEquals("function *g(x) { yield x; }",
                   LibManager.renameFunctionDeclaration("f", "g", "function *f(x) { yield x; }"));
   }

   @Test
   void renameFunctionDeclarationLeavesASiblingVarAlone() {
      String text = "var fBackup = 1;\nvar other = f;";

      assertEquals(text, LibManager.renameFunctionDeclaration("f", "g", text));
   }

   /** A name that merely begins with "function" is not a declarator keyword. */
   @Test
   void renameFunctionDeclarationDoesNotTreatAFunctionPrefixedNameAsADeclaration() {
      String text = "var y = functionTotal(1);\nvar Total = function(x) { return x; };";

      assertEquals("var y = functionTotal(1);\nvar Sum = function(x) { return x; };",
                   LibManager.renameFunctionDeclaration("Total", "Sum", text));
      assertEquals("var y = functionTotal(1);",
                   LibManager.renameFunctionDeclaration("Total", "Sum", "var y = functionTotal(1);"));
   }

   @Test
   void renameFunctionDeclarationRenamesSelfReferencesInCode() {
      assertEquals("function g(n) { return n <= 1 ? 1 : n * g(n - 1); }",
                   LibManager.renameFunctionDeclaration(
                      "f", "g", "function f(n) { return n <= 1 ? 1 : n * f(n - 1); }"));
      assertEquals("var g = function(n) { return n ? g(n - 1) : 0; };",
                   LibManager.renameFunctionDeclaration(
                      "f", "g", "var f = function(n) { return n ? f(n - 1) : 0; };"));
      assertEquals("const g = n => n ? g(n - 1) : 0;",
                   LibManager.renameFunctionDeclaration(
                      "f", "g", "const f = n => n ? f(n - 1) : 0;"));
      assertEquals("function* g(n) { if(n) { yield* g(n - 1); } yield n; }",
                   LibManager.renameFunctionDeclaration(
                      "f", "g", "function* f(n) { if(n) { yield* f(n - 1); } yield n; }"));
   }

   @Test
   void renameFunctionDeclarationLeavesStringsTemplatesAndCommentsInTheBodyAlone() {
      String text = "function f(n) {\n" +
         "   // f calls f\n" +
         "   /* f( */\n" +
         "   var a = 'f(' + \"f\" + `f ${f(n - 1)} f`;\n" +
         "   var re = /f'/.test(a);\n" +
         "   return f(n - 1);\n" +
         "}";
      String expected = "function g(n) {\n" +
         "   // f calls f\n" +
         "   /* f( */\n" +
         "   var a = 'f(' + \"f\" + `f ${g(n - 1)} f`;\n" +
         "   var re = /f'/.test(a);\n" +
         "   return g(n - 1);\n" +
         "}";

      assertEquals(expected, LibManager.renameFunctionDeclaration("f", "g", text));
   }

   @Test
   void renameFunctionDeclarationLeavesPropertyAccessAndLongerIdentifiersAlone() {
      String text = "function f(x) { return x.f + fBackup(x) + functionf(x) + obj.f(x) + $f + f$ + f(x); }";

      assertEquals("function g(x) { return x.f + fBackup(x) + functionf(x) + obj.f(x) + $f + f$ + g(x); }",
                   LibManager.renameFunctionDeclaration("f", "g", text));
      assertEquals("function g(a) { return g(...[a]) + [...g]; }",
                   LibManager.renameFunctionDeclaration(
                      "f", "g", "function f(a) { return f(...[a]) + [...f]; }"));
   }

   /** A comment holding the same declaration earlier on the same line must not win. */
   @Test
   void renameFunctionDeclarationFindsTheDeclarationOnlyInCode() {
      assertEquals("/* function f() old */ function g(x) { return x; }",
                   LibManager.renameFunctionDeclaration(
                      "f", "g", "/* function f() old */ function f(x) { return x; }"));
      assertEquals("var s = \"function f(\"; var g = 1;",
                   LibManager.renameFunctionDeclaration("f", "g", "var s = \"function f(\"; var f = 1;"));
      assertEquals("// function f(x)\nvar y = 1;",
                   LibManager.renameFunctionDeclaration("f", "g", "// function f(x)\nvar y = 1;"));
   }

   /** A local declaration of the same name in an earlier helper is not the library function. */
   @Test
   void renameFunctionDeclarationPrefersATopLevelDeclarationOverANestedLocal() {
      String text = "function helper() { var f = 1; return f; }\nfunction f(x) { return helper() + x; }";

      String renamed = LibManager.renameFunctionDeclaration("f", "g", text);

      assertTrue(renamed.contains("function g(x)"), renamed);
      assertTrue(renamed.contains("function helper()"), renamed);
      assertFalse(renamed.contains("function f("), renamed);
   }

   @Test
   void renameFunctionDeclarationTolerantOfNullText() {
      assertNull(LibManager.renameFunctionDeclaration("f", "g", null));
   }

   private LibManager newLibManager() {
      // no-op cluster: the test cluster would deliver LibManager's own change message synchronously
      // and reload away the unsaved rename
      return new LibManager(new NoopLibrarySecurity(), "r78735-org", mock(Cluster.class),
                            blobStorageManager) { };
   }
}
