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
package inetsoft.web.composer.vs.dialog;

import inetsoft.test.*;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.graph.aesthetic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #81499, #81501: a text-label highlight on a treemap must resolve to the bound text
 * measure, not the size measure or the group dimension.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class HighlightDialogServiceTreemapTextTest {
   private static VSChartAggregateRef measure(String col) {
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setColumnValue(col);
      ref.setDataRef(new AttributeRef(null, col));
      ref.setFormulaValue("Sum");
      return ref;
   }

   private static VSAestheticRef aesthetic(VSChartAggregateRef ref) {
      VSAestheticRef aref = new VSAestheticRef();
      aref.setDataRef(ref);
      return aref;
   }

   private static DefaultVSChartInfo treemap(String sizeCol, String textCol) {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_TREEMAP);
      info.setRTChartType(GraphTypes.CHART_TREEMAP);
      VSChartDimensionRef dim = new VSChartDimensionRef(new AttributeRef(null, "Category"));
      info.addGroupField(dim);

      if(sizeCol != null) {
         info.setSizeField(aesthetic(measure(sizeCol)));
      }

      if(textCol != null) {
         info.setTextField(aesthetic(measure(textCol)));
      }

      return info;
   }

   private static ChartRef resolve(ChartInfo info, String col, boolean isText)
      throws Exception
   {
      HighlightDialogService service =
         new HighlightDialogService(null, null, null, null, null);
      Method m = HighlightDialogService.class.getDeclaredMethod(
         "getMeasure", ChartInfo.class, String.class, boolean.class, boolean.class,
         boolean.class);
      m.setAccessible(true);
      return (ChartRef) m.invoke(service, info, col, false, false, isText);
   }

   /** 81499: same measure on size and text resolves to the text ref. */
   @Test
   void sameMeasureOnSizeAndTextResolvesToTextRef() throws Exception {
      DefaultVSChartInfo info = treemap("Total", "Total");
      String name = info.getTextField().getFullName();
      ChartRef ref = resolve(info, name, true);
      assertSame(info.getTextField().getDataRef(), ref);
      assertNotSame(info.getSizeField().getDataRef(), ref);
   }

   /** 81501: the group dimension name resolves to the text ref when text is bound. */
   @Test
   void dimensionColNameResolvesToTextRef() throws Exception {
      DefaultVSChartInfo info = treemap("Total", "Qty");
      assertSame(info.getTextField().getDataRef(), resolve(info, "Category", true));
   }

   /** Distinct size/text measures: measure name resolves to the text ref. */
   @Test
   void measureColNameWithDistinctSizeAndTextResolvesToTextRef() throws Exception {
      DefaultVSChartInfo info = treemap("Total", "Qty");
      String name = info.getTextField().getFullName();
      assertSame(info.getTextField().getDataRef(), resolve(info, name, true));
   }

   /** No text field bound: the group dimension override is kept. */
   @Test
   void noTextFieldKeepsDimensionOverride() throws Exception {
      DefaultVSChartInfo info = treemap("Total", null);
      ChartRef ref = resolve(info, "Category", true);
      assertSame(info.getGroupFields()[0], ref);
   }

   /** Non-text highlight on the dimension is unchanged. */
   @Test
   void nonTextDimensionHighlightUnchanged() throws Exception {
      DefaultVSChartInfo info = treemap("Total", "Qty");
      assertSame(info.getGroupFields()[0], resolve(info, "Category", false));
   }
}
