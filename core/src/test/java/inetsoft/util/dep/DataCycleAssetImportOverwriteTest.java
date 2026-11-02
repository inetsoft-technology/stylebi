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

package inetsoft.util.dep;

import inetsoft.mv.MVManager;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.ScheduleCondition;
import inetsoft.sree.schedule.TimeCondition;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.util.DataSpace;
import inetsoft.util.IndexedStorage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79842, importing a data cycle with "Overwrite existing files" unchecked creates a cycle
 * that does not exist on the target and keeps one that does, including a stored cycle without
 * any condition. Runs against the real DataCycleManager and IndexedStorage.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, ScheduleTestConfiguration.class,
                                  DataCycleAssetImportOverwriteTest.DataCycleManagerConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataCycleAssetImportOverwriteTest {
   @Autowired
   private DataCycleManager manager;

   private final List<String> cycles = new ArrayList<>();

   @AfterEach
   void tearDown() {
      for(String cycle : cycles) {
         manager.removeDataCycle(cycle, null);
      }
   }

   @Test
   void overwriteOff_absentCycle_isCreated() throws Exception {
      String cycle = cycle("absentOff");
      assertFalse(asset(cycle).exists());

      importCycle(cycle, 3, false);

      assertEquals(List.of(3), hours(cycle));
      assertTrue(asset(cycle).exists());
   }

   @Test
   void overwriteOff_presentCycle_isPreserved() throws Exception {
      String cycle = cycle("presentOff");
      manager.addCondition(cycle, null, TimeCondition.at(22, 0, 0));
      manager.save();
      assertTrue(asset(cycle).exists());

      importCycle(cycle, 3, false);

      assertEquals(List.of(22), hours(cycle));
   }

   @Test
   void overwriteOn_absentCycle_isCreated() throws Exception {
      String cycle = cycle("absentOn");

      importCycle(cycle, 4, true);

      assertEquals(List.of(4), hours(cycle));
   }

   @Test
   void overwriteOn_presentCycle_isReplaced() throws Exception {
      String cycle = cycle("presentOn");
      manager.addCondition(cycle, null, TimeCondition.at(22, 0, 0));
      manager.save();

      importCycle(cycle, 5, true);

      assertEquals(List.of(5), hours(cycle));
   }

   @Test
   void overwriteOff_zeroConditionCycle_isUntouched() throws Exception {
      String cycle = cycle("zeroOff");
      DataCycleManager.CycleInfo info = new DataCycleManager.CycleInfo(cycle, currentOrg());
      info.setThreshold(77);
      manager.setCycleInfo(cycle, null, info);
      manager.save();
      assertTrue(manager.getConditions(cycle, null).isEmpty());
      assertTrue(asset(cycle).exists(), "a stored cycle without conditions exists");

      importCycle(cycle, 3, false);

      assertTrue(manager.getConditions(cycle, null).isEmpty());
      assertEquals(77, manager.getCycleInfo(cycle, null).getThreshold());
   }

   @Test
   void importedCycleInfo_keepsCycleNameAndTargetOrg() throws Exception {
      String cycle = cycle("info");

      // overwrite on, so this checks the CycleInfo on its own: the import stored a null name
      // and kept the source org before the fix
      importCycle(cycle, 3, true);

      DataCycleManager.CycleInfo info = manager.getCycleInfo(cycle, null);
      assertNotNull(info);
      assertEquals(cycle, info.getName());
      assertEquals(currentOrg(), info.getOrgId());
      assertEquals(9, info.getThreshold());
   }

   private String cycle(String name) {
      String cycle = "Bug79842_" + name;
      cycles.add(cycle);
      return cycle;
   }

   private static String currentOrg() {
      return OrganizationManager.getInstance().getCurrentOrgID();
   }

   private static DataCycleAsset asset(String cycle) {
      DataCycleAsset asset = new DataCycleAsset();
      asset.parseIdentifier(cycle, null);
      return asset;
   }

   /**
    * Import a cycle the way DeployManagerService does: the asset is built from its path (the org
    * stays unset) and the overwrite flag comes from the XAssetConfig. The content carries a
    * CycleInfo from another org, as an exported cycle does.
    */
   private void importCycle(String cycle, int hour, boolean overwriting) throws Exception {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
      writer.println("<DataCycle name=\"" + cycle + "\">");
      TimeCondition.at(hour, 0, 0).writeXML(writer);
      DataCycleManager.CycleInfo info = new DataCycleManager.CycleInfo(cycle, "source-org");
      info.setThreshold(9);
      info.writeXML(writer);
      writer.println("</DataCycle>");
      writer.flush();

      XAssetConfig config = new XAssetConfig();
      config.setOverwriting(overwriting);
      asset(cycle).parseContent(
         new ByteArrayInputStream(buf.toString().getBytes(StandardCharsets.UTF_8)), config, true, true);
   }

   private List<Integer> hours(String cycle) {
      List<Integer> hours = new ArrayList<>();

      for(ScheduleCondition condition : manager.getConditions(cycle, null)) {
         hours.add(((TimeCondition) condition).getHour());
      }

      return hours;
   }

   /**
    * Builds the real DataCycleManager with its Spring constructor, as in production.
    */
   @Configuration
   static class DataCycleManagerConfig {
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
   }
}
