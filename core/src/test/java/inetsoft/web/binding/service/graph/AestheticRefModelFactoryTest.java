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
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.graph.VSAestheticRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.binding.model.graph.AestheticInfo;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Finding 13 (L3 plan doc): {@code set_aesthetic_field} on a multi-style chart leaves an
 * {@code AestheticRef} on the chart-info-level color/shape/size/text slot whose
 * {@code getVisualFrameWrapper()} is {@code null} -- {@code ChartInfoModelBuilder}'s write at
 * {@code updateChartInfo():359} is unconditional, but the paired repair,
 * {@code GraphUtil.fixVisualFrames()}, only revisits per-aggregate refs when
 * {@code cinfo.isMultiAesthetic()} is true, so this frame-less ref is never repaired. Every
 * subsequent DTO build for that chart then re-enters this factory and must tolerate the null
 * wrapper instead of dereferencing it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AestheticRefModelFactoryTest {
   private final AestheticRefModelFactory factory = new AestheticRefModelFactory(
      new VisualFrameModelFactoryService(List.of()),
      new ChartRefModelFactoryService(List.of(
         new ChartDimensionInfoFactory.VSChartDimensionInfoFactory(
            new DataRefModelFactoryService(List.of())))));

   @Test
   void createAestheticInfoToleratesANullVisualFrameWrapper() {
      VSChartDimensionRef dataRef = new VSChartDimensionRef();
      dataRef.setDataRef(new AttributeRef("REGION"));

      VSAestheticRef ref = new VSAestheticRef();
      ref.setDataRef(dataRef);
      assertNull(ref.getVisualFrameWrapper(), "precondition: this is the frame-less ref shape "
         + "left behind by ChartAestheticMutator.setField on a multi-style chart");

      VSChartInfo cinfo = new VSChartInfo();

      AestheticInfo model = assertDoesNotThrow(
         () -> factory.createAestheticInfo(ref, cinfo, null),
         "a null visualFrameWrapper must not reach "
            + "VisualFrameModelFactoryService.getFactory(), which dereferences "
            + "wrapper.getClass() unconditionally");

      assertNotNull(model);
      assertNull(model.getFrame());
      assertNotNull(model.getDataInfo(), "the ref's dataRef must still be modeled even though "
         + "the frame is absent");
   }
}
