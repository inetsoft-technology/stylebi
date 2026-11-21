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
import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.wiz.controller.WizDataModelWire.Failure;
import inetsoft.web.wiz.controller.WizLogicalModelWire.*;
import inetsoft.web.wiz.controller.WizModelKindOps.Ctx;
import inetsoft.web.wiz.controller.WizModelKindOps.ModelRef;
import inetsoft.web.wiz.controller.WizModelKindOps.OpResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 2 (lane J2b): extended logical models. An extended model is created under a base model
 * over the same view; its base entities and attributes are inherited and read-only, its own
 * entities and attributes are editable. The extended model is addressed by {@code name} plus
 * {@code definition.parent}.
 */
class WizLogicalModelExtendedTest extends WizLogicalModelTestBase {
   @BeforeEach
   void createBase() throws Exception {
      Entity sales = entity("Sales", typed(col("Amount", "T1", "AMOUNT"), "double"),
                            typed(col("Name", "T1", "NAME"), "string"));
      create(new Definition("base", "the base", "pv", "F", null, null, null,
                            new ArrayList<>(List.of(sales))));
   }

   private static Attribute typed(Attribute a, String dataType) {
      return new Attribute(a.name(), a.oldName(), a.description(), a.visible(), a.type(),
                           a.table(), a.column(), a.expression(), a.aggregate(), a.parseable(),
                           dataType, a.refType(), a.formula(), a.browseData(), a.browseQuery(),
                           a.format(), a.drill(), a.baseElement(), a.errorMessage());
   }

   /** A child definition: only its OWN elements (the base ones are merged in by the bridge). */
   private static Definition child(String name, Entity... own) {
      return new Definition(name, null, null, null, null, "base", null,
                            new ArrayList<>(List.of(own)));
   }

   private static Entity ownMargin() {
      return entity("Sales", exp("Margin", "T1.AMOUNT * 0.1", "double"));
   }

   private Definition createChild(String name) throws Exception {
      Object result = controller.create(new CreateRequest(ds, child(name, ownMargin(),
         entity("Extra", exp("Flag", "T1.ID", "integer"))), null), bob);
      assertInstanceOf(CreateResponse.class, result, String.valueOf(result));
      return ((CreateResponse) result).definition();
   }

   private GetResponse getChild(String name) throws Exception {
      Object result = controller.get(new GetRequest(ds, name, "base"), bob);
      assertInstanceOf(GetResponse.class, result, String.valueOf(result));
      return (GetResponse) result;
   }

   private static Entity entityOf(Definition d, String name) {
      return d.entities().stream().filter(e -> name.equals(e.name())).findFirst().orElseThrow();
   }

   private static Attribute attributeOf(Entity e, String name) {
      return e.attributes().stream().filter(a -> name.equals(a.name())).findFirst().orElseThrow();
   }

   /** The get result with one attribute of Sales replaced (null removes it). */
   private static Definition editSales(Definition d, String attribute, Attribute replacement) {
      List<Entity> entities = new ArrayList<>();

      for(Entity e : d.entities()) {
         if(e.name().equals("Sales")) {
            List<Attribute> attributes = new ArrayList<>();

            for(Attribute a : e.attributes()) {
               if(a.name().equals(attribute)) {
                  if(replacement != null) {
                     attributes.add(replacement);
                  }
               }
               else {
                  attributes.add(a);
               }
            }

            e = new Entity(e.name(), e.oldName(), e.description(), e.visible(), e.baseElement(),
                           e.errorMessage(), attributes);
         }

         entities.add(e);
      }

      return new Definition(d.name(), d.description(), d.view(), d.folder(), d.connection(),
                            d.parent(), d.extended(), entities);
   }

   // -- create and read back -------------------------------------------------------------------

