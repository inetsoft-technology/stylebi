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
package inetsoft.util.migrate;

import inetsoft.sree.schedule.ScheduleAction;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.schedule.ViewsheetAction;
import inetsoft.sree.security.Organization;
import inetsoft.uql.util.Identity;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.PasswordEncryption;
import inetsoft.util.Tool;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.util.config.SecretsConfig;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationContext;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #77162: the email recipient migration of {@link MigrateScheduleTask} on an identity
 * rename.
 */
@Tag("core")
class MigrateScheduleTaskTest {
   private static final String VS_ID = "1^128^__NULL__^vs1^host-org";
   private static final String ORG = "host-org";

   private ApplicationContext savedAppContext;

   // EmailInfo.parseXML reaches Spring through Tool.decryptPassword (see
   // ScheduleActionXmlRoundTripTest)
   @BeforeEach
   void setUpAppContext() {
      PasswordEncryption mockPwdEnc = mock(PasswordEncryption.class);
      when(mockPwdEnc.decryptPassword(any())).thenAnswer(inv -> inv.getArgument(0));

      InetsoftConfig mockInetsoftConfig = mock(InetsoftConfig.class);
      when(mockInetsoftConfig.getSecrets()).thenReturn(new SecretsConfig());

      ApplicationContext mockAppCtx = mock(ApplicationContext.class);
      when(mockAppCtx.getBean(PasswordEncryption.class)).thenReturn(mockPwdEnc);
      when(mockAppCtx.getBean(InetsoftConfig.class)).thenReturn(mockInetsoftConfig);

      savedAppContext = ConfigurationContext.getContext().getApplicationContext();
      ConfigurationContext.getContext().setApplicationContext(mockAppCtx);
   }

   @AfterEach
   void tearDownAppContext() {
      ConfigurationContext.getContext().setApplicationContext(savedAppContext);
   }

   @Test
   void userRename_renamesToCcBccAndNotify_keepingDelimiters() throws Exception {
      Document doc = migrateUser("alice", "alice2", action(
         mailTo("email", "alice(User) , x@y.com;carol", "ccAddresses", "alice(User)",
                "bccAddresses", "alice") +
         notify("alice ; carol")));

      Element mailTo = child(doc, 0, "MailTo");
      assertEquals("alice2(User) , x@y.com;carol", mailTo.getAttribute("email"));
      assertEquals("alice2(User)", mailTo.getAttribute("ccAddresses"));
      assertEquals("alice2", mailTo.getAttribute("bccAddresses"));
      assertEquals("alice2 ; carol", child(doc, 0, "Notify").getAttribute("email"));
   }

   @Test
   void userRename_renamesBareNameTokens() throws Exception {
      Document doc = migrateUser("alice", "alice2", action(
         mailTo("email", "alice, alice(User), alice@x.com, alice(Group)")));

      assertEquals("alice2, alice2(User), alice@x.com, alice(Group)",
                   child(doc, 0, "MailTo").getAttribute("email"));
   }

   @Test
   void noMatch_leavesActionXmlUnchanged_includingLegacySeparators() throws Exception {
      String legacyNotify = Tool.byteEncode2("bob(User);张三");
      String xml = action(
         mailTo("email", "carol , dave;e@f.com", "ccAddresses", "bob(User) ; carol") +
         notify(legacyNotify)) +
         action(mailTo("ccAddresses", "carol"));
      Document before = parse(task(xml));
      Document after = migrateUser("alice", "alice2", xml);

      assertEquals(serializeActions(before), serializeActions(after));
      assertEquals(legacyNotify, child(after, 0, "Notify").getAttribute("email"));
      assertFalse(child(after, 1, "MailTo").hasAttribute("email"),
                  "no email attribute must be injected");
   }

   @Test
   void legacyEncodedRecipients_areRenamed_andMixedElementRoundTrips() throws Exception {
      Document doc = migrateUser("bob", "bob2", action(
         mailTo("email", "张三(User), bob(User)",
                "ccAddresses", Tool.byteEncode2("李四(User),bob(User)"),
                "bccAddresses", Tool.byteEncode2("carol,dave")) +
         notify(Tool.byteEncode2("bob(User);张三"))));

      ViewsheetAction action = (ViewsheetAction) parseScheduleAction(
         (Element) doc.getElementsByTagName("Action").item(0));

      assertEquals("张三(User), bob2(User)", action.getEmails());
      assertEquals("李四(User),bob2(User)", action.getCCAddresses());
      assertEquals("carol,dave", action.getBCCAddresses());
      assertEquals("bob2(User);张三", action.getNotifications());
   }

