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
package inetsoft.sree.schedule.cloudrunner;

import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.test.*;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.config.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.quartz.*;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78107: the cloud runner's cluster node is built from the {@code ClusterConfig} that
 * {@link ScheduleTaskCloudJob} publishes in the {@code cloud.runner.config} map, so the server's
 * {@code cluster.failureDetectionTimeout} must be copied into it, both when the runner config is
 * first created and when an existing one is updated.
 *
 * <p>The real {@code execute()} is driven with a mock {@link Cluster}. No cloud job factory matches
 * the configured runner type and the task timeout is 1 ms, so {@code execute()} publishes the
 * runner config and then times out without starting anything.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScheduleTaskCloudJobClusterConfigTest {
   @TempDir
   Path tempDir;

   @BeforeEach
   @SuppressWarnings("unchecked")
   void setUp() {
      cluster = mock(Cluster.class);
      configMap = mock(DistributedMap.class);
      when(cluster.getLock(anyString())).thenReturn(new ReentrantLock());
      when(cluster.<String, InetsoftConfig>getMap("cloud.runner.config")).thenReturn(configMap);
      when(cluster.getClusterAddresses()).thenReturn(List.of());

      serverConfig = InetsoftConfig.createDefault(tempDir);
      serverConfig.setCluster(new ClusterConfig());
      CloudRunnerConfig cloudRunner = new CloudRunnerConfig();
      cloudRunner.setType("noFactory78107");
      serverConfig.setCloudRunner(cloudRunner);

      PropertiesEngine properties = mock(PropertiesEngine.class);
      when(properties.getProperty(eq("schedule.task.timeout"), anyBoolean())).thenReturn("1");

      ApplicationContext appContext = mock(ApplicationContext.class);
      when(appContext.getBean(Cluster.class)).thenReturn(cluster);
      when(appContext.getBean(InetsoftConfig.class)).thenReturn(serverConfig);
      when(appContext.getBean(PropertiesEngine.class)).thenReturn(properties);

      savedAppContext = ConfigurationContext.getContext().getApplicationContext();
      ConfigurationContext.getContext().setApplicationContext(appContext);
   }

   @AfterEach
   void tearDown() {
      ConfigurationContext.getContext().setApplicationContext(savedAppContext);
   }

   @Test
   void newRunnerConfigCopiesFailureDetectionTimeout() {
      when(configMap.containsKey("config")).thenReturn(false);
      serverConfig.getCluster().setFailureDetectionTimeout(30_000L);

      execute();

      ArgumentCaptor<InetsoftConfig> captor = ArgumentCaptor.forClass(InetsoftConfig.class);
      verify(configMap).put(eq("config"), captor.capture());
      ClusterConfig runnerCluster = captor.getValue().getCluster();
      assertTrue(runnerCluster.isClientMode());
      assertEquals(Long.valueOf(30_000L), runnerCluster.getFailureDetectionTimeout());
   }

   @Test
   void existingRunnerConfigTakesTheServersCurrentTimeout() {
      InetsoftConfig runnerConfig = new InetsoftConfig();
      runnerConfig.setCluster(new ClusterConfig());
      runnerConfig.getCluster().setFailureDetectionTimeout(45_000L);
      when(configMap.containsKey("config")).thenReturn(true);
      when(configMap.get("config")).thenReturn(runnerConfig);

      serverConfig.getCluster().setFailureDetectionTimeout(20_000L);
      execute();
      assertEquals(Long.valueOf(20_000L), runnerConfig.getCluster().getFailureDetectionTimeout());

      // the server no longer sets it, so the runner goes back to the Ignite default too
      serverConfig.getCluster().setFailureDetectionTimeout(null);
      execute();
      assertNull(runnerConfig.getCluster().getFailureDetectionTimeout());
   }

   private void execute() {
      JobDetail detail = JobBuilder.newJob(ScheduleTaskCloudJob.class)
         .withIdentity(TASK, "test")
         .build();
      detail.getJobDataMap().put(ScheduleTask.class.getName(), new ScheduleTask(TASK));
      JobExecutionContext context = mock(JobExecutionContext.class);
      when(context.getJobDetail()).thenReturn(detail);

      JobExecutionException e = assertThrows(
         JobExecutionException.class, () -> new ScheduleTaskCloudJob().execute(context));
      // the timeout is thrown inside execute()'s try block and wrapped by its catch-all
      Throwable cause = e.getCause();
      assertNotNull(cause, e.getMessage());
      assertTrue(String.valueOf(cause.getMessage()).contains("timed out"), cause.toString());
   }

   private static final String TASK = "org~;admin:T";

   private Cluster cluster;
   private DistributedMap<String, InetsoftConfig> configMap;
   private InetsoftConfig serverConfig;
   private ApplicationContext savedAppContext;
}
