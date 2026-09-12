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

import inetsoft.sree.security.IdentityID;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.util.PasswordEncryption;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.XMLSerializable;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.util.config.SecretsConfig;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationContext;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Layer 2 — XML round-trip tests.
 *
 * <p>Verifies that ViewsheetAction and BatchAction serialize and deserialize correctly
 * via {@code writeXML(PrintWriter)} / {@code parseXML(Element)}. These tests run
 * without Spring, Quartz, or network — they are pure serialization contracts.
 *
 * <p>Production path: tasks are written to IndexedStorage as XML and loaded back on
 * restart, so a field that is set in memory but not persisted to XML will silently
 * disappear on reload.
 */
@Tag("core")
class ScheduleActionXmlRoundTripTest {

   // Viewsheet identifiers have the format: scope^assetType^user^path^orgID (4 carets).
   // Using the full 5-segment form so handleViewsheetLinkOrgMismatch() treats the string
   // as already org-qualified and returns it unchanged during parseXML.
   private static final String VS_ID = "1^128^__NULL__^vs1^host-org";

   private ApplicationContext savedAppContext;

   // EmailInfo.parseXML() calls Tool.decryptPassword() which reaches Spring via:
   //   PasswordEncryption.newLocalInstance(false)
   //   → InetsoftConfig.getInstance().getSecrets()  [needs Spring bean]
   //   → newLocalInstance(SecretsConfig{type="local"})
   //   → newInstance()  [needs PasswordEncryption Spring bean]
   // Register minimal mocks so both lookups succeed without a full Spring context.
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

   // -----------------------------------------------------------------------
   // ViewsheetAction round-trips
   // -----------------------------------------------------------------------

   @Test
   void viewsheetAction_viewsheetAndEmailsRoundTrip() throws Exception {
      ViewsheetAction original = new ViewsheetAction();
      original.setViewsheet(VS_ID);
      original.setEmails("alice@example.com, bob@example.com");
      original.setFrom("scheduler@example.com");
      original.setSubject("Monthly Sales Report");

      ViewsheetAction loaded = roundTripAction(original, ViewsheetAction.class);

      assertEquals(VS_ID, loaded.getViewsheet());
      assertEquals("alice@example.com, bob@example.com", loaded.getEmails());
      assertEquals("scheduler@example.com", loaded.getFrom());
      assertEquals("Monthly Sales Report", loaded.getSubject());
   }

   @Test
   void viewsheetAction_filePathRoundTrip() throws Exception {
      ViewsheetAction original = new ViewsheetAction();
      original.setViewsheet(VS_ID);
      ServerPathInfo pathInfo = new ServerPathInfo("/exports/sales/report.pdf");
      original.setFilePath(FileFormatInfo.EXPORT_TYPE_PDF, pathInfo);

      ViewsheetAction loaded = roundTripAction(original, ViewsheetAction.class);

      ServerPathInfo loadedPath = loaded.getFilePathInfo(FileFormatInfo.EXPORT_TYPE_PDF);
      assertNotNull(loadedPath, "PDF file path must survive round-trip");
      assertEquals("/exports/sales/report.pdf", loadedPath.getPath());
   }

   @Test
   void viewsheetAction_bookmarksRoundTrip() throws Exception {
      ViewsheetAction original = new ViewsheetAction();
      original.setViewsheet(VS_ID);
      original.setBookmarks(new String[]{"Monthly View", "Quarterly View"});
      original.setBookmarkTypes(new int[]{1, 2});
      original.setBookmarkUsers(new IdentityID[]{
         new IdentityID("alice", "host-org"),
         new IdentityID("bob",   "host-org")
      });

      ViewsheetAction loaded = roundTripAction(original, ViewsheetAction.class);

      assertArrayEquals(new String[]{"Monthly View", "Quarterly View"}, loaded.getBookmarks());
      assertArrayEquals(new int[]{1, 2}, loaded.getBookmarkTypes());
      assertNotNull(loaded.getBookmarkUsers());
      assertEquals("alice", loaded.getBookmarkUsers()[0].getName());
      assertEquals("bob",   loaded.getBookmarkUsers()[1].getName());
   }

   // -----------------------------------------------------------------------
   // BatchAction round-trips
   // -----------------------------------------------------------------------

   @Test
   void batchAction_taskIdRoundTrip() throws Exception {
      BatchAction original = new BatchAction();
      original.setTaskId("scheduler-test^host:sales-batch-task");

      BatchAction loaded = roundTripAction(original, BatchAction.class);

      assertEquals("scheduler-test^host:sales-batch-task", loaded.getTaskId());
   }

