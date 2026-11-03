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
package inetsoft.web.admin.ai.scriptlibrary;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class ScriptLibraryValidatorTest {
   @Test void acceptsIdentifiersIncludingContextualKeywords() {
      for(String name : new String[] { "fn", "_x", "$y", "a1", "let", "async", "Größe" }) {
         assertDoesNotThrow(() -> ScriptLibraryValidator.requireValidName("name", name), name);
      }
   }

   @Test void refusesNonIdentifiersReservedWordsAndPaddedNames() {
      for(String name : new String[] { "qa-scr-bad-name", "1abc", "a b", "class", "return",
                                       " fn", "fn ", "" })
      {
         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ScriptLibraryValidator.requireValidName("name", name), "[" + name + "]");
         assertTrue(ex.getMessage().startsWith("name \"" + name + "\""), ex.getMessage());
         assertTrue(ex.getMessage().endsWith("Nothing was saved."), ex.getMessage());
      }
   }

   @Test void compileFailureNamesTheFieldAndKeepsTheFirstLineOnly() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> ScriptLibraryValidator.requireCompiles(t -> {
            throw new RuntimeException("Unexpected token\nstack line");
         }, "text", "function ("));

      assertEquals("text does not compile: Unexpected token. Nothing was saved.", ex.getMessage());
   }

   @Test void illegalStateFromTheCheckerIsReportedAsAnUnavailableEngine() {
      ScriptLibraryValidator.EngineUnavailableException ex = assertThrows(
         ScriptLibraryValidator.EngineUnavailableException.class,
         () -> ScriptLibraryValidator.requireCompiles(t -> {
            throw new IllegalStateException("Engine not initialized");
         }, "text", "x"));

      assertTrue(ex.getMessage().contains("Engine not initialized"), ex.getMessage());
      assertInstanceOf(IllegalStateException.class, ex.getCause());
   }

   @Test void compilingTextPasses() {
      assertDoesNotThrow(() -> ScriptLibraryValidator.requireCompiles(t -> {}, "text", "x"));
   }
}
