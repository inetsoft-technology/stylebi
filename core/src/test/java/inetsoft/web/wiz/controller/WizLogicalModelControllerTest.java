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

import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.DependenciesInfo;
import inetsoft.uql.asset.sync.RenameDependencyInfo;
import inetsoft.uql.asset.sync.RenameInfo;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.web.portal.controller.database.LogicalModelService;
import inetsoft.web.portal.model.database.LogicalModelDefinition;
import inetsoft.web.wiz.controller.WizDataModelWire.Failure;
import inetsoft.web.wiz.controller.WizLogicalModelWire.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The {@code /logical/get}, {@code /logical/create} and {@code /logical/update} routes against
 * the real registry and the real {@code LogicalModelService}. Each create test that is about a
 * name uses a Chinese name once (design section 9, DM-A8).
 */
class WizLogicalModelControllerTest extends WizLogicalModelTestBase {
   private static final String CN = "销售模型";

   private Attribute fullAmount() {
      return new Attribute("Amount", null, "the amount", true, null, "T1", "AMOUNT", null, null,
                           null, "double", "measure", "Sum", true, null,
                           new Format("Decimal", "#,##0.00", null),
                           new Drill(List.of(new WizLogicalModelWire.DrillPath(
                              "To details", "web", "http://example.test/{id}", "SELF", "tip",
                              true, false, List.of(new DrillParam("id", "ID", null)), null,
                              null))),
                           null, null);
   }

   private static Attribute typed(Attribute a, String dataType) {
      return new Attribute(a.name(), a.oldName(), a.description(), a.visible(), a.type(),
                           a.table(), a.column(), a.expression(), a.aggregate(), a.parseable(),
                           dataType, a.refType(), a.formula(), a.browseData(), a.browseQuery(),
                           a.format(), a.drill(), a.baseElement(), a.errorMessage());
   }

   private Definition sales(String name) {
      return def(name, entity("Sales", fullAmount(), typed(col("Name", "T1", "NAME"), "string"),
                              exp("Double", "T1.AMOUNT * 2", "double")));
   }

   // -- DM-A1 round trip -----------------------------------------------------------------------

   @Test
   void createGetRoundTrip() throws Exception {
      Definition written = inFolder(sales(CN), "F");
      Object result = controller.create(new CreateRequest(ds, written, null), bob);
      assertInstanceOf(CreateResponse.class, result, String.valueOf(result));
      CreateResponse created = (CreateResponse) result;
      assertTrue(created.ok());
      assertEquals(CN, created.name());
      assertEquals("F", created.folder());

      GetResponse fetched = get(CN);
      assertTrue(fetched.ok());
      assertFalse(fetched.readOnly());
      assertEquals("F", fetched.folder());
      assertNull(WizLogicalModelReadBack.diff(written, fetched.definition()),
                 "the definition read back equals what was written");
      assertNull(WizLogicalModelReadBack.diff(written, created.definition()));
      Attribute amount = fetched.definition().entities().get(0).attributes().get(0);
      assertEquals("measure", amount.refType());
      assertEquals("Sum", amount.formula());
      assertEquals("#,##0.00", amount.format().formatSpec());
      assertEquals("To details", amount.drill().paths().get(0).name());
      Attribute computed = fetched.definition().entities().get(0).attributes().get(2);
      assertEquals("T1.AMOUNT * 2", computed.expression());
      assertEquals("expression", computed.type());
      assertEquals(ds, ds);
      assertNotNull(fresh().getLogicalModel(CN), "the model is in the registry");
      assertEquals("F", fresh().getLogicalModel(CN).getFolder());
   }

   @Test
   void createFillsTheDataTypeFromThePhysicalColumnAndSaysSo() throws Exception {
      Object result = controller.create(new CreateRequest(
         ds, def("filled", entity("E", col("Amount", "T1", "AMOUNT"))), null), bob);
      CreateResponse created = (CreateResponse) result;
      assertEquals("double", created.definition().entities().get(0).attributes().get(0)
                      .dataType());
      assertTrue(created.warnings().stream().anyMatch(w -> w.contains("filled from the physical")),
                 created.warnings().toString());
   }

