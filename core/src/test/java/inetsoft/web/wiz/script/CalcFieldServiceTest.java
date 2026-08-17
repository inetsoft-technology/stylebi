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
package inetsoft.web.wiz.script;

import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@WizAgentTestSupport
class CalcFieldServiceTest {
   private final CalcFieldService service = new CalcFieldService();

   private static CalculateRef calc(String name, String expression, boolean sql) {
      ExpressionRef inner = new ExpressionRef();
      inner.setName(name);
      inner.setExpression(expression);

      CalculateRef ref = new CalculateRef(true);
      ref.setDataRef(inner);
      ref.setSQL(sql);
      return ref;
   }

   /** A viewsheet with one chart bound to Query1, which has two calc fields. */
   private static Viewsheet vsWithCalcFields() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      // There is NO setTableName. getTableName() derives from the SourceInfo, and returns null
      // unless the type is ASSET or VS_ASSEMBLY -- so a fixture that skips this enumerates nothing.
      info.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "Query1"));

      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getName()).thenReturn("Chart1");
      when(chart.getVSAssemblyInfo()).thenReturn(info);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssemblies()).thenReturn(new Assembly[]{ chart });
      when(vs.getCalcFields("Query1")).thenReturn(new CalculateRef[]{
         calc("Margin", "field['PRICE'] - field['COST']", false),
         calc("TaxRate", "0.2", true),
      });
      return vs;
   }

   @Test
   void enumeratesTablesFromTheBoundAssemblies() {
      assertEquals(List.of("Query1"), service.tablesWithCalcFields(vsWithCalcFields()));
   }

   @Test
   void listsEveryCalcFieldWithItsTableAndFlags() {
      List<CalcFieldService.Found> found = service.list(vsWithCalcFields());

      assertEquals(2, found.size());
      assertEquals("Query1", found.get(0).table());
      assertEquals("Margin", found.get(0).name());
      assertFalse(found.get(0).sql(), "Margin is a JavaScript expression");
      assertTrue(found.get(1).sql(), "TaxRate is a SQL expression");
   }

   /**
    * The sql flag must come from ColumnRef, not from the wrapped ExpressionRef.
    * ExpressionRef.isSQL() returns a hardcoded false, so reading it would report every calc field
    * as JavaScript -- silently, and for the one field where it matters most.
    */
   @Test
   void readsTheSqlFlagFromTheCalculateRefNotTheInnerExpression() {
      CalculateRef sqlField = calc("TaxRate", "0.2", true);

      assertTrue(sqlField.isSQL(), "guards the premise: ColumnRef carries the flag");
      assertFalse(((ExpressionRef) sqlField.getDataRef()).isSQL(),
                  "and the inner ref does not -- this is the trap");
   }

   @Test
   void readsAnExpressionByTableAndName() throws Exception {
      assertEquals("field['PRICE'] - field['COST']",
                   service.read(vsWithCalcFields(), "Query1", "Margin"));
   }

   @Test
   void writesAnExpressionThroughToTheInnerRef() throws Exception {
      Viewsheet vs = vsWithCalcFields();

      service.write(vs, "Query1", "Margin", "field['PRICE'] * 2");

      assertEquals("field['PRICE'] * 2", service.read(vs, "Query1", "Margin"));
   }

   @Test
   void anUnknownFieldNameFailsLoudRatherThanSilently() {
      Viewsheet vs = vsWithCalcFields();

      PairingException ex = assertThrows(
         PairingException.class, () -> service.read(vs, "Query1", "Nope"));
      assertTrue(ex.getMessage().contains("Nope"), ex.getMessage());
      assertTrue(ex.getMessage().contains("Margin"),
                 "the refusal should say what IS there: " + ex.getMessage());
   }

   @Test
   void anUnknownTableFailsLoud() {
      assertThrows(PairingException.class,
                   () -> service.read(vsWithCalcFields(), "NoSuchTable", "Margin"));
   }

   /**
    * getCalcFields returns null, not an empty array, for a table with none. A null-blind
    * implementation NPEs on the most ordinary viewsheet there is -- one with no calc fields.
    */
   @Test
   void aTableWithNoCalcFieldsYieldsNothingRatherThanThrowing() {
      Viewsheet vs = mock(Viewsheet.class);
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "Plain"));
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getName()).thenReturn("Chart1");
      when(chart.getVSAssemblyInfo()).thenReturn(info);
      when(vs.getAssemblies()).thenReturn(new Assembly[]{ chart });
      when(vs.getCalcFields("Plain")).thenReturn(null);

      assertTrue(service.list(vs).isEmpty());
   }

   @Test
   void writingAFieldThatDoesNotExistIsRefusedRatherThanCreatingIt() {
      Viewsheet vs = vsWithCalcFields();

      PairingException ex = assertThrows(
         PairingException.class,
         () -> service.write(vs, "Query1", "BrandNew", "1"));
      assertTrue(ex.getMessage().toLowerCase().contains("create"),
                 "must say creation is out of scope, not just 'not found': " + ex.getMessage());
      verify(vs, never()).addCalcField(anyString(), any());
   }
}