   @Test
   void groupRename_renamesGroupTokens_keepingSameNamedUser() throws Exception {
      Document doc = migrate(new MigrateScheduleTask(null, "g1", "g9", new Organization(ORG),
                                                     Identity.GROUP),
         action(mailTo("email", "g1(Group) ; g1(User)", "ccAddresses", "g1(Group)",
                       "bccAddresses", "g1") +
                notify("g1(Group);g2(Group)")));

      Element mailTo = child(doc, 0, "MailTo");
      assertEquals("g9(Group) ; g1(User)", mailTo.getAttribute("email"));
      assertEquals("g9(Group)", mailTo.getAttribute("ccAddresses"));
      assertEquals("g1", mailTo.getAttribute("bccAddresses"));
      assertEquals("g9(Group);g2(Group)", child(doc, 0, "Notify").getAttribute("email"));
   }

   @Test
   void secondViewsheetActionWithoutBookmark_isMigrated() throws Exception {
      Document doc = migrateUser("alice", "alice2",
         action(mailTo("email", "alice(User)")) +
         action(mailTo("email", "alice(User)", "ccAddresses", "alice") + notify("alice")));

      assertEquals("alice2(User)", child(doc, 0, "MailTo").getAttribute("email"));
      assertEquals("alice2(User)", child(doc, 1, "MailTo").getAttribute("email"));
      assertEquals("alice2", child(doc, 1, "MailTo").getAttribute("ccAddresses"));
      assertEquals("alice2", child(doc, 1, "Notify").getAttribute("email"));
   }

   @Test
   void orgMigration_keepsRecipientBytes_andMigratesLaterActionIds() throws Exception {
      String legacyCc = Tool.byteEncode2("bob(User);张三");
      String xml = action(
         mailTo("email", "carol , dave;e@f.com", "ccAddresses", legacyCc,
                "bccAddresses", "bob") +
         notify("bob(User) ; carol")) +
         action(mailTo("email", "carol") + "<Bookmark user=\"bob~;~" + ORG + "\"/>");
      Document before = parse(task(xml));
      Document after = migrate(new MigrateScheduleTask(null, new Organization(ORG),
                                                       new Organization("org2")), xml);

      for(int i = 0; i < 2; i++) {
         assertEquals(serialize(child(before, i, "MailTo")), serialize(child(after, i, "MailTo")));
      }

      assertEquals(serialize(child(before, 0, "Notify")), serialize(child(after, 0, "Notify")));
      assertEquals(legacyCc, child(after, 0, "MailTo").getAttribute("ccAddresses"));

      String nvs = "1^128^__NULL__^vs1^org2";
      NodeList actions = after.getElementsByTagName("Action");
      assertEquals(nvs, ((Element) actions.item(0)).getAttribute("viewsheet"));
      // the first action has no Bookmark, the later action must still be migrated
      assertEquals(nvs, ((Element) actions.item(1)).getAttribute("viewsheet"));
      assertEquals("bob~;~org2", child(after, 1, "Bookmark").getAttribute("user"));
   }

   @Test
   void actionAfterMvActionWithoutMvDef_isMigrated() throws Exception {
      Document doc = migrateUser("alice", "alice2",
         "<Action type=\"MV\"></Action>" +
         action(mailTo("email", "alice(User)", "ccAddresses", "alice") + notify("alice")));

      Element mailTo = (Element) doc.getElementsByTagName("MailTo").item(0);
      assertEquals("alice2(User)", mailTo.getAttribute("email"));
      assertEquals("alice2", mailTo.getAttribute("ccAddresses"));
      assertEquals("alice2",
                   ((Element) doc.getElementsByTagName("Notify").item(0)).getAttribute("email"));
   }

