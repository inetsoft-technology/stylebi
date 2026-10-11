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
package inetsoft.web.wiz.controller;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins {@link WizDatasourceNames} to the rules of the native Angular validators
 * ({@code FormValidators.DATASOURCE_NAME_REGEXP} and {@code isValidDataSourceFolderName}), so a
 * name the native editor refuses is refused here with the rule named.
 */
@Tag("core")
class WizDatasourceNamesTest {
   @ParameterizedTest
   @ValueSource(strings = { "\\", "/", "?", "\"", "*", ":", "<", ">", "|", ".", "^" })
   void datasourceNameRejectsEachForbiddenCharacterAnywhere(String c) {
      for(String name : new String[] { c + "abc", "ab" + c + "cd", "abc" + c, c }) {
         IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> WizDatasourceNames.requireValidDatasourceName(name), name);
         assertTrue(e.getMessage().contains("Data source name"), e.getMessage());
         assertTrue(e.getMessage().contains("^"), e.getMessage());
      }
   }

   @Test
   void datasourceNameAcceptsOrdinaryAndNonAsciiNames() {
      for(String name : new String[] { "Orders", "my db 2", "sales-2026_q1", "数据源", "a b" }) {
         assertDoesNotThrow(() -> WizDatasourceNames.requireValidDatasourceName(name), name);
      }
   }

   @Test
   void datasourceNameMatchesTheWholeString() {
      assertThrows(IllegalArgumentException.class,
                   () -> WizDatasourceNames.requireValidDatasourceName("abc\n.x"));
      assertDoesNotThrow(() -> WizDatasourceNames.requireValidDatasourceName("abc\n"));
   }

   @ParameterizedTest
   @ValueSource(strings = { "\\", "/", "?", "\"", "*", ":", "<", ">", "|", "^" })
   void folderNameRejectsEachForbiddenCharacter(String c) {
      for(String name : new String[] { c + "abc", "ab" + c + "cd", "abc" + c }) {
         IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> WizDatasourceNames.requireValidFolderName(name), name);
         assertTrue(e.getMessage().contains("Folder name"), e.getMessage());
      }
   }

   @Test
   void folderNameAllowsADotInsideAndRejectsReservedAndDotSegments() {
      assertDoesNotThrow(() -> WizDatasourceNames.requireValidFolderName("v1.2"));
      assertDoesNotThrow(() -> WizDatasourceNames.requireValidFolderName("cubes"));

      IllegalArgumentException cubes = assertThrows(
         IllegalArgumentException.class, () -> WizDatasourceNames.requireValidFolderName("Cubes"));
      assertTrue(cubes.getMessage().contains("Cubes"), cubes.getMessage());

      assertThrows(IllegalArgumentException.class,
                   () -> WizDatasourceNames.requireValidFolderName("."));
      assertThrows(IllegalArgumentException.class,
                   () -> WizDatasourceNames.requireValidFolderName(".."));
   }

   @Test
   void datasourcePathValidatesFolderSegmentsAndTheLeaf() {
      assertDoesNotThrow(
         () -> WizDatasourceNames.requireValidDatasourcePath("spec.name", "Folder/Sub/Orders"));
      assertDoesNotThrow(
         () -> WizDatasourceNames.requireValidDatasourcePath("spec.name", "Orders"));

      IllegalArgumentException leaf = assertThrows(IllegalArgumentException.class,
         () -> WizDatasourceNames.requireValidDatasourcePath("spec.name", "Folder/a.b"));
      assertTrue(leaf.getMessage().startsWith("spec.name"), leaf.getMessage());

      IllegalArgumentException folder = assertThrows(IllegalArgumentException.class,
         () -> WizDatasourceNames.requireValidDatasourcePath("spec.name", "Fo>lder/Orders"));
      assertTrue(folder.getMessage().startsWith("spec.name"), folder.getMessage());
   }

   @Test
   void folderPathValidatesEverySegment() {
      assertDoesNotThrow(() -> WizDatasourceNames.requireValidFolderPath("folderPath", "A/B.c"));

      assertThrows(IllegalArgumentException.class,
                   () -> WizDatasourceNames.requireValidFolderPath("folderPath", "A/B>"));
      assertThrows(IllegalArgumentException.class,
                   () -> WizDatasourceNames.requireValidFolderPath("folderPath", "A/Cubes"));
      assertThrows(IllegalArgumentException.class,
                   () -> WizDatasourceNames.requireValidFolderPath("folderPath", "A//B"));
   }
}
