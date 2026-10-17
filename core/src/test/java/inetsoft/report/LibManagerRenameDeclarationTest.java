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

      assertEquals("// f is used by g\nvar s = 'function f(';\nfunction fooBackup(x) { return f; }\n" +
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
