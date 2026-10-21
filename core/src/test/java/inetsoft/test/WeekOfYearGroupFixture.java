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
package inetsoft.test;

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.erm.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;

import inetsoft.mv.MVManager;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.report.composition.execution.DistributedTableCacheStore;
import inetsoft.uql.service.DataSourceRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.sql.Date;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A flat {@code TableVSAssembly} over a worksheet table grouped by "Week of Year" exactly the
 * way the Group and Aggregate dialog ({@code AggregateDialogService.processDateGrouping})
 * builds it: the date column is replaced by a {@code ColumnRef(DateRangeRef WEEK_OF_YEAR_PART)}
 * and the aggregate info groups by a {@code GroupRef} on that column. The lens is the real
 * worksheet aggregation output, so the group cell is whatever the product really produces
 * (an Integer week number), not a hand-built {@code java.sql.Date}.
 */
public final class WeekOfYearGroupFixture {
   public final TableVSAssembly table;
   public final TableLens lens;
   /** Name of the week-of-year group column. */
   public static final String GROUP_COLUMN = "WeekOfYear(d)";

   /** Spring beans the worksheet aggregation needs; add to the test's {@code classes}. */
   @Configuration
   public static class Config {
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

   private WeekOfYearGroupFixture(TableVSAssembly table, TableLens lens) {
      this.table = table;
      this.lens = lens;
   }

   /**
    * Build the fixture; dates are aggregated under the current default locale/week.start.
    * The aggregate is Sum of an int column {@code v}, so every distinct week is one row.
    */
   public int groupColumn() {
      for(int c = 0; c < lens.getColCount(); c++) {
         if(GROUP_COLUMN.equals(String.valueOf(lens.getObject(0, c)))) {
            return c;
         }
      }

      throw new AssertionError("no " + GROUP_COLUMN + " column in the lens");
   }

   /** Number of data rows (excluding the header), reading the lens to its end. */
   public int dataRows() {
      int rows = 0;

      while(lens.moreRows(rows + 1)) {
         rows++;
      }

      return rows;
   }

   public static WeekOfYearGroupFixture create(Date... dates) throws Exception {
      Object[][] data = new Object[dates.length + 1][];
      data[0] = new Object[] { "d", "v" };

      for(int i = 0; i < dates.length; i++) {
         data[i + 1] = new Object[] { dates[i], 1 };
      }

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "D");
      table.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.DATE, XSchema.INTEGER }, data));
      ws.addAssembly(table);

      ColumnSelection columns = table.getColumnSelection();
      ColumnRef dcol = (ColumnRef) columns.getAttribute("d");
      ColumnRef vcol = (ColumnRef) columns.getAttribute("v");

      DateRangeRef range = new DateRangeRef(
         DateRangeRef.getName("d", XConstants.WEEK_OF_YEAR_DATE_GROUP), dcol.getDataRef(),
         XConstants.WEEK_OF_YEAR_DATE_GROUP);
      range.setOriginalType(XSchema.DATE);
      ColumnRef wcol = new ColumnRef(range);
      wcol.setDataType(range.getDataType());
      columns.addAttribute(wcol);
      table.setColumnSelection(columns);

      AggregateInfo ainfo = new AggregateInfo();
      GroupRef grp = new GroupRef(wcol);
      grp.setDateGroup(XConstants.WEEK_OF_YEAR_DATE_GROUP);
      ainfo.addGroup(grp);
      ainfo.addAggregate(new AggregateRef(vcol, AggregateFormula.SUM));
      table.setAggregateInfo(ainfo);
      table.setColumnSelection(table.getColumnSelection(), false);
      table.setColumnSelection(table.getColumnSelection(), true);

      Viewsheet vs = new Viewsheet();
      Method setBase = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBase.setAccessible(true);
      setBase.invoke(vs, ws);

      TableVSAssembly tv = new TableVSAssembly(vs, "Table1");
      tv.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "D"));
      vs.addAssembly(tv);

      TableLens lens = new AssetQuerySandbox(ws).getTableLens("D", AssetQuerySandbox.RUNTIME_MODE);

      // the builders read lens cells by column position, so order the table's columns like the lens
      ColumnSelection pub = table.getColumnSelection(true);
      ColumnSelection ordered = new ColumnSelection();

      for(int c = 0; c < lens.getColCount(); c++) {
         ordered.addAttribute(pub.getAttribute(String.valueOf(lens.getObject(0, c))));
      }

      tv.setColumnSelection(ordered);
      return new WeekOfYearGroupFixture(tv, lens);
   }
}
