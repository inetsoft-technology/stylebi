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

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.security.IdentityID;
import inetsoft.storage.KeyValueEngine;
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
 * A RenameInfo in a queued rename task must survive the XML that Jackson-backed
 * key-value engines store, with every field the rename consumers read, its parent,
 * names containing XML special characters, and ChangeTableOptionInfo entries
 * (Bug #77846).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RenameInfoXmlTest {
   private static final String SPECIAL = "R&D \"x\" <y> 'z'";

   @Test
   void xmlRoundTripKeepsEveryConsumedField() throws Exception {
      RenameInfo info = createFullInfo("Old");

      RenameInfo result = new RenameInfo();
      result.parseXML(write(info));

      assertSameInfo(info, result);
   }

   @Test
   void specialCharactersRoundTrip() throws Exception {
      RenameInfo info = createFullInfo(SPECIAL);

      RenameInfo result = new RenameInfo();
      result.parseXML(write(info));

      assertSameInfo(info, result);
   }

   @Test
   void parentRenameInfoRoundTrip() throws Exception {
      RenameInfo parent = new RenameInfo("OldEnt", "NewEnt",
         RenameInfo.LOGIC_MODEL | RenameInfo.TABLE, "myDs/Model", null);
      RenameInfo info = new RenameInfo("OldEnt.col", "NewEnt.col",
         RenameInfo.LOGIC_MODEL | RenameInfo.COLUMN, "myDs/Model", "OldEnt");
      info.setParentRenameInfo(parent);

      RenameInfo result = new RenameInfo();
      result.parseXML(write(info));

      assertNotNull(result.getParentRenameInfo());
      assertSameInfo(parent, result.getParentRenameInfo());
      assertTrue(result.getParentRenameInfo().isLogicalModel());
   }

   @Test
   void persistedQueueKeepsFieldsParentAndTableOptionInfo() throws Exception {
      RenameDependencyInfo dinfo = createDependencyInfo();
      RenameTransformQueue queue = new RenameTransformQueue();
      queue.add(dinfo);

      ObjectMapper mapper = new ObjectMapper();
      RenameTransformQueue result =
         mapper.readValue(mapper.writeValueAsString(queue), RenameTransformQueue.class);

      assertEquals(1, result.size());
      assertSameDependencyInfo(dinfo, result.peek());
   }

   @Test
   void typedValueQueueRoundTrip() throws Exception {
      // the wire format of the Jackson-backed engines (e.g. DatabaseKeyValueEngine):
      // KeyValueEngine.createObjectMapper() over a value wrapper with @JsonTypeInfo(CLASS)
      RenameDependencyInfo dinfo = createDependencyInfo();
      RenameTransformQueue queue = new RenameTransformQueue();
      queue.add(dinfo);
      ValueObject value = new ValueObject();
      value.value = queue;

      ObjectMapper mapper = KeyValueEngine.createObjectMapper();
      ValueObject result = mapper.readValue(mapper.writeValueAsBytes(value), ValueObject.class);

      RenameTransformQueue rqueue = (RenameTransformQueue) result.value;
      assertEquals(1, rqueue.size());
      assertSameDependencyInfo(dinfo, rqueue.peek());
   }

   @Test
   void entryFromPreviousWriterStillParses() throws Exception {
      // written by the writer before Bug #77846: unescaped attributes, no prefix/entity/
      // path fields, parent nested inside <parentRenameInfo>
      String xml = "<renameInfo class=\"inetsoft.uql.asset.sync.RenameInfo\"" +
         " oname=\"OldEnt.col\" nname=\"NewEnt.col\" table=\"OldEnt\" source=\"Model\"" +
         " type=\"260\" organizationId=\"host-org\" alias=\"false\">\n" +
         "<parentRenameInfo>\n" +
         "<renameInfo class=\"inetsoft.uql.asset.sync.RenameInfo\"" +
         " oname=\"OldEnt\" nname=\"NewEnt\" source=\"Model\" type=\"258\"" +
         " organizationId=\"host-org\" alias=\"false\">\n" +
         "</renameInfo>\n" +
         "</parentRenameInfo>\n" +
         "</renameInfo>\n";

      RenameInfo result = new RenameInfo();
      result.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());

      assertEquals("OldEnt.col", result.getOldName());
      assertEquals("NewEnt.col", result.getNewName());
      assertEquals("OldEnt", result.getTable());
      assertEquals("Model", result.getSource());
      assertEquals(260, result.getType());
      assertEquals("host-org", result.getOrganizationId());
      assertNull(result.getPrefix());
      assertNull(result.getEntity());
      assertNull(result.getOldPath());
      assertFalse(result.isPrimaryTable());

      RenameInfo parent = result.getParentRenameInfo();
      assertNotNull(parent);
      assertEquals("OldEnt", parent.getOldName());
      assertEquals("NewEnt", parent.getNewName());
      assertEquals(258, parent.getType());
      assertTrue(parent.isLogicalModel());
   }

   @Test
   void dependencyInfoFromPreviousWriterStillParses() throws Exception {
      String xml = "<renameDependencyInfo class=\"" + RenameDependencyInfo.class.getName() +
         "\" recursive=\"true\" updateStorage=\"true\" runtime=\"false\" id=\"task-1\">" +
         "<renameInfos><renameInfo class=\"inetsoft.uql.asset.sync.RenameInfo\"" +
         " oname=\"a\" nname=\"b\" type=\"16\" alias=\"false\">\n</renameInfo>\n</renameInfos>" +
         "</renameDependencyInfo>";

      RenameDependencyInfo result = new RenameDependencyInfo();
      result.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());

      assertEquals(1, result.getRenameInfos().size());
      assertEquals(RenameInfo.class, result.getRenameInfos().get(0).getClass());
      assertEquals("a", result.getRenameInfos().get(0).getOldName());
      assertEquals("b", result.getRenameInfos().get(0).getNewName());
   }

   private static RenameInfo createFullInfo(String name) {
      RenameInfo info = new RenameInfo(name + "Col", "New" + name + "Col",
         RenameInfo.LOGIC_MODEL | RenameInfo.COLUMN, name + "Model", name + "Table", name + "Ent");
      info.setPrefix(name + "Ds");
      info.setOldEntity(name + "OldEnt");
      info.setOldPath(name + "/opath");
      info.setNewPath(name + "/npath");
      info.setModelFolder(name + "Folder");
      info.setPrimaryTable(true);
      info.setSourceIndex(3);
      info.setUpdateStorage(true);
      info.setRest(true);
      info.setAlias(true);
      info.setBookmarkVS(name + "Vs");
      info.setBookmarkUser(new IdentityID(name + "User", "host-org"));
      return info;
   }

   private static RenameDependencyInfo createDependencyInfo() {
      RenameInfo parent = new RenameInfo(SPECIAL + "Ent", "NewEnt",
         RenameInfo.LOGIC_MODEL | RenameInfo.TABLE, "myDs/Model", null);
      RenameInfo child = createFullInfo(SPECIAL);
      child.setParentRenameInfo(parent);
      ChangeTableOptionInfo option = new ChangeTableOptionInfo(SPECIAL + "Ds", 1, 2);

      RenameDependencyInfo dinfo = new RenameDependencyInfo();
      dinfo.setRenameInfos(List.of(child, option));
      dinfo.addRenameInfo(entry("vs1"), child);
      dinfo.addRenameInfo(entry("vs2"), option);
      return dinfo;
   }

   private static void assertSameDependencyInfo(RenameDependencyInfo expected,
                                                RenameDependencyInfo actual)
   {
      assertNotNull(actual);
      assertEquals(expected.getTaskId(), actual.getTaskId());
      assertSameInfos(expected.getRenameInfos(), actual.getRenameInfos());
      assertEquals(expected.getDependencyMap().keySet(), actual.getDependencyMap().keySet());

      for(AssetEntry key : List.of(entry("vs1"), entry("vs2"))) {
         assertSameInfos(expected.getRenameInfo(key), actual.getRenameInfo(key));
      }
   }

   private static void assertSameInfos(List<RenameInfo> expected, List<RenameInfo> actual) {
      assertNotNull(actual);
      assertEquals(expected.size(), actual.size(), "renameInfos size");

      for(int i = 0; i < expected.size(); i++) {
         assertSameInfo(expected.get(i), actual.get(i));
      }
   }

   private static void assertSameInfo(RenameInfo expected, RenameInfo actual) {
      assertEquals(expected.getClass(), actual.getClass(), "class");
      assertEquals(expected.getOldName(), actual.getOldName(), "oname");
      assertEquals(expected.getNewName(), actual.getNewName(), "nname");
      assertEquals(expected.getType(), actual.getType(), "type");
      assertEquals(expected.getSource(), actual.getSource(), "source");
      assertEquals(expected.getTable(), actual.getTable(), "table");
      assertEquals(expected.getEntity(), actual.getEntity(), "entity");
      assertEquals(expected.getOldEntity(), actual.getOldEntity(), "oentity");
      assertEquals(expected.getPrefix(), actual.getPrefix(), "prefix");
      assertEquals(expected.getOldPath(), actual.getOldPath(), "opath");
      assertEquals(expected.getNewPath(), actual.getNewPath(), "npath");
      assertEquals(expected.getModelFolder(), actual.getModelFolder(), "modelFolder");
      assertEquals(expected.getOrganizationId(), actual.getOrganizationId(), "organizationId");
      assertEquals(expected.getBookmarkVS(), actual.getBookmarkVS(), "bookmarkVS");
      assertEquals(expected.getBookmarkUser(), actual.getBookmarkUser(), "bookmarkUser");
      assertEquals(expected.isPrimaryTable(), actual.isPrimaryTable(), "primaryTable");
      assertEquals(expected.getSourceIndex(), actual.getSourceIndex(), "sourceIndex");
      assertEquals(expected.isUpdateStorage(), actual.isUpdateStorage(), "updateStorage");
      assertEquals(expected.isRest(), actual.isRest(), "rest");
      assertEquals(expected.isAlias(), actual.isAlias(), "alias");

      if(expected instanceof ChangeTableOptionInfo option) {
         ChangeTableOptionInfo roption = (ChangeTableOptionInfo) actual;
         assertEquals(option.getOldOption(), roption.getOldOption(), "ooption");
         assertEquals(option.getNewOption(), roption.getNewOption(), "noption");
      }

      if(expected.getParentRenameInfo() == null) {
         assertNull(actual.getParentRenameInfo(), "parent");
      }
      else {
         assertNotNull(actual.getParentRenameInfo(), "parent");
         assertSameInfo(expected.getParentRenameInfo(), actual.getParentRenameInfo());
      }
   }

   private static AssetEntry entry(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, path, null);
   }

   private static Element write(RenameInfo info) throws Exception {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      info.writeXML(writer);
      writer.flush();
      return Tool.parseXML(new StringReader(buf.toString())).getDocumentElement();
   }

   public static final class ValueObject {
      @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
      public Object value;
   }
}
