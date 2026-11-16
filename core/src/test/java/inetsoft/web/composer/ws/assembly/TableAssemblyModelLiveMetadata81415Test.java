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
package inetsoft.web.composer.ws.assembly;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.asset.*;
import inetsoft.web.wiz.pairing.TestPrincipals;
import inetsoft.web.wiz.pairing.TestWorksheets;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #81415: building a table's model executes the table for its row count and column infos
 * ({@code setTableLensMetaData}, {@code setColumnInfos}). A caller that cannot afford that - the
 * worksheet refresh broadcast, which answers an HTTP edit - builds the model with
 * {@code liveMetadata = false}, which must reach neither call: both end in
 * {@code AssetQuerySandbox.getTableLens}.
 */
@Tag("core")
@WizAgentTestSupport
class TableAssemblyModelLiveMetadata81415Test {
   private final Worksheet ws = new Worksheet();
   private final RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
   private final AssetQuerySandbox box = mock(AssetQuerySandbox.class);
   private final Principal principal = TestPrincipals.user("alice", "host-org");

   TableAssemblyModelLiveMetadata81415Test() {
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
   }

   private AbstractTableAssembly table(String name) {
      BoundTableAssembly t = TestWorksheets.nonEmbeddedTableWithColumns(ws, name, "cust", "amount");
      ws.addAssembly(t);
      return t;
   }

   private AbstractTableAssembly mirror(String name, AbstractTableAssembly base) {
      MirrorTableAssembly m = new MirrorTableAssembly(ws, name, base);
      ws.addAssembly(m);
      return m;
   }

   @Test
   void withoutLiveMetadataTheTableIsNeverExecuted() throws Exception {
      AbstractTableAssembly mirror = mirror("M", table("T"));

      TableAssemblyModel model = TableAssemblyModelFactory.createModelFrom(
         mirror, rws, principal, false);

      verify(box, never()).getTableLens(anyString(), anyInt());
      verify(box, never()).getColumnInfos(anyString(), anyInt());
      assertEquals("M", model.getName());
      assertFalse(model.getRowsCompleted(), "the row count is unknown, not zero");
      assertTrue(model.getColInfos().isEmpty());
      assertNotNull(model.getInfo(), "the definition-only parts are still filled in");
   }

   @Test
   void withoutLiveMetadataAPlainTableIsNeverExecutedEither() throws Exception {
      TableAssemblyModelFactory.createModelFrom(table("T"), rws, principal, false);

      verify(box, never()).getTableLens(anyString(), anyInt());
      verify(box, never()).getColumnInfos(anyString(), anyInt());
   }

   @Test
   void theWorksheetAssemblyEntryPassesTheFlagThrough() throws Exception {
      WSAssemblyModelFactory.createModelFrom(mirror("M", table("T")), rws, principal, false);

      verify(box, never()).getTableLens(anyString(), anyInt());
      verify(box, never()).getColumnInfos(anyString(), anyInt());
   }

   @Test
   void theDefaultOverloadStillExecutesTheTableForNativeCallers() throws Exception {
      AbstractTableAssembly t = table("T");

      TableAssemblyModelFactory.createModelFrom(t, rws, principal);
      verify(box, atLeastOnce()).getTableLens(anyString(), anyInt());
      verify(box, atLeastOnce()).getColumnInfos(anyString(), anyInt());

      clearInvocations(box);
      WSAssemblyModelFactory.createModelFrom(t, rws, principal);
      verify(box, atLeastOnce()).getTableLens(anyString(), anyInt());
   }
}
