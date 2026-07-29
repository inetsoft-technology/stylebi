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
package inetsoft.report.composition;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.embed.EmbedAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Coverage for two follow-up points raised in code review on the fix that replaced
 * {@code RuntimeViewsheet}'s single runtime-wide {@code EmbedAssemblyInfo} field with a map keyed
 * by assembly name (see {@code VSRefreshServiceEmbedAssemblyTest} /
 * {@code CoreLifecycleServiceEmbedSizeTest} for the core race-condition regression coverage):
 *
 * <ol>
 *   <li>The persisted JSON format changed from a single object to an array. A
 *   {@code RuntimeViewsheetState} can outlive a single process (cluster failover/rolling
 *   restarts), so a blob written by a not-yet-upgraded node may still be in the old format -
 *   {@link RuntimeViewsheet}'s loader must fall back to parsing that instead of silently
 *   dropping it.</li>
 *   <li>Tracking embed info per assembly name means an assembly that stops being embedded (e.g.
 *   a type switch replaces it with a differently-named one) leaves an orphaned entry behind.
 *   {@code putEmbedAssemblyInfo} prunes entries for assemblies no longer present on the
 *   viewsheet so a long-lived runtime (wiz keeps one per conversation) doesn't accumulate them
 *   without bound.</li>
 * </ol>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RuntimeViewsheetEmbedAssemblyInfoTest {
   @SuppressWarnings("unchecked")
   private static List<EmbedAssemblyInfo> loadEmbedAssemblyInfoList(String json) throws Exception {
      Method method = RuntimeViewsheet.class
         .getDeclaredMethod("loadEmbedAssemblyInfoList", String.class, ObjectMapper.class);
      method.setAccessible(true);
      return (List<EmbedAssemblyInfo>) method.invoke(null, json, new ObjectMapper());
   }

   /**
    * The public {@code setViewsheet(Viewsheet)} setter no-ops unless the runtime already has a
    * live {@code ViewsheetSandbox} (irrelevant plumbing for these tests) - set the backing field
    * directly instead, exactly as {@code CoreLifecycleServiceEmbedSizeTest} reaches into private
    * members via reflection for focused unit coverage.
    */
   private static void injectViewsheet(RuntimeViewsheet rvs, Viewsheet vs) throws Exception {
      java.lang.reflect.Field field = RuntimeViewsheet.class.getDeclaredField("vs");
      field.setAccessible(true);
      field.set(rvs, vs);
   }

   @Test
   void parsesTheCurrentArrayFormat() throws Exception {
      String json = "[{\"assemblyName\":\"vs_chart_A\",\"assemblySize\":{\"width\":540,\"height\":300}}," +
         "{\"assemblyName\":\"vs_table_B\",\"assemblySize\":{\"width\":540,\"height\":260}}]";

      List<EmbedAssemblyInfo> infos = loadEmbedAssemblyInfoList(json);

      assertEquals(2, infos.size());
      assertEquals("vs_chart_A", infos.get(0).getAssemblyName());
      assertEquals(new Dimension(540, 300), infos.get(0).getAssemblySize());
      assertEquals("vs_table_B", infos.get(1).getAssemblyName());
      assertEquals(new Dimension(540, 260), infos.get(1).getAssemblySize());
   }

   @Test
   void fallsBackToTheLegacySingleObjectFormat() throws Exception {
      // The format persisted before this fix - a bare object, not an array. A cluster node still
      // running the old code could have written this to the shared runtime sheet cache.
      String legacyJson = "{\"assemblyName\":\"vs_chart_A\",\"assemblySize\":{\"width\":540,\"height\":300}}";

      List<EmbedAssemblyInfo> infos = loadEmbedAssemblyInfoList(legacyJson);

      assertEquals(1, infos.size(),
         "a pre-upgrade single-object blob must still be recovered, not silently dropped");
      assertEquals("vs_chart_A", infos.get(0).getAssemblyName());
      assertEquals(new Dimension(540, 300), infos.get(0).getAssemblySize());
   }

   @Test
   void returnsEmptyListForUnparseableJson() throws Exception {
      List<EmbedAssemblyInfo> infos = loadEmbedAssemblyInfoList("not json at all");
      assertTrue(infos.isEmpty());
   }

   @Test
   void putEmbedAssemblyInfoPrunesEntriesForAssembliesNoLongerOnTheViewsheet() throws Exception {
      RuntimeViewsheet rvs = new RuntimeViewsheet();
      Viewsheet vs = mock(Viewsheet.class);
      injectViewsheet(rvs, vs);

      EmbedAssemblyInfo tableInfo = new EmbedAssemblyInfo();
      tableInfo.setAssemblyName("vs_table_1");
      tableInfo.setAssemblySize(new Dimension(908, 600));

      // The table assembly exists on the viewsheet at the time it's first tracked.
      when(vs.getAssembly("vs_table_1")).thenReturn(mock(VSAssembly.class));
      rvs.putEmbedAssemblyInfo("vs_table_1", tableInfo);
      assertNotNull(rvs.getEmbedAssemblyInfo("vs_table_1"));

      // User switches type: changeType() removes the table assembly and adds a differently-named
      // crosstab assembly. The next embed refresh (for the new assembly) should prune the now-gone
      // table entry rather than leaving it behind forever.
      when(vs.getAssembly("vs_table_1")).thenReturn(null);
      EmbedAssemblyInfo crosstabInfo = new EmbedAssemblyInfo();
      crosstabInfo.setAssemblyName("vs_crosstab_2");
      crosstabInfo.setAssemblySize(new Dimension(908, 600));
      when(vs.getAssembly("vs_crosstab_2")).thenReturn(mock(VSAssembly.class));
      rvs.putEmbedAssemblyInfo("vs_crosstab_2", crosstabInfo);

      assertNull(rvs.getEmbedAssemblyInfo("vs_table_1"),
         "the stale entry for the removed assembly must be pruned, or the map grows without " +
         "bound across repeated type switches on a long-lived runtime");
      assertNotNull(rvs.getEmbedAssemblyInfo("vs_crosstab_2"));
   }

   @Test
   void putEmbedAssemblyInfoDoesNotPruneStillLiveSiblingAssemblies() throws Exception {
      RuntimeViewsheet rvs = new RuntimeViewsheet();
      Viewsheet vs = mock(Viewsheet.class);
      injectViewsheet(rvs, vs);

      // Two chart/table cards from the same wiz conversation, both still present on the SAME
      // shared viewsheet at once - pruning must never remove a sibling that's still embedded.
      when(vs.getAssembly("vs_chart_A")).thenReturn(mock(VSAssembly.class));
      when(vs.getAssembly("vs_table_B")).thenReturn(mock(VSAssembly.class));

      EmbedAssemblyInfo infoA = new EmbedAssemblyInfo();
      infoA.setAssemblyName("vs_chart_A");
      infoA.setAssemblySize(new Dimension(540, 300));
      rvs.putEmbedAssemblyInfo("vs_chart_A", infoA);

      EmbedAssemblyInfo infoB = new EmbedAssemblyInfo();
      infoB.setAssemblyName("vs_table_B");
      infoB.setAssemblySize(new Dimension(540, 260));
      rvs.putEmbedAssemblyInfo("vs_table_B", infoB);

      assertNotNull(rvs.getEmbedAssemblyInfo("vs_chart_A"));
      assertNotNull(rvs.getEmbedAssemblyInfo("vs_table_B"));
   }
}
