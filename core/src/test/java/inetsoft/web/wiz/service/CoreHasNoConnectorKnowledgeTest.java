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
package inetsoft.web.wiz.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mechanical guard for the charter's core-side reverse assertion: {@code community/core} must not
 * import any {@code inetsoft.uql.odata} type, and no {@code org.w3c.dom} (EDMX) concept may leave
 * a connector plugin. This scans the COMPILED {@link TabularCatalogService} class's constant pool
 * (its UTF-8 constants, which include every referenced class's internal name) rather than the
 * source, so it also catches a reference introduced through generated code or reflection strings.
 *
 * A constant-pool UTF-8 entry for an ASCII class name is byte-identical whether decoded as UTF-8
 * or ISO-8859-1, so a raw Latin-1 scan of the class file bytes is sufficient here — no class file
 * parser needed.
 */
@Tag("core")
class CoreHasNoConnectorKnowledgeTest {
   @Test
   void tabularCatalogServiceReferencesNoConnectorOrEdmxTypes() throws IOException {
      String classFile = classFileAsLatin1(TabularCatalogService.class);

      assertFalse(classFile.contains("inetsoft/uql/odata"),
         "TabularCatalogService must not reference any OData connector type");
      assertFalse(classFile.contains("org/w3c/dom"),
         "TabularCatalogService must not reference any DOM/EDMX type");
      assertFalse(classFile.contains("TabularQueryParamsSchemaBuilder"),
         "annotation's catalog path must not depend on the query-builder schema contract");
      assertFalse(classFile.contains("TabularQuerySchema"),
         "annotation's catalog path must not depend on the query-builder schema contract");
   }

   /**
    * Charter C6 (tabular empty-result error report, 2026-09-10): {@code WorksheetTableService}
    * reads a connector's {@code getValidJsonPath()} (the effective row path, for the empty-columns
    * message) reflectively -- {@code Class.getMethod("getValidJsonPath").invoke(query)} -- so that
    * core never needs a compile-time reference to {@code RestJsonQuery} or any other type under
    * {@code inetsoft.uql.rest}. This scans the same way as the test above: a raw Latin-1 read of
    * the compiled class file's constant pool, which would contain the connector package name as a
    * UTF-8 constant if an import (or any other compile-time reference) existed.
    */
   @Test
   void worksheetTableServiceReferencesNoRestConnectorType() throws IOException {
      String classFile = classFileAsLatin1(WorksheetTableService.class);

      assertFalse(classFile.contains("inetsoft/uql/rest"),
         "WorksheetTableService must reach RestJsonQuery.getValidJsonPath() only by reflection, " +
            "never by importing an inetsoft-rest connector type");
   }

   private String classFileAsLatin1(Class<?> clazz) throws IOException {
      String resourceName = clazz.getSimpleName() + ".class";

      try(InputStream in = clazz.getResourceAsStream(resourceName)) {
         assertTrue(in != null, "compiled class file not found on test classpath: " + resourceName);
         return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
      }
   }
}
