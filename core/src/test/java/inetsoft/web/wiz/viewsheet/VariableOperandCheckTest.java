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
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.composer.model.condition.ConditionModel;
import inetsoft.web.composer.model.condition.ConditionValueModel;
import inetsoft.web.composer.model.condition.ExpressionValueModel;
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

   private static Object[] expressionConditions(String script) {
      DataRefModel field = mock(DataRefModel.class);
      when(field.getName()).thenReturn("Order:Num");
      ExpressionValueModel expression = new ExpressionValueModel();
      expression.setExpression(script);
      ConditionValueModel value = new ConditionValueModel();
      value.setType(ConditionValueModel.EXPRESSION);
      value.setValue(expression);
      ConditionModel condition = new ConditionModel();
      condition.setField(field);
      condition.setOperation(XCondition.GREATER_THAN);
      condition.setValues(new ConditionValueModel[]{ value });
      return new Object[]{ condition };
   }

   private static List<String> runExpression(String script, Set<String> declared) {
      List<String> warnings = new ArrayList<>();
      VariableOperandCheck.check(expressionConditions(script), new VariableTable(), declared,
                                 false, warnings);
      return warnings;
   }

   @Test
   void expressionOperandReadingADeclaredVariableIsNotReportedAsNotDefined() {
      List<String> warnings = runExpression("parameter.cutoff * 2", Set.of("cutoff"));

      assertEquals(1, warnings.size(), warnings.toString());
      assertTrue(warnings.get(0).startsWith("Variable 'cutoff' used by"), warnings.get(0));
      assertFalse(warnings.get(0).contains("not defined"), warnings.get(0));
      assertTrue(warnings.get(0).contains("no value yet"), warnings.get(0));
      assertEquals(warnings, runExpression("parameter['cutoff'] * 2", Set.of("cutoff")));
   }

   @Test
   void expressionOperandReadingAnUndeclaredVariableStillSaysNotDefined() {
      List<String> warnings = runExpression("parameter.GHOST + 1", Set.of("cutoff"));

      assertEquals(1, warnings.size(), warnings.toString());
      assertTrue(warnings.get(0).contains("'GHOST' used by"), warnings.get(0));
      assertTrue(warnings.get(0).contains("is not defined"), warnings.get(0));
   }

   @Test
   void expressionOperandIgnoresBuiltInsLiteralsCommentsAndValuedNames() {
      assertTrue(runExpression("parameter._USER_ == 'x' ? parameter._TODAY : 0",
                               Set.of()).isEmpty());
      assertTrue(runExpression("'parameter.ghost' + \"parameter.ghost2\" // parameter.ghost3\n",
                               Set.of()).isEmpty());
      assertTrue(runExpression("/* parameter.ghost */ 1", Set.of()).isEmpty());
      assertTrue(runExpression("myparameter.ghost + x.parameter.ghost", Set.of()).isEmpty());

      VariableTable vars = new VariableTable();
      vars.put("cutoff", 5);
      List<String> warnings = new ArrayList<>();
      VariableOperandCheck.check(expressionConditions("parameter.cutoff"), vars, Set.of(), false,
                                 warnings);
      assertTrue(warnings.isEmpty(), warnings.toString());
   }

   @Test
   void declaredNamesIncludesVariablesReferencedOnlyByViewsheetConditions() {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAllVariables()).thenReturn(
         new UserVariable[]{ new UserVariable("vsOnlyVar"), new UserVariable("offVar") });
      when(vs.getDisabledVariables()).thenReturn(Set.of("offVar"));
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getViewsheet()).thenReturn(vs);

      Set<String> declared = VariableOperandCheck.declaredNames(box);

      assertTrue(declared.contains("vsOnlyVar"), declared.toString());
      assertFalse(declared.contains("offVar"), declared.toString());
      assertFalse(declared.contains("ghost"), declared.toString());
      assertFalse(run("vsOnlyVar", new VariableTable(), declared).get(0).contains("not defined"));
      assertTrue(run("ghost", new VariableTable(), declared).get(0).contains("not defined"));
   }

   @Test
   void declaredNamesSurvivesAViewsheetThatCannotListItsVariables() {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAllVariables()).thenThrow(new RuntimeException("boom"));
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getViewsheet()).thenReturn(vs);

      assertTrue(VariableOperandCheck.declaredNames(box).isEmpty());
   }

   @Test
   void noValueNotesSayTheConditionIsIgnoredAndMatchesEveryRow() {
      for(String note : List.of(run("cutoff", new VariableTable(), Set.of("cutoff")).get(0),
                                run("ghost", new VariableTable(), Set.of("cutoff")).get(0),
                                runExpression("parameter.cutoff", Set.of("cutoff")).get(0)))
      {
         assertTrue(note.contains("ignores this condition"), note);
         assertTrue(note.contains("matches every row"), note);
         assertFalse(note.contains("nothing to compare against"), note);
      }
   }

   @Test
   void emptyValuedVariableDoesNotOverClaimMatchingEveryRow() {
      VariableTable vars = new VariableTable();
      vars.put("cutoff", new Object[0]);
      List<String> warnings = run("cutoff", vars, Set.of());

      assertEquals(1, warnings.size(), warnings.toString());
      assertTrue(warnings.get(0).contains("no value yet"), warnings.get(0));
      assertFalse(warnings.get(0).contains("every row"), warnings.get(0));
   }
}
