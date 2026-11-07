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

import inetsoft.report.Hyperlink;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.filter.TextHighlight;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #80210: a shelf write rebuilds every ref on the shelf, and the series / text highlight
 * groups live on the ref, so a field that stays bound lost them (a hyperlink was already carried
 * by #80003). These tests run the real model factories ({@code pasteChartRef} builds a fresh ref
 * for a wiz-built model, which has no original descriptor) around {@link ChartHyperlinkCarry}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartHighlightCarryTest {
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

   private static HighlightGroup group(String name) {
      HighlightGroup group = new HighlightGroup();
      group.addHighlight(name, new TextHighlight());
      return group;
   }

   private static VSChartAggregateRef measure(String column, String formula) {
      AttributeRef attribute = new AttributeRef(null, column);
      attribute.setDataType(XSchema.DOUBLE);
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setDataRef(new ColumnRef(attribute));
      ref.setFormulaValue(formula);
      return ref;
   }

   private static VSChartDimensionRef dimension(String column, int level) {
      AttributeRef attribute = new AttributeRef(null, column);
      attribute.setDataType(level == XConstants.NONE_DATE_GROUP ? XSchema.STRING : XSchema.DATE);
      VSChartDimensionRef ref = new VSChartDimensionRef();
      ref.setDataRef(new ColumnRef(attribute));
      ref.setDateLevelValue(String.valueOf(level));
      return ref;
   }

   private static VSChartAggregateRef highlighted(String column, String formula) {
      VSChartAggregateRef ref = measure(column, formula);
      ref.setHighlightGroup(group("Big"));
      ref.setTextHighlightGroup(group("BigText"));
      return ref;
   }

   private static FieldRef agg(String column, String formula) {
      return new FieldRef(column, "measure", formula, null, null);
   }

   private static FieldRef dim(String column, String level) {
      return new FieldRef(column, "dimension", null, level, null);
   }

   /** What setShelf does: snapshot, rebuild the shelf from wiz-built models, reapply. */
   private List<String> rewrite(VSChartInfo info, String shelf, FieldRef... fields) {
      List<ChartHyperlinkCarry.Saved> saved = ChartHyperlinkCarry.snapshot(info, shelf);
      List<ChartRef> fresh = new ArrayList<>();

      for(FieldRef field : fields) {
         ChartRefModel model = FieldRefFactory.toChartRef(field);
         fresh.add(refService.pasteChartRef(info, model));
      }

      if(shelf.equals("y")) {
         info.removeYFields();
         fresh.forEach(info::addYField);
      }
      else {
         info.removeXFields();
         fresh.forEach(info::addXField);
      }

      return ChartHyperlinkCarry.reapply(info, shelf, saved);
   }

   private static VSChartAggregateRef y(VSChartInfo info, int i) {
      return (VSChartAggregateRef) info.getYField(i);
   }

   @Test
   void aMeasureKeepsBothHighlightsWhenAnotherMeasureIsAdded() {
      VSChartInfo info = new DefaultVSChartInfo();
      VSChartAggregateRef revenue = highlighted("REVENUE", "Sum");
      revenue.setHyperlink(new Hyperlink("https://example.com/rev"));
      info.addYField(revenue);

      List<String> notes = rewrite(info, "y", agg("QUANTITY", "Sum"), agg("REVENUE", "Sum"));

      assertNull(y(info, 0).getHighlightGroup(), "the new measure has no highlight");
      assertNotNull(y(info, 1).getHighlightGroup(), "series highlight must survive");
      assertNotNull(y(info, 1).getHighlightGroup().getHighlight("Big"));
      assertNotNull(y(info, 1).getTextHighlightGroup().getHighlight("BigText"));
      assertEquals("https://example.com/rev", y(info, 1).getHyperlink().getLink(),
                   "the hyperlink is still carried");
      assertTrue(notes.isEmpty());
   }

   @Test
   void aResendOfTheSameShelfKeepsTheHighlight() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.addYField(highlighted("REVENUE", "Sum"));

      rewrite(info, "y", agg("REVENUE", "Sum"));

      assertNotNull(y(info, 0).getHighlightGroup());
      assertNotNull(y(info, 0).getTextHighlightGroup());
   }

   @Test
   void aRemovedMeasureIsNotCarriedOntoTheRemainingOne() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.addYField(highlighted("REVENUE", "Sum"));
      info.addYField(measure("QUANTITY", "Sum"));

      List<String> notes = rewrite(info, "y", agg("QUANTITY", "Sum"));

      assertEquals(1, info.getYFields().length);
      assertNull(y(info, 0).getHighlightGroup());
      assertTrue(notes.isEmpty(), "a field that left the shelf is not reported");
   }

   @Test
   void aFormulaChangeDropsTheHighlightAndSaysSo() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.addYField(highlighted("REVENUE", "Sum"));

      List<String> notes = rewrite(info, "y", agg("REVENUE", "Average"));

      assertNull(y(info, 0).getHighlightGroup());
      assertNull(y(info, 0).getTextHighlightGroup());
      assertEquals(1, notes.size());
      assertTrue(notes.get(0).contains("Sum(REVENUE)"), notes.get(0));
      assertTrue(notes.get(0).contains("highlight"), notes.get(0));
   }

   @Test
   void aDimensionKeepsItsHighlights() {
      VSChartInfo info = new DefaultVSChartInfo();
      VSChartDimensionRef region = dimension("REGION", XConstants.NONE_DATE_GROUP);
      region.setHighlightGroup(group("Big"));
      region.setTextHighlightGroup(group("BigText"));
      info.addXField(region);

      List<String> notes = rewrite(info, "x", dim("REGION", "none"), dim("CATEGORY_NAME", "none"));

      VSChartDimensionRef kept = (VSChartDimensionRef) info.getXFields()[0];
      assertNotNull(kept.getHighlightGroup());
      assertNotNull(kept.getTextHighlightGroup());
      assertNull(((VSChartDimensionRef) info.getXFields()[1]).getHighlightGroup());
      assertTrue(notes.isEmpty());
   }

   @Test
   void aDateLevelChangeDropsTheDimensionHighlightAndSaysSo() {
      VSChartInfo info = new DefaultVSChartInfo();
      VSChartDimensionRef year = dimension("ORDER_DATE", XConstants.YEAR_DATE_GROUP);
      year.setHighlightGroup(group("Big"));
      info.addXField(year);

      List<String> notes = rewrite(info, "x", dim("ORDER_DATE", "month"));

      assertNull(((VSChartDimensionRef) info.getXFields()[0]).getHighlightGroup());
      assertEquals(1, notes.size());
      assertTrue(notes.get(0).contains("date level"), notes.get(0));
   }
}
