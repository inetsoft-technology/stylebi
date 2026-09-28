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
package inetsoft.uql.asset.internal;

import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the worksheet dependency-cycle check (found during Testing #77123).
 * {@link AssetUtil#getDependedAssemblies(AbstractSheet, Assembly, boolean)} with
 * {@code included=false} must still reach the root through a cycle, otherwise
 * {@link Assembly#checkDependency()} can never report "Dependency cycle found".
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AssetUtilDependencyCycleTest {
   @Test
   void expressionCycleIsRejected() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t1 = new EmbeddedTableAssembly(ws, "T1");
      EmbeddedTableAssembly t2 = new EmbeddedTableAssembly(ws, "T2");
      ws.addAssembly(t1);
      ws.addAssembly(t2);
      addExpression(t1, "f", "T2['c'][0]");
      addExpression(t2, "g", "T1['d'][0]");

      assertThrows(InvalidDependencyException.class, t1::checkDependency);
      assertThrows(InvalidDependencyException.class, ws::checkDependencies);
      assertSameAsPreviousAlgorithm(ws);
   }

   @Test
   void mirrorCycleIsRejected() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t1 = new EmbeddedTableAssembly(ws, "T1");
      ws.addAssembly(t1);
      MirrorTableAssembly m = new MirrorTableAssembly(ws, "M", t1);
      ws.addAssembly(m);
      addExpression(t1, "f", "M['c'][0]");

      assertThrows(InvalidDependencyException.class, m::checkDependency);
      assertThrows(InvalidDependencyException.class, ws::checkDependencies);
      assertSameAsPreviousAlgorithm(ws);
   }

   @Test
   void threeCycleIsRejected() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly x = new EmbeddedTableAssembly(ws, "X");
      ws.addAssembly(x);
      MirrorTableAssembly y = new MirrorTableAssembly(ws, "Y", x);
      ws.addAssembly(y);
      MirrorTableAssembly z = new MirrorTableAssembly(ws, "Z", y);
      ws.addAssembly(z);
      addExpression(x, "f", "Z['c'][0]");

      assertThrows(InvalidDependencyException.class, x::checkDependency);
      assertThrows(InvalidDependencyException.class, y::checkDependency);
      assertThrows(InvalidDependencyException.class, z::checkDependency);
      assertThrows(InvalidDependencyException.class, ws::checkDependencies);
      assertSameAsPreviousAlgorithm(ws);
   }

   @Test
   void acyclicWorksheetIsAcceptedAndOutputUnchanged() throws Exception {
      // A <- M1(A), M2(A); C expression reads M1 and M2; D mirrors C
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = new EmbeddedTableAssembly(ws, "A");
      ws.addAssembly(a);
      MirrorTableAssembly m1 = new MirrorTableAssembly(ws, "M1", a);
      ws.addAssembly(m1);
      MirrorTableAssembly m2 = new MirrorTableAssembly(ws, "M2", a);
      ws.addAssembly(m2);
      EmbeddedTableAssembly c = new EmbeddedTableAssembly(ws, "C");
      ws.addAssembly(c);
      addExpression(c, "f", "M1['x'][0] + M2['y'][0]");
      MirrorTableAssembly d = new MirrorTableAssembly(ws, "D", c);
      ws.addAssembly(d);

      ws.checkDependencies();
      assertSameAsPreviousAlgorithm(ws);

      assertEquals(Set.of("C", "M1", "M2", "A"),
                   names(AssetUtil.getDependedAssemblies(ws, d, false)));
      assertEquals(Set.of("D", "C", "M1", "M2", "A"),
                   names(AssetUtil.getDependedAssemblies(ws, d, true)));
      assertEquals(0, AssetUtil.getDependedAssemblies(ws, a, false).length);
      // A is reached through both M1 and M2 but listed once
      assertEquals(5, AssetUtil.getDependedAssemblies(ws, d, true).length);
   }

   @Test
   void realisticAcyclicWorksheetIsAcceptedAndOutputUnchanged() throws Exception {
      // A, B base tables; J = A inner join B; MB mirrors B; U = A union MB; V variable;
      // C mirrors J with a sub-query condition on B and a variable condition on V;
      // E expression reads U and C; F mirrors E with a post condition on V
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = new EmbeddedTableAssembly(ws, "A");
      ws.addAssembly(a);
      EmbeddedTableAssembly b = new EmbeddedTableAssembly(ws, "B");
      ws.addAssembly(b);

      TableAssemblyOperator join = new TableAssemblyOperator();
      TableAssemblyOperator.Operator jop = new TableAssemblyOperator.Operator();
      jop.setOperation(TableAssemblyOperator.INNER_JOIN);
      jop.setLeftTable("A");
      jop.setRightTable("B");
      jop.setLeftAttribute(column("id"));
      jop.setRightAttribute(column("id"));
      join.addOperator(jop);
      RelationalJoinTableAssembly j = new RelationalJoinTableAssembly(
         ws, "J", new TableAssembly[] { a, b }, new TableAssemblyOperator[] { join });
      ws.addAssembly(j);

      MirrorTableAssembly mb = new MirrorTableAssembly(ws, "MB", b);
      ws.addAssembly(mb);
      TableAssemblyOperator union = new TableAssemblyOperator();
      TableAssemblyOperator.Operator uop = new TableAssemblyOperator.Operator();
      uop.setOperation(TableAssemblyOperator.UNION);
      uop.setLeftTable("A");
      uop.setRightTable("MB");
      union.addOperator(uop);
      ConcatenatedTableAssembly u = new ConcatenatedTableAssembly(
         ws, "U", new TableAssembly[] { a, mb }, new TableAssemblyOperator[] { union });
      ws.addAssembly(u);

      DefaultVariableAssembly v = new DefaultVariableAssembly(ws, "V");
      v.setVariable(new AssetVariable("v"));
      ws.addAssembly(v);

      MirrorTableAssembly c = new MirrorTableAssembly(ws, "C", j);
      ws.addAssembly(c);
      SubQueryValue sub = new SubQueryValue();
      sub.setQuery("B");
      AssetCondition subCond = new AssetCondition();
      subCond.setOperation(XCondition.ONE_OF);
      subCond.setType(XSchema.INTEGER);
      subCond.addValue(sub);
      AssetCondition varCond = new AssetCondition();
      varCond.setOperation(XCondition.EQUAL_TO);
      varCond.setType(XSchema.INTEGER);
      varCond.addValue(new UserVariable("v"));
      ConditionList pre = new ConditionList();
      pre.append(new ConditionItem(column("id"), subCond, 0));
      pre.append(new JunctionOperator(JunctionOperator.AND, 0));
      pre.append(new ConditionItem(column("x"), varCond, 0));
      c.setPreConditionList(pre);

      EmbeddedTableAssembly e = new EmbeddedTableAssembly(ws, "E");
      ws.addAssembly(e);
      addExpression(e, "f", "U['x'][0] + C['y'][0]");
      MirrorTableAssembly f = new MirrorTableAssembly(ws, "F", e);
      ws.addAssembly(f);
      ConditionList post = new ConditionList();
      post.append(new ConditionItem(column("f"), varCond, 0));
      f.setPostConditionList(post);

      ws.checkDependencies();

      for(Assembly assembly : ws.getAssemblies()) {
         assembly.checkDependency();
      }

      assertSameAsPreviousAlgorithm(ws);
      assertEquals(Set.of("E", "U", "A", "MB", "B", "C", "J", "V"),
                   names(AssetUtil.getDependedAssemblies(ws, f, false)));
      assertEquals(Set.of("J", "A", "B", "V"),
                   names(AssetUtil.getDependedAssemblies(ws, c, false)));
   }

   @Test
   void viewsheetCycleIsRejected() {
      Viewsheet vs = new Viewsheet();
      TextVSAssembly t1 = new TextVSAssembly(vs, "Text1");
      vs.addAssembly(t1);
      TextVSAssembly t2 = new TextVSAssembly(vs, "Text2");
      vs.addAssembly(t2);
      t1.setTextValue("$(Text2)");
      t2.setTextValue("$(Text1)");

      assertThrows(InvalidDependencyException.class, t1::checkDependency);
      assertThrows(InvalidDependencyException.class, t2::checkDependency);
      assertThrows(InvalidDependencyException.class, vs::checkDependencies);
      assertSameAsPreviousAlgorithm(vs, false, true);
   }

   @Test
   void acyclicViewsheetIsAccepted() throws Exception {
      Viewsheet vs = new Viewsheet();
      TextVSAssembly t1 = new TextVSAssembly(vs, "Text1");
      vs.addAssembly(t1);
      TextVSAssembly t2 = new TextVSAssembly(vs, "Text2");
      vs.addAssembly(t2);
      TextVSAssembly t3 = new TextVSAssembly(vs, "Text3");
      vs.addAssembly(t3);
      t2.setTextValue("$(Text1)");
      t3.setTextValue("$(Text2)");

      t1.checkDependency();
      t2.checkDependency();
      t3.checkDependency();
      vs.checkDependencies();
      assertSameAsPreviousAlgorithm(vs, false, true);
      assertEquals(List.of("Text2", "Text1"), orderedNames(
         AssetUtil.getDependedAssemblies(vs, t3, false, false, true)));
   }

   private static ColumnRef column(String name) {
      return new ColumnRef(new AttributeRef(null, name));
   }

   private static void addExpression(TableAssembly table, String name, String script) {
      ExpressionRef ref = new ExpressionRef(null, name);
      ref.setExpression(script);
      ColumnRef col = new ColumnRef(ref);
      col.setSQL(false);
      ColumnSelection cols = table.getColumnSelection();
      cols.addAttribute(col);
      table.setColumnSelection(cols);
   }

   /**
    * Compares the output, including order, with the algorithm used before the HashSet change
    * (community #3596/#3763), for every assembly and both values of {@code included}.
    */
   private static void assertSameAsPreviousAlgorithm(Worksheet ws) {
      assertSameAsPreviousAlgorithm(ws, true, false);
   }

   private static void assertSameAsPreviousAlgorithm(AbstractSheet sheet, boolean view, boolean out) {
      for(Assembly assembly : sheet.getAssemblies()) {
         for(boolean included : new boolean[] { false, true }) {
            List<Assembly> expected = new ArrayList<>();
            previousAlgorithm(sheet, assembly, expected, included, view, out);

            assertEquals(orderedNames(expected.toArray(new Assembly[0])),
                         orderedNames(AssetUtil.getDependedAssemblies(
                            sheet, assembly, included, view, out)),
                         assembly.getName() + " included=" + included);
         }
      }
   }

   private static void previousAlgorithm(AbstractSheet sheet, Assembly assembly,
                                         List<Assembly> assemblies, boolean included,
                                         boolean view, boolean out)
   {
      if(assemblies.contains(assembly)) {
         return;
      }

      if(included) {
         assemblies.add(assembly);
      }

      for(AssemblyRef ref : sheet.getDependeds(assembly.getAssemblyEntry(), view, out)) {
         if(ref.getType() != AssemblyRef.INPUT_DATA && ref.getType() != AssemblyRef.OUTPUT_DATA) {
            continue;
         }

         Assembly assembly2 = sheet.getAssembly(ref.getEntry());

         if(assembly2 != null) {
            previousAlgorithm(sheet, assembly2, assemblies, true, view, out);
         }
      }
   }

   private static Set<String> names(Assembly[] arr) {
      Set<String> names = new HashSet<>();

      for(Assembly assembly : arr) {
         names.add(assembly.getName());
      }

      return names;
   }

   private static List<String> orderedNames(Assembly[] arr) {
      List<String> names = new ArrayList<>();

      for(Assembly assembly : arr) {
         names.add(assembly.getName());
      }

      return names;
   }
}
