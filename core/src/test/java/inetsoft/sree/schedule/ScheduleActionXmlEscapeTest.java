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

import inetsoft.sree.DynamicParameterValue;
import inetsoft.sree.RepletRequest;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.*;
import inetsoft.util.dep.XAsset;
import inetsoft.web.composer.model.vs.DynamicValueModel;
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
 *    <li>Bug #77806: characters XML 1.0 can't carry (C0 controls) in CDATA free text: the
 *    email message, viewsheet action parameters and batch action parameters.</li>
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
   // Bug #77806: C0 control characters in CDATA
   // ---------------------------------------------------------------------------------------

   @ParameterizedTest
   @ValueSource(strings = { "hello\u001Fworld C:\\dir \\u001F ]]> x", "a\u0001b\nc\u0000",
                            "plain ]]> text\ttab C:\\dir" })
   void emailMessage_survivesStorageAndCopy(String message) throws Exception {
      ScheduleTask task = task("msg77806", viewsheet(message, null));

      assertAll(
         () -> assertEquals(message, message(ScheduleTask.copyScheduleTask(task))),
         () -> assertEquals(message, message(storeAndReload(task))),
         () -> assertTrue(scheduleManager.getScheduleTasks(ORG).stream()
                             .anyMatch(t -> t.getTaskId().equals(task.getTaskId()))));
   }

   @Test
   void viewsheetParameters_surviveStorageAndCopy() throws Exception {
      RepletRequest request = new RepletRequest();
      request.setParameter("s", "x\u001Fy");
      request.setParameter("cr", "a\rb\r\nc");
      request.setParameter("arr", new Object[] { "a\u0001b", "c" });
      request.setParameter("dv", new DynamicParameterValue("d\u001Fe", DynamicValueModel.VALUE,
                                                           "string"));
      request.setParameter("dva", new DynamicParameterValue(new Object[] { "f\u001Fg", "h" },
                                                            DynamicValueModel.VALUE, "string"));
      request.setParameter("ex", new DynamicParameterValue("1+\u001F2 C:\\dir",
                                                           DynamicValueModel.EXPRESSION, "string"));
      request.setParameter("excd", new DynamicParameterValue("a[b[0]]>1",
                                                             DynamicValueModel.EXPRESSION, "string"));
      ScheduleTask task = task("vp77806", viewsheet(null, request));

      assertAll(
         () -> assertParameters(request, ScheduleTask.copyScheduleTask(task)),
         () -> assertParameters(request, storeAndReload(task)));
   }

   // a parameter typed as the literal text ~_1f_~ is decoded to U+001F on read (as before);
   // it must not break the task on the next save
   @Test
   void literalByteEncodedParameterText_survivesResave() throws Exception {
      RepletRequest request = new RepletRequest();
      request.setParameter("s", "x~_1f_~y");
      request.setParameter("arr", new Object[] { "x~_1f_~y", "c" });
      request.setParameter("dv", new DynamicParameterValue("x~_1f_~y", DynamicValueModel.VALUE,
                                                           "string"));
      ScheduleTask task = task("lit77806", viewsheet(null, request));

      ScheduleTask loaded = storeAndReload(task);
      ScheduleTask reloaded = storeAndReload(loaded);
      RepletRequest result = ((ViewsheetAction) reloaded.getAction(0)).getViewsheetRequest();

      assertEquals("x\u001Fy", result.getParameter("s"));
      assertArrayEquals(new Object[] { "x\u001Fy", "c" }, (Object[]) result.getParameter("arr"));
      assertEquals("x\u001Fy", ((DynamicParameterValue) result.getParameter("dv")).getValue());
   }

   @Test
   void batchParameters_surviveStorageAndCopy() throws Exception {
      Map<String, Object> embedded = new LinkedHashMap<>();
      embedded.put("k\u001F1", "v\u001Fw C:\\dir");
      embedded.put("cd]]>", "a]]>b");
      embedded.put("arr", new Object[] { "a^b\u0002", "c~d" });
      embedded.put("dyn", new DynamicParameterValue("1+\u001F2 ]]> C:\\x",
                                                    DynamicValueModel.EXPRESSION, "string"));
      Map<String, Object> query = new LinkedHashMap<>();
      query.put("q", "p\u001Fq");
      BatchAction batch = batch(new IdentityID("admin", ORG).convertToKey() + ":target");
      batch.setEmbeddedParameters(new ArrayList<>(List.of(embedded)));
      batch.setQueryParameters(query);
      ScheduleTask task = task("bp77806", batch);

      assertAll(
         () -> assertBatchParameters(embedded, query, ScheduleTask.copyScheduleTask(task)),
         () -> assertBatchParameters(embedded, query, storeAndReload(task)));
   }

   // ]]> without any control character (expression parameter, batch key and value)
   @Test
   void cdataEnd_survivesStorageAndCopy() throws Exception {
      RepletRequest request = new RepletRequest();
      request.setParameter("excd", new DynamicParameterValue("a[b[0]]>1",
                                                             DynamicValueModel.EXPRESSION, "string"));
      ScheduleTask vsTask = task("cdv77806", viewsheet(null, request));
      Map<String, Object> embedded = new LinkedHashMap<>();
      embedded.put("cd]]>", "a]]>b");
      BatchAction batch = batch(new IdentityID("admin", ORG).convertToKey() + ":target");
      batch.setEmbeddedParameters(new ArrayList<>(List.of(embedded)));
      ScheduleTask btTask = task("cdb77806", batch);

      assertAll(
         () -> assertEquals("a[b[0]]>1", ((DynamicParameterValue) ((ViewsheetAction)
            storeAndReload(vsTask).getAction(0)).getViewsheetRequest().getParameter("excd"))
            .getValue()),
         () -> assertEquals(embedded, ((BatchAction) ScheduleTask.copyScheduleTask(btTask)
            .getAction(0)).getEmbeddedParameters().get(0)),
         () -> assertEquals(embedded, ((BatchAction) storeAndReload(btTask).getAction(0))
            .getEmbeddedParameters().get(0)));
   }

   @Test
   void ordinaryText_writtenAsBefore() {
      RepletRequest request = new RepletRequest();
      request.setParameter("s", "plain C:\\dir \u00e9");
      request.setParameter("arr", new Object[] { "a", "b\tc" });
      request.setParameter("dv", new DynamicParameterValue("dv", DynamicValueModel.VALUE, "string"));
      request.setParameter("ex", new DynamicParameterValue("a[b[0]] > 1 \\",
                                                           DynamicValueModel.EXPRESSION, "string"));
      String vsXml = xml(viewsheet("plain ]]> text C:\\dir\n", request));

      // what the writers produced before the fix
      assertTrue(vsXml.contains(
         "<message><![CDATA[plain ]]]]><![CDATA[> text C:\\dir\n]]></message>"), vsXml);
      assertTrue(vsXml.contains("<value><![CDATA[" + Tool.byteEncode("plain C:\\dir \u00e9") +
                                   "]]></value>"), vsXml);
      assertTrue(vsXml.contains("<value><![CDATA[b\tc]]></value>"), vsXml);
      assertTrue(vsXml.contains("<value><![CDATA[dv]]></value>"), vsXml);
      assertTrue(vsXml.contains("<value><![CDATA[a[b[0]] > 1 \\]]></value>"), vsXml);

      Map<String, Object> map = new LinkedHashMap<>();
      map.put("k", "v C:\\dir");
      map.put("arr", new Object[] { "a^b", "c" });
      map.put("dyn", new DynamicParameterValue("1+2", DynamicValueModel.EXPRESSION, "string"));
      BatchAction batch = batch("admin~;~" + ORG + ":t");
      batch.setEmbeddedParameters(new ArrayList<>(List.of(map)));
      String btXml = xml(batch);

      assertTrue(btXml.contains("<key><![CDATA[k]]></key><value><![CDATA[v C:\\dir]]></value>"),
                 btXml);
      assertTrue(btXml.contains("<key><![CDATA[arr]]></key><value><![CDATA[" +
                                   Tool.getDataString(map.get("arr")) + "]]></value>"), btXml);
      assertTrue(btXml.contains("<dynamicParameterValue><value><![CDATA[1+2]]></value>"), btXml);
      assertFalse(vsXml.contains("ctrlEncoded") || btXml.contains("ctrlEncoded"));
   }

   @Test
   void controlCharParameter_writtenByteEncodedWithoutMarker() {
      RepletRequest request = new RepletRequest();
      request.setParameter("s", "x\u001Fy\rz");
      String xml = xml(viewsheet(null, request));

      // the form the unchanged reader (also in older versions) already decodes
      assertTrue(xml.contains("<value><![CDATA[x~_1f_~y~_d_~z]]></value>"), xml);
      assertFalse(xml.contains("ctrlEncoded"), xml);
   }

   @Test
   void unmarkedOldText_readUnchanged() throws Exception {
      String xml = "<Task name=\"old77806\" owner=\"admin~;~" + ORG + "\" enabled=\"true\">" +
         condition() +
         "<Action type=\"Viewsheet\" viewsheet=\"1^128^__NULL__^vs1^" + ORG + "\">" +
         "<MailTo email=\"a@b.com\"><message><![CDATA[m \\u001F C:\\\\x]]></message></MailTo>" +
         "<Request><Parameter name=\"ex\"  dynamicType=\"" + DynamicValueModel.EXPRESSION +
         "\" type=\"string\"><value><![CDATA[e \\u001F \\\\]]></value></Parameter>" +
         "</Request></Action>" +
         "<Action type=\"Batch\" class=\"" + BatchAction.class.getName() + "\" " +
         "taskId=\"admin~;~" + ORG + ":t\" ><queryParameters></queryParameters>" +
         "<embeddedParameters><map><entry><key><![CDATA[k \\u001F]]></key>" +
         "<value><![CDATA[v \\u001F \\\\]]></value><valueType><![CDATA[string]]></valueType>" +
         "</entry></map></embeddedParameters></Action></Task>";
      ScheduleTask task = parse(xml);

      // text without the marker is never decoded
      ViewsheetAction vs = (ViewsheetAction) task.getAction(0);
      assertEquals("m \\u001F C:\\\\x", vs.getMessage());
      assertEquals("e \\u001F \\\\",
                   ((DynamicParameterValue) vs.getViewsheetRequest().getParameter("ex")).getValue());
      Map<String, Object> map = ((BatchAction) task.getAction(1)).getEmbeddedParameters().get(0);
      assertEquals(Map.of("k \\u001F", "v \\u001F \\\\"), map);
   }

   @Test
   void legacyMessageAttribute_survivesResave() throws Exception {
      String xml = "<Task name=\"lgm77806\" owner=\"admin~;~" + ORG + "\" enabled=\"true\">" +
         condition() +
         "<Action type=\"Viewsheet\" viewsheet=\"1^128^__NULL__^vs1^" + ORG + "\">" +
         "<MailTo email=\"a@b.com\" message=\"a~_1f_~b\"></MailTo></Action></Task>";
      ScheduleTask task = parse(xml);
      assertEquals("a\u001Fb", message(task));

      assertEquals("a\u001Fb", message(storeAndReload(task)));
   }

   // ---------------------------------------------------------------------------------------
   // helpers
   // ---------------------------------------------------------------------------------------

   private static ViewsheetAction viewsheet(String message, RepletRequest request) {
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet("1^128^__NULL__^vs1^" + ORG);
      action.setEmails("a@b.com");
      action.setMessage(message);

      if(request != null) {
         action.setViewsheetRequest(request);
      }

      return action;
   }

   private static String message(ScheduleTask task) {
      assertTrue(task.getActionCount() > 0, "actions dropped on parse");
      return ((ViewsheetAction) task.getAction(0)).getMessage();
   }

   private static void assertParameters(RepletRequest expected, ScheduleTask task) {
      assertTrue(task.getActionCount() > 0, "actions dropped on parse");
      RepletRequest actual = ((ViewsheetAction) task.getAction(0)).getViewsheetRequest();

      for(String name : new String[] { "s", "cr", "arr", "dv", "dva", "ex", "excd" }) {
         assertParameterValue(name, expected.getParameter(name), actual.getParameter(name));
      }
   }

   private static void assertParameterValue(String name, Object expected, Object actual) {
      if(expected instanceof DynamicParameterValue dv) {
         assertInstanceOf(DynamicParameterValue.class, actual, name);
         DynamicParameterValue av = (DynamicParameterValue) actual;
         assertEquals(dv.getType(), av.getType(), name);
         assertEquals(dv.getDataType(), av.getDataType(), name);
         assertParameterValue(name, dv.getValue(), av.getValue());
      }
      else if(expected instanceof Object[] arr) {
         assertArrayEquals(arr, (Object[]) actual, name);
      }
      else {
         assertEquals(expected, actual, name);
      }
   }

   private static void assertBatchParameters(Map<String, Object> embedded,
                                             Map<String, Object> query, ScheduleTask task)
   {
      assertTrue(task.getActionCount() > 0, "actions dropped on parse");
      BatchAction batch = (BatchAction) task.getAction(0);
      assertMap(query, batch.getQueryParameters());
      assertEquals(1, batch.getEmbeddedParameters().size());
      assertMap(embedded, batch.getEmbeddedParameters().get(0));
   }

   private static void assertMap(Map<String, Object> expected, Map<String, Object> actual) {
      assertEquals(expected.keySet(), actual.keySet());

      for(String key : expected.keySet()) {
         assertParameterValue(key, expected.get(key), actual.get(key));
      }
   }

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
