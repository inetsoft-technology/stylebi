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
package inetsoft.report.io.viewsheet;

import inetsoft.report.composition.VSTableLens;
import inetsoft.report.internal.XNodeMetaTable;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * A viewsheet table whose live query fails is swallowed into a design-time sample row (e.g.
 * ORDER_ID 999 and the current timestamp). The wiz export must fail on it, for every table
 * assembly type and through the VSTableLens wrapper the exporter really reads, instead of
 * writing the fabricated row as data (bug #80075).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AbstractVSExporterFailedQueryTest {
   private static final String CAUSE = "inconsistent datatypes: expected DATE got NUMBER";

   private static VSTableLens failedLens() {
      DefaultTableLens base = new DefaultTableLens(2, 1);
      base.setProperty(XNodeMetaTable.FAILED_QUERY_PROPERTY, CAUSE);
      return new VSTableLens(base);
   }

   private static void check(AbstractVSExporter exporter, VSAssembly assembly, VSTableLens lens)
      throws Throwable
   {
      Method m = AbstractVSExporter.class.getDeclaredMethod(
         "checkFailedQuery", VSAssembly.class, VSTableLens.class);
      m.setAccessible(true);

      try {
         m.invoke(exporter, assembly, lens);
      }
      catch(InvocationTargetException e) {
         throw e.getCause();
      }
   }

   private static AbstractVSExporter exporter(boolean failOnFailedQuery) {
      AbstractVSExporter exporter = mock(AbstractVSExporter.class, CALLS_REAL_METHODS);
      exporter.setFailOnFailedQuery(failOnFailedQuery);
      return exporter;
   }

   @Test
   void everyTableAssemblyTypeFailsTheExportAndNamesItself() {
      for(Class<? extends VSAssembly> type : List.of(
         TableVSAssembly.class, CrosstabVSAssembly.class, CalcTableVSAssembly.class))
      {
         VSAssembly assembly = mock(type);
         when(assembly.getAbsoluteName()).thenReturn("Orders1");

         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> check(exporter(true), assembly, failedLens()), type.getSimpleName());
         assertTrue(e.getMessage().contains("Orders1"), e.getMessage());
         assertTrue(e.getMessage().contains(CAUSE), e.getMessage());
      }
   }

   @Test
   void theMessageNamesTheConditionFieldAndTheVariableOperand() {
      Condition condition = new Condition("date");
      condition.setOperation(XCondition.GREATER_THAN);
      condition.addValue(new UserVariable("LateSlider"));
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new AttributeRef("ORDER_DATE"), condition, 0));

      TableVSAssembly table = mock(TableVSAssembly.class);
      when(table.getAbsoluteName()).thenReturn("Orders1");
      when(table.getPreConditionList()).thenReturn(list);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> check(exporter(true), table, failedLens()));
      assertTrue(e.getMessage().contains("LateSlider"), e.getMessage());
      assertTrue(e.getMessage().contains("ORDER_DATE"), e.getMessage());
   }

   @Test
   void aTableThatRenderedRealDataExportsNormally() {
      VSAssembly table = mock(TableVSAssembly.class);
      when(table.getAbsoluteName()).thenReturn("Orders1");
      assertDoesNotThrow(() -> check(exporter(true), table,
                                     new VSTableLens(new DefaultTableLens(2, 1))));
   }

   @Test
   void theGuardIsOptInSoTheNativeExportIsUnchanged() throws Throwable {
      VSAssembly table = mock(TableVSAssembly.class);
      when(table.getAbsoluteName()).thenReturn("Orders1");
      // no exception: the native (non-wiz) export keeps its existing behaviour
      check(exporter(false), table, failedLens());
   }

   @Test
   void aTableWithNoLensIsLeftToTheExistingEmptyFileHandling() throws Throwable {
      VSAssembly table = mock(TableVSAssembly.class);
      check(exporter(true), table, null);
   }
}