   @Test
   void getReturnsOldNamesAndErrorMessages() throws Exception {
      XLogicalModel stored = new XLogicalModel("broken");
      stored.setPartition("pv");
      XEntity entity = new XEntity("Sales");
      entity.addAttribute(new XAttribute("Gone", "T9", "X", "string"));
      entity.addAttribute(new XAttribute("Amount", "T1", "AMOUNT", "double"));
      stored.addEntity(entity);
      dataModel.addLogicalModel(stored);

      GetResponse fetched = get("broken");
      Entity sales = fetched.definition().entities().get(0);
      assertEquals("Sales", sales.oldName(), "oldName is the current name");
      assertNotNull(sales.errorMessage(), "the entity reports that an attribute is broken");
      assertEquals("Gone", sales.attributes().get(0).oldName());
      assertNotNull(sales.attributes().get(0).errorMessage(), "the missing table is reported");
      assertNull(sales.attributes().get(1).errorMessage());
   }

   @Test
   void createDryRunWritesNothing() throws Exception {
      Object result = controller.create(new CreateRequest(ds, sales(CN), true), bob);
      CreateResponse dry = (CreateResponse) result;
      assertTrue(dry.ok());
      assertNull(dry.definition(), "no read-back on a dry run");
      assertNull(fresh().getLogicalModel(CN));
      verifyNoInteractions(treeService, dependencyHandler);
   }

   @Test
   void getThenUpdateIsNoOp() throws Exception {
      create(inFolder(sales("noop"), "F"));
      reset(dependencyHandler, assetDataCache, transformHandler);
      long before = fresh().getLogicalModel("noop").getLastModified();

      Object result = update("noop", get("noop").definition(), null);

      assertInstanceOf(UpdateResponse.class, result, String.valueOf(result));
      assertTrue(((UpdateResponse) result).ok());
      assertEquals(before, fresh().getLogicalModel("noop").getLastModified());
      verifyNoInteractions(dependencyHandler, assetDataCache, transformHandler);
   }

   @Test
   void formatNullRoundTrips() throws Exception {
      Definition written = def("nofmt", entity("E", typed(col("Amount", "T1", "AMOUNT"),
                                                              "double")));
      create(written);
      Attribute read = get("nofmt").definition().entities().get(0).attributes().get(0);
      assertNull(read.format(), "a stored XFormatInfo with no format reads back as no format");
      assertNull(read.drill());
      assertNull(WizLogicalModelReadBack.diff(written, get("nofmt").definition()));
   }

   // -- DM-A3 refusal before saving --------------------------------------------------------------

   @Test
   void unknownTableIsRefusedAndNothingIsSaved() throws Exception {
      Object result = controller.create(new CreateRequest(
         ds, def("bad", entity("E", col("A", "NOPE", "ID"))), null), bob);

      Failure failure = assertInstanceOf(Failure.class, result);
      assertEquals(WizDataModelWire.INVALID, failure.reason());
      assertEquals("entities[E].attributes[A].table", failure.errors().get(0).field());
      assertEquals("TABLE_NOT_IN_VIEW", failure.errors().get(0).code());
      assertNull(fresh().getLogicalModel("bad"));
   }

   @Test
   void duplicateAttributeFollowedByAnotherNeverReachesTheService() throws Exception {
      // LogicalModelService.createModel throws IndexOutOfBoundsException for this shape
      Object result = controller.create(new CreateRequest(
         ds, def("dup", entity("E", col("A", "T1", "ID"), col("A", "T1", "NAME"),
                               col("B", "T1", "AMOUNT"))), null), bob);

      Failure failure = assertInstanceOf(Failure.class, result);
      assertEquals(List.of("ATTRIBUTE_DUPLICATE"), codes(failure));
      assertNull(fresh().getLogicalModel("dup"));
   }

