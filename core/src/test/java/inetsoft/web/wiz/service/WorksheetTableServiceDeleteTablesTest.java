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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.MirrorTableAssembly;
import inetsoft.uql.asset.PhysicalBoundTableAssembly;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for {@link WorksheetTableService#restorePrimaryAssembly}.
 *
 * <p>The bug: {@link Worksheet#removeAssembly} clears the worksheet's primary assembly to
 * {@code null} when the deleted table was primary, but never assigns a replacement — there is
 * no leaf-recomputation logic anywhere in the delete path. A caller who builds a chain of
 * tables (A -> B, B primary), later adds a diagnostic leaf C on top of B and makes it primary,
 * then deletes C, is left with a worksheet that has NO primary table even though B is once
 * again the sole, unambiguous leaf. The next {@code create_viewsheet} call then fails with
 * "Available worksheet columns: []" instead of resolving to B. {@code restorePrimaryAssembly}
 * fixes this by re-picking the primary when exactly one remaining table qualifies as a leaf
 * (no other remaining table depends on it) — mirroring the "don't guess when ambiguous"
 * convention from the create-path primary-selection fixes: zero or multiple candidates leave
 * the worksheet without a primary rather than silently picking one.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetTableServiceDeleteTablesTest {
   private static WorksheetTableService service() {
      // restorePrimaryAssembly and its helper (findExternalDependent) read only their
      // parameters, never instance state, so null dependencies are safe here (mirrors
      // WorksheetTableServiceShouldProbeTest / WorksheetTableServiceWindowColumnsTest).
      return new WorksheetTableService(null, null, null, null, null, null, null, null, null);
   }

   @Test
   void singleRemainingLeafBecomesPrimary() {
      Worksheet ws = new Worksheet();
      TableAssembly base = new PhysicalBoundTableAssembly(ws, "BASE");
      ws.addAssembly(base);

      // Simulate: BASE was primary, then a diagnostic mirror was added on top and made
      // primary, then that mirror was deleted -- removeAssembly() already cleared primary.
      ws.setPrimaryAssembly("BASE");
      MirrorTableAssembly diag = new MirrorTableAssembly(ws, "DIAG", base);
      ws.addAssembly(diag);
      ws.setPrimaryAssembly("DIAG");
      ws.removeAssembly("DIAG");
      assertNull(ws.getPrimaryAssemblyName(), "removeAssembly should have cleared the primary");

      service().restorePrimaryAssembly(ws);

      assertEquals("BASE", ws.getPrimaryAssemblyName(),
         "the sole remaining leaf table must be restored as primary");
   }

   @Test
   void ambiguousLeavesLeavePrimaryNull() {
      Worksheet ws = new Worksheet();
      TableAssembly base = new PhysicalBoundTableAssembly(ws, "BASE");
      ws.addAssembly(base);
      TableAssembly leafA = new MirrorTableAssembly(ws, "LEAF_A", base);
      ws.addAssembly(leafA);
      TableAssembly leafB = new MirrorTableAssembly(ws, "LEAF_B", base);
      ws.addAssembly(leafB);

      // Simulate a deleted former primary (on top of LEAF_A) so the worksheet starts from
      // primary==null, same as the real delete path, instead of relying on addAssembly()'s
      // "first assembly added becomes primary" default -- which would make this a test of
      // that default, not of restorePrimaryAssembly.
      TableAssembly deletedPrimary = new MirrorTableAssembly(ws, "DELETED_PRIMARY", leafA);
      ws.addAssembly(deletedPrimary);
      ws.setPrimaryAssembly("DELETED_PRIMARY");
      ws.removeAssembly("DELETED_PRIMARY");
      assertNull(ws.getPrimaryAssemblyName(), "removeAssembly should have cleared the primary");

      // Two independent leaves remain (LEAF_A, LEAF_B) -- there is no unambiguous choice.
      service().restorePrimaryAssembly(ws);

      assertNull(ws.getPrimaryAssemblyName(),
         "ambiguous (multiple) leaf candidates must not be silently guessed");
   }

   @Test
   void noRemainingTablesLeavePrimaryNull() {
      Worksheet ws = new Worksheet();

      service().restorePrimaryAssembly(ws);

      assertNull(ws.getPrimaryAssemblyName(), "an empty worksheet has no candidate to restore");
   }
}
