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
package inetsoft.uql.viewsheet;

import inetsoft.report.composition.graph.calc.PercentCalc;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80057: converting a crosstab whose measure carries a PercentCalc to a freehand table threw
 * a NullPointerException because the measure header (prefixed with the calculator text) matched
 * neither the plain aggregate name nor the legacy percentage option.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSLayoutToolPercentCalcAggregateTest {
   private static final String TOTAL = "Product:Total";
   private static final String QUANTITY = "Product:Quantity";

   @Test
   void grandTotalPercentCalcHeaderResolvesToGrandTotalPercentage() {
      PercentCalc calc = percentCalc(PercentCalc.GRAND_TOTAL, null);
      DataRef[] vrefs = runtimeAggregates(calc);
      AggregateRef[] refs = VSLayoutTool.getAggregateRefs(null, vrefs, true);

      assertNull(VSLayoutTool.findAggregateRef(refs, header(calc, refs[0]), XConstants.PERCENTAGE_BY_COL),
                 "the legacy lookup does not know a calculator, that is the defect");

      AggregateRef found = VSLayoutTool.findPercentCalcAggregateRef(
         vrefs, refs, header(calc, refs[0]));

      assertSame(refs[0], found);
      assertEquals(XConstants.PERCENTAGE_OF_GRANDTOTAL, found.getPercentageOption());
      assertTrue(found.getFormulaName().contains("<" + XConstants.PERCENTAGE_OF_GRANDTOTAL + ">"),
                 "freehand summary formula must carry the percent: " + found.getFormulaName());
   }

   @Test
   void subTotalPercentCalcResolvesToGroupPercentage() {
      PercentCalc calc = percentCalc(PercentCalc.SUB_TOTAL, null);
      DataRef[] vrefs = runtimeAggregates(calc);
      AggregateRef[] refs = VSLayoutTool.getAggregateRefs(null, vrefs, true);

      AggregateRef found = VSLayoutTool.findPercentCalcAggregateRef(
         vrefs, refs, header(calc, refs[0]));

      assertSame(refs[0], found);
      assertEquals(XConstants.PERCENTAGE_OF_GROUP, found.getPercentageOption());
   }

   @Test
   void unrelatedHeaderIsNotMatched() {
      PercentCalc calc = percentCalc(PercentCalc.GRAND_TOTAL, null);
      DataRef[] vrefs = runtimeAggregates(calc);
      AggregateRef[] refs = VSLayoutTool.getAggregateRefs(null, vrefs, true);

      assertNull(VSLayoutTool.findPercentCalcAggregateRef(vrefs, refs, "Sum(" + QUANTITY + ")"));
      assertNull(VSLayoutTool.findPercentCalcAggregateRef(vrefs, refs, null));
   }

   @Test
   void measureWithoutPercentCalcIsNotMatched() {
      DataRef[] vrefs = runtimeAggregates(null);
      AggregateRef[] refs = VSLayoutTool.getAggregateRefs(null, vrefs, true);

      assertNull(VSLayoutTool.findPercentCalcAggregateRef(
         vrefs, refs, "% of Grand Total: Sum(" + TOTAL + ")"));
   }

   @Test
   void percentOfADimensionIsRefusedNotConvertedToAPlainSum() {
      PercentCalc calc = percentCalc(PercentCalc.SUB_TOTAL, "Product:Category");
      DataRef[] vrefs = runtimeAggregates(calc);
      AggregateRef[] refs = VSLayoutTool.getAggregateRefs(null, vrefs, true);
      String header = header(calc, refs[0]);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> VSLayoutTool.findPercentCalcAggregateRef(vrefs, refs, header));
      assertTrue(ex.getMessage().contains("Product:Category"), ex.getMessage());
   }

   private static String header(PercentCalc calc, AggregateRef ref) {
      return calc.getPrefix() + "Sum(" + TOTAL + ")";
   }

   private static PercentCalc percentCalc(int level, String column) {
      PercentCalc calc = new PercentCalc();
      calc.setLevel(level);

      if(column != null) {
         calc.setColumnName(column);
      }

      return calc;
   }

   private static DataRef[] runtimeAggregates(PercentCalc calc) {
      VSAggregateRef ref = new VSAggregateRef();
      ref.setColumnValue(TOTAL);
      ref.setFormulaValue("Sum");
      ref.setRefType(DataRef.MEASURE);
      ref.setCalculator(calc);

      VSCrosstabInfo info = new VSCrosstabInfo();
      info.setDesignAggregates(new DataRef[] { ref });
      info.update(null, columns(), null, false, null, null);
      return info.getRuntimeAggregates();
   }

   private static ColumnSelection columns() {
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(column(TOTAL, XSchema.DOUBLE));
      columns.addAttribute(column(QUANTITY, XSchema.DOUBLE));
      columns.addAttribute(column("Product:Category", XSchema.STRING));
      return columns;
   }

   private static ColumnRef column(String name, String type) {
      ColumnRef col = new ColumnRef(new AttributeRef(null, name));
      col.setDataType(type);
      return col;
   }
}
