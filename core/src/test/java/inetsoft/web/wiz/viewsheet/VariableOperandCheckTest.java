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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.ConditionList;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.asset.AssetVariable;
import inetsoft.uql.asset.BoundTableAssembly;
import inetsoft.uql.asset.DefaultVariableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.binding.drm.DataRefModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80214: a variable declared on the base worksheet but holding no value was reported as
 * "not defined". The variable sources are: declared on the base worksheet, valued (session or
 * input assembly), built in, and undeclared.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VariableOperandCheckTest {
   private static Object[] conditions(String variable) {
      DataRefModel field = mock(DataRefModel.class);
      when(field.getName()).thenReturn("Order:Num");
      when(field.getDataType()).thenReturn("integer");
      ConditionVocabulary.Clause clause = new ConditionVocabulary.Clause(
         "Order:Num", "greater_than", List.of(Map.of("type", "variable", "name", variable)),
         null, false, false, 0);
      return ConditionVocabulary.toConditionList(List.of(clause), new DataRefModel[]{ field });
   }

   private static List<String> run(String variable, VariableTable vars, Set<String> declared) {
      List<String> warnings = new ArrayList<>();
      VariableOperandCheck.check(conditions(variable), vars, declared, false, warnings);
      return warnings;
   }

   @Test
   void declaredButUnvaluedIsNotReportedAsNotDefined() {
      List<String> warnings = run("cutoff", new VariableTable(), Set.of("cutoff"));

      assertEquals(1, warnings.size(), warnings.toString());
      assertFalse(warnings.get(0).contains("not defined"), warnings.get(0));
      assertTrue(warnings.get(0).contains("declared on the base worksheet"), warnings.get(0));
      assertTrue(warnings.get(0).contains("no value yet"), warnings.get(0));
   }

   @Test
   void undeclaredStillSaysNotDefined() {
      List<String> warnings = run("ghost", new VariableTable(), Set.of("cutoff"));

      assertEquals(1, warnings.size(), warnings.toString());
      assertTrue(warnings.get(0).contains("not defined"), warnings.get(0));
   }

   @Test
   void nullDeclaredBehavesAsEmpty() {
      List<String> warnings = run("cutoff", new VariableTable(), null);

      assertTrue(warnings.get(0).contains("not defined"), warnings.get(0));
   }

   @Test
   void valuedVariableIsNotWarnedAbout() {
      VariableTable vars = new VariableTable();
      vars.put("cutoff", 5);

      assertTrue(run("cutoff", vars, Set.of("cutoff")).isEmpty());
   }

   @Test
   void builtinVariableIsNeverReportedAsNotDefined() {
      for(String warning : run("_USER_", new VariableTable(), Set.of())) {
         assertFalse(warning.contains("not defined"), warning);
      }
   }

   @Test
   void declaredNamesReadsTheBaseWorksheetsVariableAssemblies() {
      Worksheet ws = new Worksheet();
      DefaultVariableAssembly assembly = new DefaultVariableAssembly(ws, "cutoffAssembly");
      assembly.setVariable(new AssetVariable("cutoff"));
      ws.addAssembly(assembly);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getWorksheet()).thenReturn(ws);

      assertEquals(Set.of("cutoff"), VariableOperandCheck.declaredNames(box));
   }

   @Test
   void declaredNamesIsEmptyWithoutABaseWorksheetOrSandbox() {
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);

      assertTrue(VariableOperandCheck.declaredNames(box).isEmpty());
      assertTrue(VariableOperandCheck.declaredNames(null).isEmpty());

      when(box.getWorksheet()).thenThrow(new IllegalStateException("expired"));
      assertTrue(VariableOperandCheck.declaredNames(box).isEmpty());
   }

   private static Worksheet conditionOnlyWorksheet(String variable) {
      Worksheet ws = new Worksheet();
      BoundTableAssembly table = new BoundTableAssembly(ws, "T1");
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.GREATER_THAN);
      cond.setType(XSchema.INTEGER);
      cond.addValue(new UserVariable(variable));
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new AttributeRef(null, "REVENUE"), cond, 0));
      table.setPreConditionList(list);
      ws.addAssembly(table);
      return ws;
   }

   @Test
   void declaredNamesIncludesVariablesReferencedOnlyByABaseWorksheetCondition() {
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getWorksheet()).thenReturn(conditionOnlyWorksheet("condOnlyVar"));

      Set<String> declared = VariableOperandCheck.declaredNames(box);

      assertTrue(declared.contains("condOnlyVar"), declared.toString());
      assertFalse(declared.contains("ghost"), declared.toString());
      List<String> warnings = run("condOnlyVar", new VariableTable(), declared);
      assertFalse(warnings.get(0).contains("not defined"), warnings.get(0));
      assertTrue(warnings.get(0).contains("no value yet"), warnings.get(0));
      assertTrue(run("ghost", new VariableTable(), declared).get(0).contains("not defined"));
   }

   @Test
   void declaredNamesKeepsVariableAssembliesWhenConditionVariablesCannotBeRead() {
      Worksheet ws = spy(conditionOnlyWorksheet("condOnlyVar"));
      DefaultVariableAssembly assembly = new DefaultVariableAssembly(ws, "cutoffAssembly");
      assembly.setVariable(new AssetVariable("cutoff"));
      ws.addAssembly(assembly);
      doThrow(new RuntimeException("Unsupported method called!")).when(ws).getAllVariables();
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getWorksheet()).thenReturn(ws);

      assertEquals(Set.of("cutoff"), VariableOperandCheck.declaredNames(box));
   }
}
