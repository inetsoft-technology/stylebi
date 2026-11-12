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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.uql.util.XEmbeddedTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80479: a bare EmbeddedTableVSAssembly bound to a worksheet embedded table makes the
 * viewsheet wrap that table in a mirror, so the cell edit tools refuse it. The agent add path
 * flags the assembly and the table binding un-wraps the mirror. Everything here but the
 * runtime-viewsheet shell is real: the viewsheet, its worksheet and the mirror preparation.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class EmbeddedTableFlagTest {
   private static final String TABLE = "Targets";
   private static final String GRID = "TargetGrid";

   @Test
   void unflaggedEmbeddedTableMakesTheWorksheetTableAMirror() throws Exception {
      Viewsheet vs = viewsheetWithBoundGrid(false);

      assertInstanceOf(MirrorTableAssembly.class, vs.getBaseWorksheet().getAssembly(TABLE),
                       "the pre-fix state the cell tools refuse");
      assertFalse(EmbeddedTableFlag.unwrap(runtime(vs), GRID), "nothing flagged, nothing to un-wrap");
      assertInstanceOf(MirrorTableAssembly.class, vs.getBaseWorksheet().getAssembly(TABLE));
   }

   @Test
   void flaggedEmbeddedTableKeepsTheWorksheetTableEmbedded() throws Exception {
      Viewsheet vs = viewsheetWithBoundGrid(true);

      assertInstanceOf(EmbeddedTableAssembly.class, vs.getBaseWorksheet().getAssembly(TABLE));
      assertFalse(vs.getBaseWorksheet().getAssembly(TABLE) instanceof MirrorTableAssembly);
      assertNull(vs.getBaseWorksheet().getAssembly(TABLE + Viewsheet.OUTER_TABLE_SUFFIX));

      vs.resetWS();
      assertInstanceOf(EmbeddedTableAssembly.class, vs.getBaseWorksheet().getAssembly(TABLE));
      assertFalse(EmbeddedTableFlag.unwrap(runtime(vs), GRID), "already embedded");
   }

   @Test
   void unwrapRemovesAMirrorBuiltBeforeTheAssemblyWasFlagged() throws Exception {
      Viewsheet vs = viewsheetWithBoundGrid(false);
      assertInstanceOf(MirrorTableAssembly.class, vs.getBaseWorksheet().getAssembly(TABLE));

      EmbeddedTableFlag.flag(vs.getAssembly(GRID));
      assertTrue(EmbeddedTableFlag.unwrap(runtime(vs), GRID));

      assertInstanceOf(EmbeddedTableAssembly.class, vs.getBaseWorksheet().getAssembly(TABLE));
      assertFalse(vs.getBaseWorksheet().getAssembly(TABLE) instanceof MirrorTableAssembly);
      assertNull(vs.getBaseWorksheet().getAssembly(TABLE + Viewsheet.OUTER_TABLE_SUFFIX));
      assertFalse(EmbeddedTableFlag.unwrap(runtime(vs), GRID), "second call is a no-op");
      assertFalse(EmbeddedTableFlag.unwrap(runtime(vs), "Missing"));
   }

   @Test
   void flagOnlyTouchesEmbeddedTableAssemblies() {
      Viewsheet vs = new Viewsheet();
      TableVSAssembly plain = new TableVSAssembly(vs, "Plain");
      EmbeddedTableVSAssembly embedded = new EmbeddedTableVSAssembly(vs, "Embedded");

      EmbeddedTableFlag.flag(plain);
      EmbeddedTableFlag.flag(embedded);
      EmbeddedTableFlag.flag(null);

      assertFalse(((TableVSAssemblyInfo) plain.getVSAssemblyInfo()).isEmbeddedTable());
      assertTrue(((TableVSAssemblyInfo) embedded.getVSAssemblyInfo()).isEmbeddedTable());
   }

   @Test
   void flaggedTableReboundToANonEmbeddedSourceStaysHarmless() throws Exception {
      Viewsheet vs = viewsheetWithBoundGrid(true);
      ((EmbeddedTableVSAssembly) vs.getAssembly(GRID))
         .setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "Missing"));

      assertFalse(EmbeddedTableFlag.unwrap(runtime(vs), GRID));
   }

   @Test
   void formTableRefusalPointsAPlainEmbeddedTableAtTheCellTool() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new EmbeddedTableVSAssembly(vs, GRID));
      vs.addAssembly(new TableVSAssembly(vs, "Plain"));
      RuntimeViewsheet rvs = runtime(vs);

      // every form_table_* operation (read, insert, delete, set cell, apply) goes through
      // requireForm, so one refusal covers them all
      IllegalArgumentException embedded = assertThrows(IllegalArgumentException.class,
         () -> FormTableRowService.requireForm(rvs, GRID));
      assertTrue(embedded.getMessage().contains("is not a Form table"), embedded.getMessage());
      assertTrue(embedded.getMessage().contains("embedded_table_set_cells"),
                 embedded.getMessage());
      assertTrue(embedded.getMessage().contains("cannot insert or delete rows"),
                 embedded.getMessage());

      IllegalArgumentException plain = assertThrows(IllegalArgumentException.class,
         () -> FormTableRowService.requireForm(rvs, "Plain"));
      assertTrue(plain.getMessage().contains("is not a Form table"), plain.getMessage());
      assertFalse(plain.getMessage().contains("embedded_table_set_cells"), plain.getMessage());
   }

   /**
    * A real viewsheet whose base worksheet is prepared (Viewsheet.setBaseWorksheet, the same
    * mirror preparation the runtime uses) with an embedded table assembly bound to the worksheet
    * table, flagged before the preparation or not.
    */
   private static Viewsheet viewsheetWithBoundGrid(boolean flagged) throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, TABLE);
      table.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.STRING, XSchema.INTEGER },
         new Object[][] { { "Region", "Target" }, { "East", 100 }, { "West", 200 } }));
      ws.addAssembly(table);

      Viewsheet vs = new Viewsheet();
      EmbeddedTableVSAssembly grid = new EmbeddedTableVSAssembly(vs, GRID);
      grid.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, TABLE));
      vs.addAssembly(grid);

      if(flagged) {
         EmbeddedTableFlag.flag(grid);
      }

      Method setBase = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBase.setAccessible(true);
      setBase.invoke(vs, ws);
      return vs;
   }

   private static RuntimeViewsheet runtime(Viewsheet vs) {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.empty());
      return rvs;
   }
}
