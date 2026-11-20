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
package inetsoft.uql.rest.auth;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.storage.BlobStorageManager;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.jdbc.ConnectionPoolFactory;
import inetsoft.uql.rest.AbstractRestDataSource;
import inetsoft.uql.rest.datasource.adobeanalytics.AdobeAnalyticsDataSource;
import inetsoft.uql.rest.datasource.constantcontact.ConstantContactDataSource;
import inetsoft.uql.rest.datasource.keap.KeapDataSource;
import inetsoft.uql.rest.datasource.quickbooks.QuickbooksReportsDataSource;
import inetsoft.uql.rest.datasource.remedyforce.RemedyforceDataSource;
import inetsoft.uql.rest.datasource.twitter.TwitterDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.XEngine;
import inetsoft.uql.tabular.oauth.Tokens;
import inetsoft.uql.util.Config;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.IndexedStorage;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.RefreshTokenCredential;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #81957, #81961: updateTokens applied a Tokens as if every field the token service can
 * return were present. A refresh result without a refresh token must keep the old one (null and
 * empty alike, in the runtime instance and in the stored definition, with or without a
 * credential), and a result without an issue time must not give an expiration in 1970.
 * These are unit-level checks on the data source classes with a hand-built Tokens; whether the
 * hosted token service really omits either field is not shown here.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, OAuthTokenUpdateTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
class OAuthTokenUpdateTest {
   private static final long DAY = TimeUnit.HOURS.toMillis(24);
   private static final long SLACK = TimeUnit.MINUTES.toMillis(5);
   private static final String[] EXPIRY_CLASSES = { "Keap", "ConstantContact", "AdobeAnalytics" };

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   @Autowired
   private String securityInitializer;

