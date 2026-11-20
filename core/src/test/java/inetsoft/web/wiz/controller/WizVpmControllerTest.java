/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Role;
import inetsoft.sree.security.User;
import inetsoft.test.*;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.web.portal.controller.database.VpmService;
import inetsoft.web.portal.model.database.VPMDefinition;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The four VPM routes of the bridge over the real {@code VpmService} and a real registry: gates,
 * shared-namespace uniqueness, validation, dry runs that write nothing, whole-definition update
 * with omitted keys kept, the name guard, read-back (C2), and {@code test} refusing an unknown
 * user or role before the processor runs.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  WizVpmTestSupport.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@Tag("integration")
class WizVpmControllerTest {
   private WizVpmTestSupport support;
   private WizVpmController controller;

   @BeforeEach
   void setUp() throws Exception {
      support = new WizVpmTestSupport();
      XDataModel dataModel = support.fresh();
      XLogicalModel lm = new XLogicalModel("lm");
      lm.setPartition("pv");
      dataModel.addLogicalModel(lm);
      controller = support.controller();
   }

   // -- create ------------------------------------------------------------------------------

   @Test
   void createStoresAChineseNamedVpmAndReadsItBack() throws Exception {
      Map<String, Object> definition = definition("区域规则");
      definition.put("description", "东区");
      definition.put("lookup", "var 区域 = 'East';");
      definition.put("conditions", List.of(condition("东区", clause("ORDERS.REGION", "=", "东"))));

      Object result = create(definition, false);

      WizVpmWire.WriteResponse response = assertInstanceOf(WizVpmWire.WriteResponse.class, result);
      assertTrue(response.ok());
      assertEquals("区域规则", response.name());
      assertEquals(support.mapper.normalize(definition), support.mapper.normalize(response.definition()));
      assertNotNull(support.fresh().getVirtualPrivateModel("区域规则"));
      assertEquals("var 区域 = 'East';",
                   support.fresh().getVirtualPrivateModel("区域规则").getScript());
   }

   @Test
   void createDryRunValidatesAndWritesNothing() throws Exception {
      Map<String, Object> definition = definition("Dry");
      definition.put("conditions", List.of(condition("c", clause("ORDERS.REGION", "=", "East"))));

      WizVpmWire.WriteResponse response = assertInstanceOf(
         WizVpmWire.WriteResponse.class, create(definition, true));

      assertTrue(response.ok());
      assertNull(response.definition());
      assertEquals(List.of(
         "TARGET_UNVERIFIED: conditions[c].target.name: table \"ORDERS\" cannot be verified " +
            "without reading the database metadata"), response.warnings());
      assertNull(support.fresh().getVirtualPrivateModel("Dry"));

      // and a dry run of an invalid definition is refused like the real call
      Map<String, Object> invalid = definition("Dry2");
      invalid.put("conditions", List.of(condition("c", clause("ORDERS.REGION", "nope", "East"))));
      assertEquals("INVALID", failure(create(invalid, true)).reason());
   }

   @Test
   void aNameTakenByAnyKindIsDuplicateAndNamesTheKind() throws Exception {
      create(definition("Taken"), false);

      for(String[] pair : new String[][] { { "Taken", "vpm" }, { "lm", "logicalModel" },
                                           { "pv", "physicalView" } })
      {
         WizDataModelWire.Failure failure = failure(create(definition(pair[0]), false));
         assertEquals("DUPLICATE_NAME", failure.reason(), pair[0]);
         assertEquals(pair[1], failure.existingKind());
         assertTrue(failure.message().contains("a " + pair[1] + " named \"" + pair[0] +
                                                  "\" already exists"), failure.message());
      }

      assertEquals(List.of("Taken"), Arrays.asList(support.fresh().getVirtualPrivateModelNames()));
   }

   @Test
   void createRefusesABadNameWithA400AndAnInvalidDefinitionWithTheErrors() throws Exception {
      for(String bad : new String[] { "a/b", "x:y", " padded", "Domain", "" }) {
         assertThrows(IllegalArgumentException.class, () -> create(definition(bad), false), bad);
      }

      Map<String, Object> invalid = definition("Bad");
      invalid.put("conditions", List.of(
         condition("c1", clause("ORDERS.A", "frob", "1")),
         condition("c2", clause("ORDERS.A", "=", "$(x)"))));
      WizDataModelWire.Failure failure = failure(create(invalid, false));
      assertEquals("INVALID", failure.reason());
      assertEquals("conditions[c1].tree.op", failure.field());
      assertEquals(2, failure.errors().size());
      assertEquals("OPERATION_INVALID", failure.errors().get(0).code());
      assertEquals("OPERAND_TYPE_INVALID", failure.errors().get(1).code());
      assertNull(support.fresh().getVirtualPrivateModel("Bad"));
   }

