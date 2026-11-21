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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.security.ResourceType;
import inetsoft.uql.erm.*;
import inetsoft.web.portal.model.database.PhysicalTableModel;
import inetsoft.web.wiz.controller.WizDataModelWire.Failure;
import inetsoft.web.wiz.controller.WizPhysicalViewWire.*;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The one-request session of a physical view write, against the real registry, the real runtime
 * service and the real manager service (design 8, assertions DM-A10, DM-A11, counter-assertion
 * C1).
 */
class WizPhysicalViewSessionTest extends WizPhysicalViewTestBase {
   @Test
   void createFromDeclarativeDefinition() throws Exception {
      WriteResponse response = createView(definition(
         "sales", tables(plain("T1"), plain("T2"), alias("T1_A", "T1"), inline("V1", "select 1")),
         join("T2", "T1_ID", "T1", "ID")));

      assertTrue(response.ok());
      assertEquals("sales", response.name());
      XPartition stored = storedView("sales");
      assertTrue(stored.containsTable("T1"));
      assertTrue(stored.containsTable("T2"));
      assertTrue(stored.isAlias("T1_A"));
      assertTrue(stored.containsTable("V1"));
      assertEquals(1, stored.getRelationshipCount());
      XRelationship join = stored.getRelationship(0);
      assertEquals("T2", join.getDependentTable());
      assertEquals("T1_ID", join.getDependentColumn());
      assertEquals("T1", join.getIndependentTable());
      assertEquals(XRelationship.MANY, join.getDependentCardinality());
      assertEquals(XRelationship.ONE, join.getIndependentCardinality());

      // the read-back is the stored view in wire form
      Map<String, Object> back = response.definition();
      assertEquals("sales", back.get("name"));
      assertEquals(4, ((List<?>) back.get("tables")).size());
      assertEquals(1, ((List<?>) back.get("joins")).size());
      assertTrue(runtimes.isEmpty());
   }

   @Test
   void createReadsBackWhatGetReturns() throws Exception {
      WriteResponse created = createView(definition(
         "sales", tables(plain("T1"), plain("T2")), join("T2", "T1_ID", "T1", "ID")));
      GetResponse got = getView("sales");

      assertEquals(created.definition(), got.definition());
      assertFalse(got.readOnly());
   }

   // -- C1: no runtime left, no runtime id out -------------------------------------------------

   @Test
   void runtimeAlwaysDestroyedOnFailure() throws Exception {
      // success
      createView(definition("ok", tables(plain("T1"))));
      assertTrue(runtimes.isEmpty(), "success: " + runtimes.keySet());
      assertTrue(runtimePuts.get() > 0, "the session did open a runtime");

      // a validation failure found while applying (the table is not in the database)
      Object failure = createViewRaw(definition("bad", tables(plain("NOPE_TABLE"))), null);
      assertEquals(WizDataModelWire.INVALID, ((Failure) failure).reason());
      assertTrue(runtimes.isEmpty(), "validation failure: " + runtimes.keySet());
      assertNull(storedView("bad"));

      // unjoined tables without the cross join permission
      when(security.checkPermission(any(), eq(ResourceType.CROSS_JOIN), anyString(), any()))
         .thenReturn(false);
      failure = createViewRaw(definition("cross", tables(plain("T1"), plain("T2"))), null);
      assertEquals(WizDataModelWire.UNJOINED_TABLES_PROHIBITED, ((Failure) failure).reason());
      assertTrue(runtimes.isEmpty(), "unjoined: " + runtimes.keySet());
      assertNull(storedView("cross"));
      when(security.checkPermission(any(), eq(ResourceType.CROSS_JOIN), anyString(), any()))
         .thenReturn(true);

      // the view changed under the request
      createView(definition("stale", tables(plain("T1"))));
      doAnswer(i -> {
         Object result = i.callRealMethod();
         XDataModel model = fresh();
         XPartition changed = model.getPartition("stale");
         changed.setLastModified(changed.getLastModified() + 5000);
         model.addPartition(changed, true);
         return result;
      }).when(manager).addTable(anyString(), anyString(), any(), any(), any(), any(), any(),
                                any(), anyBoolean(), anyBoolean());
      failure = updateView("stale", definition("stale", tables(plain("T1"), plain("T2"))), null);
      assertEquals(WizDataModelWire.STALE_VIEW, ((Failure) failure).reason());
      assertTrue(runtimes.isEmpty(), "stale: " + runtimes.keySet());
      assertFalse(storedView("stale").containsTable("T2"), "nothing was saved");
      reset(manager);

      // an exception thrown by the third operation
      AtomicInteger calls = new AtomicInteger();
      doAnswer(i -> {
         if(calls.incrementAndGet() == 3) {
            throw new IllegalStateException("boom password=hunter2");
         }

         return i.callRealMethod();
      }).when(manager).addTable(anyString(), anyString(), any(), any(), any(), any(), any(),
                                any(), anyBoolean(), anyBoolean());
      failure = createViewRaw(definition("boom", tables(plain("T1"), plain("T2"), plain("T3"))),
                              null);
      assertEquals(WizDataModelWire.UNKNOWN, ((Failure) failure).reason());
      assertFalse(((Failure) failure).message().contains("hunter2"),
                  "no raw exception text: " + ((Failure) failure).message());
      assertEquals(3, calls.get(), "the third operation was the one that failed");
      assertTrue(runtimes.isEmpty(), "third operation: " + runtimes.keySet());
      assertNull(storedView("boom"));
      reset(manager);

      // a dry run
      Object dry = createViewRaw(definition("dry", tables(plain("T1"))), true);
      assertTrue(((WriteResponse) dry).ok());
      assertNull(((WriteResponse) dry).definition());
      assertTrue(runtimes.isEmpty(), "dry run: " + runtimes.keySet());
      assertNull(storedView("dry"));

      // a dry run of an update
      dry = updateView("ok", definition("ok", tables(plain("T1"), plain("T2"))), true);
      assertTrue(((WriteResponse) dry).ok());
      assertTrue(runtimes.isEmpty(), "update dry run: " + runtimes.keySet());
      assertFalse(storedView("ok").containsTable("T2"));
   }

