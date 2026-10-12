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

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77549: the query entry of a batch action comes from the client (the task editor or an
 * imported task) with its properties as sent. With openAutoSaved, AbstractAssetEngine.getSheet
 * reads the auto-saved file named by autoFileName (e.g. another user's unsaved worksheet) without
 * a permission check, so the auto-save properties are removed when the entry is set, when it's
 * parsed (a stored or imported task) and when the query runs.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class BatchActionAutoSavePropertiesTest {
   private static final String VICTIM_FILE = "4^WORKSHEET^victim~;~host-org^Private^~";

   @Test
   void setQueryEntry_removesAutoSaveProperties_andKeepsTheOthers() {
      AssetEntry entry = autoSavedEntry();
      BatchAction action = new BatchAction();

      action.setQueryEntry(entry);

      assertAutoSavePropertiesRemoved(action.getQueryEntry());
      assertEquals("kept", action.getQueryEntry().getProperty("other"));
      assertEquals(entry, action.getQueryEntry(), "the same worksheet");
      assertEquals("true", entry.getProperty("openAutoSaved"), "the caller's entry is unchanged");
   }

   @Test
   void removeAutoSaveProperties_entryWithoutThem_isReturnedAsIs() {
      AssetEntry entry = AssetEntry.createAssetEntry("1^2^__NULL__^ws1^host-org");

      assertSame(entry, BatchAction.removeAutoSaveProperties(entry));
      assertNull(BatchAction.removeAutoSaveProperties(null));
   }

   // a stored task, or an imported one (parseXML(true), the restricted and site admin import)
   @Test
   void parseXML_removesAutoSaveProperties() throws Exception {
      String xml = xmlWithAutoSavedQuery();

      for(boolean importAsSiteAdmin : new boolean[] { false, true }) {
         BatchAction parsed = new BatchAction();
         parsed.parseXML(Tool.parseXML(new ByteArrayInputStream(
            xml.getBytes(StandardCharsets.UTF_8))).getDocumentElement(), importAsSiteAdmin);

         assertNotNull(parsed.getQueryEntry());
         assertAutoSavePropertiesRemoved(parsed.getQueryEntry());
         assertEquals("kept", parsed.getQueryEntry().getProperty("other"));
      }
   }

   // an entry changed in place after it was set still runs without them
   @Test
   void run_queryIsReadWithoutAutoSaveProperties() throws Throwable {
      BatchAction action = new BatchAction();
      action.setQueryEntry(AssetEntry.createAssetEntry("1^2^__NULL__^ws1^host-org"));
      action.getQueryEntry().setProperty("openAutoSaved", "true");
      action.getQueryEntry().setProperty("autoFileName", VICTIM_FILE);
      action.setQueryParameters(Map.of("key1", "col1"));
      Principal principal = new SRPrincipal(new IdentityID("alice", "host-org"),
                                            new IdentityID[0], new String[0], "host-org", 1L);
      AssetRepository repository = mock(AssetRepository.class);
      // no table, nothing else runs
      when(repository.getSheet(any(AssetEntry.class), any(), anyBoolean(), any(AssetContent.class)))
         .thenReturn(mock(Worksheet.class));

      try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class)) {
         assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(repository);

         invokeQuery(action, new ScheduleTask("batch"), principal);
      }

      ArgumentCaptor<AssetEntry> read = ArgumentCaptor.forClass(AssetEntry.class);
      verify(repository).getSheet(read.capture(), eq(principal), eq(true), eq(AssetContent.ALL));
      assertAutoSavePropertiesRemoved(read.getValue());
      assertEquals("1^2^__NULL__^ws1^host-org", read.getValue().toIdentifier());
   }

   private static AssetEntry autoSavedEntry() {
      AssetEntry entry = AssetEntry.createAssetEntry("1^2^__NULL__^ws1^host-org");
      entry.setProperty("openAutoSaved", "true");
      entry.setProperty("autoFileName", VICTIM_FILE);
      entry.setProperty("isRecycle", "true");
      entry.setProperty("other", "kept");
      return entry;
   }

   // written the way a task stored before this fix was, the properties included
   private static String xmlWithAutoSavedQuery() {
      AssetEntry entry = autoSavedEntry();
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      writer.print("<Action type=\"Batch\" class=\"" + BatchAction.class.getName() +
                   "\" taskId=\"alice~;~host-org:Child\">");
      writer.println("<queryEntry>");
      entry.writeXML(writer);
      writer.println("</queryEntry>");
      writer.println("<queryParameters></queryParameters><embeddedParameters></embeddedParameters>");
      writer.println("</Action>");
      writer.flush();
      assertTrue(buffer.toString().contains("autoFileName"), "test setup: " + buffer);
      return buffer.toString();
   }

   private static void assertAutoSavePropertiesRemoved(AssetEntry entry) {
      assertNull(entry.getProperty("openAutoSaved"));
      assertNull(entry.getProperty("autoFileName"));
      assertNull(entry.getProperty("isRecycle"));
   }

   private static void invokeQuery(BatchAction action, ScheduleTask task, Principal principal)
      throws Throwable
   {
      Method method = BatchAction.class.getDeclaredMethod(
         "runScheduleTaskWithQueryParameters", ScheduleTask.class, Principal.class,
         Principal.class);
      method.setAccessible(true);

      try {
         method.invoke(action, task, principal, principal);
      }
      catch(java.lang.reflect.InvocationTargetException e) {
         throw e.getCause();
      }
   }
}