   // Bug #77883, on a user rename without an organization, the bare name of a task with an owner
   // key is kept, even when it starts with "<owner>:", and the task ids in the actions keep the
   // whole name after the first ':'
   @Test
   void userRename_keepsColonInTaskNameAndTaskIds() throws Exception {
      Document doc = parse(colonTask("alice:report"));
      new MigrateScheduleTask(null, "alice", "alice2").processAssemblies(doc.getDocumentElement());

      Element task = doc.getDocumentElement();
      assertEquals("alice:report", task.getAttribute("name"));
      assertEquals("alice2~;~" + ORG, task.getAttribute("owner"));
      assertEquals("alice2~;~" + ORG + ":a:b", batchTaskId(doc));
      assertEquals("alice2~;~" + ORG + ":a:b", backupTaskPath(doc));
   }

   // Bug #77883, an org migration keeps the whole task name after the first ':'
   @Test
   void orgMigration_keepsColonInTaskNameAndTaskIds() throws Exception {
      Document doc = parse(colonTask("task:name"));
      new MigrateScheduleTask(null, new Organization(ORG), new Organization("org2"))
         .processAssemblies(doc.getDocumentElement());

      assertEquals("task:name", doc.getDocumentElement().getAttribute("name"));
      assertEquals("alice~;~org2:a:b", batchTaskId(doc));
      assertEquals("alice~;~org2:a:b", backupTaskPath(doc));
   }

   private static String colonTask(String name) {
      return "<Task name=\"" + name + "\" owner=\"alice~;~" + ORG + "\">" +
         "<Action type=\"Batch\" taskId=\"alice~;~" + ORG + ":a:b\"/>" +
         "<Action type=\"Backup\"><XAsset type=\"SCHEDULETASK\" path=\"alice~;~" + ORG +
         ":a:b\"/></Action></Task>";
   }

   private static String batchTaskId(Document doc) {
      return ((Element) doc.getElementsByTagName("Action").item(0)).getAttribute("taskId");
   }

   private static String backupTaskPath(Document doc) {
      return ((Element) doc.getElementsByTagName("XAsset").item(0)).getAttribute("path");
   }

   private static String serialize(Node node) throws Exception {
      StringWriter writer = new StringWriter();
      var transformer = TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      transformer.transform(new DOMSource(node), new StreamResult(writer));
      return writer.toString();
   }

   private static Document migrateUser(String oname, String nname, String actions)
      throws Exception
   {
      return migrate(new MigrateScheduleTask(null, oname, nname, new Organization(ORG),
                                             Identity.USER), actions);
   }

   private static Document migrate(MigrateScheduleTask migrate, String actions)
      throws Exception
   {
      Document doc = parse(task(actions));
      migrate.processAssemblies(doc.getDocumentElement());
      return doc;
   }

   private static String task(String actions) {
      return "<Task name=\"t1\">" + actions + "</Task>";
   }

   private static String action(String children) {
      return "<Action type=\"Viewsheet\" viewsheet=\"" + VS_ID + "\">" + children + "</Action>";
   }

   private static String mailTo(String... attrs) {
      StringBuilder sb = new StringBuilder("<MailTo");

      for(int i = 0; i < attrs.length; i += 2) {
         sb.append(' ').append(attrs[i]).append("=\"").append(Tool.escape(attrs[i + 1]))
            .append('"');
      }

      return sb.append("></MailTo>").toString();
   }

   private static String notify(String email) {
      return "<Notify email=\"" + Tool.escape(email) + "\" onError=\"false\" link=\"false\"/>";
   }

   private static Document parse(String xml) throws Exception {
      return DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
   }

   private static Element child(Document doc, int action, String name) {
      Element elem = (Element) doc.getElementsByTagName("Action").item(action);
      return (Element) elem.getElementsByTagName(name).item(0);
   }

   private static String serializeActions(Document doc) throws Exception {
      StringBuilder sb = new StringBuilder();
      NodeList actions = doc.getElementsByTagName("Action");

      for(int i = 0; i < actions.getLength(); i++) {
         StringWriter writer = new StringWriter();
         var transformer = TransformerFactory.newInstance().newTransformer();
         transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
         transformer.transform(new DOMSource(actions.item(i)), new StreamResult(writer));
         sb.append(writer);
      }

      return sb.toString();
   }

   // the reader of the stored task XML, which decodes the recipient attributes
   private static ScheduleAction parseScheduleAction(Element action) throws Exception {
      Method method = ScheduleTask.class.getDeclaredMethod(
         "parseScheduleAction", Element.class, boolean.class);
      method.setAccessible(true);
      return (ScheduleAction) method.invoke(null, action, false);
   }
}
