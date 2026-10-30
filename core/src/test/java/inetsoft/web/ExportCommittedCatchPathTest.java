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
package inetsoft.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.web.composer.vs.controller.ExportController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class ExportCommittedCatchPathTest {
   private static final Class<? extends IOException> ABORT = new ByteBuddy()
      .subclass(IOException.class).name("org.apache.catalina.connector.ClientAbortException")
      .make().load(ExportCommittedCatchPathTest.class.getClassLoader(),
                   ClassLoadingStrategy.Default.WRAPPER).getLoaded();

   private Map<String, Level> run(Exception thrown) throws Exception {
      Logger log = (Logger) LoggerFactory.getLogger(ExportController.class);
      Level old = log.getLevel();
      log.setLevel(Level.DEBUG);
      ListAppender<ILoggingEvent> app = new ListAppender<>();
      app.start();
      log.addAppender(app);
      try {
         ExportController c = new ExportController(null, null, null, null, null, null, null);
         HttpServletRequest req = mock(HttpServletRequest.class);
         HttpServletResponse resp = mock(HttpServletResponse.class);
         when(resp.isCommitted()).thenReturn(true);
         when(req.getParameterMap()).thenAnswer(i -> { throw thrown; });
         when(req.getSession()).thenReturn(mock(jakarta.servlet.http.HttpSession.class));
         c.exportViewsheet("gallery/report", "host-org", Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), resp, req, null);
         Map<String, Level> r = new LinkedHashMap<>();
         for(ILoggingEvent e : app.list) {
            r.put(e.getFormattedMessage(), e.getLevel());
         }
         return r;
      }
      finally {
         log.detachAppender(app);
         log.setLevel(old);
      }
   }

   private Level level(Exception e) throws Exception {
      Collection<Level> v = run(e).values();
      assertEquals(1, v.size(), v.toString());
      return v.iterator().next();
   }

   @Test
   void ticket() throws Exception {
      Exception t = new AsyncRequestNotUsableException("ServletOutputStream failed to write: java.io.IOException: Broken pipe",
         ABORT.getConstructor(Throwable.class).newInstance(new IOException("Broken pipe")));
      assertEquals(Level.DEBUG, level(t));
   }

   @Test
   void abortVariants() throws Exception {
      for(String m : new String[] {"Broken pipe", "Connection reset", "Connection reset by peer",
         "An established connection was aborted by the software in your host machine",
         "An existing connection was forcibly closed by the remote host"}) {
         assertEquals(Level.DEBUG, level(new IOException(m)), m);
      }
   }

   @Test
   void realFailures() throws Exception {
      assertEquals(Level.ERROR, level(new IOException("No space left on device")));
      assertEquals(Level.ERROR, level(new IOException("Stream closed")));
      assertEquals(Level.ERROR, level(new RuntimeException("boom")));
      assertEquals(Level.ERROR, level(new IllegalStateException("Broken pipe")));
      assertEquals(Level.ERROR, level(new AsyncRequestNotUsableException("x", new IOException("No space left on device"))));
   }
}