   @Test
   void missingFolderIsRefused() throws Exception {
      Object result = controller.create(new CreateRequest(
         ds, inFolder(def("nof", entity("E", col("A", "T1", "ID"))), "Nope"), null), bob);

      assertEquals(List.of("FOLDER_NOT_FOUND"), codes(result));
      assertEquals("folder", ((Failure) result).errors().get(0).field());
   }

   @Test
   void metadataThatCannotBeReadIsReportedOnceNotPerAttribute() throws Exception {
      when(metaProvider.getMetaData(any(XPartition.class), anyBoolean(), any()))
         .thenThrow(new RuntimeException("db down"));

      Object result = controller.create(new CreateRequest(
         ds, def("nometa", entity("E", col("A", "T1", "ID"), col("B", "T1", "NAME"))), null),
                                        bob);

      assertEquals(List.of("METADATA_UNAVAILABLE"), codes(result));
      assertTrue(((Failure) result).errors().get(0).message().contains("db down"));
   }

   // -- DM-A8 shared namespace, Chinese names ------------------------------------------------------

   @Test
   void createRefusedWhenAViewHasTheName() throws Exception {
      Object result = controller.create(new CreateRequest(
         ds, def("pv", entity("E", col("A", "T1", "ID"))), null), bob);

      Failure failure = assertInstanceOf(Failure.class, result);
      assertEquals(WizDataModelWire.DUPLICATE_NAME, failure.reason());
      assertEquals("physicalView", failure.existingKind());
      assertTrue(failure.message().contains("physicalView named \"pv\""), failure.message());
   }

   @Test
   void createRefusedWhenAVpmOrAnotherModelHasTheName() throws Exception {
      dataModel.addVirtualPrivateModel(new VirtualPrivateModel("rule"), true);
      create(def("taken", entity("E", col("A", "T1", "ID"))));

      Failure vpm = (Failure) controller.create(new CreateRequest(
         ds, def("rule", entity("E", col("A", "T1", "ID"))), null), bob);
      Failure model = (Failure) controller.create(new CreateRequest(
         ds, def("taken", entity("E", col("A", "T1", "ID"))), null), bob);

      assertEquals("vpm", vpm.existingKind());
      assertEquals("logicalModel", model.existingKind());
   }

   @Test
   void nonAsciiNameRoundTrips() throws Exception {
      create(sales(CN));
      assertEquals(CN, get(CN).definition().name());
      assertEquals(List.of(CN), List.of(dataModel.getLogicalModelNames()).stream()
         .filter(CN::equals).toList());
   }

   @Test
   void aBadNameIsAMalformedRequest() {
      assertThrows(IllegalArgumentException.class, () -> controller.create(
         new CreateRequest(ds, def("a/b", entity("E", col("A", "T1", "ID"))), null), bob));
   }

   @Test
   void nonJdbcSourceIsRefusedWithNotJdbc() throws Exception {
      when(repository.getDataSource(ds)).thenReturn(mock(XDataSource.class));

      Failure failure = (Failure) controller.get(new GetRequest(ds, "x", null), bob);

      assertEquals(WizDataModelWire.NOT_JDBC, failure.reason());
      assertEquals("database", failure.field());
   }

   // -- DM-A11 extended models --------------------------------------------------------------------

   @Test
   void extendedParentRefusedOnCreatePhase1() throws Exception {
      create(def("base", entity("E", col("A", "T1", "ID"))));
      Definition withParent = new Definition("child", null, "pv", null, null, "base", null,
                                             List.of(entity("E", col("A", "T1", "ID"))));

      Object result = controller.create(new CreateRequest(ds, withParent, null), bob);

      assertEquals(List.of("EXTENDED_UNSUPPORTED"), codes(result));
      assertEquals("parent", ((Failure) result).errors().get(0).field());
      assertTrue(((Failure) result).errors().get(0).message().contains("not supported"));
      assertNull(fresh().getLogicalModel("base").getLogicalModel("child"));
   }

