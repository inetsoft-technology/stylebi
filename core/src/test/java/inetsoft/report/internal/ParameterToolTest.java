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
package inetsoft.report.internal;

import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Calendar;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The {parameter.x, format, 'spec'} text of dashboard text and titles.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ParameterToolTest {
   private final ParameterTool tool = new ParameterTool();
   private Locale locale;

   @BeforeEach
   void setUp() {
      locale = Locale.getDefault();
      Locale.setDefault(Locale.US);
   }

   @AfterEach
   void tearDown() {
      Locale.setDefault(locale);
   }

   private String parse(String text, Object... nameAndValues) {
      VariableTable vars = new VariableTable();

      for(int i = 0; i < nameAndValues.length; i += 2) {
         vars.put((String) nameAndValues[i], nameAndValues[i + 1]);
      }

      return tool.parseParameters(vars, text);
   }

   @Nested
   class ContainsParameter {
      @Test
      void nullAndPlainText_haveNoParameter() {
         assertFalse(tool.containsParameter(null));
         assertFalse(tool.containsParameter("plain text"));
         assertFalse(tool.containsParameter("{parameter"));
         assertFalse(tool.containsParameter("{parameter.x"));
      }

      @Test
      void closedParameter_isFound() {
         assertTrue(tool.containsParameter("{parameter.x}"));
         assertTrue(tool.containsParameter("Total: {parameter.x, number, '0.0'}"));
      }

   }

   @Nested
   class Plain {
      @Test
      void value_isInsertedWithSurroundingText() {
         assertEquals("Total: 5 units", parse("Total: {parameter.x} units", "x", 5));
      }

      @Test
      void missingParameter_isWrittenAsTextNull() {
         assertEquals("Total: null", parse("Total: {parameter.x}"));
      }

      @Test
      void severalParameters_areAllReplaced() {
         assertEquals("1-2", parse("{parameter.a}-{parameter.b}", "a", 1, "b", 2));
      }

      @Test
      void sameParameterTwice_isReplacedTwice() {
         assertEquals("1+1", parse("{parameter.a}+{parameter.a}", "a", 1));
      }

      @Test
      void nameContainingPrefix_isLookedUpByItsFullName() {
         // Bug #81109: only the leading "parameter." is the prefix, not every occurrence
         assertEquals("5", parse("{parameter.myparameter.x}", "myparameter.x", 5, "myx", 9));
         assertEquals("5|6", parse("{parameter.a.parameter.b}|{parameter.parameter.c}",
                                   "a.parameter.b", 5, "parameter.c", 6));
         assertEquals("1,234.50", parse("{parameter.myparameter.x, number, '#,##0.00'}",
                                         "myparameter.x", 1234.5));
      }

      @Test
      void textWithoutParameter_isUnchanged() {
         assertEquals("hello", parse("hello", "x", 1));
      }

      @Test
      void unclosedParameter_isUnchanged() {
         assertEquals("{parameter.x", parse("{parameter.x", "x", 1));
      }
   }

   @Nested
   class Formats {
      @Test
      void number_usesTheFormatSpec() {
         assertEquals("1,234.50", parse("{parameter.x, number, '#,##0.00'}", "x", 1234.5));
      }

      @Test
      void number_parsesAStringValue() {
         assertEquals("1,234.50", parse("{parameter.x, number, '#,##0.00'}", "x", "1234.5"));
      }

      @Test
      void number_nonNumericValueIsWrittenAsIs() {
         assertEquals("abc", parse("{parameter.x, number, '#,##0.00'}", "x", "abc"));
      }

      @Test
      void number_withoutSpecIsWrittenAsIs() {
         assertEquals("1234.5", parse("{parameter.x, number}", "x", 1234.5));
      }

      @Test
      void text_usesMessageFormatWithTheValueAsArgument() {
         assertEquals("Hello abc", parse("{parameter.x, text, 'Hello {0}'}", "x", "abc"));
      }

      @Test
      void date_formatsADateValue() {
         Calendar cal = Calendar.getInstance();
         cal.clear();
         cal.set(2024, Calendar.MARCH, 15);

         assertEquals("2024-03-15",
                      parse("{parameter.x, date, 'yyyy-MM-dd'}", "x", cal.getTime()));
      }

      @Test
      void date_nonDateValueIsWrittenAsIs() {
         assertEquals("2024-03-15", parse("{parameter.x, date, 'MM/d/yy'}", "x", "2024-03-15"));
      }

      @Test
      void percent_usesTheDefaultLocale() {
         assertEquals("50%", parse("{parameter.x, percent}", "x", 0.5));
      }

      @Test
      void currency_usesTheDefaultLocale() {
         assertEquals("$1,234.50", parse("{parameter.x, currency}", "x", 1234.5));
      }

      @Test
      void unknownFormatType_isWrittenAsIs() {
         // see Bug #79691, duration is documented but not implemented
         assertEquals("90", parse("{parameter.x, duration, 'HH:mm:ss'}", "x", 90));
      }

      @Test
      void missingParameterWithFormat_isWrittenAsTextNull() {
         assertEquals("null", parse("{parameter.x, number, '0.0'}"));
      }
   }

   /**
    * Bug #81113: integral and decimal values beyond 2^53 lost digits (converted to double first).
    */
   @Nested
   class ExactNumbers {
      private static final String GROUPED = "{parameter.x, number, '#,##0'}";

      @Test
      void long_keepsEveryDigit() {
         assertEquals("9,007,199,254,740,993", parse(GROUPED, "x", 9007199254740993L));
         assertEquals("9,223,372,036,854,775,807", parse(GROUPED, "x", Long.MAX_VALUE));
         assertEquals("-9,007,199,254,740,993", parse(GROUPED, "x", -9007199254740993L));
      }

      @Test
      void bigInteger_keepsEveryDigit() {
         assertEquals("123,456,789,012,345,678,901,234",
                      parse(GROUPED, "x", new BigInteger("123456789012345678901234")));
      }

      @Test
      void bigDecimal_keepsEveryDigit() {
         assertEquals("9,007,199,254,740,993",
                      parse(GROUPED, "x", new BigDecimal("9007199254740993")));
         assertEquals("9,007,199,254,740,993.12",
                      parse("{parameter.x, number, '#,##0.00'}", "x",
                            new BigDecimal("9007199254740993.123")));
      }

      @Test
      void string_keepsEveryDigit() {
         // a viewer URL parameter arrives as a string
         assertEquals("9,007,199,254,740,993", parse(GROUPED, "x", "9007199254740993"));
      }

      @Test
      void noGroupingSpec_keepsEveryDigit() {
         assertEquals("9007199254740993", parse("{parameter.x, number, '0'}", "x",
                                                  9007199254740993L));
      }

      @Test
      void percentAndCurrency_keepEveryDigit() {
         assertEquals("$9,007,199,254,740,993.00",
                      parse("{parameter.x, currency}", "x", 9007199254740993L));
         assertEquals("900,719,925,474,099,300%",
                      parse("{parameter.x, percent}", "x", 9007199254740993L));
      }

      @Test
      void arrayElements_keepEveryDigit() {
         assertEquals("[9,007,199,254,740,993,1]",
                      parse(GROUPED, "x", new Object[] { 9007199254740993L, 1L }));
      }

      @Test
      void smallValues_areUnchanged() {
         assertEquals("1,234", parse(GROUPED, "x", 1234L));
         assertEquals("1,234", parse(GROUPED, "x", 1234));
         assertEquals("1,235", parse(GROUPED, "x", 1234.5));
         assertEquals("-1,234", parse(GROUPED, "x", new BigDecimal("-1234")));
         assertEquals("1.01", parse("{parameter.x, number, '0.00'}", "x", new BigDecimal("1.005")));
         assertEquals("1,234.50", parse("{parameter.x, number, '#,##0.00'}", "x", 1234.5f));
      }

      @Test
      void negativeRoundedToZero_hasNoMinusSign() {
         assertEquals("0.00", parse("{parameter.x, number, '0.00'}", "x", new BigDecimal("-0.0001")));
         assertEquals("0.00", parse("{parameter.x, number, '0.00'}", "x", -0.0001));
      }

      @Test
      void doubleBeyondPrecision_isStillADouble() {
         // a Double has no more digits than it holds, nothing to recover
         assertEquals("9,007,199,254,740,992", parse(GROUPED, "x", 9007199254740993d));
      }

      @Test
      void unitSuffix_stillUsesTheUnit() {
         assertEquals("9,007,199B", parse("{parameter.x, number, '#,##0B'}", "x",
                                                    9007199254740993L));
         assertEquals("1,234K", parse("{parameter.x, number, '#,##0K'}", "x", 1234000L));
      }
   }

   @Nested
   class Arrays {
      @Test
      void multipleValues_areWrittenInBrackets() {
         assertEquals("[a,b]", parse("{parameter.x}", "x", new Object[] { "a", "b" }));
      }

      @Test
      void number_formatsEachElement() {
         assertEquals("[1.0,2.5]",
                      parse("{parameter.x, number, '0.0'}", "x", new Object[] { 1, 2.5 }));
      }

      @Test
      void currency_formatsEachElement() {
         assertEquals("[$1.00,$2.00]",
                      parse("{parameter.x, currency}", "x", new Object[] { 1, 2 }));
      }
   }
}