   @Test
   void staleStoredVersionOverwriteIsDetected() throws Exception {
      createView(definition("v", tables(plain("T1"))));
      // another writer (the Portal) saves the view while the bridge request is between its read
      // and its save
      doAnswer(i -> {
         Object result = i.callRealMethod();
         XDataModel model = fresh();
         XPartition portal = model.getPartition("v");
         portal.setDescription("written by the Portal");
         portal.setLastModified(portal.getLastModified() + 5000);
         model.addPartition(portal, true);
         return result;
      }).when(manager).addTable(anyString(), anyString(), any(), any(), any(), any(), any(),
                                any(), anyBoolean(), anyBoolean());

      Object result = updateView("v", definition("v", tables(plain("T1"), plain("T2"))), null);

      assertEquals(WizDataModelWire.STALE_VIEW, ((Failure) result).reason());
      assertTrue(((Failure) result).message().contains("nothing was saved"));
      verify(manager, never()).updateAndSaveModel(any(), any(), any(), any(), any(), any());
      XPartition stored = storedView("v");
      assertEquals("written by the Portal", stored.getDescription(),
                   "the other writer's change was not overwritten");
      assertFalse(stored.containsTable("T2"));
      assertTrue(runtimes.isEmpty());
   }

   @Test
   void aRuntimeThatExpiresMidRequestIsReportedAndDestroyed() throws Exception {
      doReturn(null).when(manager).addTable(anyString(), anyString(), any(), any(), any(), any(),
                                            any(), any(), anyBoolean(), anyBoolean());
      Object failure = createViewRaw(definition("exp", tables(plain("T1"))), null);

      assertEquals(WizDataModelWire.UNKNOWN, ((Failure) failure).reason());
      assertTrue(((Failure) failure).message().contains("edit session expired"));
      assertTrue(runtimes.isEmpty());
   }

