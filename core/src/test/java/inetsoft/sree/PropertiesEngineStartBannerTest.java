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
package inetsoft.sree;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #78124: the "InetSoft ... started" banner must be logged when the properties are first
 * loaded, but not on the reload that every property save causes on every node.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PropertiesEngineStartBannerTest {
   @BeforeEach
   void attachAppender() {
      logger = (Logger) LoggerFactory.getLogger(PropertiesEngine.class);
      originalLevel = logger.getLevel();
      logger.setLevel(Level.INFO);
      appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
      engine = PropertiesEngine.getInstance();
      engine.clear();
   }

   @AfterEach
   void detachAppender() {
      logger.detachAppender(appender);
      logger.setLevel(originalLevel);
      engine.clear();
      engine.init();
   }

   @Test
   void bannerIsLoggedOnStartOnly() {
      engine.init();
      assertEquals(1, countBanners(), "the start banner is not logged on the first load");

      appender.list.clear();
      engine.init(true);
      engine.init(true);
      assertEquals(0, countBanners(), "the start banner is logged on a property reload");

      // a second init() on the loaded properties is not a start either
      engine.init();
      assertEquals(0, countBanners());
   }

   private long countBanners() {
      return appender.list.stream()
         .filter(e -> e.getLevel() == Level.INFO)
         .map(ILoggingEvent::getFormattedMessage)
         .filter(m -> m.startsWith("InetSoft ") && m.endsWith(" started"))
         .count();
   }

   private Logger logger;
   private Level originalLevel;
   private ListAppender<ILoggingEvent> appender;
   private PropertiesEngine engine;
}
