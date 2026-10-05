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
package inetsoft.util;

import inetsoft.report.internal.table.TableFormat;
import inetsoft.test.*;
import inetsoft.uql.util.XUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.Format;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77415: choice patterns that java.text.MessageFormat accepts but that throw every time
 * they are formatted must be rejected when the format is created.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MessageFormatTest {
   @AfterEach
   void clearMessages() {
      Tool.clearUserMessage();
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "{0,choice,}", "{0,choice,0L}", "{0,choice,abc}", "{0,choice,0#{ }n}",
      "{0,choice,0#ok|1#{1,foo}}", "{0,choice,0#{0,choice,}}", "{0,choice,0#{}}",
      "{0,choice,0#'{'x'}'}"
   })
   void malformedChoiceIsRejected(String pattern) {
      assertThrows(IllegalArgumentException.class, () -> new MessageFormat(pattern));
      assertThrows(IllegalArgumentException.class, () -> new MessageFormat(pattern, Locale.US));
      assertNull(TableFormat.getFormat(TableFormat.MESSAGE_FORMAT, pattern, Locale.US));
   }

   @Test
   void nestedChoiceStillFormats() {
      Format fmt = TableFormat.getFormat(
         TableFormat.MESSAGE_FORMAT,
         "{0,choice,0#no files|1#one file|1<{0,number,integer} files}", Locale.US);

      assertNotNull(fmt);
      assertEquals("no files", XUtil.format(fmt, 0));
      assertEquals("one file", XUtil.format(fmt, 1));
      assertEquals("1,234 files", XUtil.format(fmt, 1234));
   }

   @Test
   void quotedBracesStillFormat() {
      assertEquals("{x}", new MessageFormat("{0,choice,0#''{''x''}''|1#y}").format(0));
      assertEquals("y", new MessageFormat("{0,choice,0#''{''x''}''|1#y}").format(1));
      assertEquals("{0,choice,} 5", new MessageFormat("'{0,choice,}' {0}").format(5));
   }

   @Test
   void simpleChoiceStillFormats() {
      Format fmt = TableFormat.getFormat(
         TableFormat.MESSAGE_FORMAT, "{0,choice,0#none|1#one|1<many}", Locale.US);

      assertNotNull(fmt);
      assertEquals("none", XUtil.format(fmt, 0));
      assertEquals("many", XUtil.format(fmt, 42));
      assertEquals("20,250 USD", XUtil.format(
         TableFormat.getFormat(TableFormat.MESSAGE_FORMAT, "{0} USD", Locale.US), 20250));
   }

   // Bug #77804: K/M/B number subformats must be swapped in by element index. These patterns
   // have an element index that differs from its argument index, so they fail with
   // setFormatByArgumentIndex.
   @Test
   void extendedFormatOnArgumentUsedTwice() {
      String pattern = "{0} ({0,number,#,##0.0K})";
      assertEquals("1,234 (1.2K)", new MessageFormat(pattern, Locale.US).format(1234));
      assertEquals("1,234 (1.2K)", XUtil.format(
         TableFormat.getFormat(TableFormat.MESSAGE_FORMAT, pattern, Locale.US), 1234));
   }

   @Test
   void extendedFormatOnReorderedArguments() {
      MessageFormat fmt = new MessageFormat("{1} {0,number,0.0K}", Locale.US);
      assertEquals("5,678 1.2K", fmt.format(new Object[] { 1234, 5678 }));
      assertEquals("Sales 1.2K", fmt.format(new Object[] { 1234, "Sales" }));
   }

   @Test
   void extendedFormatDoesNotOverwriteSiblingElement() {
      assertEquals("1.2K (1,234)",
         new MessageFormat("{0,number,0.0K} ({0,number,#,##0})", Locale.US).format(1234));
   }

   @Test
   void extendedFormatOnSkippedArgument() {
      assertEquals("1.2K",
         new MessageFormat("{1,number,0.0K}", Locale.US).format(new Object[] { "x", 1234 }));
   }

   @Test
   void extendedFormatWhenElementMatchesArgument() {
      assertEquals("Sales 1.2K",
         new MessageFormat("{0} {1,number,0.0K}", Locale.US).format(new Object[] { "Sales", 1234 }));
      assertEquals("1.2K", XUtil.format(
         TableFormat.getFormat(TableFormat.MESSAGE_FORMAT, "{0,number,#,##0.0K}", Locale.US), 1234));
   }
}
