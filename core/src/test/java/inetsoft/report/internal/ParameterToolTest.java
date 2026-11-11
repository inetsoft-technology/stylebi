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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
      void hugeExponentString_staysOnTheDoublePath() {
         long start = System.nanoTime();
         String out = parse(GROUPED, "x", "1E+100000000");

         assertTrue(out.length() < 50, "output length " + out.length());
         assertTrue(System.nanoTime() - start < 5_000_000_000L);
         assertTrue(parse(GROUPED, "x", "1e400").length() < 50);
         assertTrue(parse(GROUPED, "x", "1E-100000000").length() < 50);
      }

      @Test
      void exponentString_withinBound_isExact() {
         assertEquals("12,345,678,901,234,567,890", parse(GROUPED, "x", "1.234567890123456789E+19"));
      }

      @Test
      void longDecimalString_keepsItsFraction() {
         assertEquals("1,234,567,890,123,456.79",
                      parse("{parameter.x, number, '#,##0.00'}", "x", "1234567890123456.789"));
      }

      @Test
      void stringWithWhitespace_isTrimmed() {
         assertEquals("9,007,199,254,740,993", parse(GROUPED, "x", "  9007199254740993 "));
      }

      @Test
      void negativeRoundedToZero_onTheExactPath_hasNoMinusSign() {
         assertEquals("0.00", parse("{parameter.x, number, '0.00'}", "x",
                                    new BigDecimal("-0.0000123456789012345678")));
         // not tiny enough to be zero: keeps its sign as on the double path
         assertEquals("-0.01", parse("{parameter.x, number, '0.00'}", "x",
                                     new BigDecimal("-0.0123456789012345678")));
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

   @Nested
   class HardeningRows {
      @Disabled("mismatch T01")
      @DisplayName("T01 a variable name containing 'parameter.' is found")
      @Test
      void t01_nameContainingPrefix() {
         assertEquals("V", parse("{parameter.myparameter.x}", "myparameter.x", "V"));
         assertEquals("W", parse("{parameter.a.parameter.b}", "a.parameter.b", "W"));
      }

      @DisplayName("T02 a comma inside the format spec is part of the spec")
      @Test
      void t02_commaInSpec() {
         assertEquals("x, 1234.5", parse("{parameter.p, text, 'x, {0}'}", "p", 1234.5));
         assertEquals("1,234.50", parse("{parameter.p, number, '#,##0.00'}", "p", 1234.5));
      }

      @DisplayName("T03 a Long is written without an exponent")
      @Test
      void t03_longNoFormat() {
         assertEquals("3000000000", parse("{parameter.p}", "p", 3000000000L));
      }

      @Disabled("mismatch T03")
      @DisplayName("T03 a Long above 2^53 keeps its digits with a number format")
      @Test
      void t03_longNumberFormat() {
         assertEquals("9,007,199,254,740,993",
                      parse("{parameter.p, number, '#,##0'}", "p", 9007199254740993L));
      }

      @DisplayName("T03 a BigDecimal keeps its scale with a number format")
      @Test
      void t03_bigDecimal() {
         assertEquals("1.10",
                      parse("{parameter.p, number, '0.00'}", "p", new java.math.BigDecimal("1.10")));
      }

      @DisplayName("T04 currency / percent follow the default locale like NumberFormat")
      @ParameterizedTest(name = "[{index}] {0}")
      @ValueSource(strings = { "en-US", "de-DE", "fr-FR" })
      void t04_currencyAndPercent(String tag) {
         Locale.setDefault(Locale.forLanguageTag(tag));
         assertEquals(java.text.NumberFormat.getCurrencyInstance().format(1234.5),
                      parse("{parameter.p, currency}", "p", 1234.5));
         assertEquals(java.text.NumberFormat.getPercentInstance().format(0.256),
                      parse("{parameter.p, percent}", "p", 0.256));
      }

      @DisplayName("T05 a builtin variable is resolved")
      @Test
      void t05_builtin() {
         String text = parse("{parameter._TODAY}");
         assertNotEquals("null", text);
         assertEquals(inetsoft.uql.util.XUtil.replaceVariable("$(_TODAY)", new VariableTable()),
                      text);
      }

      @DisplayName("T07 a date format follows the default locale like ExtendedDateFormat")
      @ParameterizedTest(name = "[{index}] {0}")
      @ValueSource(strings = { "en-US", "fr-FR" })
      void t07_dateFormatLocale(String tag) {
         Locale.setDefault(Locale.forLanguageTag(tag));
         Calendar cal = Calendar.getInstance();
         cal.clear();
         cal.set(2024, Calendar.MARCH, 15);
         String expected = new inetsoft.util.ExtendedDateFormat("d MMM yyyy").format(cal.getTime());

         assertEquals(expected, parse("{parameter.d, date, 'd MMM yyyy'}", "d", cal.getTime()));
      }
   }
}
