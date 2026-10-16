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

import inetsoft.mv.MVExecutionException;
import inetsoft.report.composition.AssetTreeModel;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.util.script.ScriptException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78640: one viewsheet table whose script fails used to throw out of
 * {@code appendVSAssemblyTree}, so no other assembly could list the Components folder.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSTreeHandlerScriptFailureTest {
   private final Principal user = () -> "admin";
   private final Viewsheet vs = mock(Viewsheet.class);
   private final ViewsheetSandbox box = mock(ViewsheetSandbox.class);
   private final RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
   private VSTreeHandler handler;
   private TableVSAssembly scoped;
   private final Map<String, String> skipped = new LinkedHashMap<>();

   @BeforeEach
   void setUp() throws Exception {
      handler = new VSTreeHandler(mock(VSChartHandler.class), mock(SecurityEngine.class));
      ViewsheetInfo info = mock(ViewsheetInfo.class);
      when(info.isMetadata()).thenReturn(false);
      when(vs.getViewsheetInfo()).thenReturn(info);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      scoped = table("Scoped", "WS1");
   }

   private TableVSAssembly table(String name, String source) {
      TableVSAssembly table = mock(TableVSAssembly.class);
      when(table.getName()).thenReturn(name);
      when(table.getAbsoluteName()).thenReturn(name);
      when(table.isVisible()).thenReturn(true);
      when(table.getSourceInfo()).thenReturn(
         source == null ? null : new SourceInfo(SourceInfo.ASSET, null, source));
      return table;
   }

   private void assemblies(TableVSAssembly... others) {
      Assembly[] all = new Assembly[others.length + 1];
      all[0] = scoped;
      System.arraycopy(others, 0, all, 1, others.length);
      when(vs.getAssemblies(true)).thenReturn(all);
   }

   private AssetTreeModel append() throws Exception {
      AssetTreeModel.Node root = new AssetTreeModel.Node();
      AssetTreeModel model = new AssetTreeModel(root);
      handler.appendVSAssemblyTree(rvs, model, user, scoped, skipped::put);
      return model;
   }

   private static int componentCount(AssetTreeModel model) {
      AssetTreeModel.Node root = (AssetTreeModel.Node) model.getRoot();
      return root.getNodeCount() == 0 ? 0 : root.getNodes()[0].getNodeCount();
   }

   private void healthy(String name) throws Exception {
      when(box.getTableData(Assembly.TABLE_VS_BOUND + name))
         .thenReturn(new DefaultTableLens(new Object[][] {{"A", "B"}, {"x", 1}}));
   }

   @Test
   void aFailingSiblingIsSkippedAndTheHealthyOneStillListed() throws Exception {
      TableVSAssembly bad = table("Bad", "WS1");
      TableVSAssembly good = table("Good", "WS1");
      assemblies(bad, good);
      when(box.getTableData(Assembly.TABLE_VS_BOUND + "Bad"))
         .thenThrow(new ScriptException("ReferenceError: undefinedFn is not defined\n  at line 1"));
      healthy("Good");

      AssetTreeModel model = append();

      assertEquals(1, componentCount(model));
      assertEquals(1, skipped.size());
      String reason = skipped.get("Bad");
      assertTrue(reason.contains("script failure: ReferenceError: undefinedFn is not defined"),
                 reason);
      assertFalse(reason.contains("at line 1"), "only the first message line is reported");
   }

   @Test
   void aScriptFailureWrappedByMvIsSkippedToo() throws Exception {
      TableVSAssembly bad = table("Bad", "WS1");
      assemblies(bad);
      when(box.getTableData(Assembly.TABLE_VS_BOUND + "Bad"))
         .thenThrow(new MVExecutionException(new ScriptException("boom")));

      assertEquals(0, componentCount(append()));
      assertTrue(skipped.get("Bad").contains("script failure: boom"));
   }

   @Test
   void aStoppedScriptStillPropagates() throws Exception {
      TableVSAssembly bad = table("Bad", "WS1");
      assemblies(bad);
      ScriptException stop = new ScriptException("stopped");
      stop.setStopped(true);
      when(box.getTableData(Assembly.TABLE_VS_BOUND + "Bad")).thenThrow(stop);

      assertSame(stop, assertThrows(ScriptException.class, this::append));
      assertTrue(skipped.isEmpty());
   }

   @Test
   void aNonScriptFailureStillPropagates() throws Exception {
      TableVSAssembly bad = table("Bad", "WS1");
      assemblies(bad);
      when(box.getTableData(Assembly.TABLE_VS_BOUND + "Bad"))
         .thenThrow(new IllegalStateException("not a script problem"));

      assertThrows(IllegalStateException.class, this::append);
      assertTrue(skipped.isEmpty());
   }

   /** The sandbox caches a failed read as null, so the steady state is a null lens, not a throw. */
   @Test
   void aBoundAssemblyWithNoLensIsReportedAndAnUnboundOneIsNot() throws Exception {
      TableVSAssembly cached = table("Cached", "WS1");
      TableVSAssembly unbound = table("Unbound", null);
      assemblies(cached, unbound);
      when(box.getTableData(Assembly.TABLE_VS_BOUND + "Cached")).thenReturn(null);
      when(box.getTableData(Assembly.TABLE_VS_BOUND + "Unbound")).thenReturn(null);

      append();

      assertEquals(1, skipped.size());
      assertTrue(skipped.get("Cached").contains("may have failed to render"));
   }

   @Test
   void theOverloadWithoutACollectorStillSkipsInsteadOfThrowing() throws Exception {
      TableVSAssembly bad = table("Bad", "WS1");
      assemblies(bad);
      when(box.getTableData(Assembly.TABLE_VS_BOUND + "Bad")).thenThrow(new ScriptException("x"));

      AssetTreeModel model = new AssetTreeModel(new AssetTreeModel.Node());
      handler.appendVSAssemblyTree(rvs, model, user, scoped);

      assertEquals(0, componentCount(model));
   }
}
