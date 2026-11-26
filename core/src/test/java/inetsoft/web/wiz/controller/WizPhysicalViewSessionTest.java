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
   void aServerThatSavesNothingIsReportedNeverSuccess() throws Exception {
      createView(definition("v", tables(plain("T1"))));
      doNothing().when(manager).updateAndSaveModel(any(), any(), any(), any(), any(), any());
      Object updated = updateView("v", definition("v", tables(plain("T1"), plain("T2"))), null);

      assertEquals(WizDataModelWire.READ_BACK_FAILED, ((Failure) updated).reason());
      assertTrue(((Failure) updated).message().contains("tables[T2]"),
                 ((Failure) updated).message());

      doNothing().when(manager).createAndSaveModel(any(), any(), any(), any(), any());
      Object created = createViewRaw(definition("w", tables(plain("T1"))), null);

      assertEquals(WizDataModelWire.READ_BACK_FAILED, ((Failure) created).reason());
      assertTrue(((Failure) created).message().contains("is not there"));
      assertTrue(runtimes.isEmpty());
   }

   // -- D-9: an edit planned from a stale read is refused ----------------------------------------

   private Object updateFrom(String name, Map<String, Object> definition, Boolean dryRun,
                             String baseRevision)
      throws Exception
   {
      return physicalController.update(
         new WriteRequest(ds, name, null, definition, dryRun, baseRevision), bob);
   }

   @Test
   void getReturnsTheRevisionOfTheDefinitionItReturns() throws Exception {
      createView(definition("v", tables(plain("T1"))));
      GetResponse got = getView("v");

      // the revision is over the definition without its layout (a moved table keeps it)
      Map<String, Object> noLayout = new LinkedHashMap<>(got.definition());
      List<Map<String, Object>> shown = new ArrayList<>();

      for(Object table : (List<?>) got.definition().get("tables")) {
         @SuppressWarnings("unchecked")
         Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) table);
         copy.remove("bounds");
         shown.add(copy);
      }

      noLayout.put("tables", shown);
      assertEquals(WizDataModelRevision.of(noLayout), got.revision());
      assertTrue(got.revision().matches("[0-9a-f]{64}"));
   }

   @Test
   void staleBaseRevisionIsRefusedAndNothingIsWritten() throws Exception {
      createView(definition("v", tables(plain("T1"))));
      String read = getView("v").revision();

      // two callers plan from the same read; the first one applies
      Object first = updateFrom("v", definition("v", tables(plain("T1"), plain("T2"))), null,
                                read);
      assertTrue(first instanceof WriteResponse, String.valueOf(first));

      Object second = updateFrom("v", definition("v", tables(plain("T1"), plain("T3"))), null,
                                 read);

      assertEquals(WizDataModelWire.STALE_VIEW, ((Failure) second).reason());
      assertEquals("baseRevision", ((Failure) second).field());
      assertTrue(((Failure) second).message().contains("read it again"));
      assertTrue(((Failure) second).message().contains("nothing was written"));
      XPartition stored = storedView("v");
      assertTrue(stored.containsTable("T2"), "the first caller's table is still there");
      assertFalse(stored.containsTable("T3"));
      assertTrue(stored.containsTable("T1"));
      assertTrue(runtimes.isEmpty());
   }

   @Test
   void matchingBaseRevisionApplies() throws Exception {
      createView(definition("v", tables(plain("T1"))));
      String read = getView("v").revision();
      WriteResponse applied = ok(updateFrom(
         "v", definition("v", tables(plain("T1"), plain("T2"))), null, read));

      assertTrue(storedView("v").containsTable("T2"));
      // the response carries the revision of the saved result: the next edit can chain on it
      assertEquals(getView("v").revision(), applied.revision());
      assertNotEquals(read, applied.revision());
      ok(updateFrom("v", definition("v", tables(plain("T1"), plain("T2"), plain("T3"))), null,
                    applied.revision()));
      assertTrue(storedView("v").containsTable("T3"));
   }

   @Test
   void absentBaseRevisionKeepsOldBehavior() throws Exception {
      createView(definition("v", tables(plain("T1"))));
      ok(updateFrom("v", definition("v", tables(plain("T1"), plain("T2"))), null, null));
      ok(updateFrom("v", definition("v", tables(plain("T1"), plain("T3"))), null, null));

      XPartition stored = storedView("v");
      assertTrue(stored.containsTable("T3"));
      assertFalse(stored.containsTable("T2"), "last write wins for a client without a revision");
   }

   @Test
   void noRuntimeOpenedOnStale() throws Exception {
      createView(definition("v", tables(plain("T1"))));
      String read = getView("v").revision();
      ok(updateFrom("v", definition("v", tables(plain("T1"), plain("T2"))), null, read));
      int puts = runtimePuts.get();
      reset(manager);

      Object stale = updateFrom("v", definition("v", tables(plain("T1"), plain("T3"))), null,
                                read);

      assertEquals(WizDataModelWire.STALE_VIEW, ((Failure) stale).reason());
      assertEquals(puts, runtimePuts.get(), "no runtime was opened");
      verify(manager, never()).updateAndSaveModel(any(), any(), any(), any(), any(), any());
      verify(manager, never()).addTable(anyString(), anyString(), any(), any(), any(), any(),
                                        any(), any(), anyBoolean(), anyBoolean());
   }

   @Test
   void theDryRunAlsoHonorsBaseRevision() throws Exception {
      createView(definition("v", tables(plain("T1"))));
      String read = getView("v").revision();
      ok(updateFrom("v", definition("v", tables(plain("T1"), plain("T2"))), null, read));
      int puts = runtimePuts.get();

      Object stale = updateFrom("v", definition("v", tables(plain("T1"), plain("T3"))), true,
                                read);
      assertEquals(WizDataModelWire.STALE_VIEW, ((Failure) stale).reason());
      assertEquals(puts, runtimePuts.get());

      Object fresh = updateFrom("v", definition("v", tables(plain("T1"), plain("T2"),
                                                            plain("T3"))), true,
                                getView("v").revision());
      WriteResponse dry = ok(fresh);
      assertNull(dry.definition());
      assertNull(dry.revision(), "a dry run saved nothing, so it has no revision");
   }

   @Test
   void aGetResultFedBackWithItsRevisionIsANoOp() throws Exception {
      createView(definition("v", tables(plain("T1"), plain("T2"))));
      GetResponse got = getView("v");
      int puts = runtimePuts.get();
      WriteResponse result = ok(updateFrom("v", got.definition(), null, got.revision()));

      assertEquals(got.revision(), result.revision());
      assertEquals(puts, runtimePuts.get());
   }

   @Test
   void aStoredViewAboveTheCapIsNotLockedOut() throws Exception {
      XPartition big = new XPartition("big");

      for(int i = 0; i < 250; i++) {
         big.addTable("BT" + i, null, null);
      }

      dataModel.addPartition(big);
      GetResponse got = getView("big");
      assertEquals(250, ((List<?>) got.definition().get("tables")).size());
      int puts = runtimePuts.get();

      // fed back unchanged: no change, no runtime
      WriteResponse same = ok(updateView("big", got.definition(), null));
      assertEquals(puts, runtimePuts.get());
      assertEquals(got.revision(), same.revision());

      // a description edit with the whole list applies
      Map<String, Object> edited = new LinkedHashMap<>(got.definition());
      edited.put("description", "still editable");
      ok(updateView("big", edited, null));
      assertEquals("still editable", storedView("big").getDescription());
      assertEquals(250, Collections.list(storedView("big").getTables()).size());

      // growing it over the cap is refused
      Map<String, Object> grown = new LinkedHashMap<>(got.definition());
      List<Object> list = new ArrayList<>((List<?>) got.definition().get("tables"));
      list.add(plain("T1"));
      grown.put("tables", list);
      Object refused = updateView("big", grown, null);
      assertEquals("LIMIT_EXCEEDED", ((Failure) refused).errors().get(0).code());
      assertEquals("tables", ((Failure) refused).field());
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
      Pattern uuid = Pattern.compile("(?<![0-9a-f])[0-9a-f]{32}(?![0-9a-f])");

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
   void anExtendedViewJoinsTheSharedNameSpace() throws Exception {
      createView(definition("base", tables(plain("T1"))));
      dataModel.addVirtualPrivateModel(new inetsoft.uql.erm.vpm.VirtualPrivateModel("vp"), true);
      int puts = runtimePuts.get();

      for(String taken : List.of("pv2", "vp", "base")) {
         Map<String, Object> ext = definition(taken, tables(plain("T2")));
         ext.put("parent", "base");
         Object result = createViewRaw(ext, null);

         assertEquals(WizDataModelWire.DUPLICATE_NAME, ((Failure) result).reason(), taken);
      }

      assertEquals(puts, runtimePuts.get(), "no runtime was opened for a refused name");
      // a free name still works, and an extended view twice under one base is refused
      Map<String, Object> ext = definition("freeExt", tables(plain("T2")));
      ext.put("parent", "base");
      ok(createViewRaw(ext, null));
      assertEquals(WizDataModelWire.DUPLICATE_NAME,
                   ((Failure) createViewRaw(ext, null)).reason());
   }

   @Test
   void aDefinitionIsCappedAtTwoHundredTablesAndAThousandJoins() throws Exception {
      List<Map<String, Object>> many = new ArrayList<>();

      for(int i = 0; i <= WizPhysicalViewMapper.MAX_TABLES; i++) {
         many.add(plain("T" + i));
      }

      int puts = runtimePuts.get();
      Object tooManyTables = createViewRaw(definition("big", many), null);
      assertEquals("tables", ((Failure) tooManyTables).field());
      assertEquals("LIMIT_EXCEEDED", ((Failure) tooManyTables).errors().get(0).code());
      assertTrue(((Failure) tooManyTables).message().contains("200"));

      Map<String, Object> joins = definition("big", tables(plain("T1")));
      List<Object> list = new ArrayList<>();

      for(int i = 0; i <= WizPhysicalViewMapper.MAX_JOINS; i++) {
         list.add(join("T1", "C" + i, "T2", "D" + i));
      }

      joins.put("joins", list);
      Object tooManyJoins = createViewRaw(joins, null);
      assertEquals("joins", ((Failure) tooManyJoins).field());
      assertTrue(((Failure) tooManyJoins).message().contains("1000"));
      assertEquals(puts, runtimePuts.get(), "a definition over the cap opens no runtime");

      // an update is capped the same way
      createView(definition("v", tables(plain("T1"))));
      Object update = updateView("v", definition("v", many), null);
      assertEquals("tables", ((Failure) update).field());
   }

   @Test
   void cardinalityProbesAreCappedPerRequest() throws Exception {
      int joinCount = WizPhysicalViewSession.MAX_CARDINALITY_PROBES + 5;
      Map<String, String> columns1 = new LinkedHashMap<>();
      Map<String, String> columns2 = new LinkedHashMap<>();

      for(int i = 0; i < joinCount; i++) {
         columns1.put("C" + i, "integer");
         columns2.put("D" + i, "integer");
      }

      physical.put("T1", columns1);
      physical.put("T2", columns2);
      List<Object> joins = new ArrayList<>();

      for(int i = 0; i < joinCount; i++) {
         Map<String, Object> join = join("T2", "D" + i, "T1", "C" + i);
         join.remove("cardinality");
         joins.add(join);
      }

      Map<String, Object> definition = definition("probed", tables(plain("T1"), plain("T2")));
      definition.put("joins", joins);
      WriteResponse response = ok(createViewRaw(definition, null));

      verify(physicalService, times(WizPhysicalViewSession.MAX_CARDINALITY_PROBES))
         .getCardinality(any(), any(), any(), any());
      assertTrue(response.warnings().stream()
                    .anyMatch(w -> "CARDINALITY_NOT_DETECTED".equals(w.code())),
                 response.warnings().toString());
      assertEquals(joinCount, storedView("probed").getRelationshipCount(),
                   "every join was written, probed or not");

      // exactly at the cap: no warning
      reset(physicalService);
      Map<String, Object> atCap = definition("atcap", tables(plain("T1"), plain("T2")));
      atCap.put("joins", new ArrayList<>(joins.subList(0, WizPhysicalViewSession.MAX_CARDINALITY_PROBES)));
      WriteResponse capped = ok(createViewRaw(atCap, null));

      assertTrue(capped.warnings().stream()
                    .noneMatch(w -> "CARDINALITY_NOT_DETECTED".equals(w.code())));
   }

   @Test
   void parsingHasAnAbsoluteCeiling() {
      Map<String, Object> map = new LinkedHashMap<>();
      List<Object> tables = new ArrayList<>();

      for(int i = 0; i <= WizPhysicalViewMapper.PARSE_CEILING_TABLES; i++) {
         tables.add(plain("T" + i));
      }

      map.put("name", "v");
      map.put("tables", tables);

      assertThrows(inetsoft.web.wiz.controller.WizDataModelErrors.DmFailure.class,
                   () -> WizPhysicalViewMapper.parse(map));
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

      // design 8.4: the posted table list carries oldAlias == alias for an alias that is not
      // renamed, never null (a null would make the save rewrite every table-less attribute)
      org.mockito.ArgumentCaptor<inetsoft.web.portal.model.database.PhysicalModelDefinition> posted =
         org.mockito.ArgumentCaptor.forClass(
            inetsoft.web.portal.model.database.PhysicalModelDefinition.class);
      verify(manager).updateAndSaveModel(any(), any(), any(), any(), posted.capture(), any());
      PhysicalTableModel newAlias = posted.getValue().getTables().stream()
         .filter(t -> "NEWALIAS".equals(t.getAlias())).findFirst().orElseThrow();
      assertEquals("NEWALIAS", newAlias.getOldAlias());
      PhysicalTableModel plain = posted.getValue().getTables().stream()
         .filter(t -> "T1".equals(t.getQualifiedName())).findFirst().orElseThrow();
      assertNull(plain.getOldAlias());
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
   void aJoinWithoutACardinalityGetsOneFromTheServer() throws Exception {
      Map<String, Object> join = join("T2", "T1_ID", "T1", "ID");
      join.remove("cardinality");
      WriteResponse response = createView(definition(
         "v", tables(plain("T1"), plain("T2")), join));
      Map<?, ?> stored = (Map<?, ?>) ((List<?>) response.definition().get("joins")).get(0);

      assertNotNull(stored.get("cardinality"), "a cardinality was decided: " + stored);
      assertTrue(runtimes.isEmpty());
   }

   @Test
   void anExtendedViewLivesInTheFolderOfItsBase() throws Exception {
      createView(definition("base", tables(plain("T1"))));
      Map<String, Object> ext = definition("ext", tables(plain("T2")));
      ext.put("parent", "base");
      ext.put("folder", "F");
      int puts = runtimePuts.get();
      Object result = createViewRaw(ext, null);

      assertEquals("definition.folder", ((Failure) result).field());
      assertEquals(puts, runtimePuts.get());
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

   private static Map<String, Object>[] triangle() {
      @SuppressWarnings("unchecked")
      Map<String, Object>[] joins = new Map[] {
         join("T2", "T1_ID", "T1", "ID"), join("T3", "T2_CODE", "T2", "CODE"),
         join("T3", "ID", "T1", "ID")};
      return joins;
   }

   @Test
   void aCycleWarningNamesTheJoinsOfTheCycle() throws Exception {
      WriteResponse response = createView(definition(
         "v", tables(plain("T1"), plain("T2"), plain("T3")), triangle()));

      assertEquals(1, response.warnings().size());
      Warning warning = response.warnings().get(0);
      assertEquals("CYCLE", warning.code());
      assertEquals(List.of("T2.T1_ID -> T1.ID",
                           "T3.ID -> T1.ID", "T3.T2_CODE -> T2.CODE"), warning.joins());

      for(String join : warning.joins()) {
         assertTrue(warning.message().contains(join), join);
      }
   }

   @Test
   void aCycleWarningDoesNotNameTablesOutsideTheCycle() throws Exception {
      Map<String, Object>[] joins = Arrays.copyOf(triangle(), 4);
      joins[3] = join("T4", "X", "T1", "NAME");
      WriteResponse response = createView(definition(
         "v", tables(plain("T1"), plain("T2"), plain("T3"), plain("T4")), joins));

      Warning warning = response.warnings().get(0);
      assertEquals("CYCLE", warning.code());
      assertEquals(3, warning.joins().size());
      assertFalse(warning.message().contains("T4"));
   }

   @Test
   void nonCycleWarningsCarryNoJoins() throws Exception {
      WriteResponse response = createView(definition("v", tables(plain("T1"), plain("T2"))));

      assertEquals("UNJOINED_TABLES", response.warnings().get(0).code());
      assertNull(response.warnings().get(0).joins());
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