   @Test
   void batchAction_queryParametersRoundTrip() throws Exception {
      BatchAction original = new BatchAction();
      original.setTaskId("scheduler-test^host:params-task");
      Map<String, Object> params = new LinkedHashMap<>();
      params.put("month", "January");
      params.put("year", "2025");
      original.setQueryParameters(params);

      BatchAction loaded = roundTripAction(original, BatchAction.class);

      Map<String, Object> loadedParams = loaded.getQueryParameters();
      assertNotNull(loadedParams);
      assertEquals("January", loadedParams.get("month"));
      assertEquals("2025",    loadedParams.get("year"));
   }

   @Test
   void batchAction_embeddedParametersRoundTrip() throws Exception {
      BatchAction original = new BatchAction();
      original.setTaskId("scheduler-test^host:embedded-task");

      List<Map<String, Object>> embedded = new ArrayList<>();
      Map<String, Object> row1 = new LinkedHashMap<>();
      row1.put("region",  "North");
      row1.put("quarter", "Q1");
      embedded.add(row1);
      Map<String, Object> row2 = new LinkedHashMap<>();
      row2.put("region",  "South");
      row2.put("quarter", "Q2");
      embedded.add(row2);
      original.setEmbeddedParameters(embedded);

      BatchAction loaded = roundTripAction(original, BatchAction.class);

      List<Map<String, Object>> loadedEmbedded = loaded.getEmbeddedParameters();
      assertNotNull(loadedEmbedded);
      assertEquals(2, loadedEmbedded.size());
      assertEquals("North", loadedEmbedded.get(0).get("region"));
      assertEquals("Q1",    loadedEmbedded.get(0).get("quarter"));
      assertEquals("South", loadedEmbedded.get(1).get("region"));
      assertEquals("Q2",    loadedEmbedded.get(1).get("quarter"));
   }

   // -----------------------------------------------------------------------
   // ScheduleTask container round-trip
   // -----------------------------------------------------------------------

   @Test
   void scheduleTask_actionsRoundTrip() throws Exception {
      ScheduleTask original = new ScheduleTask("sales-task");
      original.setOwner(new IdentityID("scheduler-test", "host"));
      original.setPath("/");

      // ScheduleTask.parseXML() returns early when no <Condition> elements are present,
      // so it never reaches the <Action> block. Add a daily TimeCondition to ensure
      // action parsing is exercised.
      TimeCondition cond = new TimeCondition();
      cond.setType(TimeCondition.EVERY_DAY);
      original.addCondition(cond);

      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet(VS_ID);
      action.setEmails("test@example.com");
      original.addAction(action);

      // Serialize
      StringWriter sw = new StringWriter();
      original.writeXML(new PrintWriter(sw));
      String wrapped = "<root>" + sw + "</root>";

      // Parse
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
      dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      Document doc = dbf.newDocumentBuilder()
         .parse(new ByteArrayInputStream(wrapped.getBytes(StandardCharsets.UTF_8)));
      Element taskElem = firstChildElement(doc.getDocumentElement());

      ScheduleTask loaded = new ScheduleTask();
      loaded.parseXML(taskElem);

      assertEquals(1, loaded.getActionCount(), "Action count must survive round-trip");
      assertInstanceOf(ViewsheetAction.class, loaded.getAction(0));
      assertEquals("test@example.com",
         ((ViewsheetAction) loaded.getAction(0)).getEmails());
   }

   // -----------------------------------------------------------------------
   // Helpers
   // -----------------------------------------------------------------------

   /**
    * Serialize {@code original} to XML, wrap in a {@code <root>} element, parse
    * the DOM, and deserialize into a fresh instance of {@code type}.
    */
   private <T extends XMLSerializable> T roundTripAction(T original, Class<T> type)
      throws Exception
   {
      StringWriter sw = new StringWriter();
      original.writeXML(new PrintWriter(sw));
      String wrapped = "<root>" + sw + "</root>";

      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
      dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      Document doc = dbf.newDocumentBuilder()
         .parse(new ByteArrayInputStream(wrapped.getBytes(StandardCharsets.UTF_8)));

      Element first = firstChildElement(doc.getDocumentElement());
      T loaded = type.getDeclaredConstructor().newInstance();
      loaded.parseXML(first);
      return loaded;
   }

   /** Returns the first Element child of {@code parent}, skipping text nodes. */
   private static Element firstChildElement(Element parent) {
      NodeList children = parent.getChildNodes();

      for(int i = 0; i < children.getLength(); i++) {
         if(children.item(i) instanceof Element e) {
            return e;
         }
      }

      throw new AssertionError("No child element found under <" + parent.getTagName() + ">");
   }
}
