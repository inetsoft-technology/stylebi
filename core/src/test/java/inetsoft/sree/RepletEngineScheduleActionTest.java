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
package inetsoft.sree;

import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79850: a hard delete of a viewsheet (removeSheet with force) left the viewsheet actions
 * of schedule tasks pointing at the deleted viewsheet, because the ASSET_DELETED event path only
 * strips AssetSupport actions. Runs against the real asset repository (RepletEngine) and the
 * real ScheduleManager.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RepletEngineScheduleActionTest {
   @BeforeEach
   void setUp() {
      AssetRepository.IGNORE_PERM.set(true);
      repository = AssetUtil.getAssetRepository(false);
      scheduleManager = ScheduleManager.getScheduleManager();
      admin = new SRPrincipal(new IdentityID("admin", ORG),
                              new IdentityID[] { new IdentityID("Administrator", null) },
                              new String[0], ORG, Tool.getSecureRandom().nextLong());
      admin.setIgnoreLogin(true);
   }

   @AfterEach
   void tearDown() throws Exception {
      try {
         for(String id : taskIds) {
            try {
               scheduleManager.removeScheduleTask(id, admin, false);
            }
            catch(Exception ignore) {
               // best-effort cleanup
            }
         }

         for(AssetEntry entry : cleanup) {
            try {
               if(repository.containsEntry(entry)) {
                  repository.removeSheet(entry, null, true);
               }
            }
            catch(Exception ignore) {
               // best-effort cleanup
            }
         }
      }
      finally {
         taskIds.clear();
         cleanup.clear();
         AssetRepository.IGNORE_PERM.remove();
      }
   }

   @Test
   void removeViewsheet_stripsOnlyActionsOfTheRemovedViewsheet() throws Exception {
      AssetEntry doomed = saveViewsheet("vs79850doomed");
      AssetEntry other = saveViewsheet("vs79850other");
      AssetEntry ws = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                     "ws79850", null, ORG);

      ViewsheetAction doomedAction = new ViewsheetAction();
      doomedAction.setViewsheet(doomed.toIdentifier());
      ViewsheetAction otherAction = new ViewsheetAction();
      otherAction.setViewsheet(other.toIdentifier());
      MVAction mvAction = new MVAction();
      mvAction.setEntry(ws);
      BatchAction batchAction = new BatchAction();
      batchAction.setQueryEntry(ws);

      ScheduleTask task = new ScheduleTask("t79850");
      task.setOwner(new IdentityID("admin", ORG));
      task.addCondition(TimeCondition.at(10, 35, 59));
      task.addAction(doomedAction);
      task.addAction(otherAction);
      task.addAction(mvAction);
      task.addAction(batchAction);
      String taskId = "admin~;~" + ORG + ":t79850";
      taskIds.add(taskId);
      scheduleManager.setScheduleTask(taskId, task, admin);
      assertEquals(4, scheduleManager.getScheduleTask(taskId).getActionCount());

      repository.removeSheet(doomed, admin, true);

      ScheduleTask after = scheduleManager.getScheduleTask(taskId);
      assertNotNull(after, "the task itself must stay");
      assertEquals(3, after.getActionCount());
      List<String> viewsheets = new ArrayList<>();

      for(int i = 0; i < after.getActionCount(); i++) {
         if(after.getAction(i) instanceof ViewsheetAction) {
            viewsheets.add(((ViewsheetAction) after.getAction(i)).getViewsheetName());
         }
      }

      assertEquals(List.of(other.toIdentifier()), viewsheets);
      assertTrue(java.util.stream.IntStream.range(0, after.getActionCount())
                    .anyMatch(i -> after.getAction(i) instanceof MVAction));
      assertTrue(java.util.stream.IntStream.range(0, after.getActionCount())
                    .anyMatch(i -> after.getAction(i) instanceof BatchAction));
   }

   @Test
   void removeWorksheet_leavesViewsheetActionsAlone() throws Exception {
      AssetEntry vs = saveViewsheet("vs79850keep");
      AssetEntry ws = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                     "ws79850x", null, ORG);
      cleanup.add(ws);
      repository.setSheet(ws, new inetsoft.uql.asset.Worksheet(), admin, true);

      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet(vs.toIdentifier());
      ScheduleTask task = new ScheduleTask("t79850b");
      task.setOwner(new IdentityID("admin", ORG));
      task.addCondition(TimeCondition.at(10, 35, 59));
      task.addAction(action);
      String taskId = "admin~;~" + ORG + ":t79850b";
      taskIds.add(taskId);
      scheduleManager.setScheduleTask(taskId, task, admin);

      repository.removeSheet(ws, admin, true);

      assertEquals(1, scheduleManager.getScheduleTask(taskId).getActionCount());
   }

   private AssetEntry saveViewsheet(String path) throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        path, null, ORG);
      cleanup.add(entry);
      repository.setSheet(entry, new Viewsheet(), admin, true);
      assertTrue(repository.containsEntry(entry));
      return entry;
   }

   private static final String ORG = Organization.getDefaultOrganizationID();
   private final List<AssetEntry> cleanup = new ArrayList<>();
   private final List<String> taskIds = new ArrayList<>();
   private AssetRepository repository;
   private ScheduleManager scheduleManager;
   private SRPrincipal admin;
}
