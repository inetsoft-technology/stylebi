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

package inetsoft.report.internal.table;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.util.Tool;
import inetsoft.util.UserMessage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.Format;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77456: TableFormat.getFormat is called once per cell, so a rejected spec must be
 * cached like a good one instead of being rebuilt and logged with a stack trace per cell,
 * while the per-request user message keeps being added.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TableFormatFailedSpecCacheTest {
   @BeforeEach
   void setUp() {
      TableFormat.invalidateTableFormatCache();
      Tool.clearUserMessage();
      logger = (Logger) LoggerFactory.getLogger(TableFormat.class);
      // production default is log.detail.level=INFO
      oldLevel = logger.getLevel();
      logger.setLevel(Level.INFO);
      appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
   }

   @AfterEach
   void tearDown() {
      logger.detachAppender(appender);
      logger.setLevel(oldLevel);
      Tool.clearUserMessage();
      TableFormat.invalidateTableFormatCache();
   }

   @ParameterizedTest(name = "{0} \"{1}\"")
   @CsvSource(delimiter = '|', value = {
      "MessageFormat|{0,choice,}",
      "MessageFormat|{0",
      "DecimalFormat|#,##0.0.0",
      "DateFormat|yyyy-qq"
   })
   void rejectedSpecIsLoggedWithTraceOnlyOnce(String type, String spec) {
      for(int i = 0; i < 50; i++) {
         assertNull(TableFormat.getFormat(type, spec, Locale.US), "lookup " + i);
      }

      assertEquals(1, appender.list.size(), "a rejected spec must be logged once, not per lookup");
      assertNotNull(appender.list.get(0).getThrowableProxy(), "the first failure keeps its trace");
   }

   @Test
   void cachedFailureStillAddsUserMessageOnLaterRequests() throws Exception {
      String spec = "{0,choice,}";
      String expected = "Failed to get format \"MessageFormat\" for specification \"" + spec +
         "\" in locale " + Locale.US;
      assertNull(TableFormat.getFormat("MessageFormat", spec, Locale.US));
      assertTrue(Tool.existUserMessage(expected));

      // user messages are per thread (request), so a later request is another thread that
      // only ever hits the cached failure
      AtomicReference<UserMessage> later = new AtomicReference<>();
      AtomicReference<Format> laterFormat = new AtomicReference<>(new java.text.DecimalFormat());
      Thread thread = new Thread(() -> {
         laterFormat.set(TableFormat.getFormat("MessageFormat", spec, Locale.US));
         TableFormat.getFormat("MessageFormat", spec, Locale.US);
         later.set(Tool.getUserMessage());
      });
      thread.start();
      thread.join(10000);

      assertNull(laterFormat.get());
      assertNotNull(later.get(), "a cached failure must still warn the user");
      assertEquals(expected, later.get().getMessage(), "the message is added once per request");
      assertEquals(1, appender.list.size());
   }

   @Test
   void invalidatingTheCacheRebuildsARejectedSpec() {
      assertNull(TableFormat.getFormat("DecimalFormat", "#,##0.0.0", Locale.US));
      TableFormat.invalidateTableFormatCache();
      assertNull(TableFormat.getFormat("DecimalFormat", "#,##0.0.0", Locale.US));
      assertEquals(2, appender.list.size());
   }

   @Test
   void validSpecIsUnaffected() {
      Format first = TableFormat.getFormat("DecimalFormat", "#,##0.00", Locale.US);
      Format second = TableFormat.getFormat("DecimalFormat", "#,##0.00", Locale.US);
      assertNotNull(first);
      assertSame(first, second, "same thread gets the cached thread-local copy");
      assertEquals("1,234.50", first.format(1234.5));
      assertEquals("5 USD", TableFormat.getFormat("MessageFormat", "{0} USD", Locale.US).format(new Object[] { 5 }));
      assertTrue(appender.list.isEmpty());
      assertNull(Tool.getUserMessage());
   }

   @Test
   void unknownFormatTypeStaysSilent() {
      assertNull(TableFormat.getFormat("Bogus", "x", Locale.US));
      assertNull(TableFormat.getFormat("Bogus", "x", Locale.US));
      assertTrue(appender.list.isEmpty());
      assertNull(Tool.getUserMessage());
   }

   @Test
   void cachedFailureDoesNotShadowADifferentKey() {
      // without a separator in the key, spec "null" and a null spec collide
      assertNull(TableFormat.getFormat("DateFormat", "null", Locale.US));
      Format fmt = TableFormat.getFormat("DateFormat", null, Locale.US);
      assertNotNull(fmt, "a valid null-spec date format must not be shadowed by spec \"null\"");
   }

   @Test
   void tableLensReadLogsEachRejectedColumnFormatOnce() throws Exception {
      String[][] specs = {
         { "MessageFormat", "{0,choice,}" }, { "DecimalFormat", "#,##0.0.0" }, { "DateFormat", "yyyy-qq" }
      };

      assertEquals(3, readCells(specs).size(), "each column format warns the first request");

      // a later request reads the same cells on another thread
      AtomicReference<List<String>> later = new AtomicReference<>();
      Thread thread = new Thread(() -> later.set(readCells(specs)));
      thread.start();
      thread.join(10000);

      assertEquals(3, later.get().size(), "each column format still warns a later request");
      assertEquals(3, appender.list.size(), "one trace per rejected spec, not one per cell");
   }

   // formats every data cell of a 3-column table and returns this request's user messages
   private static List<String> readCells(String[][] specs) {
      Object[][] data = new Object[21][];
      data[0] = new Object[] { "message", "decimal", "date" };

      for(int r = 1; r < data.length; r++) {
         data[r] = new Object[] { r, r * 1.5, new Date() };
      }

      DefaultTableLens table = new DefaultTableLens(data);
      table.setHeaderRowCount(1);
      FormatTableLens2 lens = new FormatTableLens2(table);

      for(int c = 0; c < specs.length; c++) {
         TableFormat format = new TableFormat();
         format.format = specs[c][0];
         format.format_spec = specs[c][1];
         lens.getFormatMap().put(table.getDescriptor().getColDataPath(c), format);
      }

      for(int r = 1; r < data.length; r++) {
         for(int c = 0; c < specs.length; c++) {
            assertEquals(data[r][c], lens.getObject(r, c), "an unformatted value is shown");
         }
      }

      UserMessage message = Tool.getUserMessage();
      return message == null ? List.of() : List.of(message.getMessage().split("\n"));
   }

   private Logger logger;
   private Level oldLevel;
   private ListAppender<ILoggingEvent> appender;
}