   @Test
   void aServerThatDropsAFieldIsReportedNotReturnedAsSuccess() throws Exception {
      VpmService dropping = spy(support.service);
      doAnswer(inv -> {
         VPMDefinition definition = inv.getArgument(1);
         definition.setDescription(null);
         return inv.callRealMethod();
      }).when(dropping).create(anyString(), any(VPMDefinition.class), any());
      WizVpmController dropper = new WizVpmController(
         support.repository, support.dataSourceService,
         mock(inetsoft.web.portal.controller.database.LogicalModelService.class), dropping,
         support.factory, support.securityEngine);
      Map<String, Object> definition = definition("Dropped");
      definition.put("description", "must survive");

      WizDataModelWire.Failure failure = failure(dropper.create(
         new WizVpmWire.WriteRequest(support.ds, null, definition, false), support.bob));

      assertEquals("READ_BACK_FAILED", failure.reason());
      assertTrue(failure.message().contains("definition.description"), failure.message());
   }

   @Test
   void aOneItemGroupIsRefusedByCreateAndUpdateNamingTheTreePath() throws Exception {
      // P4 D-4: the server refuses it itself, whatever the client did
      Map<String, Object> one = group("and", clause("ORDERS.REGION", "=", "East"));
      Map<String, Object> nested = group("or", clause("ORDERS.A", "=", "1"),
                                         group("and", clause("ORDERS.B", "=", "2")));
      Map<String, Object> definition = definition("OneItem");
      definition.put("conditions", List.of(condition("EastOnly", one)));

      WizDataModelWire.Failure failure = failure(create(definition, false));
      assertEquals("INVALID", failure.reason());
      assertEquals("conditions[EastOnly].tree.items", failure.field());
      assertEquals("TREE_SHAPE_INVALID", failure.errors().get(0).code());
      assertNull(support.fresh().getVirtualPrivateModel("OneItem"));

      definition.put("conditions", List.of(condition("EastOnly", nested)));
      assertEquals("conditions[EastOnly].tree.items[1].items",
                   failure(create(definition, false)).field());

      create(definition("Ok"), false);
      Map<String, Object> change = new LinkedHashMap<>();
      change.put("conditions", List.of(condition("EastOnly", one)));
      assertEquals("conditions[EastOnly].tree.items", failure(controller.update(
         new WizVpmWire.WriteRequest(support.ds, "Ok", change, false), support.bob)).field());
      assertEquals(0, support.fresh().getVirtualPrivateModel("Ok").getConditions()
                      .hasMoreElements() ? 1 : 0);
   }

   // -- get ---------------------------------------------------------------------------------

   @Test
   void getReturnsTheDefinitionAndRefusesWrongKindMissingAndOtherSourceTypes() throws Exception {
      Map<String, Object> definition = definition("G");
      definition.put("conditions", List.of(condition("c", clause("ORDERS.A", ">", "5"))));
      create(definition, false);

      WizVpmWire.GetResponse response = assertInstanceOf(WizVpmWire.GetResponse.class,
         controller.get(new WizVpmWire.GetRequest(support.ds, "G"), support.bob));
      assertTrue(response.ok());
      assertNull(response.folder());
      assertFalse(response.readOnly());
      assertEquals(support.mapper.normalize(definition), support.mapper.normalize(response.definition()));

      assertEquals("NOT_FOUND", failure(controller.get(
         new WizVpmWire.GetRequest(support.ds, "missing"), support.bob)).reason());
      WizDataModelWire.Failure wrong = failure(controller.get(
         new WizVpmWire.GetRequest(support.ds, "lm"), support.bob));
      assertEquals("WRONG_KIND", wrong.reason());
      assertEquals("logicalModel", wrong.existingKind());

      when(support.repository.getDataSource("tab")).thenReturn(
         mock(inetsoft.uql.tabular.TabularDataSource.class));
      assertEquals("NOT_JDBC", failure(controller.get(
         new WizVpmWire.GetRequest("tab", "x"), support.bob)).reason());
      when(support.repository.getDataSource("gone")).thenReturn(null);
      assertEquals("NOT_FOUND", failure(controller.get(
         new WizVpmWire.GetRequest("gone", "x"), support.bob)).reason());

      assertThrows(IllegalArgumentException.class,
                   () -> controller.get(new WizVpmWire.GetRequest(support.ds, " "), support.bob));
   }

