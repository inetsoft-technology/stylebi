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

import inetsoft.uql.serverfile.ServerFileQuery;
import inetsoft.uql.serverfile.ServerFileRuntime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers charter A1. Lives in this connector module (not core, which cannot depend on connector
 * classes) but declares itself in {@code WizDatabaseController}'s package so it can call the
 * package-private {@code classifyQueryClass} directly -- same shape as
 * {@code ODataDatasourceAnnotationClassTest}.
 *
 * <p>Deliberately calls the TWO-ARG overload with {@code ServerFileRuntime.class} as the runtime,
 * not the one-arg convenience overload: {@code ServerFileQuery} is a {@code SelectableTabularQuery},
 * so the one-arg overload (which never sees a runtime class) would still classify it {@code "FILE"}
 * -- the whole point of A1 is that the TWO-ARG classifier, with a runtime that implements {@link
 * inetsoft.uql.tabular.TabularCatalogProvider}, overrides that to {@code "METADATA"}.
 */
@Tag("core")
class ServerFileAnnotationClassTest {
   @Test
   void aServerFileQueryIsAskedForItsMetadataOnceItsRuntimeImplementsTheCatalogSpi() {
      assertEquals("METADATA",
         WizDatabaseController.classifyQueryClass(ServerFileQuery.class, ServerFileRuntime.class));
   }
}
