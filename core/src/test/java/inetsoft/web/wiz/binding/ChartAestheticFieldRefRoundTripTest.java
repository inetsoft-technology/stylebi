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

import inetsoft.web.binding.model.graph.OriginalDescriptor;
import inetsoft.uql.asset.SNamedGroupInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.graph.AestheticRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.binding.drm.AttributeRefModel;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.AestheticInfo;
import inetsoft.web.binding.model.graph.calc.PercentCalcInfo;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.graph.AestheticRefModelFactory;
import inetsoft.web.binding.service.graph.ChartAestheticService;
import inetsoft.web.binding.service.graph.ChartAggregateInfoFactory;
import inetsoft.web.binding.service.graph.ChartDimensionInfoFactory;
import inetsoft.web.binding.service.graph.ChartRefModelFactoryService;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78232 (1): {@code get_chart_aesthetics} reports each channel's structured binding
 * ({@code fieldRef}), not just its display name.
 *
 * <p>Every case writes through {@code ChartAestheticMutator.setField}, pastes the result onto a
 * real {@code AestheticRef} and rebuilds the model from it with
 * {@code AestheticRefModelFactory.createAestheticInfo} -- the path the runtime read takes -- so
 * the assertions describe what survives the round trip, not what the in-memory write model holds.
 */
@WizAgentTestSupport
class ChartAestheticFieldRefRoundTripTest {
   /** Writes {@code field} to {@code channel}, round-trips it, and returns the channel's view. */
   @SuppressWarnings("unchecked")
   private static Map<String, Object> roundTrip(String channel, FieldRef field) {
      try(AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
         context.register(Beans.class, ChartAestheticService.class,
                          AestheticRefModelFactory.class, ChartRefModelFactoryService.class,
                          ChartAggregateInfoFactory.VSChartAggregateInfoFactory.class,
                          ChartDimensionInfoFactory.VSChartDimensionInfoFactory.class);
         context.refresh();
         AestheticRefModelFactory factory = context.getBean(AestheticRefModelFactory.class);

         ChartBindingModel written = new ChartBindingModel();
         ChartAestheticMutator.setField(written, channel, field);
         AestheticInfo writtenInfo = read(written, channel);

         VSChartInfo cinfo = new DefaultVSChartInfo();
         cinfo.setChartType(GraphTypes.CHART_POINT);
         AestheticRef ref = factory.pasteAestheticRef(cinfo, null, writtenInfo);

         // The live read always has a source-resolved column behind the ref (the change-ref
         // service attaches one from the table); the agent write carries only the column name.
         if(ref.getDataRef() instanceof VSChartDimensionRef dim) {
            AttributeRef column = new AttributeRef(field.column());
            dim.setDataRef(column);

            if(dim.getNamedGroupInfo() instanceof SNamedGroupInfo named) {
               named.setDataRef(column);
            }
         }
         else if(ref.getDataRef() instanceof VSChartAggregateRef aggregate) {
            aggregate.setDataRef(new AttributeRef(field.column()));
         }

         AestheticInfo reread = factory.createAestheticInfo(
            ref, cinfo, new OriginalDescriptor(channel.equals("size")
               ? OriginalDescriptor.SIZE : OriginalDescriptor.COLOR, null));

         ChartBindingModel model = new ChartBindingModel();
         model.setColorField("color".equals(channel) ? reread : null);
         model.setSizeField("size".equals(channel) ? reread : null);
         return (Map<String, Object>) ChartAestheticMutator.describe(model).get(channel);
      }
   }

   private static AestheticInfo read(ChartBindingModel model, String channel) {
      return "size".equals(channel) ? model.getSizeField() : model.getColorField();
   }

   @Test
   void aDimensionKeepsItsDateLevel() {
      Map<String, Object> view = roundTrip("color", new FieldRef(
         "Order Date", "dimension", null, "month", null));

      FieldRef ref = assertInstanceOf(FieldRef.class, view.get("fieldRef"));
      assertEquals("Order Date", ref.column());
      assertEquals("dimension", ref.type());
      assertEquals(DateLevels.normalize("month"), ref.dateLevel());
   }

   /**
    * Bug #78359: the read model promotes an inline group to an EXPERT group of per-member
    * conditions; {@code FieldRefFactory.from} recognizes that shape and reports the members.
    * (Re-paste durability is covered in {@link InlineNamedGroupReadTest}.)
    */
   @Test
   void anInlineNamedGroupReadsBackAsNamedGroupValues() {
      FieldRef.NamedGroupValues groups = new FieldRef.NamedGroupValues(List.of(
         new FieldRef.NamedGroupValues.Clause("West", List.of("CA", "OR"))), null);

      Map<String, Object> view = roundTrip("color", new FieldRef(
         "State", "dimension", null, null, null, null, null, groups));

      FieldRef ref = assertInstanceOf(FieldRef.class, view.get("fieldRef"));
      assertEquals("State", ref.column());
      assertNull(ref.namedGroup());
      assertEquals("West", ref.namedGroupValues().groups().get(0).name());
      assertEquals(List.of("CA", "OR"), ref.namedGroupValues().groups().get(0).values());
   }

   @Test
   void aMeasureKeepsItsAggregateAndCalculator() {
      PercentCalcInfo percent = new PercentCalcInfo();
      percent.setLevel(1);

      Map<String, Object> view = roundTrip("size", new FieldRef(
         "Sales", "measure", "Sum", null, null, null, null, null, percent));

      FieldRef ref = assertInstanceOf(FieldRef.class, view.get("fieldRef"));
      assertEquals("measure", ref.type());
      assertEquals("Sum", ref.aggregate());
      assertInstanceOf(PercentCalcInfo.class, ref.calculateInfo());
      assertEquals(1, ((PercentCalcInfo) ref.calculateInfo()).getLevel());
   }

   @Test
   void anUnboundChannelReportsANullFieldRef() {
      ChartBindingModel model = new ChartBindingModel();

      @SuppressWarnings("unchecked")
      Map<String, Object> view = (Map<String, Object>) ChartAestheticMutator.describe(model)
         .get("color");

      assertTrue(view.containsKey("fieldRef"));
      assertNull(view.get("fieldRef"));
      assertNull(view.get("field"));
   }

   @Configuration
   static class Beans {
      @Bean
      DataRefModelFactoryService dataRefModelFactoryService() {
         return new DataRefModelFactoryService(List.of(
            new AttributeRefModel.AttributeRefModelFactory(),
            new ColumnRefModel.ColumnRefModelFactory()));
      }

      @Bean
      VisualFrameModelFactoryService visualFrameModelFactoryService() {
         return new VisualFrameModelFactoryService(List.of());
      }
   }
}
