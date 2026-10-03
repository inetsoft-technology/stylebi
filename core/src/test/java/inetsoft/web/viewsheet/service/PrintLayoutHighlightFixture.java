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

package inetsoft.web.viewsheet.service;

import inetsoft.mv.MVManager;
import inetsoft.report.*;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.report.composition.execution.DistributedTableCacheStore;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.filter.TextHighlight;
import inetsoft.report.internal.ElementIterator;
import inetsoft.report.internal.TableElementDef;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.report.io.viewsheet.VSExporter;
import inetsoft.report.io.viewsheet.pdf.PDFVSExporter;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import inetsoft.uql.viewsheet.vslayout.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Bug #77621 test fixture: a print-layout viewsheet whose table has a highlight with a
 * JavaScript condition value that reads the viewsheet's own table data. The highlight is
 * evaluated when the PDF exporter paints its queued report in write(), against the sandbox
 * that exported the bookmark.
 */
public final class PrintLayoutHighlightFixture {
   private PrintLayoutHighlightFixture() {
   }

   /**
    * Beans the real bookmark sandboxes need beyond BaseTestConfiguration.
    */
   @Configuration
   public static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }

      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }
   }

   /**
    * Wraps a real PDF exporter. Its write() first records, for each queued report, the number
    * of table rows painted with the highlight color, then runs the real write().
    */
   public static VSExporter recordHighlightsAtWrite(VSExporter real, List<Integer> counts)
      throws Exception
   {
      VSExporter exporter = spy((PDFVSExporter) real);

      doAnswer(inv -> {
         for(ReportSheet report : reports(exporter)) {
            counts.add(countHighlighted(report));
         }

         return inv.callRealMethod();
      }).when(exporter).write();

      return exporter;
   }

   /**
    * Rows (of ROWS) whose value is above the expression TableV.table[1][0] * 2500 = 17500.
    */
   public static int expectedHighlightedRows() {
      return ROWS - 2500;
   }

   @SuppressWarnings("unchecked")
   private static List<ReportSheet> reports(VSExporter exporter) throws Exception {
      Field field = PDFVSExporter.class.getDeclaredField("reportList");
      field.setAccessible(true);
      return (List<ReportSheet>) field.get(exporter);
   }

   // the table element is the one the report engine paints in write()
   private static int countHighlighted(ReportSheet report) {
      Enumeration<?> elems = ElementIterator.elements(report);
      int count = 0;

      while(elems.hasMoreElements()) {
         Object elem = elems.nextElement();

         if(elem instanceof TableElementDef table) {
            TableLens lens = table.getBaseTable();

            for(int r = 1; lens.moreRows(r); r++) {
               if(HIGHLIGHT.equals(lens.getForeground(r, 0))) {
                  count++;
               }
            }
         }
      }

      return count;
   }

   public static Viewsheet viewsheet() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly wsTable = new EmbeddedTableAssembly(ws, "A");
      Object[][] data = new Object[ROWS + 1][];
      data[0] = new Object[] { "value", "id" };

      for(int i = 1; i <= ROWS; i++) {
         data[i] = new Object[] { i * 7, i };
      }

      wsTable.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.INTEGER, XSchema.INTEGER }, data));
      ws.addAssembly(wsTable);

      Viewsheet vs = new Viewsheet();
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, ws);
      TableVSAssembly table = new TableVSAssembly(vs, "TableV");
      table.setSourceInfo(new SourceInfo(XSourceInfo.ASSET, null, "A"));
      ColumnSelection columns = new ColumnSelection();

      for(String name : new String[] { "value", "id" }) {
         columns.addAttribute(new ColumnRef(new AttributeRef(null, name)));
      }

      table.setColumnSelection(columns);
      ((TableDataVSAssemblyInfo) table.getVSAssemblyInfo()).setHighlightAttr(highlight());
      vs.addAssembly(table);

      PrintInfo printInfo = new PrintInfo();
      printInfo.setUnit("inches");
      PrintLayout layout = new PrintLayout();
      layout.setPrintInfo(printInfo);
      layout.setVSAssemblyLayouts(new ArrayList<>(List.of(
         new VSAssemblyLayout("TableV", new Point(0, 0), new Dimension(400, 300)))));
      vs.getLayoutInfo().setPrintLayout(layout);
      return vs;
   }

   // red when value > TableV.table[1][0] * 2500, an expression that reads viewsheet data
   private static TableHighlightAttr highlight() {
      ExpressionValue value = new ExpressionValue();
      value.setExpression("TableV.table[1][0] * 2500");
      value.setType(ExpressionValue.JAVASCRIPT);
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.GREATER_THAN);
      cond.setType(XSchema.INTEGER);
      cond.addValue(value);
      ConditionList conds = new ConditionList();
      conds.append(new ConditionItem(new ColumnRef(new AttributeRef(null, "value")), cond, 0));

      TextHighlight highlight = new TextHighlight();
      highlight.setName("above77621");
      highlight.setForeground(HIGHLIGHT);
      highlight.setConditionGroup(conds);
      HighlightGroup group = new HighlightGroup();
      group.addHighlight("above77621", highlight);

      TableHighlightAttr attr = new TableHighlightAttr();
      attr.setHighlight(new TableDataPath(-1, TableDataPath.DETAIL, XSchema.INTEGER,
                                          new String[] { "value" }), group);
      return attr;
   }

   private static final int ROWS = 5000;
   private static final Color HIGHLIGHT = Color.RED;
}
