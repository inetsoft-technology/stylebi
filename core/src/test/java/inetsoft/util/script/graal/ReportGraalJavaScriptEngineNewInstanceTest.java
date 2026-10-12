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
package inetsoft.util.script.graal;

import inetsoft.report.script.graal.ReportGraalJavaScriptEngine;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the newInstance() script global through the report engine used by
 * viewsheet and report scripts (Bug #77420), where script errors are swallowed
 * to null, and that the script.java.allowed.classes operator extra allows a
 * non-default class but cannot re-allow a blocked one.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ReportGraalJavaScriptEngineNewInstanceTest {
   private static final String EXTRA_PROPERTY = "script.java.allowed.classes";
   private static final String EXTRA_CLASS = "javax.swing.DefaultListModel";
   private static final String BLOCKED_CLASS = "java.util.concurrent.ConcurrentHashMap";

   private ReportGraalJavaScriptEngine engine;

   @BeforeEach
   void setup() throws Exception {
      engine = new ReportGraalJavaScriptEngine();
      engine.init(new java.util.HashMap<>());
      sentinelInitialized = false;
      sentinelConstructed = false;
   }

   @AfterEach
   void teardown() {
      SreeEnv.resetProperty(EXTRA_PROPERTY, false);
      engine.close();
   }

   private Object eval(String src) throws Exception {
      return engine.exec(engine.compile(src), null, null);
   }

   private String typeOf(String cls) throws Exception {
      return String.valueOf(eval("typeof newInstance('" + cls + "')"));
   }

   @Test
   void reportEngineRefusesBlockedClassAndBuildsAllowedOne() throws Exception {
      // the report engine swallows the refusal and returns null
      assertNull(eval("newInstance('" + BLOCKED_CLASS + "')"));
      assertNull(eval("newInstance('" + Sentinel.class.getName() + "')"));
      assertFalse(sentinelInitialized, "static initializer of a rejected class ran");
      assertFalse(sentinelConstructed, "constructor of a rejected class ran");

      Object size = eval("var l = newInstance('java.util.ArrayList'); l.add('a'); l.size()");
      assertEquals(1, ((Number) size).intValue());
   }

   @Test
   void operatorExtraAllowsNonDefaultClassOnly() throws Exception {
      // not on any default allow path, so refused without the extra
      assertNotEquals("object", typeOf(EXTRA_CLASS));

      SreeEnv.setProperty(EXTRA_PROPERTY, EXTRA_CLASS + "," + BLOCKED_CLASS);

      // read live on each call, no engine re-init
      assertEquals("object", typeOf(EXTRA_CLASS));
      // the extra cannot re-allow a class on the deny list
      assertNull(eval("newInstance('" + BLOCKED_CLASS + "')"));
   }

   static volatile boolean sentinelInitialized;
   static volatile boolean sentinelConstructed;

   /**
    * Lives in inetsoft.util.script.graal, which the class filter blocks.
    */
   public static class Sentinel {
      static {
         sentinelInitialized = true;
      }

      public Sentinel() {
         sentinelConstructed = true;
      }
   }
}