   @Test
   void extendedModelCreateReadsBack() throws Exception {
      Definition readBack = createChild("child");

      assertEquals("base", readBack.parent());
      assertTrue(readBack.extended());
      assertEquals("pv", readBack.view(), "the view is the base model's");
      assertEquals("F", readBack.folder(), "the folder is the base model's");
      Entity sales = entityOf(readBack, "Sales");
      assertEquals(List.of("Amount", "Name", "Margin"),
                   sales.attributes().stream().map(Attribute::name).toList(),
                   "the base attributes are inherited, the own one follows");
      assertNotNull(entityOf(readBack, "Extra"));
      assertNotNull(fresh().getLogicalModel("base").getLogicalModel("child"));
      assertNull(fresh().getLogicalModel("child"), "an extended model is not a top-level model");

      GetResponse fetched = getChild("child");
      assertNull(WizLogicalModelReadBack.diff(readBack, fetched.definition(), true));
      assertFalse(fetched.readOnly(), "the model's own elements are editable");
      assertEquals("F", fetched.folder());
   }

   @Test
   void createDryRunWritesNothing() throws Exception {
      Object result = controller.create(new CreateRequest(ds, child("child", ownMargin()), true),
                                        bob);

      assertInstanceOf(CreateResponse.class, result, String.valueOf(result));
      assertNull(fresh().getLogicalModel("base").getLogicalModel("child"));
   }

   @Test
   void baseElementsAreFlaggedReadOnly() throws Exception {
      createChild("child");

      Definition read = getChild("child").definition();
      Entity sales = entityOf(read, "Sales");

      assertTrue(sales.baseElement(), "Sales comes from the base");
      assertTrue(attributeOf(sales, "Amount").baseElement());
      assertTrue(attributeOf(sales, "Name").baseElement());
      assertFalse(attributeOf(sales, "Margin").baseElement(), "Margin is the child's own");
      assertFalse(entityOf(read, "Extra").baseElement());
   }

   // -- the base elements cannot be written ------------------------------------------------------

   @Test
   void baseElementWriteRefused() throws Exception {
      createChild("child");
      Definition read = getChild("child").definition();
      Attribute amount = attributeOf(entityOf(read, "Sales"), "Amount");

      // an edit of a base attribute
      assertBaseRefused(editSales(read, "Amount", withDescription(amount, "edited")),
                        "entities[Sales].attributes[Amount]");
      // a rename of a base attribute
      assertBaseRefused(editSales(read, "Amount", new Attribute(
         "Total", "Amount", amount.description(), amount.visible(), amount.type(),
         amount.table(), amount.column(), null, null, null, amount.dataType(), amount.refType(),
         amount.formula(), amount.browseData(), amount.browseQuery(), amount.format(),
         amount.drill(), null, null)), "entities[Sales].attributes[Total]");
      // a removal of a base attribute
      assertBaseRefused(editSales(read, "Amount", null), "entities[Sales].attributes[Amount]");
      // a retype of a base attribute (column to expression)
      assertBaseRefused(editSales(read, "Amount", exp("Amount", "T1.AMOUNT + 1", "double")),
                        "entities[Sales].attributes[Amount]");
      // an edit of the base entity
      List<Entity> entities = new ArrayList<>(read.entities());
      Entity sales = entities.get(0);
      entities.set(0, new Entity(sales.name(), sales.oldName(), "new description",
                                 sales.visible(), sales.baseElement(), null,
                                 sales.attributes()));
      assertBaseRefused(new Definition(read.name(), read.description(), read.view(),
                                       read.folder(), read.connection(), read.parent(),
                                       read.extended(), entities), "entities[Sales]");
      // a removal of the base entity
      assertBaseRefused(new Definition(read.name(), read.description(), read.view(),
                                       read.folder(), read.connection(), read.parent(),
                                       read.extended(), List.of(entityOf(read, "Extra"))),
                        "entities[Sales]");

      assertNull(get("base").definition().entities().get(0).attributes().get(0).description(),
                 "the base model is untouched");
   }

