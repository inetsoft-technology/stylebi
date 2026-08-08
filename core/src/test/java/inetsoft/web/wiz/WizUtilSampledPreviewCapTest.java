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

package inetsoft.web.wiz;

import inetsoft.uql.asset.PhysicalBoundTableAssembly;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #75989. The sampled-preview cap used to be set worksheet-WIDE
 * ({@code WorksheetInfo.setDesignMaxRows}), which capped every table assembly independently —
 * including the few-row lookup wiz injects to label a foreign key. Truncating the lookup side of that
 * INNER join destroys matches rather than sampling facts: measured on a 984-row table, a cap of 8
 * returned ZERO rows while the same binding at cap 20 returned 20.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizUtilSampledPreviewCapTest {
   @Test
   void capsTheFactTableAndTheJoinButNotTheInjectedLookup() {
      Worksheet ws = worksheetWithFkLabelJoin();

      WizUtil.applySampledPreviewCap(ws, 8);

      // The fact table and the join carry the sampled rows, so both are capped...
      assertEquals(8, table(ws, "work_packages").getMaxRows());
      assertEquals(8, table(ws, "work_packages__fkjoin").getMaxRows());
      // ...but the injected lookup is a dimension table, never part of the sampled fact stream.
      assertEquals(0, table(ws, "work_packages__fk_projects").getMaxRows(),
                   "an injected FK-label lookup must stay uncapped, or the join loses matches");
      // And never worksheet-wide — that is the defect itself.
      assertEquals(0, ws.getWorksheetInfo().getDesignMaxRows());
   }

   @Test
   void clearsAPreviousCapWhenTheCallerAsksForFullData() {
      // Same runtime is re-rendered repeatedly (WizVsService), so a lingering cap would silently make
      // "full data" partial.
      Worksheet ws = worksheetWithFkLabelJoin();
      WizUtil.applySampledPreviewCap(ws, 8);

      WizUtil.applySampledPreviewCap(ws, 0);

      assertEquals(0, table(ws, "work_packages").getMaxRows());
      assertEquals(0, table(ws, "work_packages__fkjoin").getMaxRows());
      assertEquals(0, table(ws, "work_packages__fk_projects").getMaxRows());
      assertEquals(0, ws.getWorksheetInfo().getDesignMaxRows());
   }

   @Test
   void treatsANegativeCapAsFullDataRatherThanAnInvalidRowLimit() {
      Worksheet ws = worksheetWithFkLabelJoin();

      WizUtil.applySampledPreviewCap(ws, -5);

      assertEquals(0, table(ws, "work_packages").getMaxRows());
   }

   @Test
   void doesNotConfuseTheJoinItselfWithTheLookup() {
      // "__fkjoin" has no trailing underscore, so it must NOT match the "__fk_" lookup infix — the join
      // is where the fact rows are and has to be capped like any other detail table.
      Worksheet ws = new Worksheet();
      ws.addAssembly(new PhysicalBoundTableAssembly(ws, "orders__fkjoin"));

      WizUtil.applySampledPreviewCap(ws, 25);

      assertEquals(25, table(ws, "orders__fkjoin").getMaxRows());
   }

   // The cap has to be READABLE again afterwards, and that is not cosmetic: the sampled/sampleMaxRows
   // flags (which tell the caller its Sum/Count is approximate) and the lazy re-fetch path both read it
   // back. Left reading designMaxRows, a live 8-of-984-row sample reported itself as full data with no
   // warning at all — caught only by running it, since the caps themselves were set correctly.
   @Test
   void reportsTheCapBackSoSampledStaysSampled() {
      Worksheet ws = worksheetWithFkLabelJoin();

      WizUtil.applySampledPreviewCap(ws, 8);

      assertEquals(8, WizUtil.sampledPreviewCap(ws));
   }

   @Test
   void reportsZeroOnFullDataAndAfterAPreviousCapIsCleared() {
      Worksheet ws = worksheetWithFkLabelJoin();
      assertEquals(0, WizUtil.sampledPreviewCap(ws));

      WizUtil.applySampledPreviewCap(ws, 8);
      WizUtil.applySampledPreviewCap(ws, 0);

      assertEquals(0, WizUtil.sampledPreviewCap(ws));
   }

   @Test
   void readsTheCapFromDetailTablesRatherThanTheUncappedLookup() {
      // The lookup is deliberately left at 0, so a reader that included it would always report 0 —
      // i.e. "not sampled" — no matter what cap was requested.
      Worksheet ws = worksheetWithFkLabelJoin();

      WizUtil.applySampledPreviewCap(ws, 12);

      assertEquals(0, table(ws, "work_packages__fk_projects").getMaxRows());
      assertEquals(12, WizUtil.sampledPreviewCap(ws));
   }

   @Test
   void toleratesANullWorksheet() {
      assertDoesNotThrow(() -> WizUtil.applySampledPreviewCap(null, 8));
      assertEquals(0, WizUtil.sampledPreviewCap(null));
   }

   /** A single-table worksheet after FK-label injection: fact table, injected lookup, and the join. */
   private static Worksheet worksheetWithFkLabelJoin() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new PhysicalBoundTableAssembly(ws, "work_packages"));
      ws.addAssembly(new PhysicalBoundTableAssembly(ws, "work_packages__fk_projects"));
      ws.addAssembly(new PhysicalBoundTableAssembly(ws, "work_packages__fkjoin"));
      return ws;
   }

   private static TableAssembly table(Worksheet ws, String name) {
      TableAssembly table = (TableAssembly) ws.getAssembly(name);
      assertNotNull(table, "test fixture is missing assembly " + name);
      return table;
   }
}
