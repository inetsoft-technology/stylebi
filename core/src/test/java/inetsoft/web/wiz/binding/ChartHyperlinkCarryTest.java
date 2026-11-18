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
import inetsoft.report.composition.graph.calc.ChangeCalc;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.filter.TextHighlight;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.graph.AxisDescriptor;
import inetsoft.uql.viewsheet.graph.CompositeTextFormat;
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
 * Bug #81686: changing a linked measure's aggregate formula on the shelf (Sum to Average) rebuilt
 * the ref and silently dropped its hyperlink. The link is now carried to the new aggregate of the
 * same column when the pairing is unambiguous, and reported otherwise.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartHyperlinkCarryTest {
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

   private static VSChartAggregateRef linked(String column, String formula, String url) {
      VSChartAggregateRef ref = measure(column, formula);
      ref.setHyperlink(new Hyperlink(url));
      return ref;
   }

   @Test
   void aFormulaChangeKeepsTheHyperlink() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.addYField(linked("REVENUE", "Sum", "https://example.com/rev"));

      List<String> notes = rewrite(info, "y", agg("REVENUE", "Average"));

      assertNotNull(y(info, 0).getHyperlink());
      assertEquals("https://example.com/rev", y(info, 0).getHyperlink().getLink());
      assertTrue(notes.isEmpty(), notes.toString());
   }

   @Test
   void aFormulaChangeWithACalculatorKeepsTheHyperlink() {
      VSChartInfo info = new DefaultVSChartInfo();
      VSChartAggregateRef old = linked("REVENUE", "Sum", "https://example.com/rev");
      old.setCalculator(new ChangeCalc());
      info.addYField(old);

      rewrite(info, "y", agg("REVENUE", "Average"));

      assertNotNull(y(info, 0).getHyperlink());
   }

   @Test
   void aCalculatorOnlyChangeStillKeepsTheHyperlink() {
      VSChartInfo info = new DefaultVSChartInfo();
      VSChartAggregateRef old = linked("REVENUE", "Sum", "https://example.com/rev");
      old.setCalculator(new ChangeCalc());
      info.addYField(old);

      List<String> notes = rewrite(info, "y", agg("REVENUE", "Sum"));

      assertNotNull(y(info, 0).getHyperlink());
      assertTrue(notes.isEmpty(), notes.toString());
   }

   @Test
   void anIdentityMatchedLinkIsNotStolenByANewFormula() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.addYField(linked("REVENUE", "Sum", "https://example.com/sum"));
      info.addYField(linked("REVENUE", "Max", "https://example.com/max"));

      rewrite(info, "y", agg("REVENUE", "Max"), agg("REVENUE", "Average"));

      assertEquals("https://example.com/max", y(info, 0).getHyperlink().getLink());
      assertEquals("https://example.com/sum", y(info, 1).getHyperlink().getLink());
   }

   @Test
   void anUnlinkedSiblingOnTheSameColumnMakesThePairingAmbiguous() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.addYField(linked("REVENUE", "Sum", "https://example.com/sum"));
      info.addYField(measure("REVENUE", "Max"));

      List<String> notes = rewrite(info, "y", agg("REVENUE", "Max"), agg("REVENUE", "Average"));

      assertNull(y(info, 0).getHyperlink());
      assertNull(y(info, 1).getHyperlink());
      assertEquals(1, notes.size(), notes.toString());
   }

   @Test
   void anAmbiguousFormulaChangeDropsTheLinkAndSaysSo() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.addYField(linked("REVENUE", "Sum", "https://example.com/rev"));

      List<String> notes = rewrite(info, "y", agg("REVENUE", "Min"), agg("REVENUE", "Average"));

      assertNull(y(info, 0).getHyperlink());
      assertNull(y(info, 1).getHyperlink());
      assertEquals(1, notes.size(), notes.toString());
      assertTrue(notes.get(0).contains("hyperlink"), notes.get(0));
      assertTrue(notes.get(0).contains("Sum(REVENUE)"), notes.get(0));
      assertTrue(notes.get(0).contains("set_hyperlink"), notes.get(0));
   }

   @Test
   void aLinkIsNotCarriedOntoADifferentColumn() {
      VSChartInfo info = new DefaultVSChartInfo();
      info.addYField(linked("REVENUE", "Sum", "https://example.com/rev"));

      List<String> notes = rewrite(info, "y", agg("QUANTITY", "Sum"));

      assertNull(y(info, 0).getHyperlink());
      assertTrue(notes.isEmpty(), "a field that left the shelf is not reported");
   }

   @Test
   void aDimensionDateLevelChangeStillDropsTheLink() {
      VSChartInfo info = new DefaultVSChartInfo();
      VSChartDimensionRef d = dimension("ORDER_DATE", XConstants.YEAR_DATE_GROUP);
      d.setHyperlink(new Hyperlink("https://example.com/d"));
      info.addXField(d);

      rewrite(info, "x", dim("ORDER_DATE", "month"));

      assertNull(((VSChartDimensionRef) info.getXField(0)).getHyperlink());
   }

   @Test
   void aFormulaChangeStillDropsTheHighlightAndKeepsTheLink() {
      VSChartInfo info = new DefaultVSChartInfo();
      VSChartAggregateRef old = highlighted("REVENUE", "Sum");
      old.setHyperlink(new Hyperlink("https://example.com/rev"));
      info.addYField(old);

      List<String> notes = rewrite(info, "y", agg("REVENUE", "Average"));

      assertNull(y(info, 0).getHighlightGroup());
      assertNotNull(y(info, 0).getHyperlink());
      assertEquals(1, notes.size(), notes.toString());
      assertTrue(notes.get(0).contains("highlight"), notes.get(0));
   }
}
