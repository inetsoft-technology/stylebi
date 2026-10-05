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
package inetsoft.sree.schedule;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.*;
import inetsoft.util.dep.XAsset;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * User text in schedule action XML must survive a save to storage and a reload, and the copy
 * that every run makes (ScheduleTask.copyScheduleTask). An unreadable action makes the whole
 * task disappear on reload.
 * <ul>
 *    <li>Bug #77807: name-derived attributes (backup asset owner, batch target task id) with
 *    XML-special characters.</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScheduleActionXmlEscapeTest {
   private static final String ORG = "saxeorg";
   private static final String USER_ORG = "host-org";

   @Autowired
   ScheduleManager scheduleManager;

   @BeforeEach
   void setUp() {
      SRPrincipal principal = new SRPrincipal(new IdentityID("admin", ORG), new IdentityID[0],
                                              new String[0], ORG,
                                              Tool.getSecureRandom().nextLong());
      principal.setIgnoreLogin(true);
      ThreadContext.setContextPrincipal(principal);
   }

   @AfterEach
   void tearDown() {
      @SuppressWarnings("unchecked")
      Map<String, ScheduleTask> map =
         (Map<String, ScheduleTask>) (Object) scheduleManager.getOrgTaskMap(ORG);
      map.clear();
      ThreadContext.setContextPrincipal(null);
   }

   // ---------------------------------------------------------------------------------------
   // Bug #77807: attributes
   // ---------------------------------------------------------------------------------------

   @ParameterizedTest
   @ValueSource(strings = { "Tom&Jerry", "a<b", "a\"b", "x&amp;y", "a'b", "a>b", "plainuser" })
   void backupAssetOwner_survivesStorageAndCopy(String owner) throws Exception {
      ScheduleTask task = task("bk77807", backup(owner));

      // the copy every run makes, and the reload from storage
      assertAll(
         () -> assertEquals(new IdentityID(owner, USER_ORG),
                            backupOwner(ScheduleTask.copyScheduleTask(task))),
         () -> assertEquals(new IdentityID(owner, USER_ORG), backupOwner(storeAndReload(task))));
   }

   @ParameterizedTest
   @ValueSource(strings = { "A&B", "R&D nightly", "a<b", "a\"b", "x&amp;y", "plain" })
   void batchTarget_survivesStorageAndCopy(String target) throws Exception {
      String taskId = new IdentityID("admin", ORG).convertToKey() + ":" + target;
      ScheduleTask task = task("bt77807", batch(taskId));

      assertAll(
         () -> assertEquals(taskId, ((BatchAction) ScheduleTask.copyScheduleTask(task)
            .getAction(0)).getTaskId()),
         () -> assertEquals(taskId, ((BatchAction) storeAndReload(task).getAction(0)).getTaskId()));
   }

   @Test
   void ordinaryNames_writtenAsBefore() {
      IndividualAssetBackupAction bk = backup("plainuser");
      XAsset asset = bk.getAssets().get(0);
      String bkXml = xml(bk);
      // the line the writer produced before the fix
      assertTrue(bkXml.contains("<XAsset type=\"" + asset.getType() + "\" path=\"" +
                                   bk.byteEncode(asset.getPath()) + "\" user=\"plainuser~;~" +
                                   USER_ORG + "\">"), bkXml);

      String taskId = new IdentityID("admin", ORG).convertToKey() + ":plain";
      String btXml = xml(batch(taskId));
      assertTrue(btXml.contains("taskId=\"" + taskId + "\" "), btXml);
   }

   @Test
   void legacyAttributes_stillRead() throws Exception {
      String xml = "<Task name=\"lg77807\" owner=\"admin~;~" + ORG + "\" enabled=\"true\">" +
         condition() +
         "<Action type=\"Backup\" path=\"backup/sax.zip\"><XAsset type=\"VIEWSHEET\" path=\"myvs\" " +
         "user=\"plainuser~;~" + USER_ORG + "\"></XAsset></Action>" +
         "<Action type=\"Batch\" class=\"" + BatchAction.class.getName() + "\" " +
         "taskId=\"admin~;~" + ORG + ":plain\" ><queryParameters></queryParameters>" +
         "<embeddedParameters></embeddedParameters></Action></Task>";
      ScheduleTask task = parse(xml);

      assertEquals(new IdentityID("plainuser", USER_ORG), backupOwner(task));
      assertEquals("admin~;~" + ORG + ":plain", ((BatchAction) task.getAction(1)).getTaskId());
   }

   // ---------------------------------------------------------------------------------------
   // helpers
   // ---------------------------------------------------------------------------------------

   private static IndividualAssetBackupAction backup(String owner) {
      IndividualAssetBackupAction action = new IndividualAssetBackupAction();
      List<XAsset> assets = new ArrayList<>();
      assets.add(SUtil.getXAsset("VIEWSHEET", "myvs", new IdentityID(owner, USER_ORG)));
      action.setAssets(assets);
      action.setPaths("backup/sax.zip");
      return action;
   }

   private static BatchAction batch(String taskId) {
      BatchAction action = new BatchAction();
      action.setTaskId(taskId);
      return action;
   }

   private static IdentityID backupOwner(ScheduleTask task) {
      assertTrue(task.getActionCount() > 0, "actions dropped on parse");
      ScheduleAction action = task.getAction(0);
      assertInstanceOf(IndividualAssetBackupAction.class, action);
      return ((IndividualAssetBackupAction) action).getAssets().get(0).getUser();
   }

   private static ScheduleTask task(String name, ScheduleAction action) {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(new IdentityID("admin", ORG));
      // without a condition, parseXML ignores the task's actions
      task.addCondition(TimeCondition.at(1, 0, 0));
      task.addAction(action);
      return task;
   }

   private ScheduleTask storeAndReload(ScheduleTask task) {
      String key = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK,
                                  "/" + task.getTaskId(), null, ORG).toIdentifier();
      ScheduleTaskMap map = scheduleManager.getOrgTaskMap(ORG);
      map.put(key, task);
      map.clearCache();
      ScheduleTask loaded = map.get(key);
      assertNotNull(loaded, "task skipped on reload: " + xml(task));
      return loaded;
   }

   static String xml(ScheduleTask task) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      task.writeXML(pw);
      pw.flush();
      return sw.toString();
   }

   static String xml(ScheduleAction action) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      ((XMLSerializable) action).writeXML(pw);
      pw.flush();
      return sw.toString();
   }

   static ScheduleTask parse(String xml) throws Exception {
      Document doc = Tool.parseXML(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
      ScheduleTask task = new ScheduleTask();
      task.parseXML(doc.getDocumentElement());
      return task;
   }

   private static String condition() {
      String x = xml(task("c", new ViewsheetAction()));
      int s = x.indexOf("<Condition");
      int e = x.indexOf("</Condition>") + "</Condition>".length();
      return x.substring(s, e);
   }
}
