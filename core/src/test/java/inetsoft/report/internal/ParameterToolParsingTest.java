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

import inetsoft.uql.VariableTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests (no Spring beans) for the single-pass parsing of text parameters.
 */
@Tag("core")
class ParameterToolParsingTest {
   private final ParameterTool tool = new ParameterTool();

   private String parse(String text, Object... nameValues) {
      VariableTable table = new VariableTable();

      for(int i = 0; i < nameValues.length; i += 2) {
         table.put((String) nameValues[i], nameValues[i + 1]);
      }

      // run on a separate thread so a regression cannot spin the test JVM forever
      ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
         Thread t = new Thread(r, "ParameterToolParsingTest");
         t.setDaemon(true);
         return t;
      });

      try {
         Future<String> future = exec.submit(() -> tool.parseParameters(table, text));
         return future.get(5, TimeUnit.SECONDS);
      }
      catch(TimeoutException ex) {
         fail("parseParameters did not terminate for: " + text);
         return null;
      }
      catch(ExecutionException ex) {
         if(ex.getCause() instanceof RuntimeException) {
            throw (RuntimeException) ex.getCause();
         }

         throw new IllegalStateException(ex.getCause());
      }
      catch(InterruptedException ex) {
         throw new IllegalStateException(ex);
      }
      finally {
         exec.shutdownNow();
      }
   }

   @Test
   void selfReferenceTerminates() {
      assertEquals("{parameter.a}", parse("{parameter.a}", "a", "{parameter.a}"));
   }

   @Test
   void growingValueTerminates() {
      assertEquals("x{parameter.a}x", parse("{parameter.a}", "a", "x{parameter.a}x"));
   }

   @Test
   void mutualCycleTerminates() {
      assertEquals("{parameter.b}", parse("{parameter.a}", "a", "{parameter.b}",
                                          "b", "{parameter.a}"));
   }

   @Test
   void chainInsertedLiterally() {
      assertEquals("{parameter.y}", parse("{parameter.x}", "x", "{parameter.y}", "y", "v"));
   }

   @Test
   void arrayWithNullElement() {
      assertEquals("[null,b,c]", parse("{parameter.a}", "a", new Object[] { null, "b", "c" }));
      assertEquals("[a,null,c]", parse("{parameter.a}", "a", new Object[] { "a", null, "c" }));
      assertEquals("[a,b,null]", parse("{parameter.a}", "a", new Object[] { "a", "b", null }));
   }

   @Test
   void arrayWithNullElementAndFormat() {
      assertEquals("[null,b]",
                   parse("{parameter.a, text, '{0}'}", "a", new Object[] { null, "b" }));
   }

   @Test
   void literalBraceBeforePlaceholder() {
      assertEquals("{0} abc", parse("{0} {parameter.name}", "name", "abc"));
      assertEquals("} abc", parse("} {parameter.name}", "name", "abc"));
   }

   @Test
   void valueWithBraceThenSecondPlaceholder() {
      assertEquals("a}b and 2",
                   parse("{parameter.a} and {parameter.b}", "a", "a}b", "b", "2"));
   }

   @Test
   void apostropheBeforePlaceholder() {
      assertEquals("It's Z_aa", parse("It's {parameter.a, text, '{0}_aa'}", "a", "Z"));
      assertEquals("it's 2", parse("it's {parameter.a}", "a", "2"));
   }

   @Test
   void unterminatedQuoteDoesNotThrow() {
      assertDoesNotThrow(() -> parse("{parameter.a, text, 'x}", "a", "Z"));
      assertDoesNotThrow(() -> parse("{parameter.a, text, 'x} {parameter.b}", "a", "Z", "b", "1"));
   }

   @Test
   void ordinaryTexts() {
      assertEquals("plain", parse("plain"));
      assertNull(parse(null));
      assertEquals("1 and 2", parse("{parameter.a} and {parameter.b}", "a", "1", "b", "2"));
      assertEquals("x{y 1", parse("x{y {parameter.a}", "a", "1"));
      assertEquals("null", parse("{parameter.missing}"));
      assertEquals("[1,2]", parse("{parameter.a}", "a", new Object[] { "1", "2" }));
      assertEquals("Z_aa", parse("{parameter.a, text, '{0}_aa'}", "a", "Z"));
      assertEquals("{parameter.a", parse("{parameter.a", "a", "1"));
      assertEquals("tail}", parse("{parameter.a}tail}", "a", ""));
   }

   @org.junit.jupiter.api.DisplayName("T06 pinned current behavior: an empty parameter name is written as text null")
   @Test
   void t06_emptyName() {
      assertEquals("null", parse("{parameter.}"));
      assertEquals(parse("{parameter.missing}"), parse("{parameter.}"));
   }

   @org.junit.jupiter.api.DisplayName("T09 hostile values are written literally with a text / number format")
   @org.junit.jupiter.params.ParameterizedTest(name = "[{index}] ''{0}''")
   @org.junit.jupiter.params.provider.ValueSource(strings = { "{parameter.x}", "}{", "'", "{0}" })
   void t09_hostileValues(String value) {
      assertEquals(value, parse("{parameter.x, text, '{0}'}", "x", value));
      assertEquals(value, parse("{parameter.x, number, '0.00'}", "x", value));
      assertEquals(value, parse("{parameter.x}", "x", value));
   }
}
