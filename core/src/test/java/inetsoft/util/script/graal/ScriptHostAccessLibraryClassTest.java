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
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77466: the class filter no longer admits every com./org. class on the
 * server class path by default, and library packages that reach classes and
 * members by name stay refused even when an operator turns the allowance on.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScriptHostAccessLibraryClassTest {
   /** A plain string helper: visible only when com_org is on. */
   private static final String PLAIN = "org.apache.commons.lang3.StringUtils";

   /** One class from each refused library family, all on the core class path. */
   private static final String[] REFUSED = {
      "org.apache.commons.lang3.ClassUtils",
      "org.apache.commons.lang3.SerializationUtils",
      "org.apache.commons.lang3.reflect.MethodUtils",
      "org.apache.commons.lang3.reflect.FieldUtils",
      "org.apache.commons.lang3.builder.ReflectionToStringBuilder",
      "org.apache.commons.beanutils.PropertyUtils",
      "org.apache.commons.text.StringSubstitutor",
      "org.apache.commons.io.FileUtils",
      "org.springframework.util.ClassUtils",
      "org.springframework.util.ReflectionUtils",
      "org.springframework.beans.BeanUtils",
      "org.springframework.expression.spel.standard.SpelExpressionParser",
      "org.codehaus.groovy.runtime.InvokerHelper",
      "org.apache.ignite.Ignition",
      "org.apache.logging.log4j.LogManager",
      "org.hsqldb.jdbc.JDBCDriver",
      "org.yaml.snakeyaml.Yaml",
      "org.objenesis.ObjenesisStd",
      "org.jsoup.Jsoup",
      "com.fasterxml.jackson.databind.ObjectMapper",
      "com.esotericsoftware.kryo.kryo5.Kryo",
      "com.google.common.reflect.Reflection",
      "com.jcraft.jsch.JSch",
      "com.zaxxer.hikari.HikariDataSource",
   };

   private GraalJavaScriptEngine engine;

   @AfterEach
   void teardown() {
      if(engine != null) {
         engine.close();
      }
   }

   private static Predicate<String> filter(boolean comOrg) {
      return ScriptHostAccess.classFilter(Set.of(), new String[0], comOrg);
   }

   @Test
   void refusedClassesAreOnTheClassPath() {
      for(String name : REFUSED) {
         assertDoesNotThrow(() -> Class.forName(name, false, getClass().getClassLoader()), name);
      }
   }

   @Test
   void comOrgIsOffByDefault() {
      Predicate<String> filter = ScriptHostAccess.classFilter();
      assertFalse(filter.test(PLAIN));
      assertFalse(filter.test("com.google.common.collect.ImmutableList"));

      for(String name : REFUSED) {
         assertFalse(filter.test(name), name);
      }

      // the java and inetsoft allowances are unchanged
      assertTrue(filter.test("java.awt.Color"));
      assertTrue(filter.test("java.util.ArrayList"));
      assertTrue(filter.test("inetsoft.graph.EGraph"));
   }

   @Test
   void comOrgOnStillRefusesLibraryFamilies() {
      Predicate<String> filter = filter(true);
      assertTrue(filter.test(PLAIN));
      assertTrue(filter.test("com.google.common.collect.ImmutableList"));

      for(String name : REFUSED) {
         assertFalse(filter.test(name), name);
      }
   }

   @Test
   void operatorSettingsCannotReopenLibraryFamilies() {
      String[] packages = { "org.springframework", "org.apache.commons", "com.fasterxml" };
      Set<String> extra = Set.of(REFUSED);
      Predicate<String> filter = ScriptHostAccess.classFilter(extra, packages, true);

      for(String name : REFUSED) {
         assertFalse(filter.test(name), name);
      }

      // a named package that is not refused still opens
      assertTrue(ScriptHostAccess.classFilter(Set.of(), new String[] { "org.apache.commons.lang3" },
                                              false).test(PLAIN));
      // and so does a named class
      assertTrue(ScriptHostAccess.classFilter(Set.of(PLAIN), new String[0], false).test(PLAIN));
   }

   @Test
   void jdkClassLoadingPackagesAreRefused() {
      Predicate<String> filter = filter(true);
      assertFalse(filter.test("java.awt.datatransfer.DataFlavor"));
      assertFalse(filter.test("java.awt.dnd.DragSource"));
      assertTrue(filter.test("java.awt.Color"));
      assertTrue(filter.test("java.awt.geom.Rectangle2D"));
   }

   @Test
   void refusedObjectArraysAreRefused() {
      Predicate<String> filter = filter(true);
      assertFalse(filter.test("[Lorg.apache.commons.lang3.ClassUtils;"));
      assertTrue(filter.test("[L" + PLAIN + ";"));
   }

   @ParameterizedTest
   @ValueSource(strings = { "base", "report" })
   void enginesRefuseComOrgByDefault(String kind) throws Exception {
      engine = "report".equals(kind) ? new ReportGraalJavaScriptEngine() : new GraalJavaScriptEngine();
      engine.init(new HashMap<>());

      assertTrue(run("Java.type('" + PLAIN + "')").startsWith("ERR:"));
      assertTrue(run("Java.type('org.apache.commons.lang3.ClassUtils')").startsWith("ERR:"));
      assertTrue(run("Java.type('java.awt.datatransfer.DataFlavor')").startsWith("ERR:"));
      // the legacy package roots resolve leaves through the same filter
      assertNotEquals("OK:function", run("typeof org.apache.commons.lang3.StringUtils"));
      assertEquals("OK:function", run("typeof Java.type('java.awt.Color')"));
   }

   private String run(String expr) throws Exception {
      Object result = engine.exec(engine.compile(
         "(function(){try{return 'OK:'+(" + expr + ");}catch(x){return 'ERR:'+x;}})()"),
                                  null, null);
      return String.valueOf(result);
   }
}