   @Test
   void noRuntimeIdInResponse() throws Exception {
      List<String> ids = new ArrayList<>();
      doAnswer(i -> {
         ids.add(i.getArgument(0));
         return i.callRealMethod();
      }).when(manager).closeModel(anyString());
      ObjectMapper mapper = new ObjectMapper();
      List<Object> responses = new ArrayList<>();
      responses.add(createViewRaw(definition(
         "v", tables(plain("T1"), plain("T2")), join("T2", "T1_ID", "T1", "ID")), null));
      responses.add(createViewRaw(definition("vdry", tables(plain("T1"))), true));
      responses.add(getView("v"));
      responses.add(updateView("v", definition("v", tables(plain("T1"), plain("T2"),
                                                           plain("T3"))), true));
      responses.add(updateView("v", definition("v", tables(plain("T1"), plain("T2"),
                                                           plain("T3"))), null));
      responses.add(createViewRaw(definition("v", tables(plain("T1"))), null));
      responses.add(createViewRaw(definition("w", tables(plain("NOPE_TABLE"))), null));

      // an error whose text would carry the runtime id
      doAnswer(i -> {
         String id = runtimes.keySet().iterator().next();
         throw new FileNotFoundException("The physical view runtime \"" + id +
                                            "\" does not exist");
      }).when(manager).createInlineView(anyString(), any(PhysicalTableModel.class));
      responses.add(createViewRaw(definition("x", tables(inline("IV", "select 1"))), null));

      assertFalse(ids.isEmpty());
      Pattern uuid = Pattern.compile("[0-9a-f]{32}");

      for(Object response : responses) {
         String json = mapper.writeValueAsString(response);

         for(String id : ids) {
            assertFalse(json.contains(id), "the runtime id is in " + json);
         }

         assertFalse(uuid.matcher(json).find(), "an id-like token is in " + json);
         assertFalse(json.contains("\"id\""), "an id key is in " + json);
         assertFalse(json.contains("runtime"), "a runtime is named in " + json);
      }
   }

   @Test
   void aGetResultFedBackToUpdateOpensNoRuntimeAndWritesNothing() throws Exception {
      createView(definition("sales", tables(plain("T1"), plain("T2"), alias("T1_A", "T1")),
                            join("T2", "T1_ID", "T1", "ID")));
      int puts = runtimePuts.get();
      long modified = storedView("sales").getLastModified();
      Object result = updateView("sales", getView("sales").definition(), null);

      assertTrue(((WriteResponse) result).ok(), String.valueOf(result));
      assertEquals(puts, runtimePuts.get(), "no runtime was opened");
      verify(manager, never()).updateAndSaveModel(any(), any(), any(), any(), any(), any());
      assertEquals(modified, storedView("sales").getLastModified());
   }

   // -- layout (unknown (a), path B) -----------------------------------------------------------

   @Test
   void saveUsesAddTablePathForLayout() throws Exception {
      createView(definition("laid", tables(plain("T1"), plain("T2"), plain("T3"))));

      // every plain table went through addTable with fixBounds = true
      verify(manager, times(3)).addTable(anyString(), anyString(), any(), any(), any(), any(),
                                         any(), any(), anyBoolean(), eq(true));
      verify(manager, never()).createModel(any(), any(), any(), any());
      XPartition stored = storedView("laid");
      Set<String> spots = new HashSet<>();

      for(String table : List.of("T1", "T2", "T3")) {
         Rectangle bounds = stored.getBounds(table);
         assertNotNull(bounds, table);
         assertTrue(bounds.width > 0 && bounds.x >= 0 && bounds.y >= 0,
                    table + " has a laid-out rectangle: " + bounds);
         spots.add(bounds.x + "," + bounds.y);
      }

      assertEquals(3, spots.size(), "no two tables share a spot: " + spots);
   }

   // -- the alias rule (design 8.4) ------------------------------------------------------------

   @Test
   void aliasRenameCarriesOldAlias() throws Exception {
      createView(definition("v", tables(plain("T1"), alias("A1", "T1"))));
      XLogicalModel lm = logicalModelOn("lm", "v");
      XEntity entity = new XEntity("E");
      entity.addAttribute(new XAttribute("a", "A1", "ID"));
      entity.addAttribute(new XAttribute("b", "T1", "NAME"));
      lm.addEntity(entity);
      dataModel.addLogicalModel(lm);

      Object result = updateView("v", definition(
         "v", tables(plain("T1"), withOldName(alias("A2", "T1"), "A1"))), null);

      assertTrue(((WriteResponse) result).ok(), String.valueOf(result));
      XLogicalModel after = fresh().getLogicalModel("lm");
      assertEquals("A2", after.getEntity("E").getAttribute("a").getTable(),
                   "the attribute follows the alias rename");
      assertEquals("T1", after.getEntity("E").getAttribute("b").getTable());
      assertTrue(storedView("v").isAlias("A2"));
      assertFalse(storedView("v").containsTable("A1"));
      assertTrue(runtimes.isEmpty());
   }