   private final List<String> storedNames = new ArrayList<>();

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
   }

   @AfterEach
   void tearDown() {
      for(String name : storedNames) {
         try {
            registry.removeDataSource(name);
         }
         catch(Exception ignore) {
            // best effort, the storage goes with the Spring context
         }
      }
   }

   // the classes that write the tokens: the base class through the ones without an override
   // (QuickbooksReports, Remedyforce), and the ones that override updateTokens
   private static Map<String, Supplier<AbstractRestDataSource<?>>> sources() {
      Map<String, Supplier<AbstractRestDataSource<?>>> map = new LinkedHashMap<>();
      map.put("Twitter", TwitterDataSource::new);
      map.put("Keap", KeapDataSource::new);
      map.put("ConstantContact", ConstantContactDataSource::new);
      map.put("AdobeAnalytics", AdobeAnalyticsDataSource::new);
      map.put("QuickbooksReports", QuickbooksReportsDataSource::new);
      map.put("Remedyforce", RemedyforceDataSource::new);
      return map;
   }

   private static AbstractRestDataSource<?> create(String label, boolean withCredential,
                                                   String refreshToken)
   {
      AbstractRestDataSource<?> ds = sources().get(label).get();
      ds.setName("tokenUpdate" + label + (withCredential ? "Cred" : "Field"));
      ds.setClientId("client");
      ds.setClientSecret("secret");

      if(!withCredential) {
         ds.setCredential(null);
      }

      ds.setAccessToken("OLD-AT");
      ds.setRefreshToken(refreshToken);
      ds.setTokenExpiration(System.currentTimeMillis() - 60_000L);
      return ds;
   }

   private static boolean hasRefreshTokenCredential(String label) {
      return create(label, true, null).getCredential() instanceof RefreshTokenCredential;
   }

   // the properties are what QuickbooksReports (companyId) and Salesforce (instance URL) read
   private static Tokens.Builder tokens(String refreshToken) {
      return Tokens.builder().accessToken("NEW-AT").refreshToken(refreshToken)
         .properties(Map.of("realmId", "123", "instance_url", "https://example.com"));
   }

   private static Tokens refreshResult(String refreshToken) {
      return tokens(refreshToken).issued(System.currentTimeMillis())
         .expiration(System.currentTimeMillis() + 3_600_000L).build();
   }

   // the refresh as AbstractRestDataSource does it: updateTokens on the runtime instance, and
   // the same updateTokens on a copy of the stored definition, which is then saved
   private AbstractRestDataSource<?> refresh(AbstractRestDataSource<?> ds, Tokens tokens)
      throws Exception
   {
      registry.setDataSource(ds, false);
      storedNames.add(ds.getName());
      AbstractRestDataSource<?> runtime = (AbstractRestDataSource<?>) ds.clone();
      runtime.updateTokens(tokens);
      repository.updateDataSourceTokens(runtime, tokens);
      registry.clearCache();
      return runtime;
   }

   private AbstractRestDataSource<?> stored(AbstractRestDataSource<?> ds) {
      AbstractRestDataSource<?> stored =
         (AbstractRestDataSource<?>) registry.getDataSource(ds.getName());
      assertNotNull(stored, ds.getName());
      return stored;
   }

   // Bug #81957. The data sources keep the refresh token in the credential they are created with
   // (Twitter has no refresh token credential, its token is a field), in the runtime instance
   // and in the stored definition
   @Test
   void refreshResultWithoutRefreshTokenKeepsTheOldOne() throws Exception {
      for(String label : sources().keySet()) {
         for(String missing : new String[] { null, "" }) {
            String msg = label + " new='" + missing + "'";
            AbstractRestDataSource<?> ds = create(label, true, "OLD-RT");
            AbstractRestDataSource<?> runtime = refresh(ds, refreshResult(missing));

            // the new access token is applied, the refresh token is not blanked
            assertEquals("NEW-AT", runtime.getAccessToken(), msg);
            assertEquals("OLD-RT", runtime.getRefreshToken(), "runtime " + msg);
            assertEquals("NEW-AT", stored(ds).getAccessToken(), msg);
            assertEquals("OLD-RT", stored(ds).getRefreshToken(), "stored " + msg);
         }
      }
   }

   // the same for a refresh token that is a field and not in a credential
   @Test
   void refreshResultWithoutRefreshTokenKeepsTheOldFieldValue() {
      for(String label : sources().keySet()) {
         for(String missing : new String[] { null, "" }) {
            AbstractRestDataSource<?> ds = create(label, false, "OLD-RT");
            assertFalse(ds.getCredential() instanceof RefreshTokenCredential, label);
            ds.updateTokens(refreshResult(missing));
            assertEquals("OLD-RT", ds.getRefreshToken(), label + " new='" + missing + "'");
         }
      }
   }

   // the credential-backed case: the token the credential holds is kept
   @Test
   void refreshResultWithoutRefreshTokenKeepsTheOldCredentialValue() {
      int count = 0;

      for(String label : sources().keySet()) {
         if(!hasRefreshTokenCredential(label)) {
            continue;
         }

         count++;

         for(String missing : new String[] { null, "" }) {
            AbstractRestDataSource<?> ds = create(label, true, "OLD-RT");
            RefreshTokenCredential credential = (RefreshTokenCredential) ds.getCredential();
            assertEquals("OLD-RT", credential.getRefreshToken(), label);
            ds.updateTokens(refreshResult(missing));
            assertEquals("OLD-RT", credential.getRefreshToken(),
                         label + " new='" + missing + "'");
         }
      }

      assertTrue(count >= 1, "no class has a refresh token credential");
   }

   // a refresh token the service does send replaces the old one (rotating providers)
   @Test
   void aNewRefreshTokenReplacesTheOldOne() throws Exception {
      for(String label : sources().keySet()) {
         AbstractRestDataSource<?> ds = create(label, true, "OLD-RT");
         AbstractRestDataSource<?> runtime = refresh(ds, refreshResult("NEW-RT"));

         assertEquals("NEW-RT", runtime.getRefreshToken(), "runtime " + label);
         assertEquals("NEW-RT", stored(ds).getRefreshToken(), "stored " + label);
      }
   }

   // with no old refresh token there is nothing to keep: a new one is stored, and a result
   // without one leaves the data source without
   @Test
   void withoutAnOldRefreshTokenTheNewOneIsStored() {
      for(String label : sources().keySet()) {
         AbstractRestDataSource<?> ds = create(label, false, null);
         ds.updateTokens(refreshResult("NEW-RT"));
         assertEquals("NEW-RT", ds.getRefreshToken(), label);

         ds = create(label, false, null);
         ds.updateTokens(refreshResult(null));
         assertNull(ds.getRefreshToken(), label);
      }
   }

   // the initial authorization uses the same updateTokens: a result without a refresh token keeps
   // the one the data source already had, this is the known limit stated in the PR
   @Test
   void authorizationWithoutRefreshTokenKeepsThePreviousOne() {
      AbstractRestDataSource<?> ds = create("Keap", false, "OLD-RT");
      ds.updateTokens(refreshResult(null));
      assertEquals("OLD-RT", ds.getRefreshToken());
   }

   // Bug #81961
   @Test
   void keapConstantContactAndAdobeWithoutIssueTimeDoNotExpireInThePast() throws Exception {
      for(String label : EXPIRY_CLASSES) {
         AbstractRestDataSource<?> ds = create(label, true, "OLD-RT");
         long before = System.currentTimeMillis();
         AbstractRestDataSource<?> runtime =
            refresh(ds, tokens("NEW-RT").issued(0L).expiration(0L).build());
         long after = System.currentTimeMillis();

         for(AbstractRestDataSource<?> check : List.of(runtime, stored(ds))) {
            long expiration = check.getTokenExpiration();
            assertTrue(expiration >= before + DAY && expiration <= after + DAY + SLACK,
                       label + " expiration " + expiration);
         }
      }
   }

   // a known issue time gives issued + 24 hours as before, the assertion of
   // OAuthTokenSaveTest.refreshUsesTheUpdateTokensOfTheConnector
   @Test
   void aKnownIssueTimeGivesIssuedPlus24Hours() throws Exception {
      for(String label : EXPIRY_CLASSES) {
         long issued = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(3);
         AbstractRestDataSource<?> ds = create(label, true, "OLD-RT");
         AbstractRestDataSource<?> runtime =
            refresh(ds, tokens("NEW-RT").issued(issued).expiration(0L).build());

         assertEquals(issued + DAY, runtime.getTokenExpiration(), label);
         assertEquals(issued + DAY, stored(ds).getTokenExpiration(), label);
      }
   }

   // a stale issue time is a real "refresh now" signal and stays in the past
   @Test
   void aStaleIssueTimeStaysInThePast() throws Exception {
      for(String label : EXPIRY_CLASSES) {
         long issued = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(48);
         AbstractRestDataSource<?> ds = create(label, true, "OLD-RT");
         AbstractRestDataSource<?> runtime =
            refresh(ds, tokens("NEW-RT").issued(issued).expiration(0L).build());

         assertEquals(issued + DAY, runtime.getTokenExpiration(), label);
         assertTrue(runtime.getTokenExpiration() < System.currentTimeMillis(), label);
         assertEquals(issued + DAY, stored(ds).getTokenExpiration(), label);
      }
   }

   // Adobe: without an issue time the expiration of the service is kept, not overwritten
   @Test
   void adobeWithoutIssueTimeKeepsTheServiceExpiration() throws Exception {
      long expiration = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2);
      AbstractRestDataSource<?> ds = create("AdobeAnalytics", false, "OLD-RT");
      AbstractRestDataSource<?> runtime =
         refresh(ds, tokens("NEW-RT").issued(0L).expiration(expiration).build());

      assertEquals(expiration, runtime.getTokenExpiration());
      assertEquals(expiration, stored(ds).getTokenExpiration());
   }

   // Adobe with an issue time still gives issued + 24 hours over a valid expiration of the
   // service, as before (deliberately unchanged)
   @Test
   void adobeWithAnIssueTimeStillUsesIssuedPlus24Hours() {
      long issued = System.currentTimeMillis();
      AbstractRestDataSource<?> ds = create("AdobeAnalytics", false, "OLD-RT");
      ds.updateTokens(tokens("NEW-RT").issued(issued)
                         .expiration(issued + TimeUnit.HOURS.toMillis(2)).build());

      assertEquals(issued + DAY, ds.getTokenExpiration());
   }

   // the beans that a save through the real XEngine and DataSourceRegistry needs
   @Configuration
   static class Beans {
      // the REST plugin is not installed in the test JVM, map its types to the classes
      @Bean
      public Config config() throws Exception {
         Config config = mock(Config.class);

         for(Supplier<AbstractRestDataSource<?>> supplier : sources().values()) {
            AbstractRestDataSource<?> ds = supplier.get();
            when(config.getDataSourceClass(ds.getType())).thenReturn(ds.getClass().getName());
         }

         when(config.getClass(anyString(), anyString()))
            .thenAnswer(inv -> Class.forName(inv.<String>getArgument(1)));
         return config;
      }

      @Bean
      public IndexedStorage indexedStorage(BlobStorageManager blobStorageManager) {
         return new BlobIndexedStorage(blobStorageManager);
      }

      @Bean
      public DataSourceRegistry dataSourceRegistry(IndexedStorage indexedStorage, Config config,
                                                   Cluster cluster) throws Exception
      {
         return new DataSourceRegistry(indexedStorage, config, cluster);
      }

      @Bean
      public ConnectionPoolFactory connectionPoolFactory() {
         return mock(ConnectionPoolFactory.class);
      }

      @Bean
      public XRepository xRepository(Cluster cluster, Config config,
                                     DataSourceRegistry dataSourceRegistry,
                                     ConnectionPoolFactory connectionPoolFactory)
      {
         return new XEngine(cluster, config, dataSourceRegistry, connectionPoolFactory);
      }

      // the storage of the data sources needs the security provider
      @Bean
      public String securityInitializer(SecurityEngine securityEngine) {
         securityEngine.init();
         return "initialized";
      }

      @Bean
      public RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }

      // the constructors of these beans are package private
      @Bean
      public DependencyStorageService dependencyStorageService(KeyValueStorageManager storage)
         throws Exception
      {
         Constructor<DependencyStorageService> ctor =
            DependencyStorageService.class.getDeclaredConstructor(KeyValueStorageManager.class);
         ctor.setAccessible(true);
         return ctor.newInstance(storage);
      }

      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }
   }
}
