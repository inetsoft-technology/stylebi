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

package inetsoft.test;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MockCluster;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.storage.*;
import inetsoft.storage.fs.FilesystemBlobEngineFactory;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.*;
import inetsoft.util.config.*;
import inetsoft.util.log.LogManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

@Configuration
public class BaseTestConfiguration {

   @Bean
   public Cluster cluster() {
      return new MockCluster();
   }

   @Bean
   public InetsoftConfig inetsoftConfig(Environment environment) {
      String home = environment.getProperty("sree.home", System.getProperty("sree.home", "."));
      Path configFile = Paths.get(home, "inetsoft.yaml");
      InetsoftConfig config = InetsoftConfig.BOOTSTRAP_INSTANCE = InetsoftConfig.load(configFile);
      KeyValueConfig keyValue = new KeyValueConfig();
      keyValue.setType("test");
      config.setKeyValue(keyValue);
      InetsoftConfig.save(config, configFile);
      return config;
   }

   @Bean
   public KeyValueEngine keyValueEngine() {
      return new TestKeyValueEngine();
   }

   @Bean
   public KeyValueStorageManager keyValueStorageManager(KeyValueEngine engine, Cluster cluster) {
      return new KeyValueStorageManager(engine, cluster);
   }

   @Bean
   public BlobEngine blobEngine(InetsoftConfig config) {
      return new FilesystemBlobEngineFactory().createEngine(config);
   }

   @Bean
   public BlobCache blobCache(BlobEngine blobEngine) {
      Path path = Paths.get(ConfigurationContext.getContext().getHome(), "blob_cache");
      return new BlobCache(path, blobEngine);
   }

   @Bean
   public BlobStorageManager blobStorageManager(BlobEngine blobEngine, KeyValueStorageManager keyValueStorageManager, BlobCache blobCache, Cluster cluster) {
      return new BlobStorageManager(blobEngine, keyValueStorageManager, blobCache, cluster);
   }

   @Bean
   public LicenseManager licenseManager(Environment environment) {
      if("true".equals(environment.getProperty("mock.license.manager", "false"))) {
         return mock(LicenseManager.class);
      }

      return new LicenseManager();
   }

   @Bean
   public SecurityEngine securityEngine(Cluster cluster, LicenseManager licenseManager) {
      return spy(new SecurityEngine(licenseManager, cluster));
   }

   @Bean
   public LogManager logManager() {
      return mock(LogManager.class);
   }

   @Bean
   public PropertiesEngine propertiesEngine(KeyValueStorageManager keyValueStorageManager,
                                            FileSystemService fileSystemService,
                                            ApplicationEventPublisher eventPublisher,
                                            ObjectProvider<LogManager> logManagerProvider)
   {
      return new PropertiesEngine(keyValueStorageManager, fileSystemService, eventPublisher,
                                  logManagerProvider);
   }

   @Bean
   public FileSystemService fileSystemService(Cluster cluster, ApplicationEventPublisher eventPublisher) {
      return new FileSystemService(cluster, eventPublisher);
   }

   @Bean
   public DataSpace dataSpace(BlobStorageManager blobStorageManager) {
      return new DataSpace(blobStorageManager);
   }

   @Bean
   public PortalThemesManager portalThemesManager() {
      return mock(PortalThemesManager.class);
   }

   @Bean
   public XSessionService xSessionService() {
      return new XSessionService();
   }

   @Bean
   public DataCacheSweeper dataCacheSweeper() {
      return mock(DataCacheSweeper.class);
   }

   @Bean
   public PasswordEncryption passwordEncryption() {
      return new LocalPasswordEncryptionFactory().createPasswordEncryption(new SecretsConfig());
   }

   @Bean
   public DependencyHandler dependencyHandler() {
      return mock(DependencyHandler.class);
   }
}
