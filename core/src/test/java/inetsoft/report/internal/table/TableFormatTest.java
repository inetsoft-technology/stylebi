/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.Format;
import java.text.NumberFormat;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VCA-001: {@code CURRENCY_FORMAT}'s symbol/pattern is derived entirely from the locale
 * (NumberFormat.getCurrencyInstance) and a caller-supplied {@code format_spec} is discarded --
 * previously silently, now with a warning naming both facts so the discard is attributable.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class TableFormatTest {
   @Test
   void currencyFormatIgnoresFormatSpecButKeepsBehaviorUnchanged() {
      Format withSpec = TableFormat.getFormat(TableFormat.CURRENCY_FORMAT, "$#,##0.00", Locale.US);
      Format withoutSpec = TableFormat.getFormat(TableFormat.CURRENCY_FORMAT, null, Locale.US);

      assertEquals(NumberFormat.getCurrencyInstance(Locale.US), withSpec,
                   "a formatSpec must not change the CURRENCY_FORMAT result");
      assertEquals(withoutSpec, withSpec,
                   "CURRENCY_FORMAT must produce the same Format with or without a formatSpec");
   }

   @Test
   void currencyFormatWarnsNamingTheIgnoredSpecAndTheLocale() {
      // the warning is the fix: without it, a caller-supplied formatSpec is silently discarded
      // and there is no way to attribute a wrong-looking currency symbol back to this cause
      Logger logger = (Logger) LoggerFactory.getLogger(TableFormat.class);
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);

      try {
         TableFormat.getFormat(TableFormat.CURRENCY_FORMAT, "$#,##0.00-" + System.nanoTime(),
                                Locale.US);

         assertEquals(1, appender.list.size(),
                      "a non-empty formatSpec under CURRENCY_FORMAT must warn exactly once");

         ILoggingEvent event = appender.list.get(0);
         assertEquals(Level.WARN, event.getLevel(), "the discard must be reported at WARN");

         String message = event.getFormattedMessage();
         assertTrue(message.contains("CurrencyFormat"),
                    "the warning must name the format type, but was: " + message);
         assertTrue(message.contains("$#,##0.00"),
                    "the warning must quote the discarded formatSpec, but was: " + message);
      }
      finally {
         logger.detachAppender(appender);
      }
   }

   @Test
   void currencyFormatDoesNotWarnWhenNoFormatSpecIsSupplied() {
      Logger logger = (Logger) LoggerFactory.getLogger(TableFormat.class);
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);

      try {
         TableFormat.getFormat(TableFormat.CURRENCY_FORMAT, null, Locale.US);
         TableFormat.getFormat(TableFormat.CURRENCY_FORMAT, "", Locale.US);

         assertEquals(0, appender.list.size(),
                      "an absent/empty formatSpec is not a discard and must not warn");
      }
      finally {
         logger.detachAppender(appender);
      }
   }
}