   @Test
   void newAliasDoesNotRewriteExpressionAttributes() throws Exception {
      createView(definition("v", tables(plain("T1"))));
      XLogicalModel lm = logicalModelOn("lm", "v");
      XEntity entity = new XEntity("E");
      entity.addAttribute(new XAttribute("a", "T1", "ID"));
      entity.addAttribute(new ExpressionAttribute("x", "1 + 1"));
      lm.addEntity(entity);
      dataModel.addLogicalModel(lm);
      assertNull(fresh().getLogicalModel("lm").getEntity("E").getAttribute("x").getTable());

      Object result = updateView("v", definition(
         "v", tables(plain("T1"), alias("NEWALIAS", "T1"))), null);

      assertTrue(((WriteResponse) result).ok(), String.valueOf(result));
      XLogicalModel after = fresh().getLogicalModel("lm");
      assertNull(after.getEntity("E").getAttribute("x").getTable(),
                 "an expression attribute keeps no table");
      assertEquals("T1", after.getEntity("E").getAttribute("a").getTable());
      assertTrue(storedView("v").isAlias("NEWALIAS"));
   }

   // -- update ---------------------------------------------------------------------------------

   @Test
   void updateAddsAndRemovesTablesAndJoins() throws Exception {
      createView(definition("v", tables(plain("T1"), plain("T2")),
                            join("T2", "T1_ID", "T1", "ID")));
      Map<String, Object> next = definition(
         "v", tables(plain("T2"), plain("T3")), join("T3", "T2_CODE", "T2", "CODE"));
      Object result = updateView("v", next, null);

      assertTrue(((WriteResponse) result).ok(), String.valueOf(result));
      XPartition stored = storedView("v");
      assertFalse(stored.containsTable("T1"));
      assertTrue(stored.containsTable("T3"));
      assertEquals(1, stored.getRelationshipCount());
      assertEquals("T3", stored.getRelationship(0).getDependentTable());
      assertTrue(runtimes.isEmpty());
   }

   @Test
   void updateChangesAJoinInPlaceAndMetadata() throws Exception {
      createView(definition("v", tables(plain("T1"), plain("T2")),
                            join("T2", "T1_ID", "T1", "ID")));
      Map<String, Object> changed = join("T2", "T1_ID", "T1", "ID");
      changed.put("type", "LEFT_OUTER");
      changed.put("cardinality", "ONE_TO_ONE");
      Map<String, Object> next = definition("v", tables(plain("T1"), plain("T2")), changed);
      next.put("description", "the sales view");
      next.put("folder", "F");
      Object result = updateView("v", next, null);

      assertTrue(((WriteResponse) result).ok(), String.valueOf(result));
      XPartition stored = storedView("v");
      assertEquals("the sales view", stored.getDescription());
      assertEquals("F", stored.getFolder());
      XRelationship join = stored.getRelationship(0);
      assertEquals(XRelationship.LEFT_OUTER, join.getJoinType());
      assertEquals(XRelationship.ONE, join.getDependentCardinality());
      assertEquals(XRelationship.ONE, join.getIndependentCardinality());
      assertEquals("F", ((WriteResponse) result).folder());
   }

   @Test
   void updateChangesAnInlineViewSql() throws Exception {
      createView(definition("v", tables(plain("T1"), inline("IV", "select 1 from T1"))));
      Object result = updateView("v", definition(
         "v", tables(plain("T1"), inline("IV", "select 2 from T1"))), null);

      assertTrue(((WriteResponse) result).ok(), String.valueOf(result));
      assertEquals("select 2 from T1", storedView("v").getPartitionTable("IV").getSql());
   }

   @Test
   void updateOfAMissingViewIsNotFound() throws Exception {
      Object result = updateView("nope", definition("nope", tables(plain("T1"))), null);

      assertEquals(WizDataModelWire.NOT_FOUND, ((Failure) result).reason());
   }

   // -- refusals -------------------------------------------------------------------------------

