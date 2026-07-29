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
package inetsoft.web.vswizard.handler;

import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("core")
class SyncInfoHandlerSyncConfigsTest {
   private SyncChartHandler chartHandler;
   private SyncTableHandler tableHandler;
   private SyncCrosstabHandler crosstabHandler;
   private SyncInfoHandler handler;

   @BeforeEach
   void setUp() {
      chartHandler = mock(SyncChartHandler.class);
      tableHandler = mock(SyncTableHandler.class);
      crosstabHandler = mock(SyncCrosstabHandler.class);
      handler = new SyncInfoHandler(chartHandler, tableHandler, crosstabHandler);
   }

   @Test
   void syncConfigsDispatchesChartAcrossDifferentTableNames() {
      ChartVSAssembly src = mock(ChartVSAssembly.class);
      ChartVSAssembly tgt = mock(ChartVSAssembly.class);
      when(src.getTableName()).thenReturn("T");
      when(tgt.getTableName()).thenReturn("T_FILTERED");

      handler.syncConfigs(null, src, tgt);

      // Table-name gate is NOT applied on this path — chart sync runs even across a rename.
      verify(chartHandler).syncChart(null, src, tgt, true, true);
   }

   // NOTE: syncInfo's own table-name gate (shouldSyncInfo) is structurally preserved — syncInfo still
   // runs `if(!shouldSyncInfo(...)) return;` BEFORE delegating to syncConfigs. It is not unit-asserted
   // here because syncInfo also runs syncDrillFilter unconditionally, and a bare ChartVSAssembly mock
   // (which IS-A DrillFilterVSAssembly) NPEs there; the existing wizard Sync* tests cover non-regression.

   @Test
   void syncConfigsSkipsCrossType() {
      ChartVSAssembly src = mock(ChartVSAssembly.class);
      TableVSAssembly tgt = mock(TableVSAssembly.class);

      handler.syncConfigs(null, src, tgt);

      verify(chartHandler, never()).syncChart(any(), any(), any(), anyBoolean(), anyBoolean());
      verify(tableHandler, never()).syncTable(any(), any());
   }

   @Test
   void syncConfigsDispatchesTable() {
      TableVSAssembly src = mock(TableVSAssembly.class);
      TableVSAssembly tgt = mock(TableVSAssembly.class);
      when(src.getTableName()).thenReturn("T");
      when(tgt.getTableName()).thenReturn("T_FILTERED");

      handler.syncConfigs(null, src, tgt);

      verify(tableHandler).syncTable(src, tgt);
   }
}
