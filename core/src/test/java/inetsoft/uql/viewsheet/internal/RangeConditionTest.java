/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.StringJoiner;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class RangeConditionTest {
   static Stream<TestCase> data() {
      return Stream.of(
         new TestCase.TestCaseBuilder()
            .setTestCaseDescription("Empty range []")
            .setInRange(new Object[][] {{null}, {0}, {"A"}, {"any", "values", "allowed"}})
            .setOutOfRange(new Object[][] {})
            .createTestCase(),

         new TestCase.TestCaseBuilder()
            .setTestCaseDescription("Single completely exclusive range (1 - 1)")
            .setMins(new Object[] {1})
            .setMaxes(new Object[] {1})
            .setDataTypes(new String[] {XSchema.INTEGER})
            .setLowerInclusive(false)
            .setUpperInclusive(false)
            .setInRange(new Object[][] {})
            .setOutOfRange(new Object[][] {{0}, {1}, {2}})
            .createTestCase(),

         new TestCase.TestCaseBuilder()
            .setTestCaseDescription("Single inclusive null range [null - null]")
            .setMins(new Object[] {null})
            .setMaxes(new Object[] {null})
            .setDataTypes(new String[] {XSchema.STRING})
            .setNullable(true)
            .setInRange(new Object[][] {{null}})
            .setOutOfRange(new Object[][] {{"A"}})
            .createTestCase(),

         new TestCase.TestCaseBuilder()
            .setTestCaseDescription("Single inclusive integer range [0 - 1]")
            .setMins(new Object[] {0})
            .setMaxes(new Object[] {1})
            .setDataTypes(new String[] {XSchema.INTEGER})
            .setInRange(new Object[][] {{0}, {1}})
            .setOutOfRange(new Object[][] {{-1}, {2}})
            .createTestCase(),

         new TestCase.TestCaseBuilder()
            .setTestCaseDescription("Single exclusive integer range [0 - 1)")
            .setMins(new Object[] {0})
            .setMaxes(new Object[] {1})
            .setDataTypes(new String[] {XSchema.INTEGER})
            .setUpperInclusive(false)
            .setInRange(new Object[][] {{0}})
            .setOutOfRange(new Object[][] {{-1}, {1}, {2}})
            .createTestCase(),

         new TestCase.TestCaseBuilder()
            .setTestCaseDescription("Single exclusive integer range (0 - 2)")
            .setMins(new Object[] {0})
            .setMaxes(new Object[] {2})
            .setDataTypes(new String[] {XSchema.INTEGER})
            .setLowerInclusive(false)
            .setUpperInclusive(false)
            .setInRange(new Object[][] {{1}})
            .setOutOfRange(new Object[][] {{-1}, {0}, {2}, {3}})
            .createTestCase(),

         new TestCase.TestCaseBuilder()
            .setTestCaseDescription("Single inclusive String range [B - D]")
            .setMins(new Object[] {"B"})
            .setMaxes(new Object[] {"D"})
            .setDataTypes(new String[] {XSchema.STRING})
            .setInRange(new Object[][] {{"B"}, {"C"}, {"D"}})
            .setOutOfRange(new Object[][] {{"A"}, {"E"}})
            .createTestCase(),

         new TestCase.TestCaseBuilder()
            .setTestCaseDescription("Single exclusive String range [B - D)")
            .setMins(new Object[] {"B"})
            .setMaxes(new Object[] {"D"})
            .setDataTypes(new String[] {XSchema.STRING})
            .setUpperInclusive(false)
            .setInRange(new Object[][] {{"B"}, {"C"}})
            .setOutOfRange(new Object[][] {{"A"}, {"D"}, {"E"}})
            .createTestCase(),

         new TestCase.TestCaseBuilder()
            .setTestCaseDescription("Composite inclusive integer range [1, 3 - 3, 2]")
            .setMins(new Object[] {1, 3})
            .setMaxes(new Object[] {3, 2})
            .setDataTypes(new String[] {XSchema.INTEGER, XSchema.INTEGER})
            .setInRange(new Object[][] {{1, 3}, {1, 4}, {2, 0}, {2, 5}, {3, 0}, {3, 2}})
            .setOutOfRange(new Object[][] {{0, 3}, {1, 2}, {3, 3}, {4, 1}})
            .createTestCase(),

         new TestCase.TestCaseBuilder()
            .setTestCaseDescription("Composite exclusive integer range [1, 3 - 3, 2)")
            .setMins(new Object[] {1, 3})
            .setMaxes(new Object[] {3, 2})
            .setDataTypes(new String[] {XSchema.INTEGER, XSchema.INTEGER})
            .setUpperInclusive(false)
            .setInRange(new Object[][] {{1, 3}, {1, 4}, {2, 0}, {2, 5}, {3, 0}, {3, 1}})
            .setOutOfRange(new Object[][] {{0, 3}, {1, 2}, {3, 2}, {4, 1}})
            .createTestCase(),

         new TestCase.TestCaseBuilder()
            .setTestCaseDescription("Composite inclusive mixed range [B, 3 - D, 2]")
            .setMins(new Object[] {"B", 3})
            .setMaxes(new Object[] {"D", 2})
            .setDataTypes(new String[] {XSchema.STRING, XSchema.INTEGER})
            .setInRange(new Object[][] {{"B", 3}, {"B", 4}, {"C", 0}, {"C", 5}, {"D", 0}, {"D", 2}})
            .setOutOfRange(new Object[][] {{"A", 3}, {"B", 2}, {"D", 3}, {"E", 1}})
            .createTestCase(),

         new TestCase.TestCaseBuilder()
            .setTestCaseDescription("Composite exclusive mixed range [B, 3 - D, 2)")
            .setMins(new Object[] {"B", 3})
            .setMaxes(new Object[] {"D", 2})
            .setDataTypes(new String[] {XSchema.STRING, XSchema.INTEGER})
            .setUpperInclusive(false)
            .setInRange(new Object[][] {{"B", 3}, {"B", 4}, {"C", 0}, {"C", 5}, {"D", 0}, {"D", 1}})
            .setOutOfRange(new Object[][] {{"A", 3}, {"B", 2}, {"D", 2}, {"D", 3} ,{"E", 1}})
            .createTestCase(),

         new TestCase.TestCaseBuilder()
            .setTestCaseDescription("Single inclusive nullable String range [null - C]")
            .setMins(new Object[] {null})
            .setMaxes(new Object[] {"C"})
            .setDataTypes(new String[] {XSchema.STRING})
            .setNullable(true)
            .setInRange(new Object[][] {{null}, {"A"}, {"C"}})
            .setOutOfRange(new Object[][] {{"D"}})
            .createTestCase(),

         new TestCase.TestCaseBuilder()
            .setTestCaseDescription("Single exclusive nullable String range (null - C]")
            .setMins(new Object[] {null})
            .setMaxes(new Object[] {"C"})
            .setDataTypes(new String[] {XSchema.STRING})
            .setLowerInclusive(false)
            .setNullable(true)
            .setInRange(new Object[][] {{"A"}, {"C"}})
            .setOutOfRange(new Object[][] {{null}, {"D"}})
            .createTestCase(),

         new TestCase.TestCaseBuilder()
            .setTestCaseDescription("Composite inclusive nullable mixed range [null, 3 - C, 2]")
            .setMins(new Object[] {null, 3})
            .setMaxes(new Object[] {"C", 2})
            .setDataTypes(new String[] {XSchema.STRING, XSchema.INTEGER})
            .setNullable(true)
            .setInRange(new Object[][] {{null, 3}, {"A", 0}, {"B", null}, {"C", null}, {"C", 0}, {"C", 2}})
            .setOutOfRange(new Object[][] {{null, null}, {null, 2}, {"C", 3}, {"D", 2}})
            .createTestCase(),

         new TestCase.TestCaseBuilder()
            .setTestCaseDescription("Composite inclusive nullable mixed range [B, 3 - D, null]")
            .setMins(new Object[] {"B", 3})
            .setMaxes(new Object[] {"D", null})
            .setDataTypes(new String[] {XSchema.STRING, XSchema.INTEGER})
            .setNullable(true)
            .setInRange(new Object[][] {{"B", 3}, {"C", null}, {"C", 0}, {"D", null}})
            .setOutOfRange(new Object[][] {{null, null}, {null, 2}, {"B", null}, {"B", 2}, {"D", 0}})
            .createTestCase(),

         new TestCase.TestCaseBuilder()
            .setTestCaseDescription("Composite exclusive nullable mixed range [B, 3 - D, null)")
            .setMins(new Object[] {"B", 3})
            .setMaxes(new Object[] {"D", null})
            .setDataTypes(new String[] {XSchema.STRING, XSchema.INTEGER})
            .setUpperInclusive(false)
            .setNullable(true)
            .setInRange(new Object[][] {{"B", 3}, {"C", null}, {"C", 0}})
            .setOutOfRange(new Object[][] {{null, null}, {null, 2}, {"B", null}, {"B", 2}, {"D", null}, {"D", 0}})
            .createTestCase(),

         new TestCase.TestCaseBuilder()
            .setTestCaseDescription("Composite inclusive nullable mixed range [B, null - C, 2]")
            .setMins(new Object[] {null, 3})
            .setMaxes(new Object[] {"C", 2})
            .setDataTypes(new String[] {XSchema.STRING, XSchema.INTEGER})
            .setNullable(true)
            .setInRange(new Object[][] {{null, 3}, {"A", 0}, {"B", null}, {"C", null}, {"C", 0}, {"C", 2}})
            .setOutOfRange(new Object[][] {{null, null}, {null, 2}, {"C", 3}, {"D", 2}})
            .createTestCase(),

         new TestCase.TestCaseBuilder()
            .setTestCaseDescription("Composite inclusive nullable mixed range [B, null, 3 - D, 0, 2]")
            .setMins(new Object[] {"B", null, 3})
            .setMaxes(new Object[] {"D", 0, 2})
            .setDataTypes(new String[] {XSchema.STRING, XSchema.INTEGER, XSchema.INTEGER})
            .setNullable(true)
            .setInRange(new Object[][] {{"B", null, 3}, {"B", 0, 0}, {"C", null, 0}, {"C", 0, 0}, {"D", null, 0}, {"D", 0, 2}})
            .setOutOfRange(new Object[][] {{null, null, null}, {"A", 0, 0}, {"B", null, 2}, {"D", 0, 3}})
            .createTestCase(),

         new TestCase.TestCaseBuilder()
            .setTestCaseDescription("Composite inclusive nullable mixed range [null, null - null, 2]")
            .setMins(new Object[] {null, null})
            .setMaxes(new Object[] {null, 2})
            .setDataTypes(new String[] {XSchema.STRING, XSchema.INTEGER})
            .setNullable(true)
            .setInRange(new Object[][] {{null, null}, {null, 1}})
            .setOutOfRange(new Object[][] {{null, 3}, {"A", null}, {"A", 1}})
            .createTestCase()
         );
   }

   @ParameterizedTest
   @MethodSource("data")
   void exerciseCase(TestCase testCase) throws Exception {
      final RangeCondition range = createRangeCondition(testCase);
      Stream<Executable> inRangeChecks = Arrays.stream(testCase.inRange)
         .map(vals -> () -> assertTrue(
            range.evaluate(vals),
            () -> String.format("Values %s not found in range %s", Arrays.toString(vals), range)));
      Stream<Executable> outOfRangeChecks = Arrays.stream(testCase.outOfRange)
         .map(vals -> () -> assertFalse(
            range.evaluate(vals),
            () -> String.format("Values %s found in range %s", Arrays.toString(vals), range)));
      assertAll(Stream.concat(inRangeChecks, outOfRangeChecks));
   }

   private RangeCondition createRangeCondition(TestCase testCase) throws Exception {
      final Constructor<RangeCondition> constructor = RangeCondition.class.getDeclaredConstructor(
         Object[].class, Object[].class, DataRef[].class,
         boolean.class, boolean.class, boolean.class, String.class);

      // This method is only necessary because the constructor is private.
      // If that ever changes, this convoluted construction can go away.
      assertTrue(Modifier.isPrivate(constructor.getModifiers()));
      constructor.setAccessible(true);

      return constructor.newInstance(testCase.mins,
                                     testCase.maxes,
                                     createDataRefs(testCase),
                                     testCase.lowerInclusive,
                                     testCase.upperInclusive,
                                     testCase.nullable,
                                     null);
   }

   private DataRef[] createDataRefs(TestCase testCase) {
      final DataRef[] refs = new DataRef[testCase.dataTypes.length];

      for(int i = 0; i < testCase.dataTypes.length; i++) {
         final ColumnRef col = new ColumnRef(new AttributeRef(Integer.toString(i)));
         col.setDataType(testCase.dataTypes[i]);
         refs[i] = col;
      }
      return refs;
   }

   final static class TestCase {
      private TestCase(String testCaseDescription,
                       Object[] mins,
                       Object[] maxes,
                       String[] dataTypes,
                       boolean lowerInclusive,
                       boolean upperInclusive,
                       boolean nullable,
                       Object[][] inRange,
                       Object[][] outOfRange)
      {
         this.testCaseDescription = testCaseDescription;
         this.mins = mins;
         this.maxes = maxes;
         this.dataTypes = dataTypes;
         this.lowerInclusive = lowerInclusive;
         this.upperInclusive = upperInclusive;
         this.nullable = nullable;
         this.inRange = inRange;
         this.outOfRange = outOfRange;
      }

      @Override
      public String toString() {
         final StringJoiner joiner = new StringJoiner(", ", "{", "}");

         if(testCaseDescription != null) {
            joiner.add(testCaseDescription);
         }

         return joiner
            .add("mins=" + Arrays.toString(mins))
            .add("maxes=" + Arrays.toString(maxes))
            .add("dataTypes=" + Arrays.toString(dataTypes))
            .add("lowerInclusive=" + lowerInclusive)
            .add("upperInclusive=" + upperInclusive)
            .add("nullable=" + nullable)
            .add("inRange=" + Arrays.deepToString(inRange))
            .add("outOfRange=" + Arrays.deepToString(outOfRange))
            .toString();
      }

      private static class TestCaseBuilder {
         TestCaseBuilder setTestCaseDescription(String testCaseDescription) {
            this.testCaseDescription = testCaseDescription;
            return this;
         }

         TestCaseBuilder setMins(Object[] mins) {
            this.mins = mins;
            return this;
         }

         TestCaseBuilder setMaxes(Object[] maxes) {
            this.maxes = maxes;
            return this;
         }

         TestCaseBuilder setDataTypes(String[] dataTypes) {
            this.dataTypes = dataTypes;
            return this;
         }

         TestCaseBuilder setLowerInclusive(boolean lowerInclusive) {
            this.lowerInclusive = lowerInclusive;
            return this;
         }

         TestCaseBuilder setUpperInclusive(boolean upperInclusive) {
            this.upperInclusive = upperInclusive;
            return this;
         }

         TestCaseBuilder setNullable(boolean nullable) {
            this.nullable = nullable;
            return this;
         }

         TestCaseBuilder setInRange(Object[][] inRange) {
            this.inRange = inRange;
            return this;
         }

         TestCaseBuilder setOutOfRange(Object[][] outOfRange) {
            this.outOfRange = outOfRange;
            return this;
         }

         TestCase createTestCase() {
            return new TestCase(testCaseDescription,
                                mins,
                                maxes,
                                dataTypes,
                                lowerInclusive,
                                upperInclusive,
                                nullable,
                                inRange,
                                outOfRange);
         }

         private String testCaseDescription = null;
         private Object[] mins = {};
         private Object[] maxes = {};
         private String[] dataTypes = {};
         private boolean lowerInclusive = true;
         private boolean upperInclusive = true;
         private boolean nullable = false;
         private Object[][] inRange = {};
         private Object[][] outOfRange = {};
      }

      private final String testCaseDescription;

      private final Object[] mins;
      private final Object[] maxes;
      private final String[] dataTypes;
      private final boolean lowerInclusive;
      private final boolean upperInclusive;
      private final boolean nullable;
      
      private final Object[][] inRange;
      private final Object[][] outOfRange;
   }
}