   @Test
   void everyProblemOfADefinitionIsNamedAndNothingIsCreated() throws Exception {
      Map<String, Object> bad = definition(
         "bad", tables(plain("T1"), alias("A", "MISSING"), plain("T1")),
         join("T1", "ID", "T9", "ID"));
      bad.put("colour", "red");
      Object result = createViewRaw(bad, null);

      assertEquals(WizDataModelWire.INVALID, ((Failure) result).reason());
      List<String> fields = ((Failure) result).errors().stream()
         .map(WizDataModelWire.Err::field).toList();
      assertTrue(fields.contains("definition.colour"), fields.toString());
      assertEquals(0, runtimePuts.get());
      assertNull(storedView("bad"));

      bad.remove("colour");
      result = createViewRaw(bad, null);
      fields = ((Failure) result).errors().stream().map(WizDataModelWire.Err::field).toList();
      assertTrue(fields.contains("tables[A].aliasOf"), fields.toString());
      assertTrue(fields.contains("tables[T1].name"), fields.toString());
      assertTrue(fields.stream().anyMatch(f -> f.contains("T9")), fields.toString());
      assertEquals(0, runtimePuts.get());
   }

   @Test
   void aJoinColumnThatTheTableLacksIsRefused() throws Exception {
      Object result = createViewRaw(definition(
         "v", tables(plain("T1"), plain("T2")), join("T2", "NO_SUCH", "T1", "ID")), null);

      assertEquals(WizDataModelWire.INVALID, ((Failure) result).reason());
      assertEquals("COLUMN_NOT_IN_TABLE", ((Failure) result).errors().get(0).code());
      assertTrue(((Failure) result).errors().get(0).field().endsWith("from.column"));
      assertTrue(runtimes.isEmpty());
      assertNull(storedView("v"));
   }

   @Test
   void aTableTheDatabaseLacksIsRefusedByName() throws Exception {
      Object result = createViewRaw(definition("v", tables(plain("NOPE_TABLE"))), null);

      assertEquals("TABLE_NOT_FOUND", ((Failure) result).errors().get(0).code());
      assertEquals("tables[NOPE_TABLE].name", ((Failure) result).errors().get(0).field());
   }

   @Test
   void unjoinedTablesWarnWhenTheCallerMayCrossJoin() throws Exception {
      WriteResponse response = createView(definition("v", tables(plain("T1"), plain("T2"))));

      assertEquals(1, response.warnings().size());
      assertEquals("UNJOINED_TABLES", response.warnings().get(0).code());
      assertNotNull(storedView("v"));
   }

   @Test
   void aNameTakenByAnotherKindIsADuplicate() throws Exception {
      Object result = createViewRaw(definition("pv2", tables(plain("T1"))), null);

      assertEquals(WizDataModelWire.DUPLICATE_NAME, ((Failure) result).reason());
      assertEquals(WizDataModelWire.KIND_PHYSICAL_VIEW, ((Failure) result).existingKind());
      assertEquals(0, runtimePuts.get());
   }

   @Test
   void aMissingFolderIsRefused() throws Exception {
      Map<String, Object> definition = definition("v", tables(plain("T1")));
      definition.put("folder", "NoSuchFolder");
      Object result = createViewRaw(definition, null);

      assertEquals(WizDataModelWire.FOLDER_NOT_FOUND, ((Failure) result).reason());
      assertEquals(0, runtimePuts.get());
   }

   @Test
   void aDefinitionNameThatDiffersFromTheTargetIsRefused() throws Exception {
      createView(definition("v", tables(plain("T1"))));
      Object result = updateView("v", definition("other", tables(plain("T1"))), null);

      assertEquals(WizDataModelWire.INVALID, ((Failure) result).reason());
      assertEquals("definition.name", ((Failure) result).field());
   }

   // -- extended views (design 8.6) ------------------------------------------------------------

