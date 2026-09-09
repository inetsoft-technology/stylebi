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

import inetsoft.uql.onedrive.OneDriveQuery;
import inetsoft.uql.onedrive.OneDriveRuntime;
import inetsoft.uql.onedrive.OneDriveService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers charter B1. Lives in this connector module's {@code WizDatabaseController} package (not
 * core, which cannot depend on connector classes) so it can call the package-private {@code
 * classifyQueryClass} directly -- same shape as {@code ServerFileAnnotationClassTest}.
 *
 * <p>Deliberately calls the TWO-ARG overload with {@code OneDriveRuntime.class} as the runtime, not
 * the one-arg convenience overload: {@code OneDriveQuery} is a {@code SelectableTabularQuery}, so the
 * one-arg overload (which never sees a runtime class) would still classify it {@code "FILE"} -- the
 * whole point of B1 is that the TWO-ARG classifier, with a runtime that now implements {@link
 * inetsoft.uql.tabular.TabularCatalogProvider}, overrides that to {@code "METADATA"}.
 */
@Tag("core")
class OneDriveAnnotationClassTest {
   @Test
   void aOneDriveQueryIsAskedForItsMetadataOnceItsRuntimeImplementsTheCatalogSpi() {
      assertEquals("METADATA",
         WizDatabaseController.classifyQueryClass(OneDriveQuery.class, OneDriveRuntime.class));
   }

   /**
    * B12's typo-catching half (mirrors ServerFile's own): the ONLY place both the canary's added
    * literal (in {@code core}'s {@code TabularCatalogProviderImplementerCanaryTest}) and the
    * runtime's real FQCN can be compared against each other is here, where the class is genuinely
    * on the classpath -- {@code core}'s canary cannot see connector classes at all, so this test
    * does not close that gap, it narrows it. A reviewer still has to diff the canary's new literal
    * character-by-character against {@code OneDriveService.getRuntimeClass()}'s.
    */
   @Test
   void theRuntimeClassOneDriveServiceDeclaresMatchesTheRealClassName() {
      assertEquals(OneDriveRuntime.class.getName(), new OneDriveService().getRuntimeClass());
   }
}
