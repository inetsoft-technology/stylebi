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
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.ExpressionRef;
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
      for(Assembly assembly : ws.getAssemblies()) {
         for(boolean included : new boolean[] { false, true }) {
            List<Assembly> expected = new ArrayList<>();
            previousAlgorithm(ws, assembly, expected, included);

            assertEquals(orderedNames(expected.toArray(new Assembly[0])),
                         orderedNames(AssetUtil.getDependedAssemblies(ws, assembly, included)),
                         assembly.getName() + " included=" + included);
         }
      }
   }

   private static void previousAlgorithm(AbstractSheet sheet, Assembly assembly,
                                         List<Assembly> assemblies, boolean included)
   {
      if(assemblies.contains(assembly)) {
         return;
      }

      if(included) {
         assemblies.add(assembly);
      }

      for(AssemblyRef ref : sheet.getDependeds(assembly.getAssemblyEntry(), true, false)) {
         if(ref.getType() != AssemblyRef.INPUT_DATA && ref.getType() != AssemblyRef.OUTPUT_DATA) {
            continue;
         }

         Assembly assembly2 = sheet.getAssembly(ref.getEntry());

         if(assembly2 != null) {
            previousAlgorithm(sheet, assembly2, assemblies, true);
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
