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
package inetsoft.web.wiz.binding;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.Tool;
import inetsoft.web.binding.model.BDimensionRefModel;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.NamedGroupInfoModel;
import inetsoft.web.binding.model.graph.ChartDimensionRefModel;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.wiz.binding.model.FieldRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Bug #78862: {@code namedGroupValues} accepted the same value in two groups. The render puts it
 * in the first group only ({@code SortOrder.findGroup} is first-match), while the read-back
 * reported it in both. The write now refuses a value that a second group also matches, using the
 * render's own equality (column type, case setting, {@code ""} = null, {@code "null"} literal).
 * <p>
 * Spring context: the check builds real {@code Condition}s, which read
 * {@code string.compare.casesensitive} through {@code SreeEnv}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class InlineNamedGroupOverlapTest {
   private static final String STATE = "Customer:State";

   private static FieldRef.NamedGroupValues groups(Object[]... groups) {
      List<FieldRef.NamedGroupValues.Clause> clauses = new ArrayList<>();

      for(int i = 0; i < groups.length; i++) {
         clauses.add(new FieldRef.NamedGroupValues.Clause(
            "Group" + (char) ('A' + i), Arrays.asList(groups[i])));
      }

      return new FieldRef.NamedGroupValues(clauses, null);
   }

   private static FieldRef field(String column, FieldRef.NamedGroupValues values) {
      return new FieldRef(column, "dimension", null, null, null, null, null, values);
   }

   private static List<BindingModel.SourceTable> tables(String column, String dataType) {
      BindingModel.SourceTable table = new BindingModel.SourceTable();
      table.setColumns(List.of(new BindingModel.SourceTableColumn(column, dataType)));
      return List.of(table);
   }

   private static CrosstabBindingModel crosstab(String column, String dataType) {
      CrosstabBindingModel model = new CrosstabBindingModel();
      model.setTables(tables(column, dataType));
      return model;
   }

   private static ChartBindingModel chart(String column, String dataType) {
      ChartBindingModel model = new ChartBindingModel();

      if(dataType != null) {
         model.setTables(tables(column, dataType));
      }

      return model;
   }

   /** Writes the group on a crosstab's rows; returns the refusal, or null if accepted. */
   private static IllegalArgumentException crosstabWrite(String column, String dataType,
                                                         FieldRef.NamedGroupValues values)
   {
      CrosstabBindingModel model = crosstab(column, dataType);

      try {
         TableBindingMutator.setShelf(model, "rows", List.of(field(column, values)));
      }
      catch(IllegalArgumentException e) {
         assertTrue(model.getRows().isEmpty(), "a refused write must store nothing");
         return e;
      }

      assertEquals(1, model.getRows().size());
      return null;
   }

   /** Writes the group on a chart's x shelf; returns the refusal, or null if accepted. */
   private static IllegalArgumentException chartWrite(String column, String dataType,
                                                      FieldRef.NamedGroupValues values)
   {
      ChartBindingModel model = chart(column, dataType);

      try {
         ChartBindingMutator.setShelf(model, "x", List.of(field(column, values)));
      }
      catch(IllegalArgumentException e) {
         assertTrue(model.getXFields().isEmpty(), "a refused write must store nothing");
         return e;
      }

      assertEquals(1, model.getXFields().size());
      return null;
   }

   private static void assertNamesBoth(IllegalArgumentException e, String value, String first,
                                       String second)
   {
      assertNotNull(e, "the overlap must be refused");
      String msg = e.getMessage();
      assertTrue(msg.contains("'namedGroupValues'"), msg);
      assertTrue(msg.contains("value " + value), msg);
      assertTrue(msg.contains("group '" + first + "'"), msg);
      assertTrue(msg.contains("group '" + second + "'"), msg);
      assertTrue(msg.contains("only one group"), msg);
      assertTrue(msg.contains("Remove " + value + " from group '" + second + "'"), msg);
      assertTrue(msg.contains("leaves the rendered grouping exactly as it is now"), msg);
   }

   @Test
   void aValueListedInTwoGroupsIsRefusedNamingTheValueAndBothGroups() {
      IllegalArgumentException e = crosstabWrite(STATE, "string",
         groups(new Object[]{ "CA", "TX" }, new Object[]{ "TX", "NY" }));
      assertNamesBoth(e, "'TX'", "GroupA", "GroupB");
      assertTrue(e.getMessage().contains(STATE), e.getMessage());

      // the same input on a chart dimension (and an all-equality worksheet group echoed back
      // from a chart read has exactly this shape) is refused with the same remedy
      assertNamesBoth(chartWrite(STATE, "string",
         groups(new Object[]{ "CA", "TX" }, new Object[]{ "TX", "NY" })),
         "'TX'", "GroupA", "GroupB");
   }

   @Test
   void theLaterGroupIsNamedWhenTheOverlapSkipsAGroup() {
      assertNamesBoth(crosstabWrite(STATE, "string",
         groups(new Object[]{ "CA" }, new Object[]{ "NY" }, new Object[]{ "WA", "CA" })),
         "'CA'", "GroupA", "GroupC");
   }

   @Test
   void valuesThatDifferOnlyInCaseCollideWhenStringComparisonIsCaseInsensitive() {
      assumeFalse(Tool.isCaseSensitive(), "string.compare.casesensitive is false by default");
      IllegalArgumentException e = crosstabWrite(STATE, "string",
         groups(new Object[]{ "TX" }, new Object[]{ "tx" }));
      assertNamesBoth(e, "'tx'", "GroupA", "GroupB");
      assertTrue(e.getMessage().contains("lists 'TX', which matches the same rows"),
                 e.getMessage());
   }

   @Test
   void numericSpellingsOfOneNumberCollideOnANumericColumn() {
      assertNamesBoth(crosstabWrite("Quantity", "integer",
         groups(new Object[]{ "1" }, new Object[]{ "01" })), "'01'", "GroupA", "GroupB");
      assertNamesBoth(chartWrite("Quantity", "integer",
         groups(new Object[]{ 1 }, new Object[]{ "1.0" })), "'1.0'", "GroupA", "GroupB");
      assertNamesBoth(crosstabWrite("Price", "double",
         groups(new Object[]{ "1.5" }, new Object[]{ "1.50" })), "'1.50'", "GroupA", "GroupB");
      // on a string column they are different text, so both groups stay live
      assertNull(crosstabWrite(STATE, "string",
         groups(new Object[]{ "1" }, new Object[]{ "01" })));
      // ...but a JSON number and its own text are the same value even there
      assertNamesBoth(crosstabWrite(STATE, "string",
         groups(new Object[]{ 1 }, new Object[]{ "1" })), "'1'", "GroupA", "GroupB");
   }

   @Test
   void emptyAndNullAreTheSameValueButTheTextNullIsNot() {
      assertNamesBoth(crosstabWrite(STATE, "string",
         groups(new Object[]{ null }, new Object[]{ "" })), "''", "GroupA", "GroupB");
      assertNull(crosstabWrite(STATE, "string",
         groups(new Object[]{ null }, new Object[]{ "null" })));
      assertNull(crosstabWrite(STATE, "string",
         groups(new Object[]{ "" }, new Object[]{ "NULL" })));
   }

   @Test
   void distinctValuesAreAccepted() {
      CrosstabBindingModel model = crosstab(STATE, "string");
      TableBindingMutator.setShelf(model, "rows", List.of(field(STATE,
         groups(new Object[]{ "CA", "OR" }, new Object[]{ "NY", "NJ" }))));
      NamedGroupInfoModel stored = ((BDimensionRefModel) model.getRows().get(0))
         .getNamedGroupInfo();
      assertEquals(2, stored.getGroups().size());

      // whitespace is significant to EQUAL_TO, so ' TX' is a different value
      assertNull(crosstabWrite(STATE, "string",
         groups(new Object[]{ "TX" }, new Object[]{ " TX" })));
      assertNull(chartWrite(STATE, "string",
         groups(new Object[]{ "CA", "OR" }, new Object[]{ "NY", "NJ" })));
   }

   @Test
   void aSingleGroupIsAcceptedEvenWithARepeatedValue() {
      ChartBindingModel model = chart(STATE, "string");
      ChartBindingMutator.setShelf(model, "x", List.of(field(STATE,
         groups(new Object[]{ "CA", "CA", "OR" }))));
      assertNotNull(((ChartDimensionRefModel) model.getXFields().get(0)).getNamedGroupInfo());
   }

   @Test
   void anUnknownColumnTypeComparesTheValuesAsText() {
      // no source table, so the type is unknown: case still collides, numeric spellings don't
      assumeFalse(Tool.isCaseSensitive(), "string.compare.casesensitive is false by default");
      assertNamesBoth(chartWrite(STATE, null,
         groups(new Object[]{ "TX" }, new Object[]{ "tx" })), "'tx'", "GroupA", "GroupB");
      assertNull(chartWrite("Quantity", null,
         groups(new Object[]{ "1" }, new Object[]{ "01" })));
   }
}