   // -- update ------------------------------------------------------------------------------

   @Test
   void aGetResultFedBackToUpdateChangesNothing() throws Exception {
      Map<String, Object> definition = definition("RT");
      definition.put("description", "d");
      definition.put("lookup", "l();");
      definition.put("conditions", List.of(
         condition("a", group("or", clause("T.A", "=", "1"), clause("T.B", "IS NULL", null))),
         condition("b", clause("T.C", "LIKE", "%x%"))));
      Map<String, Object> hidden = new LinkedHashMap<>();
      hidden.put("roles", List.of("R"));
      hidden.put("columns", List.of(column("T", "SECRET")));
      definition.put("hidden", hidden);
      create(definition, false);
      WizVpmWire.GetResponse got = assertInstanceOf(WizVpmWire.GetResponse.class,
         controller.get(new WizVpmWire.GetRequest(support.ds, "RT"), support.bob));

      WizVpmWire.WriteResponse response = assertInstanceOf(WizVpmWire.WriteResponse.class,
         controller.update(new WizVpmWire.WriteRequest(
            support.ds, "RT", got.definition(), false), support.bob));

      assertTrue(response.ok());
      assertEquals(got.definition(), response.definition());
   }

   @Test
   void updateReplacesTheConditionsAndKeepsOmittedKeys() throws Exception {
      Map<String, Object> definition = definition("U");
      definition.put("description", "kept");
      definition.put("lookup", "kept();");
      definition.put("conditions", List.of(condition("old", clause("T.A", "=", "1"))));
      create(definition, false);

      Map<String, Object> change = new LinkedHashMap<>();
      change.put("conditions", List.of(condition("new", clause("T.B", ">", "2"))));
      WizVpmWire.WriteResponse response = assertInstanceOf(WizVpmWire.WriteResponse.class,
         controller.update(new WizVpmWire.WriteRequest(support.ds, "U", change, false),
                           support.bob));

      assertTrue(response.ok());
      VirtualPrivateModel stored = support.fresh().getVirtualPrivateModel("U");
      assertEquals("kept", stored.getDescription());
      assertEquals("kept();", stored.getScript());
      assertNull(stored.getCondition("old"));
      assertNotNull(stored.getCondition("new"));

      // an explicit null clears
      Map<String, Object> clear = new LinkedHashMap<>();
      clear.put("lookup", null);
      controller.update(new WizVpmWire.WriteRequest(support.ds, "U", clear, false), support.bob);
      assertNull(support.fresh().getVirtualPrivateModel("U").getScript());
   }

   @Test
   void updateDryRunListsDependentsAndWritesNothing() throws Exception {
      create(definition("D"), false);
      Map<String, Object> change = new LinkedHashMap<>();
      change.put("description", "would change");

      WizVpmWire.WriteResponse response = assertInstanceOf(WizVpmWire.WriteResponse.class,
         controller.update(new WizVpmWire.WriteRequest(support.ds, "D", change, true),
                           support.bob));

      assertTrue(response.ok());
      assertNull(response.definition());
      assertNotNull(response.dependents());
      assertNull(support.fresh().getVirtualPrivateModel("D").getDescription());
   }

   @Test
   void updateWithADifferentBodyNameIsRefusedAndNothingChanges() throws Exception {
      Map<String, Object> a = definition("A");
      a.put("description", "original A");
      create(a, false);
      Map<String, Object> b = definition("B");
      b.put("description", "original B");
      create(b, false);

      Map<String, Object> body = definition("B");
      body.put("description", "written through update of A");
      WizDataModelWire.Failure failure = failure(controller.update(
         new WizVpmWire.WriteRequest(support.ds, "A", body, false), support.bob));

      assertEquals("INVALID", failure.reason());
      assertEquals("definition.name", failure.field());
      assertTrue(failure.message().contains("renaming goes through rename_data_model"),
                 failure.message());
      assertEquals("original A", support.fresh().getVirtualPrivateModel("A").getDescription());
      assertEquals("original B", support.fresh().getVirtualPrivateModel("B").getDescription());
   }

