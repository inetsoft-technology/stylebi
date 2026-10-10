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
package inetsoft.graph.aesthetic;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.FileNotFoundException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78238: an SVGShape resource is resolved only from the data space and the
 * classpath. A caller-supplied URL (http, jar:http, file) must never be opened.
 * <p>
 * The SVG parser (Batik) is not on core's test classpath, so the test checks the
 * failure that SVGShape logs: an absolute URI must fail as "not found" before the
 * parser or any URL is touched. With Batik present the local server also proves
 * no request is sent.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SVGShapeResourceResolutionTest {
   private static final String SVG =
      "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" " +
      "viewBox=\"0 0 16 16\"><rect width=\"16\" height=\"16\"/></svg>";

   private HttpServer server;
   private final AtomicInteger requests = new AtomicInteger();
   private String baseUrl;
   private ListAppender<ILoggingEvent> appender;
   private Logger logger;

   @BeforeEach
   void setUp() throws Exception {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", exchange -> {
         requests.incrementAndGet();
         byte[] body = SVG.getBytes(StandardCharsets.UTF_8);
         exchange.getResponseHeaders().add("Content-Type", "image/svg+xml");
         exchange.sendResponseHeaders(200, body.length);
         exchange.getResponseBody().write(body);
         exchange.close();
      });
      server.start();
      baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

      logger = (Logger) LoggerFactory.getLogger(SVGShape.class);
      appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
   }

   @AfterEach
   void tearDown() {
      logger.detachAppender(appender);
      server.stop(0);
   }

   @Test
   void httpUriIsNotFetched() {
      assertNotResolved(baseUrl + "/remote.svg");
   }

   @Test
   void jarHttpUriIsNotFetched() {
      assertNotResolved("jar:" + baseUrl + "/remote.jar!/remote.svg");
   }

   @Test
   void fileUriIsNotOpened(@TempDir Path dir) throws Exception {
      Path file = dir.resolve("local.svg");
      Files.writeString(file, SVG);
      assertNotResolved(file.toUri().toString());
   }

   @Test
   void classpathResourceIsStillResolved() {
      Image image = new SVGShape("images/check.svg").getImage(new Dimension(17, 17));

      // without Batik the parser fails to load, which is fine; it must not be "not found"
      if(image == null) {
         assertFalse(hasNotFound(), "classpath resource reported as not found");
      }
   }

   private void assertNotResolved(String uri) {
      Image image = new SVGShape(uri).getImage(new Dimension(16, 16));

      assertNull(image, uri);
      assertEquals(0, requests.get(), "a request was sent for " + uri);
      assertTrue(hasNotFound(), "expected a not-found failure for " + uri);
   }

   private boolean hasNotFound() {
      for(ILoggingEvent event : appender.list) {
         for(IThrowableProxy t = event.getThrowableProxy(); t != null; t = t.getCause()) {
            if(FileNotFoundException.class.getName().equals(t.getClassName())) {
               return true;
            }
         }
      }

      return false;
   }
}
