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

import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.util.ThreadContext;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77539: a registry data source a sheet script looks up itself must be one the script's
 * user may read. The data sources a running asset resolves, and every lookup outside a
 * script, are not checked, as before.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  ScriptDataSourceAccessTest.Credentials.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScriptDataSourceAccessTest {
   @Configuration
   static class Credentials {
      @Bean
      public CredentialService credentialService() throws Exception {
         // package-private constructor, normally reached by component scanning
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }
   }

   private static final String ORG_ID = "sda_org_id";
   private static final String DS = "sda_ds77539";
   // no permission of its own: readable through the data source root, as by default
   private static final String OPEN_DS = "sda_open77539";

   @BeforeAll
   static void security() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("sda_org", ORG_ID)
         .addUser("sdaReader", ORG_ID, "password")
         .addUser("sdaOutsider", ORG_ID, "password")
         .grantPermission(ResourceType.DATA_SOURCE, DS, ResourceAction.READ,
                          "sdaReader", Identity.USER, ORG_ID)
         .markPermissionEdited(ResourceType.DATA_SOURCE, DS, ORG_ID)
         .setup();
      reader = builder.principalOf("sdaReader", ORG_ID);
      outsider = builder.principalOf("sdaOutsider", ORG_ID);

      ThreadContext.setContextPrincipal(reader);

      try {
         // the registry root of an org is made at login
         DataSourceRegistry.getRegistry().init();

         for(String name : new String[] { DS, OPEN_DS }) {
            JDBCDataSource ds = new JDBCDataSource();
            ds.setName(name);
            ds.setCustom(true);
            ds.setDriver("org.h2.Driver");
            ds.setURL("jdbc:h2:mem:" + name);
            ds.setRequireLogin(false);
            XRepository.getRepository().updateDataSource(ds, null, false);
         }
      }
      finally {
         ThreadContext.setContextPrincipal(null);
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
      engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());

      table = new BoundTableAssembly(new Worksheet(), "T1");
      table.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, DS, "T"));
      engine.put("tbl", table);
   }

   @AfterEach
   void teardown() {
      if(engine != null) {
         engine.close();
      }

      ThreadContext.setContextPrincipal(null);
   }

   @Test void aScriptsOwnLookupNeedsReadOnTheDataSource() throws Exception {
      String script = "Java.type('inetsoft.uql.service.ScriptDataSourceProbe').canRead('" +
         DS + "')";

      assertEquals(Boolean.TRUE, run(script, reader), "a reader");
      assertEquals(Boolean.FALSE, run(script, outsider), "a user without read");
      assertEquals(Boolean.FALSE, run(script, null), "no user");
   }

   @Test void aLookupOutsideAScriptIsNotChecked() {
      ThreadContext.setContextPrincipal(outsider);
      assertTrue(ScriptDataSourceProbe.canRead(DS));
   }

   // product code a script calls reads its data sources as it does outside a script
   @Test void aLookupOfProductCodeAScriptCallsIsNotChecked() throws Exception {
      String script = "Java.type('inetsoft.report.lens.ScriptDataSourceRelay').canRead('" +
         DS + "')";

      assertEquals(Boolean.TRUE, run(script, outsider));
   }

   // the reported route, a data source looked up through the asset helper. The test
   // registry cannot load the stored data source, so a lookup returns null either way: the
   // gate's refusals show its decision on the real call stack
   @Test void aScriptsOwnAssetUtilLookupNeedsReadOnTheDataSource() throws Exception {
      String script = "Java.type('inetsoft.uql.asset.internal.AssetUtil').getDataSource(tbl)";

      assertEquals(0, refusals(() -> run(script, reader)), "a reader");
      assertEquals(1, refusals(() -> run(script, outsider)), "a user without read");
      assertEquals(0, refusals(() -> {
         ThreadContext.setContextPrincipal(outsider);
         return AssetUtil.getDataSource(table);
      }), "outside a script");
   }

   @Test void aLookupProductCodeAScriptCallsMakesIsNotChecked() throws Exception {
      String script =
         "Java.type('inetsoft.report.lens.ScriptDataSourceRelay').dataSourceName(tbl)";

      assertEquals(0, refusals(() -> run(script, outsider)));
   }

   // a script's own listing shows what its user may read, and leaves the shared name cache
   // whole for every other caller
   @Test void aScriptsOwnListingShowsTheReadableDataSources() throws Exception {
      String script = "Java.type('inetsoft.uql.service.ScriptDataSourceProbe').names()";

      List<String> outsiders = List.of(((String) run(script, outsider)).split(","));
      assertTrue(outsiders.contains(OPEN_DS), outsiders::toString);
      assertFalse(outsiders.contains(DS), outsiders::toString);

      ThreadContext.setContextPrincipal(outsider);
      String plain = ScriptDataSourceProbe.names();
      assertTrue(List.of(plain.split(",")).containsAll(List.of(DS, OPEN_DS)), plain);
      assertEquals(plain, run(script, reader));
   }

   @Test void theStackDecidesWhetherTheAccessIsTheScriptsOwn() {
      // the script called the data source plumbing through the GraalJS host interop
      assertTrue(ScriptDataSourceAccess.isScriptAccess(List.of(
         "inetsoft.uql.service.DataSourceRegistry", "inetsoft.uql.service.XEngine",
         "inetsoft.uql.asset.internal.AssetUtil", "jdk.internal.reflect.DirectMethodHandle",
         "java.lang.invoke.LambdaForm$MH", "com.oracle.truffle.host.HostMethodDesc$SingleMethod",
         "inetsoft.report.composition.execution.ViewsheetSandbox").iterator()));
      // product code ran between the script and the plumbing
      assertFalse(ScriptDataSourceAccess.isScriptAccess(List.of(
         "inetsoft.uql.service.DataSourceRegistry", "inetsoft.uql.service.XEngine",
         "inetsoft.report.composition.execution.PhysicalBoundQuery",
         "com.oracle.truffle.host.HostMethodDesc$SingleMethod").iterator()));
      // a script-facing proxy of the engine is product code too
      assertFalse(ScriptDataSourceAccess.isScriptAccess(List.of(
         "inetsoft.uql.service.DataSourceRegistry",
         "inetsoft.util.script.graal.ScopeProxy",
         "com.oracle.truffle.host.HostProxy").iterator()));
      // no script on the stack
      assertFalse(ScriptDataSourceAccess.isScriptAccess(List.of(
         "inetsoft.uql.service.DataSourceRegistry", "inetsoft.uql.service.XEngine").iterator()));
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

   private GraalJavaScriptEngine engine;
}