   @Test
   void updateOfAMissingOrOtherKindIsRefused() throws Exception {
      assertEquals("NOT_FOUND", failure(controller.update(new WizVpmWire.WriteRequest(
         support.ds, "missing", definition("missing"), false), support.bob)).reason());
      assertEquals("WRONG_KIND", failure(controller.update(new WizVpmWire.WriteRequest(
         support.ds, "pv", definition("pv"), false), support.bob)).reason());
   }

   // -- permissions -------------------------------------------------------------------------

   @Test
   void everyRouteNeedsWriteOnTheSourceReadsIncluded() throws Exception {
      create(definition("P"), false);
      support.writeAllowed = false;

      assertDenied(() -> controller.get(new WizVpmWire.GetRequest(support.ds, "P"), support.bob));
      assertDenied(() -> create(definition("Q"), false));
      assertDenied(() -> controller.update(new WizVpmWire.WriteRequest(
         support.ds, "P", definition("P"), false), support.bob));
      assertDenied(() -> controller.test(new WizVpmWire.TestRequest(
         support.ds, "P", null, "role", "R"), support.bob));

      support.writeAllowed = true;
      assertNull(support.fresh().getVirtualPrivateModel("Q"));
   }

   private void assertDenied(org.junit.jupiter.api.function.Executable call) {
      Exception denied = assertThrows(Exception.class, call);
      assertTrue(denied instanceof SecurityException ||
                    denied instanceof inetsoft.sree.security.SecurityException,
                 denied.toString());
      assertTrue(denied.getMessage().contains("Unauthorized access to resource \"" + support.ds +
                                                 "\" by user bob"), denied.getMessage());
   }

   // -- test --------------------------------------------------------------------------------

   @Test
   void testRefusesAnUnknownUserOrRoleBeforeRunningAndRunsAKnownOne() throws Exception {
      RecordingProcessor processor = new RecordingProcessor();
      Object old = installProcessor(processor);

      try {
         create(definition("T"), false);
         when(support.securityProvider.getUser(any(IdentityID.class))).thenReturn(null);
         when(support.securityProvider.getRole(any(IdentityID.class))).thenReturn(null);

         WizDataModelWire.Failure noUser = failure(controller.test(new WizVpmWire.TestRequest(
            support.ds, "T", null, "user", "ghost"), support.bob));
         assertEquals("INVALID", noUser.reason());
         assertEquals("principalName", noUser.field());
         assertTrue(noUser.message().startsWith("no user \"ghost\" in the current organization"),
                    noUser.message());
         assertEquals("PRINCIPAL_NOT_FOUND", noUser.errors().get(0).code());
         assertEquals("no role \"ghost\" in the current organization", failure(
            controller.test(new WizVpmWire.TestRequest(
               support.ds, "T", null, "ROLE", "ghost"), support.bob)).message());
         assertEquals(0, processor.calls);

         when(support.securityProvider.getRole(any(IdentityID.class))).thenReturn(mock(Role.class));
         WizVpmWire.TestResponse response = assertInstanceOf(WizVpmWire.TestResponse.class,
            controller.test(new WizVpmWire.TestRequest(
               support.ds, "T", null, "role", "Analyst"), support.bob));
         assertTrue(response.ok());
         assertEquals("SQL:T", response.result());
         assertEquals(1, processor.calls);

         when(support.securityProvider.getUser(any(IdentityID.class))).thenReturn(mock(User.class));
         controller.test(new WizVpmWire.TestRequest(
            support.ds, "T", null, "user", "alice"), support.bob);
         assertEquals(2, processor.calls);
      }
      finally {
         restoreProcessor(old);
      }
   }