   private void assertBaseRefused(Definition definition, String field) throws Exception {
      Object result = update("child", definition, null);

      Failure failure = assertInstanceOf(Failure.class, result, String.valueOf(result));
      assertEquals(WizDataModelWire.INVALID, failure.reason(), failure.message());
      assertTrue(failure.errors().stream().anyMatch(
         e -> "BASE_ELEMENT_READ_ONLY".equals(e.code()) && e.field().startsWith(field)),
                 "expected BASE_ELEMENT_READ_ONLY at " + field + ": " + failure.errors());
   }

   @Test
   void ownElementsEditable() throws Exception {
      createChild("child");
      Definition read = getChild("child").definition();
      // edit the own attribute, add one to the base entity, add an entity, drop the own entity
      Attribute margin = attributeOf(entityOf(read, "Sales"), "Margin");
      Definition edited = editSales(read, "Margin", withDescription(margin, "the margin"));
      List<Entity> entities = new ArrayList<>(edited.entities());
      Entity sales = entities.get(0);
      List<Attribute> attributes = new ArrayList<>(sales.attributes());
      attributes.add(exp("Tax", "T1.AMOUNT * 0.2", "double"));
      entities.set(0, new Entity(sales.name(), sales.oldName(), sales.description(),
                                 sales.visible(), sales.baseElement(), null, attributes));
      entities.remove(entityOf(edited, "Extra"));
      entities.add(entity("Other", typed(col("Id", "T1", "ID"), "integer")));
      Definition submitted = new Definition(edited.name(), edited.description(), edited.view(),
                                            edited.folder(), edited.connection(), edited.parent(),
                                            edited.extended(), entities);

      Object result = update("child", submitted, null);

      UpdateResponse applied = assertInstanceOf(UpdateResponse.class, result,
                                                String.valueOf(result));
      Entity storedSales = entityOf(applied.definition(), "Sales");
      assertEquals("the margin", attributeOf(storedSales, "Margin").description());
      assertNotNull(attributeOf(storedSales, "Tax"));
      assertNotNull(entityOf(applied.definition(), "Other"));
      assertFalse(applied.definition().entities().stream()
                     .anyMatch(e -> e.name().equals("Extra")), "the own entity is removed");
      assertTrue(attributeOf(storedSales, "Amount").baseElement());
      verify(assetDataCache).removeCacheDependence(ds);
   }

   // -- the parent --------------------------------------------------------------------------------

   @Test
   void extendedParentMustUseTheSameView() throws Exception {
      Definition otherView = new Definition("child", null, "pv2", null, null, "base", null,
                                            List.of(entity("E", exp("A", "T2.CODE", "string"))));

      Object result = controller.create(new CreateRequest(ds, otherView, null), bob);

      Failure failure = assertInstanceOf(Failure.class, result, String.valueOf(result));
      assertEquals(List.of("EXTENDED_VIEW_MISMATCH"), codes(failure));
      assertEquals("view", failure.errors().get(0).field());
      assertNull(fresh().getLogicalModel("base").getLogicalModel("child"));
   }

   @Test
   void extendedParentMissingIsNotFoundAndAnUnreadableParentLooksTheSame() throws Exception {
      Failure missing = (Failure) controller.create(new CreateRequest(
         ds, new Definition("child", null, null, null, null, "nothing", null,
                            List.of(ownMargin())), null), bob);
      doThrow(new inetsoft.sree.security.SecurityException("denied")).when(assetRepository)
         .checkAssetPermission(any(), any(AssetEntry.class), eq(ResourceAction.READ));
      Failure hidden = (Failure) controller.create(new CreateRequest(
         ds, child("child", ownMargin()), null), bob);

      assertEquals(WizDataModelWire.NOT_FOUND, missing.reason());
      assertEquals(WizDataModelWire.NOT_FOUND, hidden.reason());
      assertNull(hidden.existingKind());
      assertEquals(missing.message().replace("nothing", "base"), hidden.message(),
                   "the same text, nothing about the parent");
   }

