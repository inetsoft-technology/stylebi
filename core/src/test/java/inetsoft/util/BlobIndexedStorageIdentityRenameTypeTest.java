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
package inetsoft.util;

/*
 * Bug #77097: the storage migration of an identity rename matched the stored data by the bare
 * name. Renaming group "sales" therefore also moved the private assets of user "sales" and
 * rewrote their task ownership and "sales(User)" recipients, and renaming user "sales" rewrote
 * the tasks that run as group "sales" and the "sales(Group)" recipients.
 */

import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.Identity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class BlobIndexedStorageIdentityRenameTypeTest {
   @Autowired
   private IndexedStorage indexedStorage;

   @AfterEach
   void tearDown() {
      for(String key : seededKeys) {
         indexedStorage.remove(key);
      }

      seededKeys.clear();
   }

   @Test
   void groupRename_leavesSameNamedUserDataUntouched() throws Exception {
      String wsKey = seedPrivateWorksheet(ORG, "sales");
      String taskKey = seedTask(ORG, "sales", "T1", new Group(new IdentityID("sales", ORG)));

      indexedStorage.migrateStorageData(new IdentityID("sales", ORG),
                                        new IdentityID("sales2", ORG), Identity.GROUP);

      assertTrue(indexedStorage.contains(wsKey, ORG), "the user's private asset must stay");
      assertFalse(indexedStorage.contains(privateWorksheetKey(ORG, "sales2"), ORG));

      Element task = getTask(taskKey, ORG);
      assertEquals("sales~;~" + ORG, task.getAttribute("owner"), "the user still owns the task");
      assertEquals("sales2~;~" + ORG, task.getAttribute("idname"),
                   "the task runs as the renamed group");
      assertEquals(Integer.toString(Identity.GROUP), task.getAttribute("idtype"));
      assertEquals("sales(User),sales2(Group),other(User)", getEmails(task));
      assertEquals("sales~;~" + ORG + ":T2", getCompletionTask(task));
      assertEquals(PRIVATE_VS.formatted(ORG, ORG), getViewsheet(task));
   }

   @Test
   void userRename_leavesSameNamedGroupDataUntouched() throws Exception {
      seedPrivateWorksheet(ORG, "sales");
      String groupTaskKey = seedTask(ORG, "sales", "T1", new Group(new IdentityID("sales", ORG)));
      String userTaskKey = seedTask(ORG, "sales", "T3", new User(new IdentityID("sales", ORG)));

      indexedStorage.migrateStorageData(new IdentityID("sales", ORG),
                                        new IdentityID("sales3", ORG), Identity.USER);

      String newWsKey = privateWorksheetKey(ORG, "sales3");
      seededKeys.add(newWsKey);
      assertTrue(indexedStorage.contains(newWsKey, ORG), "the user's private asset is moved");
      assertFalse(indexedStorage.contains(privateWorksheetKey(ORG, "sales"), ORG));

      Element groupTask = getTask(groupTaskKey, ORG);
      assertEquals("sales3~;~" + ORG, groupTask.getAttribute("owner"));
      assertEquals("sales~;~" + ORG, groupTask.getAttribute("idname"),
                   "the task still runs as the same-named group");
      assertEquals("sales3(User),sales(Group),other(User)", getEmails(groupTask));

      Element userTask = getTask(userTaskKey, ORG);
      assertEquals("sales3~;~" + ORG, userTask.getAttribute("idname"),
                   "the task runs as the renamed user");
      assertEquals(Integer.toString(Identity.USER), userTask.getAttribute("idtype"));
   }

   @Test
   void deprecatedNameOverload_renamesUserOfCurrentOrg() throws Exception {
      String taskKey = seedTask(ORG, "sales", "T1", new Group(new IdentityID("sales", ORG)));

      indexedStorage.migrateStorageData("sales", "sales3");

      Element task = getTask(taskKey, ORG);
      assertEquals("sales3~;~" + ORG, task.getAttribute("owner"));
      assertEquals("sales~;~" + ORG, task.getAttribute("idname"));
      assertEquals("sales3(User),sales(Group),other(User)", getEmails(task));
   }

   @Test
   void groupRename_updatesTheGroupOrgNotTheCurrentOrg() throws Exception {
      String otherTaskKey =
         seedTask(OTHER_ORG, "bob", "T1", new Group(new IdentityID("sales", OTHER_ORG)));
      String currentTaskKey = seedTask(ORG, "bob", "T1", new Group(new IdentityID("sales", ORG)));

      indexedStorage.migrateStorageData(new IdentityID("sales", OTHER_ORG),
                                        new IdentityID("sales2", OTHER_ORG), Identity.GROUP);

      Element otherTask = getTask(otherTaskKey, OTHER_ORG);
      assertEquals("sales2~;~" + OTHER_ORG, otherTask.getAttribute("idname"));
      assertEquals("sales(User),sales2(Group),other(User)", getEmails(otherTask));

      Element currentTask = getTask(currentTaskKey, ORG);
      assertEquals("sales~;~" + ORG, currentTask.getAttribute("idname"),
                   "the same-named group of the current org must not be renamed");
      assertEquals("sales(User),sales(Group),other(User)", getEmails(currentTask));
   }

   private String privateWorksheetKey(String orgID, String user) {
      return new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.WORKSHEET, "wsSales",
                            new IdentityID(user, orgID), orgID).toIdentifier(true);
   }

   private String seedPrivateWorksheet(String orgID, String user) throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly assembly = new EmbeddedTableAssembly(ws, "t1");
      ws.addAssembly(assembly);
      ws.setPrimaryAssembly(assembly);

      String key = privateWorksheetKey(orgID, user);
      indexedStorage.putXMLSerializable(key, ws);
      seededKeys.add(key);
      return key;
   }

   /**
    * Seeds a task owned by the user, which runs as the identity, depends on another task of the
    * user, emails the user and the same-named group, and exports the user's private viewsheet.
    */
   private String seedTask(String orgID, String owner, String name, Identity executeAs)
      throws Exception
   {
      IdentityID ownerID = new IdentityID(owner, orgID);
      String taskId = ownerID.convertToKey() + ":" + name;

      CompletionCondition condition = new CompletionCondition();
      condition.setTaskName("sales~;~" + orgID + ":T2");

      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet(PRIVATE_VS.formatted(orgID, orgID));
      action.setEmails("sales(User),sales(Group),other(User)");

      ScheduleTask task = new ScheduleTask(taskId);
      task.setOwner(ownerID);
      task.setIdentity(executeAs);
      task.addCondition(condition);
      task.addAction(action);

      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      task.writeXML(writer);
      writer.flush();
      Document document = Tool.parseXML(new StringReader(buffer.toString()));

      String key = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK,
                                  "/" + taskId, ownerID, orgID).toIdentifier();
      indexedStorage.putDocument(key, document, ScheduleTask.class.getName(), orgID);
      seededKeys.add(key);
      return key;
   }

   private Element getTask(String key, String orgID) {
      Document document = indexedStorage.getDocument(key, orgID);
      assertNotNull(document, "the task must stay under its key: " + key);
      return document.getDocumentElement();
   }

   private String getEmails(Element task) {
      Element action = Tool.getChildNodeByTagName(task, "Action");
      Element mailTo = Tool.getChildNodeByTagName(action, "MailTo");
      return Tool.byteDecode(mailTo.getAttribute("email"));
   }

   private String getCompletionTask(Element task) {
      return Tool.getChildNodeByTagName(task, "Condition").getAttribute("task");
   }

   private String getViewsheet(Element task) {
      return Tool.getChildNodeByTagName(task, "Action").getAttribute("viewsheet");
   }

   private static final String ORG = "host-org";
   private static final String OTHER_ORG = "rename_type_other_org";
   private static final String PRIVATE_VS = "4^128^sales~;~%s^vs1^%s";
   private final List<String> seededKeys = new ArrayList<>();
}