   @Test
   void storedExtendedModelReadsReadOnly() throws Exception {
      create(def("base", entity("E", col("A", "T1", "ID"))));
      fresh().getLogicalModel("base").addLogicalModel(new XLogicalModel("child"), true);

      GetResponse fetched = (GetResponse) controller.get(new GetRequest(ds, "child", "base"),
                                                         bob);

      assertTrue(fetched.readOnly());
      assertEquals("base", fetched.definition().parent());
      assertTrue(fetched.definition().extended());
   }

   // -- DM-A2 rename markers ---------------------------------------------------------------------

   @Test
   void updateWithRenamedAttributeAndNoOldNameIsRefused() throws Exception {
      create(def("m", entity("Sales", col("Amount", "T1", "AMOUNT"))));

      // the same column under a new name and no marker: looks like a rename
      Object result = update("m", def("m", entity("Sales", col("Total", "T1", "AMOUNT"))), null);

      assertEquals(List.of("OLDNAME_REQUIRED"), codes(result));
      assertEquals("entities[Sales].attributes[Total].oldName",
                   ((Failure) result).errors().get(0).field());
      assertTrue(((Failure) result).errors().get(0).message().contains("oldName:\"Amount\""));
      assertNotNull(attribute("m", "Amount"), "the model is unchanged");
      assertNull(attribute("m", "Total"));
   }

   @Test
   void anExplicitNullOldNameSaysTheElementIsNew() throws Exception {
      create(def("m", entity("Sales", col("Amount", "T1", "AMOUNT"))));

      Object result = update("m", def("m", entity("Sales", col("Total", "", "T1", "AMOUNT"))),
                             null);

      assertInstanceOf(UpdateResponse.class, result, String.valueOf(result));
      assertNull(attribute("m", "Amount"), "the old element is deleted");
      assertNotNull(attribute("m", "Total"));
   }

   @Test
   void unknownOldNameIsRefused() throws Exception {
      create(def("m", entity("Sales", col("Amount", "T1", "AMOUNT"))));

      Object result = update("m", def("m", entity(
         "Sales", col("Total", "Nope", "T1", "AMOUNT"))), null);

      assertEquals(List.of("OLDNAME_UNKNOWN"), codes(result));
      assertEquals("entities[Sales].attributes[Total].oldName",
                   ((Failure) result).errors().get(0).field());
   }

   @Test
   void anExplicitNewElementMayNotHideAStoredOne() throws Exception {
      create(def("m", entity("Sales", col("Amount", "T1", "AMOUNT"))));

      Object result = update("m", def("m", entity("Sales", col("Amount", "", "T1", "NAME"))),
                             null);

      assertEquals(List.of("OLDNAME_REQUIRED"), codes(result));
   }

   @Test
   void renameWithMarkerAppliesAndMigratesDependents() throws Exception {
      create(def("m", entity("Sales", col("Amount", "T1", "AMOUNT"))));
      seedDependency("m");

      Object result = update("m", def("m", entity("Sales", col("Total", "Amount", "T1",
                                                                "AMOUNT"))), null);

      assertInstanceOf(UpdateResponse.class, result, String.valueOf(result));
      assertNull(attribute("m", "Amount"));
      assertNotNull(attribute("m", "Total"));
      ArgumentCaptor<RenameDependencyInfo> captor =
         ArgumentCaptor.forClass(RenameDependencyInfo.class);
      verify(transformHandler).addTransformTask(captor.capture());
      List<RenameInfo> infos = captor.getValue().getDependencyMap().values().iterator().next();
      assertEquals("Sales.Amount", infos.get(0).getOldName());
      assertEquals("Sales.Total", infos.get(0).getNewName());
   }