   @Test
   void extendedViewBaseElementsReadOnly() throws Exception {
      createView(definition("base", tables(plain("T1"), plain("T2")),
                            join("T2", "T1_ID", "T1", "ID")));
      Map<String, Object> extended = definition(
         "ext", tables(plain("T3")), join("T3", "T2_CODE", "T2", "CODE"));
      extended.put("parent", "base");
      ok(createViewRaw(extended, null));

      assertTrue(runtimes.isEmpty());
      GetResponse got = (GetResponse) physicalController.get(
         new GetRequest(ds, "ext", "base"), bob);
      Map<String, Boolean> flags = new TreeMap<>();

      for(Object table : (List<?>) got.definition().get("tables")) {
         Map<?, ?> t = (Map<?, ?>) table;
         flags.put((String) t.get("name"), Boolean.TRUE.equals(t.get("baseElement")));
      }

      assertEquals(Map.of("T1", true, "T2", true, "T3", false), flags,
                   "inherited tables are flagged: " + got.definition());
      long baseJoins = ((List<?>) got.definition().get("joins")).stream()
         .filter(j -> Boolean.TRUE.equals(((Map<?, ?>) j).get("baseElement"))).count();
      assertEquals(1, baseJoins, "the inherited join is flagged");
      assertEquals("base", got.definition().get("parent"));
   }

   @Test
   @SuppressWarnings("unchecked")
   void baseElementWriteRefused() throws Exception {
      createView(definition("base", tables(plain("T1"), plain("T2")),
                            join("T2", "T1_ID", "T1", "ID")));
      Map<String, Object> extended = definition(
         "ext", tables(plain("T3")), join("T3", "T2_CODE", "T2", "CODE"));
      extended.put("parent", "base");
      ok(createViewRaw(extended, null));
      Map<String, Object> current = ((GetResponse) physicalController.get(
         new GetRequest(ds, "ext", "base"), bob)).definition();
      int puts = runtimePuts.get();

      // removing an inherited table
      Map<String, Object> noT1 = copyOf(current);
      ((List<Object>) noT1.get("tables")).removeIf(
         t -> "T1".equals(((Map<?, ?>) t).get("name")));
      Object result = physicalController.update(
         new WriteRequest(ds, "ext", "base", noT1, null), bob);
      assertEquals(WizDataModelWire.BASE_ELEMENT_READ_ONLY, ((Failure) result).reason());
      assertEquals("tables[T1]", ((Failure) result).field());

      // changing an inherited join
      Map<String, Object> changed = copyOf(current);

      for(Object join : (List<Object>) changed.get("joins")) {
         Map<String, Object> j = (Map<String, Object>) join;

         if(Boolean.TRUE.equals(j.get("baseElement"))) {
            j.put("type", "LEFT_OUTER");
         }
      }

      result = physicalController.update(new WriteRequest(ds, "ext", "base", changed, null), bob);
      assertEquals(WizDataModelWire.BASE_ELEMENT_READ_ONLY, ((Failure) result).reason());

      // nothing was opened for either refusal
      assertEquals(puts, runtimePuts.get());
      assertTrue(runtimes.isEmpty());

      // an own element of the extended view changes freely
      Map<String, Object> noT3 = copyOf(current);
      ((List<Object>) noT3.get("tables")).removeIf(
         t -> "T3".equals(((Map<?, ?>) t).get("name")));
      ((List<Object>) noT3.get("joins")).removeIf(j -> "T3".equals(
         ((Map<?, ?>) ((Map<?, ?>) j).get("from")).get("table")));
      result = physicalController.update(new WriteRequest(ds, "ext", "base", noT3, null), bob);
      assertTrue(((WriteResponse) result).ok(), String.valueOf(result));
      assertTrue(runtimes.isEmpty());
   }

   // -- helpers --------------------------------------------------------------------------------

   private XLogicalModel logicalModelOn(String name, String view) {
      XLogicalModel model = new XLogicalModel(name);
      model.setPartition(view);
      return model;
   }

   private static Map<String, Object> withOldName(Map<String, Object> table, String oldName) {
      table.put("oldName", oldName);
      return table;
   }

   @SuppressWarnings("unchecked")
   private static Map<String, Object> copyOf(Map<String, Object> map) {
      Map<String, Object> copy = new LinkedHashMap<>();

      for(Map.Entry<String, Object> entry : map.entrySet()) {
         Object value = entry.getValue();

         if(value instanceof Map<?, ?>) {
            value = copyOf((Map<String, Object>) value);
         }
         else if(value instanceof List<?>) {
            List<Object> list = new ArrayList<>();

            for(Object item : (List<?>) value) {
               list.add(item instanceof Map<?, ?> ? copyOf((Map<String, Object>) item) : item);
            }

            value = list;
         }

         copy.put(entry.getKey(), value);
      }

      return copy;
   }
}
