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
package inetsoft.web.graph;

import inetsoft.report.composition.region.DimensionLabelArea;
import inetsoft.report.internal.Region;
import inetsoft.report.internal.binding.ExpertNamedGroupInfo;
import inetsoft.report.internal.binding.AssetNamedGroupInfo;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.graph.ChartDescriptor;
import inetsoft.uql.viewsheet.graph.ChartInfo;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.web.graph.model.ChartModel;
import inetsoft.web.graph.model.ChartRegion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@code createChartRegion}'s two {@code SNamedGroupInfo} casts (dimension-axis labels and
 * legend items) widen to {@code XNamedGroupInfo}, and the "is this label a group name" check
 * switches from {@code SNamedGroupInfo}'s own {@code getGroupValue(label) != null} to the
 * type-agnostic {@code getGroups()} membership test every {@code XNamedGroupInfo} implementation
 * supports. By the time this runs, the label is already the group name the query engine computed
 * (see {@code VSDimensionRefTest} for the {@code NamedRangeRef} expression that produces it) --
 * this test only exercises the membership check itself, not query execution.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class GraphBuilderTest {
   private static VSChartDimensionRef regionRef() {
      VSChartDimensionRef ref = new VSChartDimensionRef();
      ref.setDataRef(new ColumnRef(new AttributeRef("REGION")));
      ref.setDataType(XSchema.STRING);
      return ref;
   }

   /** Sets up a GraphBuilder + mocked DimensionLabelArea just far enough to reach the
    *  dimension-axis-label grouped-flag check, and returns the resulting region. */
   private static ChartRegion buildRegionFor(VSChartDimensionRef dimRef, String label)
      throws Exception
   {
      ChartInfo cinfo = mock(ChartInfo.class);
      when(cinfo.getFieldByName(anyString(), anyBoolean())).thenReturn(dimRef);

      ChartModel model = mock(ChartModel.class);
      when(model.getStringDictionary()).thenReturn(new ArrayList<>());

      DimensionLabelArea area = mock(DimensionLabelArea.class);
      when(area.getValue()).thenReturn(label);
      when(area.getDimensionName()).thenReturn("REGION");
      when(area.getParentValues()).thenReturn(List.of());
      when(area.getHyperlinks()).thenReturn(new inetsoft.report.Hyperlink.Ref[0]);

      GraphBuilder builder = new GraphBuilder(null, cinfo, null, new ChartDescriptor(), model);

      Method method = GraphBuilder.class.getDeclaredMethod("createChartRegion",
         inetsoft.report.composition.region.DefaultArea.class, Region[].class, String.class,
         int.class, short[][].class);
      method.setAccessible(true);
      return (ChartRegion) method.invoke(builder, area, new Region[0], null, -1, null);
   }

   @Test
   void marksAnExpertNamedGroupLabelAsGrouped() throws Exception {
      VSChartDimensionRef ref = regionRef();
      ExpertNamedGroupInfo info = new ExpertNamedGroupInfo();
      info.setGroupCondition("West", new inetsoft.uql.ConditionList());
      ref.setNamedGroupInfo(info);

      ChartRegion region = buildRegionFor(ref, "West");

      assertEquals(Boolean.TRUE, region.grouped());
   }

   @Test
   void doesNotMarkAnUnrelatedLabelAsGrouped() throws Exception {
      VSChartDimensionRef ref = regionRef();
      ExpertNamedGroupInfo info = new ExpertNamedGroupInfo();
      info.setGroupCondition("West", new inetsoft.uql.ConditionList());
      ref.setNamedGroupInfo(info);

      ChartRegion region = buildRegionFor(ref, "California");

      assertNull(region.grouped());
   }

   @Test
   void marksAnAssetNamedGroupLabelAsGrouped() throws Exception {
      VSChartDimensionRef ref = regionRef();
      AssetNamedGroupInfo info = mock(AssetNamedGroupInfo.class);
      when(info.getGroups()).thenReturn(new String[]{ "Tier1" });
      ref.setNamedGroupInfo(info);

      ChartRegion region = buildRegionFor(ref, "Tier1");

      assertEquals(Boolean.TRUE, region.grouped());
   }

   @Test
   void ungroupedDimensionIsNeverMarkedGrouped() throws Exception {
      VSChartDimensionRef ref = regionRef();

      ChartRegion region = buildRegionFor(ref, "California");

      assertNull(region.grouped());
   }
}