   @Test
   void dryRunListsTheDependentsOfEveryElementItBreaks() throws Exception {
      create(def("m", entity("Sales", col("Amount", "T1", "AMOUNT"), col("Name", "T1", "NAME"))));
      seedOuter("m", "Sales", "Amount", otherDependent());

      UpdateResponse dry = (UpdateResponse) update(
         "m", def("m", entity("Sales", col("Total", "Amount", "T1", "AMOUNT"),
                              col("Name", "T1", "NAME"))), true);

      assertNull(dry.definition(), "a dry run applies nothing");
      assertEquals(1, dry.elementDependents().size(), dry.elementDependents().toString());
      assertEquals("Sales.Amount", dry.elementDependents().get(0).element());
      assertEquals("other", dry.elementDependents().get(0).dependents().get(0).type());
      assertNotNull(attribute("m", "Amount"), "still stored");
   }

   // -- update: name, view, gates ----------------------------------------------------------------

   @Test
   void updateBodyNameMismatchIsRefusedBeforeAnythingIsChecked() throws Exception {
      create(def("m", entity("Sales", col("Amount", "T1", "AMOUNT"))));
      create(def("other", entity("Sales", col("Amount", "T1", "AMOUNT"))));

      Object result = update("m", def("other", entity("Sales", col("Name", "T1", "NAME"))), null);

      Failure failure = assertInstanceOf(Failure.class, result);
      assertEquals(WizDataModelWire.INVALID, failure.reason());
      assertEquals("definition.name", failure.errors().get(0).field());
      assertNotNull(attribute("other", "Amount"), "the other model is untouched");
   }

   @Test
   void changingTheViewRevalidatesEveryAttributeAgainstTheNewView() throws Exception {
      create(def("m", entity("Sales", col("Amount", "T1", "AMOUNT"))));

      Object result = update("m", withView(def("m", entity("Sales", col("Amount", "T1", "AMOUNT"))),
                                           "pv2"), null);

      assertEquals(List.of("TABLE_NOT_IN_VIEW"), codes(result));
      assertEquals("pv", fresh().getLogicalModel("m").getPartition());
   }

   @Test
   void viewChangeListsTheModelsDependents() throws Exception {
      create(def("m", entity("Sales", col("Code", "T1", "AMOUNT"))));
      seedOuter("m", null, null, otherDependent());

      UpdateResponse dry = (UpdateResponse) update(
         "m", withView(def("m", entity("Sales", col("Code", "T1", "AMOUNT"))), "pv3"), true);

      assertEquals(1, dry.viewDependents().size());
      assertEquals("other", dry.viewDependents().get(0).type());
   }

   @Test
   void readOnlyUserCannotUpdate() throws Exception {
      create(def("m", entity("Sales", col("Amount", "T1", "AMOUNT"))));
      doThrow(new inetsoft.sree.security.SecurityException("denied")).when(assetRepository)
         .checkAssetPermission(any(), any(AssetEntry.class), eq(ResourceAction.WRITE));

      SecurityException e = assertThrows(SecurityException.class, () -> update(
         "m", def("m", entity("Sales", col("Name", "", "T1", "NAME"))), null));

      assertTrue(e.getMessage().contains(ds + "/m"), e.getMessage());
      assertNotNull(attribute("m", "Amount"), "nothing changed");
      assertNull(attribute("m", "Name"));
   }

   @Test
   void getNeedsReadOnTheModelAndLearnsNothingWithoutIt() throws Exception {
      create(def("m", entity("Sales", col("Amount", "T1", "AMOUNT"))));
      reset(treeService);
      doThrow(new inetsoft.sree.security.SecurityException("denied")).when(assetRepository)
         .checkAssetPermission(any(), any(AssetEntry.class), eq(ResourceAction.READ));

      assertThrows(inetsoft.sree.security.SecurityException.class, () -> get("m"));
      verifyNoInteractions(treeService);
   }

