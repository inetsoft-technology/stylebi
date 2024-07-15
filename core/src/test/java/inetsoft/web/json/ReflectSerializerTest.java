/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.json;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReflectSerializerTest {
   private ListAppender<ILoggingEvent> loggingEventListAppender;
   private ObjectMapper mapper;

   @BeforeAll
   static void configureLogging() {
   }

   @BeforeEach
   void setUp() throws Exception {
      mapper = new ObjectMapper();
      mapper.registerModule(new Jdk8Module());
      mapper.registerModule(new JavaTimeModule());
      mapper.registerModule(new ThirdPartySupportModule());
      mapper.registerModule(new GuavaModule());
      mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
      loggingEventListAppender = getListAppenderForClass(ReflectSerializer.class);
   }

   @Test
   public void testSerializeMethod() throws Exception {
      ParentModel model = new ParentModel(
         new ChildModel(getClass().getMethod("testSerializeMethod")));
      String json = mapper.writeValueAsString(model);
      assertEquals("{\"child\":{\"method\":null}}", json);
      String newline = System.getProperty("line.separator");
      String expected = "Attempted to write java.lang.reflect.Method as JSON:" + newline +
         "inetsoft.web.json.ReflectSerializerTest$ParentModel" + newline +
         "  child: inetsoft.web.json.ReflectSerializerTest$ChildModel" + newline +
         "    method: java.lang.reflect.Method";
      
      assertThat(
         loggingEventListAppender.list,
         Matchers.contains(Matchers.allOf(
            Matchers.hasProperty("message", Matchers.equalTo(expected)),
            Matchers.hasProperty("level", Matchers.equalTo(Level.ERROR)))));
   }

   private static ListAppender<ILoggingEvent> getListAppenderForClass(Class<?> clazz) {
      org.slf4j.Logger slf4jLogger = LoggerFactory.getLogger(clazz);
      Logger logger = (Logger) slf4jLogger;

      ListAppender<ILoggingEvent> loggingEventListAppender = new ListAppender<>();
      loggingEventListAppender.start();

      logger.addAppender(loggingEventListAppender);

      return loggingEventListAppender;
   }

   public static class ParentModel {
      public ParentModel(ChildModel child) {
         this.child = child;
      }

      public ChildModel child;
   }

   public static class ChildModel {
      public ChildModel(Method method) {
         this.method = method;
      }

      public Method method;
   }
}