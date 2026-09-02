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
package inetsoft.web.composer.model.condition;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.test.*;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.DateCondition;
import inetsoft.uql.asset.DefaultDateRangeAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.wiz.viewsheet.ConditionVocabulary;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link ConditionVocabulary#toConditionList} builds each condition value with the same type
 * discriminator that {@link ConditionUtil#fromModelToConditionList} switches on; if the two ever
 * disagree, the DATE_IN branch below silently falls through instead of failing loud.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class ConditionUtilTest {
   @Test
   void aNamedBuiltinDateRangeFromTheVocabularyBecomesARealDateCondition() throws Exception {
      DataRefModel field = mock(DataRefModel.class);
      when(field.getName()).thenReturn("OrderDate");
      when(field.getDataType()).thenReturn(XSchema.DATE);

      String builtinName = DateCondition.getBuiltinDateConditions()[0].getName();

      Object[] model = ConditionVocabulary.toConditionList(
         List.of(new ConditionVocabulary.Clause(
            "OrderDate", "date_in", List.of(builtinName), null, false)),
         new DataRefModel[]{ field });

      Principal principal = () -> "admin";
      ConditionList conditions =
         ConditionUtil.fromModelToConditionList(model, null, null, principal);

      assertInstanceOf(DateCondition.class, conditions.getXCondition(0),
                        "a date_in value naming a builtin range must resolve to a real " +
                        "DateCondition, not fall through to a plain value condition");
   }

   /**
    * The builtin lookup must not be case-sensitive -- a correctly-spelled name in the wrong
    * case (e.g. an LLM normalizing "Last year" to "Last Year") must still resolve to the same
    * builtin range, not silently fail to match and fall through.
    */
   @Test
   void aBuiltinDateRangeNameInTheWrongCaseStillResolvesToTheSameDateCondition() throws Exception {
      DataRefModel field = mock(DataRefModel.class);
      when(field.getName()).thenReturn("OrderDate");
      when(field.getDataType()).thenReturn(XSchema.DATE);

      String correctlyCasedName = "Last year";
      String wrongCasedName = "Last Year";

      Object[] correctModel = ConditionVocabulary.toConditionList(
         List.of(new ConditionVocabulary.Clause(
            "OrderDate", "date_in", List.of(correctlyCasedName), null, false)),
         new DataRefModel[]{ field });
      Object[] wrongCaseModel = ConditionVocabulary.toConditionList(
         List.of(new ConditionVocabulary.Clause(
            "OrderDate", "date_in", List.of(wrongCasedName), null, false)),
         new DataRefModel[]{ field });

      Principal principal = () -> "admin";
      ConditionList correctConditions =
         ConditionUtil.fromModelToConditionList(correctModel, null, null, principal);
      ConditionList wrongCaseConditions =
         ConditionUtil.fromModelToConditionList(wrongCaseModel, null, null, principal);

      assertEquals(correctConditions.getXCondition(0), wrongCaseConditions.getXCondition(0),
                   "a builtin name in the wrong case must resolve to the same DateCondition " +
                   "as the correctly-cased name");
   }

   /**
    * A value that names no builtin range and no worksheet date-range assembly must fail loud,
    * not silently drop the condition or fall through to a plain value condition.
    */
   @Test
   void anUnrecognizedDateRangeNameThrows() throws Exception {
      DataRefModel field = mock(DataRefModel.class);
      when(field.getName()).thenReturn("OrderDate");
      when(field.getDataType()).thenReturn(XSchema.DATE);

      Object[] model = ConditionVocabulary.toConditionList(
         List.of(new ConditionVocabulary.Clause(
            "OrderDate", "date_in", List.of("Last Decade"), null, false)),
         new DataRefModel[]{ field });

      Principal principal = () -> "admin";

      assertThrows(IllegalArgumentException.class,
                   () -> ConditionUtil.fromModelToConditionList(model, null, null, principal),
                   "an unrecognized date range name must fail loud, not fall through silently");
   }

   /**
    * A worksheet DateRangeAssembly's name match must not be case-sensitive -- an LLM might pass
    * the range's name in the wrong case, and it must still resolve via the case-insensitive
    * fallback scan (only reached once the exact-name lookup above it misses), not fall through
    * to the "unrecognized" failure.
    */
   @Test
   void aWorksheetDateRangeAssemblyNameInTheWrongCaseStillResolves() throws Exception {
      DataRefModel field = mock(DataRefModel.class);
      when(field.getName()).thenReturn("OrderDate");
      when(field.getDataType()).thenReturn(XSchema.DATE);

      Worksheet ws = new Worksheet();
      DateCondition.MonthCondition rangeCondition = new DateCondition.MonthCondition(2, 0);
      DefaultDateRangeAssembly range = new DefaultDateRangeAssembly(ws, "MyRange");
      range.setDateRange(rangeCondition);
      ws.addAssembly(range);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      Object[] model = ConditionVocabulary.toConditionList(
         List.of(new ConditionVocabulary.Clause(
            "OrderDate", "date_in", List.of("myrange"), null, false)),
         new DataRefModel[]{ field });

      Principal principal = () -> "admin";
      ConditionList conditions =
         ConditionUtil.fromModelToConditionList(model, null, null, principal, rws);

      assertInstanceOf(DateCondition.MonthCondition.class, conditions.getXCondition(0),
                        "a wrong-case worksheet DateRangeAssembly name must still resolve to " +
                        "the assembly's actual DateCondition, not fall through unresolved");
      assertEquals(2, ((DateCondition.MonthCondition) conditions.getXCondition(0)).getMonthN());
   }

   /**
    * When two worksheet DateRangeAssemblies differ only by case, an exact-name match must win
    * over the case-insensitive fallback scan -- otherwise which one resolves depends on
    * {@code Worksheet#getAssemblies()} iteration order, which callers cannot rely on. This is
    * the determinism guarantee the exact-match-first ordering in the fix exists to provide.
    */
   @Test
   void anExactCaseMatchIsPreferredOverACaseInsensitiveScanWhenBothExist() throws Exception {
      DataRefModel field = mock(DataRefModel.class);
      when(field.getName()).thenReturn("OrderDate");
      when(field.getDataType()).thenReturn(XSchema.DATE);

      Worksheet ws = new Worksheet();

      DateCondition.MonthCondition exactRange = new DateCondition.MonthCondition(2, 0);
      DefaultDateRangeAssembly exact = new DefaultDateRangeAssembly(ws, "MyRange");
      exact.setDateRange(exactRange);
      ws.addAssembly(exact);

      DateCondition.MonthCondition otherCaseRange = new DateCondition.MonthCondition(5, 1);
      DefaultDateRangeAssembly otherCase = new DefaultDateRangeAssembly(ws, "MYRANGE");
      otherCase.setDateRange(otherCaseRange);
      ws.addAssembly(otherCase);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      Object[] model = ConditionVocabulary.toConditionList(
         List.of(new ConditionVocabulary.Clause(
            "OrderDate", "date_in", List.of("MyRange"), null, false)),
         new DataRefModel[]{ field });

      Principal principal = () -> "admin";
      ConditionList conditions =
         ConditionUtil.fromModelToConditionList(model, null, null, principal, rws);

      DateCondition.MonthCondition resolved =
         (DateCondition.MonthCondition) conditions.getXCondition(0);
      assertEquals(0, resolved.getYearN());
      assertEquals(2, resolved.getMonthN(),
         "an exact-name match must be used, not whichever case-insensitive candidate the scan " +
         "happens to hit first");
   }
}
