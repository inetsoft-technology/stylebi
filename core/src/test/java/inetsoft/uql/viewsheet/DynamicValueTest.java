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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DynamicValue: a design time string (literal, {@code $(component)} or {@code =script}) and the
 * runtime value that the sandbox fills in.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DynamicValueTest {
   @Nested
   class RuntimeValueOfString {
      @Test
      void literal_isItsOwnRuntimeValue() {
         DynamicValue value = new DynamicValue("abc");

         assertEquals("abc", value.getRValue());
         assertEquals("abc", value.getRuntimeValue(true));
      }

      @Test
      void variableOrScript_hasNoRuntimeValueUntilSet() {
         assertNull(new DynamicValue("$(x)").getRValue());
         assertNull(new DynamicValue("=x").getRValue());
         assertNull(new DynamicValue("=x").getRuntimeValue(true));
      }

      @Test
      void setRValue_isTheRuntimeValue() {
         DynamicValue value = new DynamicValue("=x");
         value.setRValue("v");

         assertEquals("v", value.getRuntimeValue(true));
      }

      @Test
      void changingTheRuntimeValue_refreshesTheCachedObject() {
         DynamicValue value = new DynamicValue("=x");
         value.setRValue("v1");
         assertEquals("v1", value.getRuntimeValue(true));

         value.setRValue("v2");
         assertEquals("v2", value.getRuntimeValue(true));
      }

      @Test
      void array_scalarTakesTheFirstElement() {
         DynamicValue value = new DynamicValue("=x");
         value.setRValue(new Object[] { "a", "b" });

         assertEquals("a", value.getRuntimeValue(true));
      }

      @Test
      void array_nonScalarKeepsAllElementsInACopy() {
         Object[] source = { "a", "b" };
         DynamicValue value = new DynamicValue("=x");
         value.setRValue(source);

         Object[] result = (Object[]) value.getRuntimeValue(false);

         assertArrayEquals(new Object[] { "a", "b" }, result);
         assertNotSame(source, result);
      }

      @Test
      void emptyArray_scalarGivesTheDefault() {
         DynamicValue value = new DynamicValue("=x");
         value.setRValue(new Object[0]);

         assertNull(value.getRuntimeValue(true));
      }

      @Test
      void setDValue_withTheSameTextKeepsTheRuntimeValue() {
         DynamicValue value = new DynamicValue("=x");
         value.setRValue("v");
         value.setDValue("=x");

         assertEquals("v", value.getRuntimeValue(true));
      }

      @Test
      void setDValue_withANewTextClearsTheRuntimeValue() {
         DynamicValue value = new DynamicValue("=x");
         value.setRValue("v");
         value.setDValue("=y");

         assertNull(value.getRValue());
         assertNull(value.getRuntimeValue(true));
      }
   }

   @Nested
   class RuntimeValueOfOtherTypes {
      private Object bool(Object runtime) {
         DynamicValue value = new DynamicValue("=x", XSchema.BOOLEAN);
         value.setRValue(runtime);
         return value.getRuntimeValue(true);
      }

      @Test
      void boolean_acceptsTextAndWords() {
         assertEquals(Boolean.TRUE, bool("true"));
         assertEquals(Boolean.TRUE, bool("TRUE"));
         assertEquals(Boolean.FALSE, bool("false"));
         assertEquals(Boolean.TRUE, bool("show"));
         assertEquals(Boolean.FALSE, bool("hide"));
      }

      @Test
      void boolean_numbersAreNonZeroTests() {
         assertEquals(Boolean.TRUE, bool(1));
         assertEquals(Boolean.TRUE, bool(-3));
         assertEquals(Boolean.FALSE, bool(0));
         assertEquals(Boolean.FALSE, bool(0.0));
      }

      @Test
      void boolean_anyOtherValueIsTrueAndNullIsFalse() {
         assertEquals(Boolean.TRUE, bool("abc"));
         assertEquals(Boolean.FALSE, bool(null));
      }

      @Test
      void boolean_emptyArrayIsFalse() {
         assertEquals(Boolean.FALSE, bool(new Object[0]));
      }

      @Test
      void boolean_arrayTakesTheFirstElement() {
         assertEquals(Boolean.FALSE, bool(new Object[] { "false", "true" }));
      }

      @Test
      void integer_isConvertedFromText() {
         DynamicValue value = new DynamicValue("=x", XSchema.INTEGER);
         value.setRValue("15");

         assertEquals(15, value.getRuntimeValue(true));
      }

      @Test
      void double_isConvertedFromText() {
         DynamicValue value = new DynamicValue("=x", XSchema.DOUBLE);
         value.setRValue("1.5");

         assertEquals(1.5, value.getRuntimeValue(true));
      }

      @Test
      void integer_unparsableTextIsNull() {
         DynamicValue value = new DynamicValue("=x", XSchema.INTEGER);
         value.setRValue("abc");

         assertNull(value.getRuntimeValue(true));
      }
   }

   @Nested
   class Restriction {
      private DynamicValue alignment(Object runtime) {
         DynamicValue value = new DynamicValue("=x", XSchema.STRING,
                                               new Object[] { "left", "center", "right" });
         value.setRValue(runtime);
         return value;
      }

      @Test
      void listedValue_isReturned() {
         assertEquals("center", alignment("center").getRuntimeValue(true));
      }

      @Test
      void match_ignoresCaseByDefaultAndReturnsTheListedSpelling() {
         assertEquals("center", alignment("CENTER").getRuntimeValue(true));
      }

      @Test
      void match_canBeCaseSensitive() {
         DynamicValue value = alignment("center");
         value.setCaseSensitive(true);
         value.setRValue("CENTER");

         assertEquals("left", value.getRuntimeValue(true));
      }

      @Test
      void unlistedValue_givesTheFirstRestriction() {
         assertEquals("left", alignment("bogus").getRuntimeValue(true));
         assertEquals("left", alignment(null).getRuntimeValue(true));
      }

      @Test
      void emptyArray_givesTheFirstRestriction() {
         assertEquals("left", alignment(new Object[0]).getRuntimeValue(true));
      }

      @Test
      void array_nonScalarMapsEachElement() {
         Object[] result = (Object[]) alignment(new Object[] { "right", "bogus", "CENTER" })
            .getRuntimeValue(false);

         assertArrayEquals(new Object[] { "right", "left", "center" }, result);
      }

      @Test
      void emptyRestrictionList_isRejected() {
         assertThrows(RuntimeException.class,
                      () -> new DynamicValue("=x", XSchema.STRING, new Object[0]));
      }

      @Test
      void booleanRestriction_isAppliedAfterConversion() {
         DynamicValue value = new DynamicValue("=x", XSchema.BOOLEAN);
         value.setRestriction(new boolean[] { true });
         value.setRValue("false");

         // false is not in the list, the first restriction is the default
         assertEquals(Boolean.TRUE, value.getRuntimeValue(true));
      }
   }

   @Nested
   class NamedRestriction {
      private DynamicValue selection(Object runtime) {
         DynamicValue value = new DynamicValue("=x", XSchema.INTEGER, new int[] { 0, 1, 2 },
                                               new String[] { "none", "single", "multi" });
         value.setRValue(runtime);
         return value;
      }

      @Test
      void name_isLookedUpToTheValue() {
         assertEquals(2, selection("multi").getRuntimeValue(true));
         assertEquals(1, selection("single").getRuntimeValue(true));
      }

      @Test
      void number_isMatchedDirectly() {
         assertEquals(1, selection("1").getRuntimeValue(true));
      }

      @Test
      void unknownName_givesTheFirstValue() {
         assertEquals(0, selection("xyz").getRuntimeValue(true));
      }

      @Test
      void getName_isTheNameOfTheRuntimeValue() {
         assertEquals("single", selection(1).getName());
         assertEquals("multi", selection("multi").getName());
      }

      @Test
      void getName_withoutNamesIsNull() {
         DynamicValue value = new DynamicValue("=x", XSchema.STRING,
                                               new Object[] { "a", "b" });
         value.setRValue("a");

         assertNull(value.getName());
      }

      @Test
      void namesMustMatchValues() {
         assertThrows(RuntimeException.class,
                      () -> new DynamicValue("=x", XSchema.INTEGER, new int[] { 0, 1 },
                                             new String[] { "a" }));
      }
   }

   @Nested
   class Identity {
      @Test
      void equalsAndHashCode_useTheDesignValueOnly() {
         DynamicValue a = new DynamicValue("=x");
         DynamicValue b = new DynamicValue("=x");
         b.setRValue("other");

         assertEquals(a, b);
         assertEquals(a.hashCode(), b.hashCode());
         assertNotEquals(a, new DynamicValue("=y"));
      }

      @Test
      void toString_isTheDesignValue() {
         assertEquals("=x", new DynamicValue("=x").toString());
      }

      @Test
      void clone_isIndependentOnceTheRuntimeValueChanges() {
         DynamicValue a = new DynamicValue("=x");
         a.setRValue("one");
         DynamicValue b = a.clone();

         b.setRValue("two");

         assertEquals("one", a.getRuntimeValue(true));
         assertEquals("two", b.getRuntimeValue(true));
      }

      @Test
      void fullHashCode_includesTheRuntimeValue() {
         DynamicValue a = new DynamicValue("=x");
         DynamicValue b = new DynamicValue("=x");
         a.setRValue("one");
         b.setRValue("two");

         assertNotEquals(a.fullHashCode(), b.fullHashCode());
      }
   }
}
