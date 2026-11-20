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
package inetsoft.web.binding.service.graph;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.*;
import inetsoft.web.binding.model.graph.AestheticInfo;
import inetsoft.web.binding.model.graph.aesthetic.*;
import inetsoft.web.binding.service.graph.aesthetic.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The colour pane is sent the ramp that is drawn when the seeded Teal draws diverging.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AestheticRefModelDivergingTest {
   @Test
   void aDrawnDivergingTealIsSentAsVariance() {
      VSChartInfo cinfo = chartInfo("Profit");
      AestheticInfo info = factory().createAestheticInfo(ref("Profit", new TealColorFrameWrapper()),
                                                         cinfo, null);

      assertInstanceOf(VarianceColorModel.class, info.getFrame());
      assertEquals("Profit", info.getFrame().getField());
      assertFalse(((VarianceColorModel) info.getFrame()).isChanged());
   }

   @Test
   void aFlaggedTealIsSentAsTeal() {
      TealColorFrameWrapper teal = new TealColorFrameWrapper();
      teal.setAuthorRamp(true);

      AestheticInfo info = factory().createAestheticInfo(ref("Profit", teal),
                                                         chartInfo("Profit"), null);

      assertInstanceOf(TealColorModel.class, info.getFrame());
   }

   @Test
   void aTealNotDrawnDivergingIsSentAsTeal() {
      AestheticInfo info = factory().createAestheticInfo(ref("Profit", new TealColorFrameWrapper()),
                                                         chartInfo("Sales"), null);

      assertInstanceOf(TealColorModel.class, info.getFrame());
   }

   @Test
   void theRoundTripKeepsTheSeededTeal() {
      TealColorFrameWrapper teal = new TealColorFrameWrapper();
      VSChartInfo cinfo = chartInfo("Profit");
      AestheticRefModelFactory factory = factory();
      AestheticInfo info = factory.createAestheticInfo(ref("Profit", teal), cinfo, null);
      assertInstanceOf(VarianceColorModel.class, info.getFrame(), "premise");

      VSAestheticRef target = new VSAestheticRef();
      target.setVisualFrameWrapper(teal);
      factory.pasteAestheticRef(cinfo, target, info);

      assertSame(teal, target.getVisualFrameWrapper());
      assertFalse(teal.isAuthorRamp());
   }

   @Test
   void theFieldSetIsNeverNull() {
      VSChartInfo cinfo = new VSChartInfo();
      assertTrue(cinfo.getDivergingColorFields().isEmpty());
      cinfo.setDivergingColorFields(null);
      assertTrue(cinfo.getDivergingColorFields().isEmpty());
   }

   private VSChartInfo chartInfo(String field) {
      VSChartInfo cinfo = new VSChartInfo();
      cinfo.setDivergingColorFields(Set.of(field));
      return cinfo;
   }

   private AestheticRef ref(String name, VisualFrameWrapper wrapper) {
      AestheticRef ref = mock(AestheticRef.class);
      when(ref.getFullName()).thenReturn(name);
      when(ref.getVisualFrameWrapper()).thenReturn(wrapper);
      return ref;
   }

   private AestheticRefModelFactory factory() {
      List<VisualFrameModelFactory<?, ?>> factories = new ArrayList<>();
      factories.add(new ColorFrameModelFactory.TealColorFactory());
      factories.add(new ColorFrameModelFactory.VarianceColorFactory());
      return new AestheticRefModelFactory(new VisualFrameModelFactoryService(factories),
                                          mock(ChartRefModelFactoryService.class));
   }
}
