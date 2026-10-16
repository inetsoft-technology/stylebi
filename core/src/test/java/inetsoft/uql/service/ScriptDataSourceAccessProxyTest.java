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
package inetsoft.uql.service;

import inetsoft.report.lens.ProxiedScriptDataSourceRelay;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.jdbc.ConnectionPoolFactory;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.interceptor.DebugInterceptor;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.HashMap;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78237: production injects the registry into {@code XEngine} as a {@code @Lazy} Spring
 * proxy. The proxy's frames must not make a script's own lookup through the data source
 * plumbing look like product code, which the #77539 gate does not check. This class wires
 * {@code xRepository} as {@code EngineConfiguration} does, unlike the shared test
 * configuration.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  ScriptDataSourceAccessTest.Credentials.class,
                                  ScriptDataSourceAccessProxyTest.ProductionWiring.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScriptDataSourceAccessProxyTest {
   /** The {@code xRepository} bean of {@code EngineConfiguration}, with its lazy registry. */
   @Configuration
   static class ProductionWiring {
      @Bean
      @Lazy
      public XRepository xRepository(@Lazy Cluster cluster, @Lazy Config config,
                                     @Lazy DataSourceRegistry dataSourceRegistry,
                                     @Lazy ConnectionPoolFactory connectionPoolFactory)
      {
         return new XEngine(cluster, config, dataSourceRegistry, connectionPoolFactory);
      }
   }

   private static final String ORG_ID = "sdap_org_id";
   private static final String DS = "sdap_ds78237";
   // a script cannot read SourceInfo.PHYSICAL_TABLE through Java.type, so it gets the value
   private static final int PHYSICAL_TABLE = SourceInfo.PHYSICAL_TABLE;

   @BeforeAll
   static void security() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("sdap_org", ORG_ID)
         .addUser("sdapReader", ORG_ID, "password")
         .addUser("sdapOutsider", ORG_ID, "password")
         .grantPermission(ResourceType.DATA_SOURCE, DS, ResourceAction.READ,
                          "sdapReader", Identity.USER, ORG_ID)
         .markPermissionEdited(ResourceType.DATA_SOURCE, DS, ORG_ID)
         .setup();
      reader = builder.principalOf("sdapReader", ORG_ID);
      outsider = builder.principalOf("sdapOutsider", ORG_ID);

      Principal saved = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(reader);

      try {
         // the registry root of an org is made at login
         DataSourceRegistry.getRegistry().init();

         JDBCDataSource ds = new JDBCDataSource();
         ds.setName(DS);
         ds.setCustom(true);
         ds.setDriver("org.h2.Driver");
         ds.setURL("jdbc:h2:mem:" + DS);
         ds.setRequireLogin(false);
         XRepository.getRepository().updateDataSource(ds, null, false);
      }
      finally {
         ThreadContext.setContextPrincipal(saved);
      }
   }

   @AfterAll
   static void teardownSecurity() {
      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   @BeforeEach
   void setup() throws Exception {
      savedPrincipal = ThreadContext.getContextPrincipal();
      engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());

      table = new BoundTableAssembly(new Worksheet(), "T1");
      table.setSourceInfo(new SourceInfo(PHYSICAL_TABLE, DS, "T"));
      engine.put("tbl", table);
   }

   @AfterEach
   void teardown() {
      if(engine != null) {
         engine.close();
      }

      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   // the wiring the defect depends on: without the proxy the cases below pass unfixed
   @Test void theEngineHoldsAProxyOfTheRegistry() throws Exception {
      XRepository repository = XRepository.getRepository();
      assertInstanceOf(XEngine.class, repository);

      Field field = XEngine.class.getDeclaredField("dataSourceRegistry");
      field.setAccessible(true);
      assertTrue(AopUtils.isAopProxy(field.get(repository)), "the registry is a proxy");
   }

   // the reported route
   @Test void aScriptsOwnAssetUtilLookupThroughTheProxyNeedsRead() throws Exception {
      String script = "Java.type('inetsoft.uql.asset.internal.AssetUtil').getDataSource(tbl)";

      assertEquals(0, refusals(() -> run(script, reader)), "a reader");
      assertEquals(1, refusals(() -> run(script, outsider)), "a user without read");
   }

   @Test void aScriptsOwnLogicalModelListingThroughTheProxyNeedsRead() throws Exception {
      String script = "Java.type('inetsoft.uql.util.XUtil').getLogicalModels('" + DS + "')";

      assertEquals(0, refusals(() -> run(script, reader)), "a reader");
      assertEquals(1, refusals(() -> run(script, outsider)), "a user without read");
   }

   // the entity and attribute listings read the data model through the same proxy: a refused
   // lookup leaves them empty, and the script gets no error
   @Test void aScriptsOwnEntityAndAttributeListingsThroughTheProxyNeedRead() throws Exception {
      String xutil = "Java.type('inetsoft.uql.util.XUtil')";
      String[] scripts = {
         xutil + ".getEntities('" + DS + "', 'LM', null, false).length",
         xutil + ".getAttributes('" + DS + "', 'LM', 'E', null, false, false).length"
      };

      for(String script : scripts) {
         Object[] result = { "unset" };

         assertEquals(0, refusals(() -> run(script, reader)), "a reader: " + script);
         assertEquals(1, refusals(() -> result[0] = run(script, outsider)),
                      "a user without read: " + script);
         assertEquals(0, ((Number) result[0]).intValue(), script);
      }
   }

   // a table the script builds itself, naming any data source
   @Test void aTableTheScriptBuildsItselfNeedsRead() throws Exception {
      String script =
         "var BT = Java.type('inetsoft.uql.asset.BoundTableAssembly');" +
         "var WS = Java.type('inetsoft.uql.asset.Worksheet');" +
         "var SI = Java.type('inetsoft.uql.asset.SourceInfo');" +
         "var t = new BT(new WS(), 'T2');" +
         "t.setSourceInfo(new SI(" + PHYSICAL_TABLE + ", '" + DS + "', 'T'));" +
         "Java.type('inetsoft.uql.asset.internal.AssetUtil').getDataSource(t)";

      assertEquals(0, refusals(() -> run(script, reader)), "a reader");
      assertEquals(1, refusals(() -> run(script, outsider)), "a user without read");
   }

   // a refused lookup leaves the table without a data source: its SQL helper is null, and
   // the script gets no error
   @Test void aRefusedSqlHelperLookupReturnsNull() throws Exception {
      Object[] result = { "unset" };

      assertEquals(1, refusals(() -> result[0] = run("tbl.getSQLHelper()", outsider)));
      assertNull(result[0]);
   }

   // a Spring-proxied product bean a script calls still reads its data sources unchecked:
   // its own frame, inside the proxy, decides
   @Test void aLookupOfAProxiedProductBeanIsNotChecked() throws Exception {
      for(boolean advised : new boolean[] { false, true }) {
         engine.put("p", proxy(new ProxiedScriptDataSourceRelay(), advised));
         assertEquals(0, refusals(() -> run("p.dataSourceName(tbl)", outsider)),
                      "advised=" + advised);
      }
   }

   // a Spring-proxied data source helper is plumbing, so the script's lookup is checked
   @Test void aLookupOfAProxiedDataSourceHelperNeedsRead() throws Exception {
      for(boolean advised : new boolean[] { false, true }) {
         engine.put("p", proxy(new ProxiedScriptDataSourceHelper(), advised));
         assertEquals(1, refusals(() -> run("p.dataSourceName(tbl)", outsider)),
                      "advised=" + advised);
      }
   }

   /** A CGLIB proxy of {@code target}, with an empty advice chain or a Spring interceptor. */
   private static Object proxy(Object target, boolean advised) {
      ProxyFactory factory = new ProxyFactory(target);
      factory.setProxyTargetClass(true);

      if(advised) {
         factory.addAdvice(new DebugInterceptor());
      }

      Object proxy = factory.getProxy();
      assertTrue(AopUtils.isCglibProxy(proxy));
      return proxy;
   }

   /** The number of refusals of {@link #DS} the gate logs while {@code action} runs. */
   private static int refusals(Callable<?> action) throws Exception {
      Logger logger = (Logger) LoggerFactory.getLogger(ScriptDataSourceAccess.class);
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);

      try {
         action.call();
      }
      finally {
         logger.detachAppender(appender);
      }

      return (int) appender.list.stream()
         .filter(e -> e.getFormattedMessage().contains("the data source " + DS + " ")).count();
   }

   private Object run(String script, SRPrincipal user) throws Exception {
      ThreadContext.setContextPrincipal(user);
      return engine.exec(engine.compile(script), null, null);
   }

   private static SecurityTestDataBuilder builder;
   private static SRPrincipal reader;
   private static SRPrincipal outsider;
   private BoundTableAssembly table;
   private Principal savedPrincipal;
   private GraalJavaScriptEngine engine;
}