   @Test
   void aVpmIsNotAParentAndItsKindIsNotRevealed() throws Exception {
      dataModel.addVirtualPrivateModel(new inetsoft.uql.erm.vpm.VirtualPrivateModel("rule"),
                                       true);
      when(dataSourceService.checkPermission(eq(ds), eq(ResourceAction.WRITE),
                                             any(java.security.Principal.class)))
         .thenReturn(false);

      Failure failure = (Failure) controller.create(new CreateRequest(
         ds, new Definition("child", null, null, null, null, "rule", null,
                            List.of(ownMargin())), null), bob);

      assertEquals(WizDataModelWire.NOT_FOUND, failure.reason());
      assertNull(failure.existingKind());
   }

   @Test
   void anExtendedNameIsUniqueUnderItsParent() throws Exception {
      createChild("child");

      Failure clash = (Failure) controller.create(new CreateRequest(
         ds, child("child", ownMargin()), null), bob);

      assertEquals(WizDataModelWire.DUPLICATE_NAME, clash.reason());
      assertEquals("logicalModel", clash.existingKind());
   }

   @Test
   void anExtendedModelInheritsItsBaseModelsDescription() throws Exception {
      Definition readBack = createChild("child");

      assertEquals("the base", readBack.description());
      Failure refused = (Failure) controller.create(new CreateRequest(
         ds, new Definition("other", "my own", null, null, null, "base", null,
                            List.of(ownMargin())), null), bob);
      assertEquals(List.of("BASE_ELEMENT_READ_ONLY"), codes(refused));
      assertEquals("description", refused.errors().get(0).field());
   }

   @Test
   void aFolderThatIsNotTheBaseModelsIsRefused() throws Exception {
      Definition elsewhere = new Definition("child", null, null, "Nope", null, "base", null,
                                            List.of(ownMargin()));

      Failure failure = (Failure) controller.create(new CreateRequest(ds, elsewhere, null), bob);

      assertEquals(List.of("EXTENDED_FOLDER_MISMATCH"), codes(failure));
   }

   // -- round trip, delete ----------------------------------------------------------------------------

   @Test
   void getThenUpdateOfAnExtendedModelIsANoOp() throws Exception {
      createChild("child");
      long before = fresh().getLogicalModel("base").getLogicalModel("child").getLastModified();
      reset(dependencyHandler, assetDataCache, transformHandler);

      Object result = update("child", getChild("child").definition(), null);

      assertInstanceOf(UpdateResponse.class, result, String.valueOf(result));
      assertEquals(before,
                   fresh().getLogicalModel("base").getLogicalModel("child").getLastModified());
      verifyNoInteractions(assetDataCache, transformHandler);
   }

   @Test
   void updateNeedsWriteOnTheExtendedModelPath() throws Exception {
      createChild("child");
      doThrow(new inetsoft.sree.security.SecurityException("denied")).when(assetRepository)
         .checkAssetPermission(any(), any(AssetEntry.class), eq(ResourceAction.WRITE));
      Definition read = getChild("child").definition();

      SecurityException e = assertThrows(SecurityException.class, () -> update(
         "child", editSales(read, "Margin", withDescription(
            attributeOf(entityOf(read, "Sales"), "Margin"), "x")), null));

      assertTrue(e.getMessage().contains(ds + "/F/child"), e.getMessage());
   }

   @Test
   void deleteBaseListsExtendedChildren() throws Exception {
      createChild("child");
      WizLogicalModelOps ops = new WizLogicalModelOps(modelService);

      OpResult result = ops.delete(new Ctx(ds, dataModel, bob),
                                   new ModelRef(WizModelKindOps.Kind.LOGICAL_MODEL, "base", null),
                                   false);

      assertTrue(result.ok(), result.message());
      assertEquals(List.of("base", "child"),
                   result.affected().stream().map(ModelRef::name).toList());
      assertEquals("base", result.affected().get(1).parent());
      assertNull(fresh().getLogicalModel("base"));
   }
}
