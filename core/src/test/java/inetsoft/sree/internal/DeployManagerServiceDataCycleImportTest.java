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
package inetsoft.sree.internal;

import inetsoft.mv.MVManager;
import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.ScheduleCondition;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.TimeCondition;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.EmbeddedTableStorage;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.*;
import inetsoft.util.dep.DataCycleAsset;
import inetsoft.web.admin.deploy.DeployUtil;
import inetsoft.web.admin.deploy.PartialDeploymentJarInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #79842, a data cycle exported with the real exporter (as a dependency) and imported through the public
 * DeployManagerService.importAssets with "Overwrite existing files" unchecked: a cycle that does
 * not exist on the target is created with its conditions and cycle info in the current
 * organization, and still exists when a new DataCycleManager reads the storage again; a cycle that
 * exists is kept.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, ScheduleTestConfiguration.class,
                                  DeployManagerServiceDataCycleImportTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DeployManagerServiceDataCycleImportTest {
   @BeforeEach
   void setUp() {
      oldPrincipal = ThreadContext.getContextPrincipal();
      LibManagerProvider libManagerProvider = mock(LibManagerProvider.class);
      when(libManagerProvider.getManager(any(Principal.class))).thenReturn(mock(LibManager.class));
      service = new DeployManagerService(
         mock(SecurityEngine.class), mock(DependencyHandler.class), mock(DataSourceRegistry.class),
         mock(DashboardRegistryManager.class), libManagerProvider, mock(DashboardManager.class),
         mock(XRepository.class), mock(FileSystemService.class), mock(DataSpace.class),
         mock(EmbeddedTableStorage.class), mock(RepletRegistryManager.class));
      principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn(new IdentityID("admin", currentOrg()).convertToKey());
      when(principal.getRoles()).thenReturn(new IdentityID[0]);
   }

   @AfterEach
   void tearDown() {
      for(String cycle : cycles) {
         manager.removeDataCycle(cycle, null);
      }

      ThreadContext.setContextPrincipal(oldPrincipal);
   }

   @Test
   void overwriteOff_absentCycle_isCreatedAndSurvivesReload() throws Exception {
      String cycle = cycle("absent");
      byte[] jar = exportCycle(cycle, 3, 41);
      manager.removeDataCycle(cycle, null);
      assertFalse(listed(manager, cycle), "the cycle is absent on the target");

      List<String> failed = importJar(jar, false);

      assertEquals(List.of(), failed);
      assertEquals(List.of(3), hours(manager, cycle));
      assertCycleInfo(manager.getCycleInfo(cycle, null), cycle, 41);
      assertTrue(listed(manager, cycle));

      // a new manager reads the cycle back from the storage
      try(DataCycleManager reloaded = newManager()) {
         assertTrue(listed(reloaded, cycle));
         assertEquals(List.of(3), hours(reloaded, cycle));
         assertCycleInfo(reloaded.getCycleInfo(cycle, null), cycle, 41);
      }
   }

   @Test
   void overwriteOff_presentCycle_isPreserved() throws Exception {
      String cycle = cycle("present");
      byte[] jar = exportCycle(cycle, 3, 41);
      manager.removeDataCycle(cycle, null);
      manager.addCondition(cycle, null, TimeCondition.at(22, 0, 0));
      DataCycleManager.CycleInfo info = new DataCycleManager.CycleInfo(cycle, currentOrg());
      info.setThreshold(77);
      manager.setCycleInfo(cycle, null, info);
      manager.save();

      List<String> failed = importJar(jar, false);

      assertEquals(List.of(), failed);
      assertEquals(List.of(22), hours(manager, cycle));
      assertEquals(77, manager.getCycleInfo(cycle, null).getThreshold());
   }

   @Test
   void overwriteOn_presentCycle_isReplaced() throws Exception {
      String cycle = cycle("replaced");
      byte[] jar = exportCycle(cycle, 3, 41);
      manager.removeDataCycle(cycle, null);
      manager.addCondition(cycle, null, TimeCondition.at(22, 0, 0));
      manager.save();

      List<String> failed = importJar(jar, true);

      assertEquals(List.of(), failed);
      assertEquals(List.of(3), hours(manager, cycle));
      assertCycleInfo(manager.getCycleInfo(cycle, null), cycle, 41);
   }

   private String cycle(String name) {
      String cycle = "Bug79842_deploy_" + name;
      cycles.add(cycle);
      return cycle;
   }

   /**
    * Creates the cycle and exports it with the real exporter.
    */
   private byte[] exportCycle(String cycle, int hour, int threshold) throws Exception {
      manager.addCondition(cycle, null, TimeCondition.at(hour, 0, 0));
      DataCycleManager.CycleInfo info = new DataCycleManager.CycleInfo(cycle, currentOrg());
      info.setThreshold(threshold);
      manager.setCycleInfo(cycle, null, info);
      manager.save();

      // a data cycle is exported as a dependency of the schedule task that uses it
      PartialDeploymentJarInfo.RequiredAsset required = new PartialDeploymentJarInfo.RequiredAsset();
      required.setType(DataCycleAsset.DATACYCLE);
      required.setPath(cycle);
      required.setRequiredBy("DataCycle Task: " + cycle);
      PartialDeploymentJarInfo jarInfo = new PartialDeploymentJarInfo(
         "bug79842", new Timestamp(System.currentTimeMillis()), false, new ArrayList<>(),
         new ArrayList<>(List.of(required)));
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      DeployUtil.createExport(jarInfo, out);
      return out.toByteArray();
   }

   /**
    * Imports the jar the way ImportAssetService does: unzip, read JarFileInfo.xml, build the
    * DeploymentInfo and call DeployManagerService.importAssets.
    */
   private List<String> importJar(byte[] jar, boolean overwriting) throws Exception {
      List<String> order = new ArrayList<>();
      Map<String, String> names = new HashMap<>();
      String folder = DeployManagerService.setJarFile(
         new ByteArrayInputStream(jar), order, names, true);
      PartialDeploymentJarInfo jarInfo = DeployManagerService.getInfo(folder, true);
      DeploymentInfo info = new DeploymentInfo(jarInfo, names, folder);
      List<String> failed = new ArrayList<>();

      try {
         service.importAssets(overwriting, order, info, false, principal, new ArrayList<>(),
                              null, failed, null, new ArrayList<>());
      }
      finally {
         Tool.deleteFile(new File(folder));
      }

      return failed;
   }

   private DataCycleManager newManager() {
      return new DataCycleManager(mock(ScheduleManager.class), indexedStorage, securityEngine,
                                  cluster, mvManager, dataSpace, repletRegistryManager);
   }

   private static void assertCycleInfo(DataCycleManager.CycleInfo info, String cycle,
                                       int threshold)
   {
      assertNotNull(info);
      assertEquals(cycle, info.getName());
      assertEquals(currentOrg(), info.getOrgId());
      assertEquals(threshold, info.getThreshold());
   }

   private static boolean listed(DataCycleManager manager, String cycle) {
      return Collections.list(manager.getDataCycles(currentOrg())).contains(cycle);
   }

   private static List<Integer> hours(DataCycleManager manager, String cycle) {
      List<Integer> hours = new ArrayList<>();

      for(ScheduleCondition condition : manager.getConditions(cycle, null)) {
         hours.add(((TimeCondition) condition).getHour());
      }

      return hours;
   }

   private static String currentOrg() {
      return OrganizationManager.getInstance().getCurrentOrgID();
   }

   @Configuration
   static class Beans {
      @Bean
      public MVManager mvManager(Cluster cluster) {
         return new MVManager(cluster);
      }

      @Bean
      public RepletRegistryManager repletRegistryManager(DataSpace dataSpace) {
         return new RepletRegistryManager(dataSpace);
      }

      @Bean
      public DataCycleManager dataCycleManager(
         ScheduleManager scheduleManager, IndexedStorage indexedStorage,
         SecurityEngine securityEngine, Cluster cluster, MVManager mvManager,
         DataSpace dataSpace, RepletRegistryManager repletRegistryManager)
      {
         return new DataCycleManager(scheduleManager, indexedStorage, securityEngine, cluster,
                                     mvManager, dataSpace, repletRegistryManager);
      }

      // the import gets the asset repository after the assets, no viewsheet is imported here
      @Bean
      public AnalyticRepository analyticRepository() {
         return mock(AnalyticRepository.class);
      }
   }

   @Autowired
   private DataCycleManager manager;
   @Autowired
   private IndexedStorage indexedStorage;
   @Autowired
   private SecurityEngine securityEngine;
   @Autowired
   private Cluster cluster;
   @Autowired
   private MVManager mvManager;
   @Autowired
   private DataSpace dataSpace;
   @Autowired
   private RepletRegistryManager repletRegistryManager;

   private final List<String> cycles = new ArrayList<>();
   private DeployManagerService service;
   private XPrincipal principal;
   private Principal oldPrincipal;
}
