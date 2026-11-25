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
package inetsoft.mv;

import inetsoft.mv.trans.TransformationDescriptor;
import inetsoft.mv.trans.TransformationFault;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The tables the MV analyzer refuses before it tries to build an MV:
 * {@code VSMVAnalyzer.isAcceptable} (cube, embedded or input bound table, physical query
 * with a variable, named group with a variable) and {@code VSMVAnalyzer.isSuitableTable}
 * (input dynamic table, post-aggregate calculated field, max rows with selections on sub
 * tables, rotated table or a table based on one, transformed table with an MV update
 * condition). Each refusal must add the fault
 * that explains it. The cube and the named group condition cases have their own tests
 * ({@link VSMVAnalyzerCubeBindingTest}, {@link VSMVAnalyzerNamedGroupConditionTest}).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSMVAnalyzerAcceptableTest {
   // ---------------------------------------------------------------- isAcceptable

   @Test
   void tableBoundToAChartIsAcceptable() throws Exception {
      Fixture fx = new Fixture();
      fx.bound("T1");
      fx.chart("Chart1", "T1");

      assertTrue(fx.acceptable("T1", "Chart1"));
      assertEquals(List.of(), fx.reasons("Chart1"));
   }

   @Test
   void missingTableIsNotAcceptable() throws Exception {
      Fixture fx = new Fixture();
      fx.chart("Chart1", "Gone");

      assertFalse(fx.acceptable("Gone", "Chart1"));
   }

   @Test
   void tableBoundToAnEmbeddedTableAssemblyIsNotAcceptable() throws Exception {
      Fixture fx = new Fixture();
      fx.bound("T1");
      fx.chart("Chart1", "T1");
      fx.embedded("Embedded1", "T1");

      assertFalse(fx.acceptable("T1", "Chart1"));
      assertReason(fx, "Chart1", "embedded");
   }

   @Test
   void tableBoundToAnInputAssemblyIsNotAcceptable() throws Exception {
      Fixture fx = new Fixture();
      fx.bound("T1");
      fx.chart("Chart1", "T1");
      fx.input("ComboBox1", "T1", "Region");

      assertFalse(fx.acceptable("T1", "Chart1"));
      assertReason(fx, "Chart1", "embedded");
   }

   @Test
   void composedTableWithEveryChildEmbeddedIsNotAcceptable() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly e1 = fx.bound("E1");
      fx.embedded("Embedded1", "E1");
      fx.mirror("M1", e1);
      fx.chart("Chart1", "M1");

      assertFalse(fx.acceptable("M1", "Chart1"));
   }

   @Test
   void composedTableWithOneChildNotEmbeddedIsAcceptable() throws Exception {
      // the child that is not embedded can still be materialized (44162)
      Fixture fx = new Fixture();
      TableAssembly e1 = fx.bound("E1");
      TableAssembly t2 = fx.bound("T2");
      fx.embedded("Embedded1", "E1");
      fx.join("J1", e1, t2);
      fx.chart("Chart1", "J1");

      assertTrue(fx.acceptable("J1", "Chart1"));
   }

   @Test
   void physicalQueryWithAVariableIsNotAcceptable() throws Exception {
      Fixture fx = new Fixture();
      fx.sql("Q1", "select * from orders where state = $(st)");
      fx.chart("Chart1", "Q1");

      assertFalse(fx.acceptable("Q1", "Chart1"));
      assertReason(fx, "Chart1", "physical query with a variable");
   }

   @Test
   void physicalQueryWithoutVariableIsAcceptable() throws Exception {
      Fixture fx = new Fixture();
      fx.sql("Q1", "select * from orders where state = 'NJ'");
      fx.chart("Chart1", "Q1");

      assertTrue(fx.acceptable("Q1", "Chart1"));
   }

   @Test
   void physicalQueryWithAVariableUnderAMirrorIsNotAcceptable() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly q1 = fx.sql("Q1", "select * from orders where state = $(st)");
      fx.mirror("M1", q1);
      fx.chart("Chart1", "M1");

      assertFalse(fx.acceptable("M1", "Chart1"));
   }

   @Test
   void namedGroupWithAVariableIsNotAcceptable() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      ConditionList cond = new ConditionList();
      AssetCondition value = new AssetCondition();
      value.setOperation(XCondition.EQUAL_TO);
      value.addValue(new UserVariable("st"));
      cond.append(new ConditionItem(column("Region"), value, 0));
      NamedGroupInfo groups = new NamedGroupInfo();
      groups.setGroupCondition("East", cond);
      GroupRef group = new GroupRef(column("Region"));
      group.setNamedGroupInfo(groups);
      AggregateInfo ainfo = new AggregateInfo();
      ainfo.addGroup(group);
      t1.setAggregateInfo(ainfo);
      fx.chart("Chart1", "T1");

      assertFalse(fx.acceptable("T1", "Chart1"));
      // the analyzer reports this with the fault of a physical query variable
      assertReason(fx, "Chart1", "physical query with a variable");
   }

   @Test
   void namedGroupWithoutVariableIsAcceptable() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      ConditionList cond = new ConditionList();
      AssetCondition value = new AssetCondition();
      value.setOperation(XCondition.EQUAL_TO);
      value.addValue("NJ");
      cond.append(new ConditionItem(column("Region"), value, 0));
      NamedGroupInfo groups = new NamedGroupInfo();
      groups.setGroupCondition("East", cond);
      GroupRef group = new GroupRef(column("Region"));
      group.setNamedGroupInfo(groups);
      AggregateInfo ainfo = new AggregateInfo();
      ainfo.addGroup(group);
      t1.setAggregateInfo(ainfo);
      fx.chart("Chart1", "T1");

      assertTrue(fx.acceptable("T1", "Chart1"));
   }

   // ---------------------------------------------------------------- isSuitableTable

   @Test
   void plainTableIsSuitable() throws Exception {
      Fixture fx = new Fixture();
      fx.bound("T1");
      fx.chart("Chart1", "T1");

      assertTrue(fx.suitable("T1", "Chart1"));
      assertEquals(List.of(), fx.reasons("Chart1"));
   }

   @Test
   void tableChangedByAnInputAssemblyIsNotSuitable() throws Exception {
      Fixture fx = new Fixture();
      fx.bound("T1");
      fx.chart("Chart1", "T1");
      fx.input("ComboBox1", "T1", "Region");

      assertFalse(fx.suitable("T1", "Chart1"));
      assertReason(fx, "Chart1", "target of input field binding");
   }

   @Test
   void calculatedFieldMarkedNoMVMakesTheTableDynamic() throws Exception {
      // the descriptor treats a table with such an expression as an input dynamic table
      // (#48975), so it is refused by that rule before the calculated field rule
      Fixture fx = new Fixture();
      fx.bound("T1").getColumnSelection().addAttribute(calc("Calc1", "/* @nomv */ 1"));
      fx.chart("Chart1", "T1");

      assertFalse(fx.suitable("T1", "Chart1"));
      assertReason(fx, "Chart1", "target of input field binding");
   }

   @Test
   void calculatedFieldWithAParameterMakesTheTableDynamic() throws Exception {
      Fixture fx = new Fixture();
      fx.bound("T1").getColumnSelection().addAttribute(calc("Calc1", "parameter.st + 1"));
      fx.chart("Chart1", "T1");

      assertFalse(fx.suitable("T1", "Chart1"));
      assertReason(fx, "Chart1", "target of input field binding");
   }

   @Test
   void calculatedFieldOnAnAggregateIsNotSuitable() throws Exception {
      // #49777
      Fixture fx = new Fixture();
      fx.bound("T1").getColumnSelection()
         .addAttribute(calc("Calc1", "field['Sum(Amount)'] * 2"));
      fx.chart("Chart1", "T1");

      assertFalse(fx.suitable("T1", "Chart1"));
      assertReason(fx, "Chart1", "aggregate calculated field");
   }

   @Test
   void plainCalculatedFieldIsSuitable() throws Exception {
      Fixture fx = new Fixture();
      fx.bound("T1").getColumnSelection().addAttribute(calc("Calc1", "field['Amount'] * 2"));
      fx.chart("Chart1", "T1");

      assertTrue(fx.suitable("T1", "Chart1"));
   }

   @Test
   void maxRowsWithASelectionOnASubTableIsNotSuitable() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      TableAssembly m1 = fx.mirror("M1", t1);
      m1.setMaxRows(100);
      fx.chart("Chart1", "M1");
      fx.selection("List1", "T1", "Region");

      assertFalse(fx.suitable("M1", "Chart1"));
      assertReason(fx, "Chart1", "max rows defined with selection");
   }

   @Test
   void maxRowsWithoutSubTableSelectionIsSuitable() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      TableAssembly m1 = fx.mirror("M1", t1);
      m1.setMaxRows(100);
      fx.chart("Chart1", "M1");

      assertTrue(fx.suitable("M1", "Chart1"));
   }

   @Test
   void rotatedTableWithMVUpdateConditionIsNotSuitable() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      RotatedTableAssembly rotated = new RotatedTableAssembly(fx.ws, "R1", t1);
      fx.ws.addAssembly(rotated);
      rotated.setMVUpdatePreConditionList(updateCondition());
      fx.chart("Chart1", "R1");

      assertFalse(fx.suitable("R1", "Chart1"));
      // a rotated table is refused with or without the condition (Bug #82323)
      assertReason(fx, "Chart1", "based on a rotated table");
   }

   @Test
   void unpivotTableWithMVUpdateConditionIsNotSuitable() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      UnpivotTableAssembly unpivot = new UnpivotTableAssembly(fx.ws, "U1", t1);
      fx.ws.addAssembly(unpivot);
      unpivot.setMVUpdatePreConditionList(updateCondition());
      fx.chart("Chart1", "U1");

      assertFalse(fx.suitable("U1", "Chart1"));
      assertReason(fx, "Chart1", "transformed (rotated/unpivot/crosstab)");
   }

   @Test
   void crosstabTableWithMVUpdateConditionIsNotSuitable() throws Exception {
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      AggregateInfo ainfo = new AggregateInfo();
      // a crosstab needs two groups and an aggregate
      ainfo.addGroup(new GroupRef(column("Region")));
      ainfo.addGroup(new GroupRef(column("State")));
      ainfo.addAggregate(new AggregateRef(column("Amount"), AggregateFormula.SUM));
      ainfo.setCrosstab(true);
      t1.setAggregateInfo(ainfo);
      t1.setMVUpdatePreConditionList(updateCondition());
      fx.chart("Chart1", "T1");

      assertFalse(fx.suitable("T1", "Chart1"));
      assertReason(fx, "Chart1", "transformed (rotated/unpivot/crosstab)");
   }

   @Test
   void mvUpdateConditionOnAPlainTableIsSuitable() throws Exception {
      Fixture fx = new Fixture();
      fx.bound("T1").setMVUpdatePreConditionList(updateCondition());
      fx.chart("Chart1", "T1");

      assertTrue(fx.suitable("T1", "Chart1"));
   }

   @Test
   void movedMVUpdateConditionIsIgnored() throws Exception {
      // the condition was moved to the parent table, so the unpivot table does not have it.
      // (a rotated table is refused anyway since Bug #82323)
      Fixture fx = new Fixture();
      TableAssembly t1 = fx.bound("T1");
      UnpivotTableAssembly unpivot = new UnpivotTableAssembly(fx.ws, "U1", t1);
      fx.ws.addAssembly(unpivot);
      unpivot.setMVUpdatePreConditionList(updateCondition());
      MVTool.setMVConditionMoved(unpivot, true);
      fx.chart("Chart1", "U1");

      assertTrue(fx.suitable("U1", "Chart1"));
   }

   // ---------------------------------------------------------------- helpers

   private static void assertReason(Fixture fx, String vassembly, String text) {
      List<String> reasons = fx.reasons(vassembly);
      assertTrue(reasons.stream().anyMatch(r -> r.contains(text)),
                 "no fault with '" + text + "': " + reasons);
   }

   private static ColumnRef column(String name) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, name));
      ref.setDataType(XSchema.STRING);
      return ref;
   }

   private static CalculateRef calc(String name, String script) {
      ExpressionRef exp = new ExpressionRef(null, name);
      exp.setExpression(script);
      CalculateRef calc = new CalculateRef(false);
      calc.setDataRef(exp);
      return calc;
   }

   private static ConditionList updateCondition() {
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.GREATER_THAN);
      cond.addValue(10);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(column("Amount"), cond, 0));
      return list;
   }

   /** A worksheet, a viewsheet on it and the analyzer of the viewsheet. */
   private static final class Fixture {
      final Worksheet ws = new Worksheet();
      final Viewsheet vs = new Viewsheet() {
         @Override
         public Worksheet getBaseWorksheet() {
            return ws;
         }
      };

      TableAssembly bound(String name) {
         PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, name);
         table.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "ds1", "ORDERS"));
         ColumnSelection cols = new ColumnSelection();
         cols.addAttribute(column("Region"));
         cols.addAttribute(column("Amount"));
         table.setColumnSelection(cols);
         ws.addAssembly(table);
         return table;
      }

      TableAssembly sql(String name, String sql) {
         SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, name);
         JDBCQuery query = new JDBCQuery();
         query.setSQLDefinition(new UniformSQL(sql, false));
         ((SQLBoundTableAssemblyInfo) table.getInfo()).setQuery(query);
         ColumnSelection cols = new ColumnSelection();
         cols.addAttribute(column("Region"));
         table.setColumnSelection(cols);
         ws.addAssembly(table);
         return table;
      }

      TableAssembly mirror(String name, TableAssembly base) {
         MirrorTableAssembly table = new MirrorTableAssembly(ws, name, base);
         ws.addAssembly(table);
         return table;
      }

      TableAssembly join(String name, TableAssembly left, TableAssembly right) {
         RelationalJoinTableAssembly table = new RelationalJoinTableAssembly(
            ws, name, new TableAssembly[] { left, right }, new TableAssemblyOperator[] {
               new TableAssemblyOperator(left.getName(), column("Region"),
                                         right.getName(), column("Region"),
                                         TableAssemblyOperator.INNER_JOIN)
            });
         ws.addAssembly(table);
         return table;
      }

      void chart(String name, String table) {
         TableVSAssembly assembly = new TableVSAssembly(vs, name);
         assembly.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, table));
         vs.addAssembly(assembly);
      }

      void embedded(String name, String table) {
         EmbeddedTableVSAssembly assembly = new EmbeddedTableVSAssembly(vs, name);
         assembly.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, table));
         vs.addAssembly(assembly);
      }

      void input(String name, String table, String col) {
         ComboBoxVSAssembly assembly = new ComboBoxVSAssembly(vs, name);
         assembly.setTableName(table);
         assembly.setColumn(column(col));
         vs.addAssembly(assembly);
      }

      void selection(String name, String table, String col) {
         SelectionListVSAssembly assembly = new SelectionListVSAssembly(vs, name);
         assembly.setTableName(table);
         assembly.setDataRef(column(col));
         vs.addAssembly(assembly);
      }

      boolean acceptable(String table, String vassembly) throws Exception {
         analyzer = prepare();
         desc = descriptor(table, vassembly);
         Object info = tableInfo(analyzer, table, vassembly);
         Method method = VSMVAnalyzer.class.getDeclaredMethod(
            "isAcceptable", Worksheet.class, info.getClass(), TransformationDescriptor.class);
         method.setAccessible(true);
         return (Boolean) method.invoke(analyzer, ws, info, desc);
      }

      boolean suitable(String table, String vassembly) throws Exception {
         analyzer = prepare();
         // the analyzer reads the dynamic tables and selections from its own descriptor
         analyzer.getDescriptor();
         desc = descriptor(table, vassembly);
         Method method = VSMVAnalyzer.class.getDeclaredMethod(
            "isSuitableTable", TableAssembly.class, TransformationDescriptor.class);
         method.setAccessible(true);
         return (Boolean) method.invoke(analyzer, ws.getAssembly(table), desc);
      }

      List<String> reasons(String vassembly) {
         List<?> faults = desc.getFaults(vassembly);
         List<String> reasons = new ArrayList<>();

         if(faults != null) {
            for(Object fault : faults) {
               reasons.add(((TransformationFault) fault).getReason());
            }
         }

         return reasons;
      }

      private VSMVAnalyzer prepare() throws Exception {
         VSMVAnalyzer analyzer =
            new VSMVAnalyzer("1^128^__NULL__^vs", vs, null, null, false, null);
         Method prepare = VSMVAnalyzer.class.getDeclaredMethod("prepare");
         prepare.setAccessible(true);
         prepare.invoke(analyzer);
         return analyzer;
      }

      private TransformationDescriptor descriptor(String table, String vassembly) {
         TransformationDescriptor desc = new TransformationDescriptor(vs, ws, 1);
         desc.reset(vassembly, table, table, table);
         return desc;
      }

      /** The analyzer's table info of a bound table, or a new one if nothing is bound. */
      private static Object tableInfo(VSMVAnalyzer analyzer, String table, String vassembly)
         throws Exception
      {
         Field field = VSMVAnalyzer.class.getDeclaredField("tmap");
         field.setAccessible(true);
         Object info = ((Map<?, ?>) field.get(analyzer)).get(table);

         if(info == null) {
            Class<?> cls = Class.forName("inetsoft.mv.VSMVAnalyzer$TableInfo");
            Constructor<?> ctor = cls.getDeclaredConstructor(String.class);
            ctor.setAccessible(true);
            info = ctor.newInstance(table);
         }

         return info;
      }

      private VSMVAnalyzer analyzer;
      private TransformationDescriptor desc;
   }
}