   @Test
   void testOfADraftValidatesItAndStoresNothing() throws Exception {
      RecordingProcessor processor = new RecordingProcessor();
      Object old = installProcessor(processor);

      try {
         when(support.securityProvider.getRole(any(IdentityID.class))).thenReturn(mock(Role.class));
         Map<String, Object> draft = new LinkedHashMap<>();
         draft.put("conditions", List.of(condition("c", clause("ORDERS.R", "=", "E"))));

         WizVpmWire.TestResponse response = assertInstanceOf(WizVpmWire.TestResponse.class,
            controller.test(new WizVpmWire.TestRequest(
               support.ds, null, draft, "role", "Analyst"), support.bob));
         assertEquals("SQL:(draft)", response.result());
         assertEquals(List.of(), Arrays.asList(support.fresh().getVirtualPrivateModelNames()));

         draft.put("conditions", List.of(condition("c", clause("ORDERS.R", "nope", "E"))));
         assertEquals("INVALID", failure(controller.test(new WizVpmWire.TestRequest(
            support.ds, null, draft, "role", "Analyst"), support.bob)).reason());
         assertEquals(1, processor.calls);
      }
      finally {
         restoreProcessor(old);
      }
   }

   @Test
   void testNeedsExactlyOneOfNameAndDefinitionAndAValidPrincipalType() throws Exception {
      assertThrows(IllegalArgumentException.class, () -> controller.test(
         new WizVpmWire.TestRequest(support.ds, null, null, "role", "R"), support.bob));
      assertThrows(IllegalArgumentException.class, () -> controller.test(
         new WizVpmWire.TestRequest(support.ds, "T", definition("T"), "role", "R"), support.bob));
      assertThrows(IllegalArgumentException.class, () -> controller.test(
         new WizVpmWire.TestRequest(support.ds, "T", null, "group", "R"), support.bob));
      assertThrows(IllegalArgumentException.class, () -> controller.test(
         new WizVpmWire.TestRequest(support.ds, "T", null, "role", " "), support.bob));
   }

   // -- helpers -----------------------------------------------------------------------------

   private Object create(Map<String, Object> definition, boolean dryRun) throws Exception {
      return controller.create(new WizVpmWire.WriteRequest(support.ds, null, definition, dryRun),
                               support.bob);
   }

   private static WizDataModelWire.Failure failure(Object result) {
      return assertInstanceOf(WizDataModelWire.Failure.class, result);
   }

   private static Map<String, Object> definition(String name) {
      Map<String, Object> definition = new LinkedHashMap<>();
      definition.put("name", name);
      definition.put("conditions", new ArrayList<>());
      return definition;
   }

   private static Map<String, Object> condition(String name, Map<String, Object> tree) {
      Map<String, Object> condition = new LinkedHashMap<>();
      condition.put("name", name);
      Map<String, Object> target = new LinkedHashMap<>();
      target.put("kind", "table");
      target.put("name", "ORDERS");
      condition.put("target", target);
      condition.put("tree", tree);
      return condition;
   }

   private static Map<String, Object> clause(String field, String op, String value) {
      Map<String, Object> clause = new LinkedHashMap<>();
      Map<String, Object> left = new LinkedHashMap<>();
      left.put("type", "field");
      left.put("expression", field);
      clause.put("left", left);
      clause.put("op", op);

      if(value != null) {
         Map<String, Object> right = new LinkedHashMap<>();
         right.put("type", "value");
         right.put("expression", value);
         clause.put("right", right);
      }

      return clause;
   }

   private static Map<String, Object> group(String op, Object... items) {
      Map<String, Object> group = new LinkedHashMap<>();
      group.put("op", op);
      group.put("items", new ArrayList<>(Arrays.asList(items)));
      return group;
   }

   private static Map<String, Object> column(String table, String column) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("table", table);
      map.put("column", column);
      return map;
   }

   private static Field processorField() throws Exception {
      Field field = VpmProcessor.class.getDeclaredField("processor");
      field.setAccessible(true);
      return field;
   }

   private static Object installProcessor(VpmProcessor processor) throws Exception {
      Field field = processorField();
      Object old = field.get(null);
      field.set(null, processor);
      return old;
   }

   private static void restoreProcessor(Object old) throws Exception {
      processorField().set(null, old);
   }

   private static final class RecordingProcessor extends VpmProcessor {
      @Override
      public String testVPM(IdentityID user, String[] groups, IdentityID[] roles, String orgID,
                            XDataModel dataModel, inetsoft.util.Catalog catalog,
                            VirtualPrivateModel vpm)
      {
         calls++;
         return "SQL:" + vpm.getName();
      }

      int calls;
   }
}
