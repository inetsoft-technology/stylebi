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
package inetsoft.report.lib.logical;

import inetsoft.report.lib.ScriptEntry;
import inetsoft.test.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ScriptLogicalLibraryTest {
   @BeforeEach
   void before() {
      lib =  new ScriptLogicalLibrary(new NoopLibrarySecurity());
   }

   @Test
   void userSignatureWithoutParens() {
      assertNull(lib.getSignature("CALC"));
   }

   @Test
   void signatureForNameWithRegexChars() {
      String func = "function a$b(x, y) { return 1; }";
      assertEquals("a$b(x, y)", ScriptEntry.builder().from("a$b", func).build().signature());
      // '.' must not match loosely
      assertEquals("a.b()", ScriptEntry.builder()
         .from("a.b", "function axb(p) { }\nfunction a.b() { }").build().signature());
   }

   @Test
   void signatureForNamesThatAreSubstringsOfFunctionKeyword() {
      for(String name : new String[] { "f", "fn", "n", "un", "ion", "unction", "function", "normal" }) {
         String func = "function " + name + "(x, y) { return 1; }";
         assertEquals(name + "(x, y)", ScriptEntry.builder().from(name, func).build().signature(),
                      name);
      }
   }

   @Test
   void renameEntryDoesNotRewriteFunctionKeyword() {
      for(String name : new String[] { "f", "fn", "n", "un", "ion", "unction" }) {
         ScriptEntry old = ScriptEntry.builder()
            .function("function " + name + "(x, y) {}").signature(name + "(x, y)").build();
         LogicalLibraryEntry<ScriptEntry> entry = LogicalLibraryEntry.<ScriptEntry>builder()
            .asset(old).audit(false).build();
         assertEquals("g(x, y)", lib.renameEntry(entry, name, "g").asset().signature(), name);
      }

      // signature persisted by the buggy version still carries the keyword
      ScriptEntry legacy = ScriptEntry.builder().function("").signature("function f(x, y)").build();
      LogicalLibraryEntry<ScriptEntry> entry = LogicalLibraryEntry.<ScriptEntry>builder()
         .asset(legacy).audit(false).build();
      assertEquals("function g(x, y)", lib.renameEntry(entry, "f", "g").asset().signature());
   }

   @Test
   void renameEntryToNameWithRegexChars() {
      ScriptEntry old = ScriptEntry.builder().function("function f(x) {}").signature("f(x)").build();
      LogicalLibraryEntry<ScriptEntry> entry = LogicalLibraryEntry.<ScriptEntry>builder()
         .asset(old).audit(false).build();
      assertEquals("a$1b(x)", lib.renameEntry(entry, "f", "a$1b").asset().signature());

      ScriptEntry dotted = ScriptEntry.builder().function("").signature("a.b(x)").build();
      entry = LogicalLibraryEntry.<ScriptEntry>builder().asset(dotted).audit(false).build();
      assertEquals("c(x)", lib.renameEntry(entry, "a.b", "c").asset().signature());
      // old name '.' must not match a different character
      dotted = ScriptEntry.builder().function("").signature("axb(a.b)").build();
      entry = LogicalLibraryEntry.<ScriptEntry>builder().asset(dotted).audit(false).build();
      assertEquals("axb(c)", lib.renameEntry(entry, "a.b", "c").asset().signature());
   }

   ScriptLogicalLibrary lib;
}
