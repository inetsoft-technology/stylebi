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

package inetsoft.web.binding.handler;

import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.AssetTreeModel;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Bug #76488 / Bug #76485: the Composer binding tree can be stranded showing its loading
 * spinner forever. The only thing that clears the client's {@code _loadingTree} flag is a
 * {@code RefreshBindingTreeCommand}, and the whole server chain that produces it
 * (VSBindingTreeController -> VSBindingTreeControllerService -> VSBindingTreeService ->
 * VSTreeHandler -> CubeTreeModelBuilder) has no exception handling, so any uncaught
 * exception in it silently drops the response.
 *
 * <p>This test covers one such throw site found by tracing that chain:
 * {@code CubeTreeModelBuilder.getCubeTreeModel()} calls {@code processor.aggInfoInvalid(info)}
 * whenever {@code isValidAggregateInfo()} finds the crosstab's stored AggregateInfo out of
 * step with the source table's columns, and {@code getTableTreeModel()}'s Processor
 * implementation re-resolved the assembly by name a second time without a null check --
 * even though the outer method's own {@code cass} lookup is guarded. A concurrently
 * disposed/reset viewsheet between those two lookups therefore produced an NPE rather than
 * a response.
 *
 * <p>Claim boundary: this reproduces the unguarded dereference structurally. It is not a
 * reproduction of either reported bug's end-user symptom -- no live reproduction of
 * #76488/#76485 was ever achieved (see docs/teams/2026-09-13-bug-76488-76485-round2/).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class VSTreeHandlerAggInfoInvalidTest {
   private static final String NAME = "Crosstab2";

   @Mock VSChartHandler chartHandler;
   @Mock SecurityEngine securityEngine;
   @Mock AssetRepository engine;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock ViewsheetSandbox sandbox;
   @Mock VSAssembly crosstab;
   @Mock CrosstabVSAssemblyInfo cinfo;
   @Mock VSCrosstabInfo vsCrosstabInfo;
   @Mock AggregateInfo ainfo;
   @Mock SourceInfo sourceInfo;
   @Mock TableAssembly tableAssembly;
   @Mock ColumnSelection columnSelection;
   @Mock Principal principal;

   private VSTreeHandler handler;

   @BeforeEach
   public void setUp() {
      handler = new VSTreeHandler(chartHandler, securityEngine);

      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.isRuntime()).thenReturn(true);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(sandbox));

      when(cinfo.getAbsoluteName()).thenReturn(NAME);
      when(cinfo.getAbsoluteName2()).thenReturn(null);
      when(cinfo.getSourceInfo()).thenReturn(sourceInfo);
      when(cinfo.getVSCrosstabInfo()).thenReturn(vsCrosstabInfo);

      when(sourceInfo.getSource()).thenReturn("customers");
      when(sourceInfo.getPrefix()).thenReturn(null);

      // a non-empty aggregate info whose bound ref count does not match the source table's
      // column count is what drives isValidAggregateInfo() to false, which is the only way
      // aggInfoInvalid() is ever called
      when(vsCrosstabInfo.getAggregateInfo()).thenReturn(ainfo);
      when(ainfo.isEmpty()).thenReturn(false);
      when(ainfo.getGroupCount()).thenReturn(0);
      when(ainfo.getAggregateCount()).thenReturn(1);

      when(tableAssembly.getName()).thenReturn("customers");
      when(tableAssembly.getColumnSelection(true)).thenReturn(columnSelection);
      when(columnSelection.clone()).thenReturn(columnSelection);
      when(columnSelection.getAttributeCount()).thenReturn(5);

      when(crosstab.getViewsheet()).thenReturn(viewsheet);
      when(crosstab.getName()).thenReturn(NAME);
      when(crosstab.isEmbedded()).thenReturn(false);

      when(viewsheet.getBaseEntry()).thenReturn(null);
      when(viewsheet.getBaseWorksheet()).thenReturn(null);
   }

   /**
    * The assembly resolves on the first lookup (so the outer {@code cass == null} guard does
    * not fire and the request proceeds) but is gone by the time the Processor callback
    * re-resolves it -- the interleaving a concurrent refresh/reset produces. Before the fix
    * this threw an NPE out of the whole unguarded chain, dropping the response and stranding
    * the client's loading state; now it completes and a tree is still returned.
    */
   @Test
   public void getTableTreeModel_doesNotThrow_whenAssemblyDisappearsBeforeAggInfoInvalid() {
      // first lookup succeeds, second (inside aggInfoInvalid) finds nothing
      when(viewsheet.getAssembly(NAME)).thenReturn(crosstab).thenReturn(null);

      try(MockedStatic<VSEventUtil> vsEventUtil = mockStatic(VSEventUtil.class);
          MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class);
          MockedStatic<VSUtil> vsUtil = mockStatic(VSUtil.class))
      {
         vsEventUtil.when(() -> VSEventUtil.getTableAssembly(any(), any(), any(), any()))
            .thenReturn(tableAssembly);
         assetUtil.when(() -> AssetUtil.isCubeTable(any())).thenReturn(false);

         AssetTreeModel[] result = new AssetTreeModel[1];

         assertDoesNotThrow(
            () -> result[0] = handler.getTableTreeModel(engine, rvs, cinfo, principal),
            "a concurrently removed assembly must not throw out of the binding tree chain");
      }
   }

   /**
    * Control case: when the assembly stays resolvable for both lookups, the callback does its
    * normal work (pushing the rebuilt info back onto the assembly) rather than bailing out.
    */
   @Test
   public void getTableTreeModel_doesNotThrow_whenAssemblyRemainsResolvable() {
      when(viewsheet.getAssembly(NAME)).thenReturn(crosstab);

      try(MockedStatic<VSEventUtil> vsEventUtil = mockStatic(VSEventUtil.class);
          MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class);
          MockedStatic<VSUtil> vsUtil = mockStatic(VSUtil.class))
      {
         vsEventUtil.when(() -> VSEventUtil.getTableAssembly(any(), any(), any(), any()))
            .thenReturn(tableAssembly);
         assetUtil.when(() -> AssetUtil.isCubeTable(any())).thenReturn(false);

         assertDoesNotThrow(() -> handler.getTableTreeModel(engine, rvs, cinfo, principal));
      }
   }
}
