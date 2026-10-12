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
package inetsoft.mv;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.UserEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.*;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.util.Catalog;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.Plugins;
import inetsoft.util.ThreadContext;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78390: positive controls for the VPM check of an MV built for another identity, with
 * the real query plan. On a source without a VPM, the plan of a viewer equals the plan of the
 * identity the MV is built for, whatever locale either side runs in, so the MV is used.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, MVDefVPMPlanTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVDefVPMPlanTest {
   private static final String ORG = "host-org";
   private static final String TABLE = "T1";

   @Configuration
   static class JdbcConfig {
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

      @Bean
      public Plugins plugins(BlobStorageManager blobStorageManager, Cluster cluster,
                             ApplicationEventPublisher eventPublisher)
      {
         return new Plugins(blobStorageManager.getStorage("plugins", true), cluster,
                            eventPublisher);
      }

      @Bean
      public Config config(Plugins plugins) {
         return new Config(plugins);
      }

      @Bean
      public Drivers drivers(Plugins plugins) {
         return new Drivers(plugins, mock(ConnectionPoolFactory.class));
      }

      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   @BeforeEach
   void setUp() {
      oldPrincipal = ThreadContext.getPrincipal();
      oldContextPrincipal = ThreadContext.getContextPrincipal();
      oldLocale = ThreadContext.getLocale();
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setPrincipal(oldPrincipal);
      ThreadContext.setContextPrincipal(oldContextPrincipal);
      ThreadContext.setLocale(oldLocale);
   }

   @Test
   void identitiesWithoutVPMHaveEqualPlans() throws Exception {
      MVDef def = def();
      XPrincipal viewer = new XPrincipal(new IdentityID("b78390u", ORG));

      String build = def.getBuildPlan();

      assertNotNull(build);
      assertFalse(build.isEmpty());
      assertTrue(build.contains("orders"), build);
      assertTrue(def.isPlanEqual(viewer, build));
   }

   @Test
   void localeOfTheViewerDoesNotMatter() throws Exception {
      MVDef def = def();
      ThreadContext.setLocale(Locale.US);
      String build = def.getBuildPlan();

      XPrincipal viewer = new XPrincipal(new IdentityID("b78390u", ORG));
      viewer.setProperty(XPrincipal.LOCALE, "zh_CN");
      ThreadContext.setLocale(Locale.SIMPLIFIED_CHINESE);

      assertTrue(def.isPlanEqual(viewer, build));
      // the thread locale is restored after the plan is built
      assertEquals(Locale.SIMPLIFIED_CHINESE, ThreadContext.getLocale());
   }

   @Test
   void viewerIsNotModified() throws Exception {
      MVDef def = def();
      XPrincipal viewer = new XPrincipal(new IdentityID("b78390u", ORG));

      assertTrue(def.isPlanEqual(viewer, def.getBuildPlan()));
      assertNull(viewer.getProperty(SUtil.VPM_USER));
   }

   // the UserEnv catalog getter, installed JVM-wide by an in-process (cloud) scheduler, takes
   // the locale from the principal rather than the thread
   @Test
   void principalLocaleDoesNotMatterWithUserCatalog() throws Exception {
      withUserCatalog(() -> {
         SRPrincipal buildUser = principal(XPrincipal.SYSTEM, Locale.FRENCH);
         MVDef def = def(new DefaultIdentity(new IdentityID(XPrincipal.SYSTEM, ORG),
                                             Identity.USER)
         {
            @Override
            public XPrincipal create() {
               return buildUser;
            }
         });
         SRPrincipal viewer = principal("b78390u", Locale.GERMAN);
         ThreadContext.setContextPrincipal(viewer);

         String build = def.getBuildPlan();

         // the localized text is used, so the plans would differ in the users' own locales
         assertTrue(def.isPlanEqual(viewer, build), build);
         assertTrue(Catalog.getCatalog(viewer).getString("common.queryMergeOK")
                       .startsWith("[de]"));
         // the principals and the thread are not changed
         assertEquals(Locale.FRENCH, buildUser.getLocale());
         assertEquals(Locale.GERMAN, viewer.getLocale());
         assertSame(viewer, ThreadContext.getContextPrincipal());
      });
   }

   // without a build user, the UserEnv catalog getter takes the locale from the context
   // principal, which is the viewer
   @Test
   void contextLocaleDoesNotMatterWithUserCatalog() throws Exception {
      withUserCatalog(() -> {
         MVDef def = def((Identity[]) null);
         SRPrincipal viewer = principal("b78390u", Locale.GERMAN);
         SRPrincipal viewer2 = principal("b78390v", Locale.FRENCH);

         ThreadContext.setContextPrincipal(viewer);
         String build = def.getBuildPlan();
         ThreadContext.setContextPrincipal(viewer2);

         assertTrue(def.isPlanEqual(viewer2, build), build);
         assertEquals(Locale.GERMAN, viewer.getLocale());
         assertSame(viewer2, ThreadContext.getContextPrincipal());

         ThreadContext.setContextPrincipal(null);
         assertTrue(def.isPlanEqual(viewer2, build), build);
         assertNull(ThreadContext.getContextPrincipal());
      });
   }

   private static void withUserCatalog(ThrowingRunnable test) throws Exception {
      String key = Catalog.class.getName() + ".catalogGetter";
      Object getter = ConfigurationContext.getContext().put(key, UserEnv.getCatalogGetter());
      String resource = SreeEnv.getProperty(CATALOG_RESOURCE);
      String viewerLocale = SreeEnv.getProperty(VIEWER_LOCALE);
      SreeEnv.setProperty(CATALOG_RESOURCE, "inetsoft/mv/b78390catalog");
      SreeEnv.setProperty(VIEWER_LOCALE, "en_US");

      try {
         test.run();
      }
      finally {
         if(getter == null) {
            ConfigurationContext.getContext().remove(key);
         }
         else {
            ConfigurationContext.getContext().put(key, getter);
         }

         restoreProperty(CATALOG_RESOURCE, resource);
         restoreProperty(VIEWER_LOCALE, viewerLocale);
      }
   }

   private static void restoreProperty(String name, String value) {
      if(value == null) {
         SreeEnv.remove(name);
      }
      else {
         SreeEnv.setProperty(name, value);
      }
   }

   private static SRPrincipal principal(String name, Locale locale) {
      SRPrincipal user = new SRPrincipal(new IdentityID(name, ORG), new IdentityID[0],
                                         new String[0], ORG, 0L);
      user.setLocale(locale);
      return user;
   }

   private static MVDef def() throws Exception {
      return def(new DefaultIdentity(new IdentityID(XPrincipal.SYSTEM, ORG), Identity.USER));
   }

   private static MVDef def(Identity... users) throws Exception {
      Worksheet ws = new Worksheet();
      sqlTable(ws);

      MVDef def = spy(new MVDef());
      set(def, "tname", TABLE);
      set(def, "wsId", "1^2^__NULL__^ws1^" + ORG);
      def.setUsers(users);
      MVMetaData data = new MVMetaData();
      data.setBypassVPM(false);
      doReturn(data).when(def).getMetaData();
      doReturn(ws).when(def).getWorksheet();
      return def;
   }

   private static void sqlTable(Worksheet ws) throws Exception {
      String text = "select orders.id, orders.amount from orders";
      UniformSQL usql = new UniformSQL();
      Method parse = UniformSQL.class.getDeclaredMethod("parse", String.class, int.class,
                                                        long.class);
      parse.setAccessible(true);
      // parse(String, int, long) is package private, PARSE_ALL is 0
      parse.invoke(usql, text, 0, 4000L);
      usql.setSQLString(text, false);

      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("b78390ds");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:memory:b78390");
      ds.setRequireLogin(false);
      usql.setDataSource(ds);
      JDBCQuery query = new JDBCQuery();
      query.setName(TABLE);
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(usql);

      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, TABLE);
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.DATASOURCE, ds.getName(), ds.getName()));
      table.setSQLEdited(true);

      ColumnSelection columns = new ColumnSelection();

      for(String name : new String[] { "id", "amount" }) {
         ColumnRef ref = new ColumnRef(new AttributeRef(name));
         ref.setDataType(XSchema.INTEGER);
         columns.addAttribute(ref);
      }

      table.setColumnSelection(columns, false);
      table.setColumnSelection((ColumnSelection) columns.clone(), true);
      ws.addAssembly(table);
   }

   private static void set(Object target, String name, Object value) throws Exception {
      Field field = MVDef.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(target, value);
   }

   @FunctionalInterface
   private interface ThrowingRunnable {
      void run() throws Exception;
   }

   private static final String CATALOG_RESOURCE = "StyleReport.locale.resource";
   private static final String VIEWER_LOCALE = "viewer.locale";
   private Principal oldPrincipal;
   private Principal oldContextPrincipal;
   private Locale oldLocale;
}
