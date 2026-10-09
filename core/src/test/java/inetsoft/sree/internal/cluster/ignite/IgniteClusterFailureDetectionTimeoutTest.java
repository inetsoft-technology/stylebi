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
package inetsoft.sree.internal.cluster.ignite;

import inetsoft.test.*;
import inetsoft.util.config.ClusterConfig;
import inetsoft.util.config.InetsoftConfig;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78107: the Ignite failure detection timeout can be configured through
 * {@code cluster.failureDetectionTimeout}. It is opt-in: when unset, the Ignite default is kept
 * and the field is not written to saved {@code inetsoft.yaml} files.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class IgniteClusterFailureDetectionTimeoutTest {
   @TempDir
   Path tempDir;

   @BeforeEach
   void saveTimeout() {
      originalTimeout = InetsoftConfig.getInstance().getCluster().getFailureDetectionTimeout();
   }

   @AfterEach
   void restoreTimeout() {
      InetsoftConfig.getInstance().getCluster().setFailureDetectionTimeout(originalTimeout);
      System.clearProperty(SYSTEM_PROPERTY);
   }

   @Test
   void unsetByDefault() {
      assertNull(new ClusterConfig().getFailureDetectionTimeout());
   }

   @Test
   void defaultConfigKeepsIgniteDefaultWhenUnset() {
      InetsoftConfig.getInstance().getCluster().setFailureDetectionTimeout(null);
      IgniteConfiguration config = IgniteCluster.getDefaultConfig(tempDir);
      assertEquals(IgniteConfiguration.DFLT_FAILURE_DETECTION_TIMEOUT,
                   config.getFailureDetectionTimeout());
      assertEquals(10_000L, config.getFailureDetectionTimeout());
   }

   @Test
   void defaultConfigAppliesConfiguredTimeout() {
      InetsoftConfig.getInstance().getCluster().setFailureDetectionTimeout(30_000L);
      IgniteConfiguration config = IgniteCluster.getDefaultConfig(tempDir);
      assertEquals(30_000L, config.getFailureDetectionTimeout());
   }

   @Test
   void defaultConfigIgnoresNonPositiveTimeout() {
      InetsoftConfig.getInstance().getCluster().setFailureDetectionTimeout(0L);
      IgniteConfiguration config = IgniteCluster.getDefaultConfig(tempDir);
      assertEquals(IgniteConfiguration.DFLT_FAILURE_DETECTION_TIMEOUT,
                   config.getFailureDetectionTimeout());
   }

   @Test
   void systemPropertyPopulatesField() throws Exception {
      Path file = tempDir.resolve("inetsoft.yaml");
      InetsoftConfig.save(InetsoftConfig.createDefault(tempDir), file);
      System.setProperty(SYSTEM_PROPERTY, "45000");

      InetsoftConfig loaded = InetsoftConfig.load(file);
      assertEquals(Long.valueOf(45_000L), loaded.getCluster().getFailureDetectionTimeout());
   }

   @Test
   void yamlRoundTripOmitsUnsetAndKeepsSetValue() throws Exception {
      Path file = tempDir.resolve("inetsoft.yaml");
      InetsoftConfig config = InetsoftConfig.createDefault(tempDir);
      config.getCluster().setFailureDetectionTimeout(null);
      InetsoftConfig.save(config, file);

      String yaml = Files.readString(file);
      assertTrue(yaml.contains("cluster:"), yaml);
      assertFalse(yaml.contains("failureDetectionTimeout"), yaml);
      assertNull(InetsoftConfig.load(file).getCluster().getFailureDetectionTimeout());

      config.getCluster().setFailureDetectionTimeout(20_000L);
      InetsoftConfig.save(config, file);
      assertTrue(Files.readString(file).contains("failureDetectionTimeout: 20000"));
      assertEquals(Long.valueOf(20_000L),
                   InetsoftConfig.load(file).getCluster().getFailureDetectionTimeout());
   }

   private static final String SYSTEM_PROPERTY = "inetsoftConfig.cluster.failureDetectionTimeout";
   private Long originalTimeout;
}
