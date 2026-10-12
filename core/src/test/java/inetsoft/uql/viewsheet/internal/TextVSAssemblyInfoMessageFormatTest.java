/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.internal.table.TableFormat;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.VSFormat;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77415: a Text object with a malformed choice message format used to throw from
 * getText() (XUtil.format has no catch); it must fall back to the plain value instead.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TextVSAssemblyInfoMessageFormatTest {
   @AfterEach
   void clearMessages() {
      Tool.clearUserMessage();
   }

   @ParameterizedTest
   @ValueSource(strings = { "{0,choice,}", "{0,choice,0L}", "{0,choice,0#{ }n}",
                            "{0,choice,0#{0,choice,}}" })
   void malformedChoiceShowsPlainValue(String pattern) {
      assertEquals("42", textWithFormat(pattern, 42));
   }

   @Test
   void validChoiceStillFormats() {
      String pattern = "{0,choice,0#no files|1#one file|1<{0,number,integer} files}";

      assertEquals("one file", textWithFormat(pattern, 1));
      assertEquals("1,234 files", textWithFormat(pattern, 1234));
      assertEquals("42 USD", textWithFormat("{0} USD", 42));
   }

   // Bug #77804: K/M/B subformats on elements whose index differs from their argument
   // index must still be scaled in a Text object, without changing sibling elements.
   @Test
   void extendedFormatOnRepeatedArgument() {
      assertEquals("1,234 (1.2K)", textWithFormat("{0} ({0,number,#,##0.0K})", 1234));
      assertEquals("1.2K (1,234)", textWithFormat("{0,number,0.0K} ({0,number,#,##0})", 1234));
      assertEquals("1234.6K / 1.23M", textWithFormat("{0,number,0.0K} / {0,number,0.00M}", 1234567));
   }

   private static String textWithFormat(String pattern, Object value) {
      TextVSAssemblyInfo info = new TextVSAssemblyInfo();
      VSFormat fmt = info.getFormat().getUserDefinedFormat();
      fmt.setFormatValue(TableFormat.MESSAGE_FORMAT);
      fmt.setFormatExtentValue(pattern);
      info.setValue(value);
      return info.getText();
   }
}
