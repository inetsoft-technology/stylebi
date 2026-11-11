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
package inetsoft.uql.util.filereader;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * How an uploaded file and the values in it are classified: the kind of file is read from its
 * first bytes ({@link TextUtil#detectType}), and the type of a column from a value
 * ({@link TextUtil#getType}). The expected types are written from the rules the code states,
 * not from running it on the values.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TextUtilTypeDetectionTest {
   private static InputStream bytes(int... values) {
      byte[] data = new byte[values.length];

      for(int i = 0; i < values.length; i++) {
         data[i] = (byte) values[i];
      }

      return new ByteArrayInputStream(data);
   }

   // ---- the kind of file ----

   @Test
   void zipHeaderIsAnXlsxFile() throws Exception {
      assertEquals(TextFileType.XLSX, TextUtil.detectType(bytes(0x50, 0x4b, 0x03, 0x04, 0, 0, 0, 0)));
   }

   @Test
   void compoundFileHeaderIsAnXlsFile() throws Exception {
      assertEquals(TextFileType.XLS, TextUtil.detectType(
         bytes(0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1)));
   }

   // the first 8 bytes of the xls header are all needed
   @Test
   void partialCompoundFileHeaderIsText() throws Exception {
      assertEquals(TextFileType.DELIMITED, TextUtil.detectType(
         bytes(0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0x00)));
   }

   @Test
   void anythingElseIsDelimitedText() throws Exception {
      assertEquals(TextFileType.DELIMITED, TextUtil.detectType(
         new ByteArrayInputStream("a,b\n1,2\n".getBytes())));
      assertEquals(TextFileType.DELIMITED, TextUtil.detectType(bytes()));
      assertEquals(TextFileType.DELIMITED, TextUtil.detectType(bytes(0x50, 0x4b)));
      assertEquals(TextFileType.DELIMITED, TextUtil.detectType(null));
   }

   // ---- the type of a value ----

   @ParameterizedTest(name = "{0} is {1}")
   @CsvSource(delimiter = '|', value = {
      "12|integer", "-5|integer", "007|integer", "0|integer",
      "1.5|double", "-0.25|double", "1,234.5|double", "1,234|double",
      "50%|double", "$12.5|double", "-$3|double", "($3)|double",
      // beyond the range of an int
      "2147483648|double",
      "abc|string", "12abc|string", "' 12'|string", "true|string", "N/A|string", "1e5|string",
      // dates and times
      "2024-02-29|date", "02/29/2024|date", "2/9/2024|date", "2/9/24|date",
      "2024-02-29 13:45:30|timeInstant",
      "13:45|time", "9:05:07|time", "9:05 PM|time"
   })
   void typeOfAValue(String value, String type) {
      assertEquals(type, TextUtil.getType(value).getType(), value);
   }

   @ParameterizedTest(name = "{0} is read with {1}")
   @CsvSource(delimiter = '|', value = {
      "2024-02-29|yyyy-MM-dd", "2024-2-9|yyyy-M-d", "02/29/2024|MM/dd/yyyy",
      "2/9/2024|M/d/yyyy", "2/9/24|M/d/yy", "2024-02-29 13:45:30|yyyy-MM-dd HH:mm:ss",
      "13:45|HH:mm", "9:05:07|H:mm:ss"
   })
   void formatOfADateOrTime(String value, String format) {
      assertEquals(format, TextUtil.getType(value).getFormat(), value);
   }

   @Test
   void nullValueIsAString() {
      assertEquals(XSchema.STRING, TextUtil.getType(null).getType());
   }

   // the types are written the way the rest of the code compares them
   @Test
   void typeNamesAreTheSchemaNames() {
      assertEquals(XSchema.INTEGER, TextUtil.getType("1").getType());
      assertEquals(XSchema.DOUBLE, TextUtil.getType("1.5").getType());
      assertEquals(XSchema.DATE, TextUtil.getType("2024-02-29").getType());
      assertEquals(XSchema.TIME_INSTANT, TextUtil.getType("2024-02-29 13:45:30").getType());
      assertEquals(XSchema.TIME, TextUtil.getType("13:45").getType());
   }

   // two dots or dashes between digits is a date, even a version number: 1.2.3 is read as
   // month.day.year with a two digit year. Pins the current behavior.
   @Test
   void threeNumbersWithDotsAreADate() {
      assertEquals(XSchema.DATE, TextUtil.getType("1.2.3").getType());
      assertEquals("M.d.yy", TextUtil.getType("1.2.3").getFormat());
   }

   // an empty value is parsed as a number completely (nothing is left over), so it is a double.
   // The file loader skips empty values before it asks for the type. Pins the current behavior.
   @Test
   void emptyValueIsADouble() {
      assertEquals(XSchema.DOUBLE, TextUtil.getType("").getType());
   }
}
