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
package inetsoft.web.wiz.controller;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the wire field names of the kind-agnostic records. The same table is kept in the plugin's
 * {@code test/dataModel/bridgeWire.test.ts}: whichever side renames a field fails the other
 * repository's test. A change to this table is a contract change and goes through the lead.
 */
@Tag("core")
class WizDataModelBridgeWireTest {
   private static final Map<Class<?>, String> WIRE = new LinkedHashMap<>();

   static {
      WIRE.put(WizDataModelWire.Ref.class, "kind,name,parent");
      WIRE.put(WizDataModelWire.Err.class, "field,message,code");
      WIRE.put(WizDataModelWire.Dependent.class, "type,name,path,element");
      WIRE.put(WizDataModelWire.Failure.class, "ok,reason,message,field,errors,existingKind");
      WIRE.put(WizDataModelWire.BrowseRequest.class, "database,folder,query,kinds,limit");
      WIRE.put(WizDataModelWire.BrowseFolder.class, "name,editable,deletable,createdBy,createdDate");
      WIRE.put(WizDataModelWire.BrowseItem.class,
               "kind,name,folder,description,extended,parent,editable,deletable,createdBy,createdDate");
      WIRE.put(WizDataModelWire.BrowseResponse.class, "database,folder,query,folders,items,total");
      WIRE.put(WizDataModelWire.PhysicalTablesRequest.class, "database,view,connection");
      WIRE.put(WizDataModelWire.PhysicalColumn.class, "name,dataType");
      WIRE.put(WizDataModelWire.PhysicalTable.class, "name,columns");
      WIRE.put(WizDataModelWire.PhysicalTablesResponse.class, "database,view,tables,unresolved,note");
      WIRE.put(WizDataModelWire.DependenciesRequest.class, "database,items");
      WIRE.put(WizDataModelWire.AlsoDeleted.class, "kind,name,reason");
      WIRE.put(WizDataModelWire.Dangling.class, "kind,name,via");
      WIRE.put(WizDataModelWire.DepItem.class,
               "kind,name,folder,exists,deletable,reason,message,dependents,text,contains," +
               "alsoDeleted,danglingAfter");
      WIRE.put(WizDataModelWire.DependenciesResponse.class, "items,coverage");
      WIRE.put(WizDataModelWire.DeleteItem.class, "kind,name,parent,force,expect");
      WIRE.put(WizDataModelWire.DeleteRequest.class, "database,items");
      WIRE.put(WizDataModelWire.DeleteResult.class,
               "kind,name,ok,reason,message,dependents,deleted");
      WIRE.put(WizDataModelWire.DeleteResponse.class, "results");
      WIRE.put(WizDataModelWire.RenameRequest.class, "database,kind,name,newName,description");
      WIRE.put(WizDataModelWire.RenameResponse.class, "ok,name,folder");
      WIRE.put(WizDataModelWire.MoveRequest.class, "database,folder,items");
      WIRE.put(WizDataModelWire.MoveResult.class, "kind,name,ok,reason,message,from,to");
      WIRE.put(WizDataModelWire.MoveResponse.class, "results");
      WIRE.put(WizDataModelWire.FolderCreateRequest.class, "database,name");
      WIRE.put(WizDataModelWire.FolderCreateResponse.class, "ok,name");
      WIRE.put(WizDataModelWire.FolderRenameRequest.class, "database,name,newName");
      WIRE.put(WizDataModelWire.FolderRenameResponse.class, "ok,name,moved");
   }

   @Test
   void everyRecordHasExactlyTheDocumentedFieldNames() {
      for(Map.Entry<Class<?>, String> entry : WIRE.entrySet()) {
         List<String> actual = new ArrayList<>();

         for(RecordComponent component : entry.getKey().getRecordComponents()) {
            actual.add(component.getName());
         }

         assertEquals(entry.getValue(), String.join(",", actual), entry.getKey().getSimpleName());
      }
   }

   @Test
   void everyWireRecordOfTheClassIsInTheTable() {
      for(Class<?> nested : WizDataModelWire.class.getDeclaredClasses()) {
         assertTrue(WIRE.containsKey(nested), nested.getSimpleName() + " is not pinned");
      }
   }

   @Test
   void reasonVocabularyIsTheDocumentedOne() {
      Set<String> reasons = new TreeSet<>();

      for(java.lang.reflect.Field field : WizDataModelWire.class.getDeclaredFields()) {
         if(field.getType() == String.class && !field.getName().startsWith("KIND_")) {
            reasons.add(field.getName());
         }
      }

      assertEquals(new TreeSet<>(List.of(
         "NOT_FOUND", "WRONG_KIND", "NOT_JDBC", "DUPLICATE_NAME", "INVALID", "FOLDER_NOT_FOUND",
         "HAS_DEPENDENCIES", "VIEW_IN_USE", "HAS_EXTENDED_CHILDREN", "CONTAINED_NOT_DELETABLE",
         "EXPECT_MISMATCH", "PERMISSION_DENIED", "UNJOINED_TABLES_PROHIBITED", "STALE_VIEW", "STALE_MODEL",
         "EXTENDED_UNSUPPORTED", "BASE_ELEMENT_READ_ONLY", "READ_BACK_FAILED",
         "METADATA_UNAVAILABLE", "STORAGE_UNAVAILABLE", "UNKNOWN")), reasons);
   }
}
