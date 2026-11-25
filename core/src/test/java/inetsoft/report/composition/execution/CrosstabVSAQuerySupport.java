/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.report.filter.*;
import inetsoft.test.*;
import inetsoft.test.dc.DcInMemoryViewsheetFixture;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;

import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/** Sales table, assembly builders and readers shared by the crosstab viewsheet tests. */
final class CrosstabVSAQuerySupport {
   // ---- a sales table: Region x Product x Grade, two rows per combination, W has no p3 ----

   static final String[] NAMES = { "Region", "Product", "Grade", "Qty", "Price", "Day" };
   static final String[] TYPES = { XSchema.STRING, XSchema.STRING, XSchema.STRING,
                                           XSchema.INTEGER, XSchema.DOUBLE, XSchema.DATE };

   static List<Object[]> salesRows() {
      List<Object[]> rows = new ArrayList<>();
      int n = 0;

      for(String region : new String[] { "E", "W" }) {
         for(String product : new String[] { "p1", "p2", "p3" }) {
            if(region.equals("W") && product.equals("p3")) {
               continue;
            }

            for(String grade : new String[] { "g1", "g2" }) {
               n++;
               rows.add(new Object[] { region, product, grade, 3 + (n * 7) % 11, 1.5 * n + 1,
                                       Date.valueOf(LocalDate.of(2024, 1, 1 + n % 7)) });
            }
         }
      }

      return rows;
   }

   static VSDimensionRef dim(String column) {
      ColumnRef col = new ColumnRef(new AttributeRef(null, column));
      col.setDataType(XSchema.STRING);
      VSDimensionRef dim = new VSDimensionRef();
      dim.setDataRef(col);
      dim.setGroupColumnValue(column);
      dim.setDataType(XSchema.STRING);
      return dim;
   }

   static VSDimensionRef dateDim(String column, int level) {
      ColumnRef col = new ColumnRef(new AttributeRef(null, column));
      col.setDataType(XSchema.DATE);
      VSDimensionRef dim = new VSDimensionRef();
      dim.setDataRef(col);
      dim.setGroupColumnValue(column);
      dim.setDataType(XSchema.DATE);
      dim.setDateLevelValue(level + "");
      return dim;
   }

   static VSAggregateRef agg(String column, AggregateFormula formula) {
      VSAggregateRef agg = new VSAggregateRef();
      agg.setColumnValue(column);
      agg.setFormulaValue(formula.getName());
      return agg;
   }

   static CrosstabVSAssembly add(DcInMemoryViewsheetFixture.RuntimeSheet sheet, String name,
                                  VSDimensionRef[] rows, VSDimensionRef[] cols,
                                  VSAggregateRef... aggs)
   {
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(sheet.vs(), name);
      crosstab.setSourceInfo((SourceInfo) sheet.source().clone());
      VSCrosstabInfo info = crosstab.getVSCrosstabInfo();
      info.setDesignRowHeaders(rows);
      info.setDesignColHeaders(cols);
      info.setDesignAggregates(aggs);
      info.setRowTotalVisibleValue("true");
      info.setColTotalVisibleValue("true");
      sheet.vs().addAssembly(crosstab);
      return crosstab;
   }

   static TableLens data(DcInMemoryViewsheetFixture.RuntimeSheet sheet, String name)
      throws Exception
   {
      TableLens lens = (TableLens) sheet.box().getData(name);
      assertNotNull(lens, name);
      lens.moreRows(Integer.MAX_VALUE);
      return lens;
   }

   /** The body (below the header rows, right of the header columns) of a crosstab. */
   static List<List<Object>> body(TableLens lens) {
      List<List<Object>> rows = new ArrayList<>();

      for(int r = lens.getHeaderRowCount(); r < lens.getRowCount(); r++) {
         List<Object> row = new ArrayList<>();

         for(int c = lens.getHeaderColCount(); c < lens.getColCount(); c++) {
            row.add(lens.getObject(r, c));
         }

         rows.add(row);
      }

      return rows;
   }

   static List<Object[]> where(List<Object[]> rows, int col, Object value) {
      List<Object[]> list = new ArrayList<>();

      for(Object[] row : rows) {
         if(value == null || Objects.equals(row[col], value)) {
            list.add(row);
         }
      }

      return list;
   }

   static double sum(List<Object[]> rows, int col) {
      return rows.stream().mapToDouble(r -> ((Number) r[col]).doubleValue()).sum();
   }

   static void assertNumber(double expected, Object actual, String message) {
      assertInstanceOf(Number.class, actual, message);
      assertEquals(expected, ((Number) actual).doubleValue(), 1e-9, message);
   }
}