   @Test
   void createNeedsWriteOnTheNewModelPath() throws Exception {
      doThrow(new inetsoft.sree.security.SecurityException("denied")).when(assetRepository)
         .checkAssetPermission(any(), any(AssetEntry.class), eq(ResourceAction.WRITE));

      SecurityException e = assertThrows(SecurityException.class, () -> controller.create(
         new CreateRequest(ds, inFolder(def("m", entity("E", col("A", "T1", "ID"))), "F"), null),
         bob));

      assertTrue(e.getMessage().contains(ds + "/F/m"), e.getMessage());
      assertNull(fresh().getLogicalModel("m"));
   }

   @Test
   void unknownAndWrongKindNamesAreBusinessOutcomes() throws Exception {
      assertEquals(WizDataModelWire.NOT_FOUND,
                   ((Failure) controller.get(new GetRequest(ds, "none", null), bob)).reason());
      Failure wrong = (Failure) controller.get(new GetRequest(ds, "pv", null), bob);
      assertEquals(WizDataModelWire.WRONG_KIND, wrong.reason());
      assertEquals("physicalView", wrong.existingKind());
   }

   // -- C2 read-back --------------------------------------------------------------------------------

   @Test
   void readBackMismatchFailsLoud() throws Exception {
      LogicalModelService spy = spy(modelService);
      doAnswer(invocation -> {
         LogicalModelDefinition dto = (LogicalModelDefinition) invocation.callRealMethod();
         dto.getEntities().get(0).getAttributes().get(0).setDescription("changed by the server");
         return dto;
      }).when(spy).getModel(anyString(), anyString(), anyString(), any(), any());
      WizLogicalModelController checked = newController(spy);

      Failure failure = (Failure) checked.create(new CreateRequest(
         ds, def("rb", entity("E", withDescription(col("A", "T1", "ID"), "mine"))), null), bob);

      assertEquals(WizDataModelWire.READ_BACK_FAILED, failure.reason());
      assertTrue(failure.message().contains("not as written"), failure.message());
      assertTrue(failure.message().contains("entities[E].attributes[A]"), failure.message());
   }

   // -- stored state the wire cannot carry ---------------------------------------------------------------

   @Test
   void anUpdateThatWouldDeleteAStoredDateSettingIsRefused() throws Exception {
      create(def("m", entity("Sales", col("Amount", "T1", "AMOUNT"))));
      XMetaInfo meta = new XMetaInfo();
      meta.setAsDate(true);
      // the server persists a meta info only together with a format or a drill
      meta.setXFormatInfo(new inetsoft.uql.XFormatInfo("Decimal", "#,##0"));
      XLogicalModel stored = dataModel.getLogicalModel("m");
      stored.getEntity("Sales").getAttribute("Amount").setXMetaInfo(meta);
      dataModel.addLogicalModel(stored);

      Object result = update("m", def("m", entity("Sales", col("Amount", "T1", "AMOUNT"),
                                                   col("Name", "T1", "NAME"))), null);

      assertEquals(List.of("STORED_UNSUPPORTED"), codes(result));
   }

   // -- helpers ------------------------------------------------------------------------------------------

   private XAttribute attribute(String model, String name) {
      XLogicalModel stored = fresh().getLogicalModel(model);
      XEntity entity = stored == null ? null : stored.getEntity("Sales");
      return entity == null ? null : entity.getAttribute(name);
   }

   /**
    * A recorded dependent that is not a sheet: the service drops sheets that no longer exist
    * from every dependents answer, and the harness has no sheets.
    */
   private AssetEntry otherDependent() {
      return new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL,
                            ds + "/someOtherModel", null);
   }

   private void seedDependency(String model) throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                        AssetEntry.Type.LOGIC_MODEL, ds + "/" + model, null);
      DependenciesInfo info = new DependenciesInfo();
      info.setDependencies(new ArrayList<>(List.of((AssetObject) new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ws1", null))));
      when(dependencyStorage.getWithOrg(eq(entry.toIdentifier()), any())).thenReturn(info);
   }
}
