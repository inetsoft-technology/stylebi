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
package inetsoft.web.wiz.binding;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.graph.AxisDescriptor;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.binding.model.graph.ChartRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.graph.ChartAestheticService;
import inetsoft.web.binding.service.graph.ChartAggregateInfoFactory;
import inetsoft.web.binding.service.graph.ChartDimensionInfoFactory;
import inetsoft.web.binding.service.graph.ChartRefModelFactoryService;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import inetsoft.web.wiz.binding.model.FieldRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #81636: a shelf write builds a fresh ref for every field from a wiz-built model, so a
 * dimension that stayed on the shelf rendered with a default axis descriptor. These tests run the
 * real model factories around {@link ChartAxisCarry}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartAxisCarryTest {
   private final DataRefModelFactoryService dataRefs = new DataRefModelFactoryService(List.of());
   private final ChartRefModelFactoryService refService = refService();

   private ChartRefModelFactoryService refService() {
      ChartAggregateInfoFactory.VSChartAggregateInfoFactory aggregates =
         new ChartAggregateInfoFactory.VSChartAggregateInfoFactory(
            new VisualFrameModelFactoryService(List.of()), dataRefs);
      aggregates.setChartAestheticService(mock(ChartAestheticService.class));
      return new ChartRefModelFactoryService(List.of(
         aggregates, new ChartDimensionInfoFactory.VSChartDimensionInfoFactory(dataRefs)));
   }

   private static final Color DEFAULT_LINE = new AxisDescriptor().getLineColor();

   private static VSChartDimensionRef dimension(String column) {
      AttributeRef attribute = new AttributeRef(null, column);
      attribute.setDataType(XSchema.STRING);
      VSChartDimensionRef ref = new VSChartDimensionRef();
      ref.setDataRef(new ColumnRef(attribute));
      ref.setDateLevelValue(String.valueOf(XConstants.NONE_DATE_GROUP));
      return ref;
   }

   private static FieldRef dim(String column) {
      return new FieldRef(column, "dimension", null, "none", null);
   }

   private static VSChartDimensionRef styled(String column) {
      VSChartDimensionRef ref = dimension(column);
      ref.getAxisDescriptor().setLineColor(Color.RED);
      ref.getAxisDescriptor().setLabelOnSecondaryAxis(true);
      return ref;
   }

   /** What setShelf does: snapshot, rebuild the shelf from wiz-built models, reapply. */
   private boolean rewrite(VSChartInfo info, FieldRef... fields) {
      List<ChartAxisCarry.Saved> saved = ChartAxisCarry.snapshot(info, "x");
      List<ChartRef> fresh = new ArrayList<>();

      for(FieldRef field : fields) {
         ChartRefModel model = FieldRefFactory.toChartRef(field);
         fresh.add(refService.pasteChartRef(info, model));
      }

      info.removeXFields();
      fresh.forEach(info::addXField);
      return ChartAxisCarry.reapply(info, "x", saved);
   }

   private static AxisDescriptor axis(VSChartInfo info, int i) {
      return ((VSChartDimensionRef) info.getXField(i)).getAxisDescriptor();
   }

   @Test
   void aResendOfTheSameShelfKeepsEachDimensionsAxis() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("Region"));
      info.addXField(styled("State"));

      assertTrue(rewrite(info, dim("Region"), dim("State")));

      assertEquals(Color.RED, axis(info, 1).getLineColor());
      assertTrue(axis(info, 1).isLabelOnSecondaryAxis());
      assertEquals(DEFAULT_LINE, axis(info, 0).getLineColor(), "an unstyled dimension stays default");
   }

   @Test
   void theAxisFollowsItsFieldWhenAnotherDimensionIsInsertedAhead() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.addXField(styled("State"));

      rewrite(info, dim("Region"), dim("State"));

      assertEquals(DEFAULT_LINE, axis(info, 0).getLineColor());
      assertEquals(Color.RED, axis(info, 1).getLineColor());
   }

   @Test
   void aRemovedDimensionsAxisIsNotCarriedOntoTheRemainingOne() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.addXField(styled("Region"));
      info.addXField(dimension("State"));

      rewrite(info, dim("State"));

      assertEquals(DEFAULT_LINE, axis(info, 0).getLineColor());
   }

   @Test
   void nothingIsReappliedWhenNoDimensionHadAStyledAxis() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.addXField(dimension("Region"));

      assertFalse(rewrite(info, dim("Region")));
   }

   @Test
   void theCarriedDescriptorIsACopy() {
      VSChartInfo info = new DefaultVSChartInfo();
      VSChartDimensionRef old = styled("State");
      info.addXField(old);

      rewrite(info, dim("State"));

      assertNotSame(old.getAxisDescriptor(), axis(info, 0));
   }
}
