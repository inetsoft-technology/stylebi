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
package inetsoft.uql.asset.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A queued rename task must read back every field that the restart replay
 * (LoadDependencyStorageTask -> RenameTransformTask.Rename) uses (Bug #77823).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RenameDependencyInfoXmlTest {
   @Test
   void parseXmlReadsBackEveryWrittenField() throws Exception {
      RenameDependencyInfo info = createInfo();

      RenameDependencyInfo result = new RenameDependencyInfo();
      result.parseXML(write(info));

      assertSameTask(info, result);
   }

   @Test
   void nonDefaultFlagsSurviveRoundTrip() throws Exception {
      RenameDependencyInfo info = createInfo();
      // flags set by WorksheetEngine.renameDep(rid) before it queues the task
      info.setRecursive(false);
      info.setUpdateStorage(false);
      info.setRuntime(true);

      RenameDependencyInfo result = new RenameDependencyInfo();
      result.parseXML(write(info));

      assertSameTask(info, result);
   }

   @Test
   void persistedQueueRoundTripKeepsTask() throws Exception {
      RenameDependencyInfo info = createInfo();
      RenameTransformQueue queue = new RenameTransformQueue();
      queue.add(info);

      ObjectMapper mapper = new ObjectMapper();
      RenameTransformQueue result =
         mapper.readValue(mapper.writeValueAsString(queue), RenameTransformQueue.class);

      assertEquals(1, result.size());
      assertSameTask(info, result.peek());
   }

   @Test
   void entryWithoutFlagAttributesUsesDefaults() throws Exception {
      // writers before Bug #77823 did not write updateStorage or runtime
      String xml = "<renameDependencyInfo class=\"" + RenameDependencyInfo.class.getName() +
         "\" recursive=\"true\" id=\"task-1\"></renameDependencyInfo>";
      Element elem = Tool.parseXML(new StringReader(xml)).getDocumentElement();

      RenameDependencyInfo result = new RenameDependencyInfo();
      result.parseXML(elem);

      assertEquals("task-1", result.getTaskId());
      assertTrue(result.isRecursive());
      assertTrue(result.isUpdateStorage());
      assertFalse(result.isRuntime());
      assertTrue(result.getRenameInfos().isEmpty());
      assertTrue(result.getDependencyMap().isEmpty());
   }

   private static RenameDependencyInfo createInfo() {
      RenameDependencyInfo info = new RenameDependencyInfo();
      info.setRecursive(true);
      info.setRenameInfos(List.of(
         new RenameInfo("oldWs", "newWs", RenameInfo.ASSET),
         new RenameInfo("oldCol", "newCol", RenameInfo.ASSET | RenameInfo.COLUMN)));
      info.addRenameInfo(entry("vs1"), new RenameInfo("oldWs", "newWs", RenameInfo.ASSET));
      info.addRenameInfo(entry("vs2"),
                         new RenameInfo("oldCol", "newCol", RenameInfo.ASSET | RenameInfo.COLUMN));
      return info;
   }

   private static void assertSameTask(RenameDependencyInfo expected, RenameDependencyInfo actual) {
      assertNotNull(actual);
      assertEquals(expected.getTaskId(), actual.getTaskId());
      assertEquals(expected.isRecursive(), actual.isRecursive(), "recursive");
      assertEquals(expected.isUpdateStorage(), actual.isUpdateStorage(), "updateStorage");
      assertEquals(expected.isRuntime(), actual.isRuntime(), "runtime");
      assertRenameInfos(expected.getRenameInfos(), actual.getRenameInfos());
      assertEquals(expected.getDependencyMap().keySet(), actual.getDependencyMap().keySet());

      for(AssetEntry key : List.of(entry("vs1"), entry("vs2"))) {
         assertRenameInfos(expected.getRenameInfo(key), actual.getRenameInfo(key));
      }
   }

   private static void assertRenameInfos(List<RenameInfo> expected, List<RenameInfo> actual) {
      assertEquals(expected.size(), actual.size(), "renameInfos size");

      for(int i = 0; i < expected.size(); i++) {
         assertEquals(expected.get(i).getOldName(), actual.get(i).getOldName());
         assertEquals(expected.get(i).getNewName(), actual.get(i).getNewName());
         assertEquals(expected.get(i).getType(), actual.get(i).getType());
      }
   }

   private static AssetEntry entry(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, path, null);
   }

   private static Element write(RenameDependencyInfo info) throws Exception {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      info.writeXML(writer);
      writer.flush();
      return Tool.parseXML(new StringReader(buf.toString())).getDocumentElement();
   }
}
