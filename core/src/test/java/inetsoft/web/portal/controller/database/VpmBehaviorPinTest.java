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
package inetsoft.web.portal.controller.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.*;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.web.binding.drm.AttributeRefModel;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.portal.model.database.events.*;
import org.apache.commons.io.FileExistsException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pins today's VPM behavior of {@link VPMController} (design 1.5 step 1, charter D6): create,
 * get, update, rename, delete, test, nested conditions, hidden columns, scripts, and the
 * name-mismatch overwrite of unknown (f). Written against the untouched controller; after the
 * {@code VpmService} extraction it must pass UNCHANGED, and the single approved change (the
 * name-consistency guard, reconcile H-A) flips only {@link #updateWithDifferentBodyNameOverwritesOtherVpm}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  VpmBehaviorPinTest.DependencyStorageConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@Tag("integration")
class VpmBehaviorPinTest {
   @Configuration
   static class DependencyStorageConfig {
      @Bean
      public DependencyStorageService dependencyStorageService() {
         return mock(DependencyStorageService.class);
      }
   }

   private static int count = 0;
   private final Principal bob = () -> "bob";
   private String ds;
   private DependencyHandler dependencyHandler;
   private RenameTransformHandler transformHandler;
   private DataSourceService dataSourceService;
   private XRepository repository;
   private VPMController controller;
   private boolean writeAllowed;

   @BeforeEach
   void setUp() throws Exception {
      DataSourceRegistry.getRegistry().init();
      ds = "vpmpin_" + (++count);
      XDataModel dataModel = new XDataModel(ds);
      XPartition view = new XPartition("pv");
      view.addTable("ORDERS", null, null);
      dataModel.addPartition(view);
      DataSourceRegistry.getRegistry().clearCache();
      writeAllowed = true;

      repository = mock(XRepository.class);
      when(repository.getDataModel(anyString())).thenAnswer(i -> new XDataModel(ds));
      dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(anyString(), any(ResourceAction.class),
                                             any(Principal.class)))
         .thenAnswer(i -> writeAllowed);
      when(dataSourceService.getModelAssetEntry(any())).thenAnswer(i -> i.getArgument(0));
      dependencyHandler = mock(DependencyHandler.class);
      transformHandler = mock(RenameTransformHandler.class);
      SecurityEngine security = mock(SecurityEngine.class);
      DataRefModelFactoryService factory = new DataRefModelFactoryService(
         List.of(new AttributeRefModel.AttributeRefModelFactory()));
      controller = new VPMController(
         factory, null, dataSourceService, repository, security, dependencyHandler,
         transformHandler, null, null);
   }

   private XDataModel fresh() {
      DataSourceRegistry.getRegistry().clearCache();
      return new XDataModel(ds);
   }

   // -- create -----------------------------------------------------------------------------

   @Test
   void createStoresConditionsHiddenColumnsAndScripts() throws Exception {
      VPMDefinition model = definition("区域规则");
      model.setDescription("only the east");
      model.setLookup("var x = 1;");
      Condition c = condition("EastOnly", 0, "ORDERS");
      c.setScript("// kept as written");
      c.setClauses(new ArrayList<>(List.of(
         clause(0, "=", field("ORDERS.REGION"), value("East")))));
      model.setConditions(new ArrayList<>(List.of(c)));
      model.setHidden(hidden(List.of("Analyst"), "hide();", hiddenRef("ORDERS", "COST")));

      create(model);

      VirtualPrivateModel vpm = fresh().getVirtualPrivateModel("区域规则");
      assertNotNull(vpm);
      assertEquals("only the east", vpm.getDescription());
      assertEquals("var x = 1;", vpm.getScript());
      VpmCondition stored = vpm.getCondition("EastOnly");
      assertEquals("// kept as written", stored.getScript());
      assertEquals(VpmCondition.TABLE, stored.getType());
      assertEquals("ORDERS", stored.getTable());
      assertEquals("=[ORDERS.REGION|East]", shape(stored.getCondition()));
      HiddenColumns hc = vpm.getHiddenColumns();
      assertEquals("hide();", hc.getScript());
      assertEquals(List.of("Analyst"), Collections.list(hc.getRoles()));
      List<DataRef> refs = Collections.list(hc.getHiddenColumns());
      assertEquals(1, refs.size());
      assertTrue(refs.get(0) instanceof AttributeRef);
      assertEquals("ORDERS", ((AttributeRef) refs.get(0)).getEntity());
      assertEquals("COST", ((AttributeRef) refs.get(0)).getAttribute());
      verify(dependencyHandler).updateVPMDependencies(any(), isNull(), eq(ds), any());
   }

   @Test
   void createRefusesExistingNameAndMissingWritePermission() throws Exception {
      create(definition("A"));
      assertThrows(FileExistsException.class, () -> create(definition("A")));

      writeAllowed = false;
      SecurityException denied =
         assertThrows(SecurityException.class, () -> create(definition("Z")));
      assertTrue(denied.getMessage().contains("Unauthorized access to resource \"" + ds + "\""));
      assertNull(fresh().getVirtualPrivateModel("Z"));
   }

   @Test
   void createOnMissingDatabaseIsNotFound() throws Exception {
      when(repository.getDataModel("nope")).thenReturn(null);
      SaveVpmEvent event = new SaveVpmEvent();
      event.setDatabase("nope");
      event.setModel(definition("A"));
      assertThrows(FileNotFoundException.class, () -> controller.addModel(event, bob));
   }

   // -- nested conditions ------------------------------------------------------------------

   @Test
   void nestedFlatListsBecomeTheTreeTheReaderExpects() throws Exception {
      // A(0) AND(0) B(1) OR(1) C(1)  ->  A and (B or C)
      assertStoredShape(
         List.of(clause(0, "=", field("T.A"), value("1")), conj(0, "and", false),
                 clause(1, "=", field("T.B"), value("2")), conj(1, "or", false),
                 clause(1, "=", field("T.C"), value("3"))),
         "and(=[T.A|1],or(=[T.B|2],=[T.C|3]))");
      // A(1) OR(1) B(1) AND(0) C(0)  ->  (A or B) and C
      assertStoredShape(
         List.of(clause(1, "=", field("T.A"), value("1")), conj(1, "or", false),
                 clause(1, "=", field("T.B"), value("2")), conj(0, "and", false),
                 clause(0, "=", field("T.C"), value("3"))),
         "and(or(=[T.A|1],=[T.B|2]),=[T.C|3])");
      // a negated group and a negated clause survive
      assertStoredShape(
         List.of(clause(0, "=", field("T.A"), value("1")), conj(0, "or", true),
                 clause(0, "=", field("T.B"), value("2"))),
         "or!(=[T.A|1],=[T.B|2])");
   }

   @Test
   void storedTreeReadsBackAsTheFlatListTheUiExpects() throws Exception {
      VPMDefinition model = definition("N");
      Condition c = condition("c", 0, "T");
      c.setClauses(new ArrayList<>(List.of(
         clause(0, "=", field("T.A"), value("1")), conj(0, "and", false),
         clause(1, "=", field("T.B"), value("2")), conj(1, "or", false),
         clause(1, "<>", field("T.C"), value("3")))));
      model.setConditions(new ArrayList<>(List.of(c)));
      create(model);

      VPMDefinition read = controller.getModel(ds, "N", bob);
      List<DataConditionItem> items = read.getConditions().get(0).getClauses();
      assertEquals(5, items.size());
      assertEquals(List.of(0, 0, 1, 1, 1),
                   items.stream().map(DataConditionItem::getLevel).toList());
      assertTrue(items.get(0) instanceof Clause);
      assertEquals("and", ((Conjunction) items.get(1)).getConjunction());
      assertEquals("or", ((Conjunction) items.get(3)).getConjunction());
      assertEquals("<>", ((Clause) items.get(4)).getOperation().getSymbol());
   }

   @Test
   void everyOperandTypeAndArityIsStored() throws Exception {
      VPMDefinition model = definition("O");
      Condition c = condition("c", 0, "T");
      Clause between = clause(0, "BETWEEN", field("T.A"), value("1"));
      between.setValue3(value("9"));
      c.setClauses(new ArrayList<>(List.of(
         between, conj(0, "and", false),
         clause(0, "IS NULL", field("T.B"), null), conj(0, "and", false),
         clause(0, "=", field("T.C"), new ClauseValue("Variable", "$(region)")), conj(0, "and", false),
         clause(0, "=", field("T.D"), new ClauseValue("Session Data", "$(_USER_)")),
         conj(0, "and", false),
         clause(0, "=", field("T.E"), new ClauseValue("Expression", "1 + 1")))));
      model.setConditions(new ArrayList<>(List.of(c)));
      create(model);

      VPMDefinition read = controller.getModel(ds, "O", bob);
      List<DataConditionItem> items = read.getConditions().get(0).getClauses();
      Clause variable = (Clause) items.get(4);
      assertEquals("Variable", variable.getValue2().getType());
      assertEquals("$(region)", variable.getValue2().getExpression());
      Clause session = (Clause) items.get(6);
      assertEquals("Session Data", session.getValue2().getType());
      Clause expression = (Clause) items.get(8);
      assertEquals("Expression", expression.getValue2().getType());
      assertEquals("BETWEEN", ((Clause) items.get(0)).getOperation().getSymbol());
      assertEquals("IS NULL", ((Clause) items.get(2)).getOperation().getSymbol());
   }

   // -- get --------------------------------------------------------------------------------

   @Test
   void getReadsEveryPartAndNeedsWriteOnTheSource() throws Exception {
      VPMDefinition model = definition("G");
      model.setDescription("d");
      model.setLookup("lookup();");
      Condition c = condition("script only", 1, "pv");
      c.setScript("cond();");
      c.setClauses(new ArrayList<>());
      model.setConditions(new ArrayList<>(List.of(c)));
      model.setHidden(hidden(List.of("R1", "R2"), null, hiddenRef("ORDERS", "COST")));
      create(model);

      VPMDefinition read = controller.getModel(ds, "G", bob);
      assertEquals("G", read.getName());
      assertEquals("d", read.getDescription());
      assertEquals("lookup();", read.getLookup());
      assertEquals(1, read.getConditions().size());
      assertEquals("cond();", read.getConditions().get(0).getScript());
      assertEquals(VpmCondition.PHYSICMODEL, read.getConditions().get(0).getType());
      assertEquals("pv", read.getConditions().get(0).getTableName());
      assertEquals(List.of("R1", "R2"), read.getHidden().getRoles());
      assertEquals(1, read.getHidden().getHiddens().size());
      assertEquals("ORDERS", ((AttributeRefModel) read.getHidden().getHiddens().get(0)).getEntity());

      writeAllowed = false;
      assertThrows(SecurityException.class, () -> controller.getModel(ds, "G", bob));
      writeAllowed = true;
      assertThrows(FileNotFoundException.class, () -> controller.getModel(ds, "missing", bob));
   }

   // -- update -----------------------------------------------------------------------------

   @Test
   void updateReplacesTheWholeModelAndKeepsTheCreator() throws Exception {
      VPMDefinition first = definition("U");
      first.setDescription("one");
      Condition c = condition("c1", 0, "ORDERS");
      c.setClauses(new ArrayList<>(List.of(clause(0, "=", field("ORDERS.A"), value("1")))));
      first.setConditions(new ArrayList<>(List.of(c)));
      create(first);

      VPMDefinition second = definition("U");
      second.setDescription("two");
      Condition c2 = condition("c2", 0, "ORDERS");
      c2.setClauses(new ArrayList<>(List.of(clause(0, ">", field("ORDERS.B"), value("5")))));
      second.setConditions(new ArrayList<>(List.of(c2)));
      update("U", second);

      VirtualPrivateModel stored = fresh().getVirtualPrivateModel("U");
      assertEquals("two", stored.getDescription());
      assertNull(stored.getCondition("c1"));
      assertEquals(">[ORDERS.B|5]", shape(stored.getCondition("c2").getCondition()));
      verify(dependencyHandler, times(2)).updateVPMDependencies(any(), any(), eq(ds), any());
      // the entry's created user and date are carried over to the saved entry
      verify(dataSourceService, atLeastOnce()).updateDataSourceAssetEntry(any());
   }

   @Test
   void updateOfAMissingModelOrWithoutWriteIsRefusedAndWritesNothing() throws Exception {
      create(definition("U"));
      VPMDefinition body = definition("U");
      body.setDescription("changed");

      assertThrows(FileNotFoundException.class, () -> update("nope", body));

      writeAllowed = false;
      assertThrows(SecurityException.class, () -> update("U", body));
      writeAllowed = true;
      assertNull(fresh().getVirtualPrivateModel("U").getDescription());
   }

   /**
    * Unknown (f), reconcile H-A. TODAY an update of A with a body named B removes A and silently
    * overwrites the existing B. The name-consistency guard flips this test in its own commit.
    */
   @Test
   void updateWithDifferentBodyNameOverwritesOtherVpm() throws Exception {
      VPMDefinition a = definition("A");
      a.setDescription("original A");
      create(a);
      VPMDefinition b = definition("B");
      b.setDescription("original B");
      create(b);

      VPMDefinition body = definition("B");
      body.setDescription("written through update of A");
      update("A", body);

      XDataModel reloaded = fresh();
      List<String> names = Arrays.asList(reloaded.getVirtualPrivateModelNames());
      assertFalse(names.contains("A"), "A is removed by the update of A");
      assertEquals("written through update of A",
                   reloaded.getVirtualPrivateModel("B").getDescription(),
                   "B is silently overwritten by the update of A");
   }

   // -- rename -----------------------------------------------------------------------------

   @Test
   void renameMovesTheModelAndOverwritesTheDescriptionWithTheArgument() throws Exception {
      VPMDefinition model = definition("Old");
      model.setDescription("stored description");
      create(model);

      RenameModelEvent event = new RenameModelEvent();
      event.setDatabase(ds);
      event.setOldName("Old");
      event.setNewName("New");
      event.setDescription(null);
      controller.renameModel(event, bob);

      XDataModel reloaded = fresh();
      assertNull(reloaded.getVirtualPrivateModel("Old"));
      VirtualPrivateModel renamed = reloaded.getVirtualPrivateModel("New");
      assertNotNull(renamed);
      // finding k: the Portal overwrites the description with the event's, even when it is null
      assertNull(renamed.getDescription());
      verify(transformHandler).addTransformTask(argThat(
         (RenameInfo info) -> info.getOldName().equals(ds + "/Old") &&
            info.getNewName().equals(ds + "/New") && info.getType() == RenameInfo.VPM));
      verify(dependencyHandler).updateDependencies(any(), any());
   }

   @Test
   void renameOfAMissingModelOrWithoutWriteIsRefused() throws Exception {
      RenameModelEvent event = new RenameModelEvent();
      event.setDatabase(ds);
      event.setOldName("nope");
      event.setNewName("New");
      assertThrows(FileNotFoundException.class, () -> controller.renameModel(event, bob));
      writeAllowed = false;
      assertThrows(SecurityException.class, () -> controller.renameModel(event, bob));
   }

   // -- delete -----------------------------------------------------------------------------

   @Test
   void removeDeletesTheNamedVpmsAndTheirDependencyRecords() throws Exception {
      create(definition("D1"));
      create(definition("D2"));
      create(definition("Keep"));
      clearInvocations(repository, dependencyHandler);

      RemoveDataModelEvent event = new RemoveDataModelEvent();
      event.setDatabase(ds);
      Vpm one = new Vpm();
      one.setName("D1");
      Vpm two = new Vpm();
      two.setName("D2");
      event.setItems(new ArrayList<>(List.of(one, two)));
      controller.removeModel(event, bob);

      assertEquals(List.of("Keep"), Arrays.asList(fresh().getVirtualPrivateModelNames()));
      verify(dependencyHandler, times(2)).deleteDependencies(any(AssetEntry.class));
      verify(repository).updateDataModel(any(XDataModel.class));
   }

   @Test
   void removeRefusesAMissingNameAndMissingWritePermission() throws Exception {
      RemoveDataModelEvent event = new RemoveDataModelEvent();
      event.setDatabase(ds);
      Vpm missing = new Vpm();
      missing.setName("nope");
      event.setItems(new ArrayList<>(List.of(missing)));
      assertThrows(FileNotFoundException.class, () -> controller.removeModel(event, bob));
      writeAllowed = false;
      assertThrows(SecurityException.class, () -> controller.removeModel(event, bob));
   }

   // -- test -------------------------------------------------------------------------------

   @Test
   void testPassesTheRoleOrUserToTheProcessorAndNeedsWrite() throws Exception {
      RecordingProcessor processor = new RecordingProcessor();
      Object old = installProcessor(processor);

      try {
         VPMDefinition model = definition("T");
         Condition c = condition("c", 0, "ORDERS");
         c.setClauses(new ArrayList<>(List.of(clause(0, "=", field("ORDERS.R"), value("E")))));
         model.setConditions(new ArrayList<>(List.of(c)));

         VpmTestEvent event = new VpmTestEvent();
         event.setDatabase(ds);
         event.setType("role");
         event.setName("Analyst");
         event.setVpm(model);
         assertEquals("SQL:T", controller.test(event, bob));
         assertEquals(1, processor.roles.length);
         assertEquals("Analyst", processor.roles[0].name);
         assertEquals(0, processor.groups.length);
         assertEquals("T", processor.vpm.getName());
         assertEquals("=[ORDERS.R|E]",
                      shape(processor.vpm.getCondition("c").getCondition()));
         // a test of a draft stores nothing
         assertNull(fresh().getVirtualPrivateModel("T"));

         writeAllowed = false;
         assertThrows(SecurityException.class, () -> controller.test(event, bob));
      }
      finally {
         restoreProcessor(old);
      }
   }

   @Test
   void testWrapsAProcessorFailureInAMessageException() throws Exception {
      RecordingProcessor processor = new RecordingProcessor();
      processor.failure = new IllegalStateException("boom");
      Object old = installProcessor(processor);

      try {
         VpmTestEvent event = new VpmTestEvent();
         event.setDatabase(ds);
         event.setType("role");
         event.setName("Analyst");
         event.setVpm(definition("T"));
         // a MessageException is swallowed (the result stays null), any other is wrapped
         inetsoft.util.MessageException ex = assertThrows(
            inetsoft.util.MessageException.class, () -> controller.test(event, bob));
         assertEquals("boom", ex.getMessage());
      }
      finally {
         restoreProcessor(old);
      }
   }

   // -- helpers ----------------------------------------------------------------------------

   private void create(VPMDefinition model) throws Exception {
      SaveVpmEvent event = new SaveVpmEvent();
      event.setDatabase(ds);
      event.setModel(model);
      controller.addModel(event, bob);
   }

   private void update(String name, VPMDefinition model) throws Exception {
      SaveVpmEvent event = new SaveVpmEvent();
      event.setDatabase(ds);
      event.setVpmName(name);
      event.setModel(model);
      controller.updateModel(event, bob);
   }

   private void assertStoredShape(List<DataConditionItem> items, String expected)
      throws Exception
   {
      String name = "S" + (++count);
      VPMDefinition model = definition(name);
      Condition c = condition("c", 0, "T");
      c.setClauses(new ArrayList<>(items));
      model.setConditions(new ArrayList<>(List.of(c)));
      create(model);
      assertEquals(expected,
                   shape(fresh().getVirtualPrivateModel(name).getCondition("c").getCondition()));
   }

   /** {@code and(=[l|r],or!(...))}: a set as relation (plus {@code !} when negated), a leaf as op and operands. */
   static String shape(XFilterNode node) {
      if(node instanceof XSet) {
         StringBuilder sb = new StringBuilder(((XSet) node).getRelation());

         if(node.isIsNot()) {
            sb.append('!');
         }

         sb.append('(');

         for(int i = 0; i < node.getChildCount(); i++) {
            if(i > 0) {
               sb.append(',');
            }

            sb.append(shape((XFilterNode) node.getChild(i)));
         }

         return sb.append(')').toString();
      }

      StringBuilder sb = new StringBuilder();

      if(node.isIsNot()) {
         sb.append('!');
      }

      String op = node instanceof XUnaryCondition ? ((XUnaryCondition) node).getOp() :
         node instanceof XBinaryCondition ? ((XBinaryCondition) node).getOp() :
         ((XTrinaryCondition) node).getOp();
      sb.append(op).append('[').append(node.getExpression1().getValue());

      if(node instanceof XBinaryCondition) {
         sb.append('|').append(((XBinaryCondition) node).getExpression2().getValue());
      }
      else if(node instanceof XTrinaryCondition) {
         sb.append('|').append(((XTrinaryCondition) node).getExpression2().getValue())
            .append('|').append(((XTrinaryCondition) node).getExpression3().getValue());
      }

      return sb.append(']').toString();
   }

   private static VPMDefinition definition(String name) {
      VPMDefinition model = new VPMDefinition();
      model.setName(name);
      model.setConditions(new ArrayList<>());
      return model;
   }

   private static Condition condition(String name, int type, String table) {
      Condition c = new Condition();
      c.setName(name);
      c.setType(type);
      c.setTableName(table);
      return c;
   }

   private static Clause clause(int level, String symbol, ClauseValue left, ClauseValue right) {
      Clause clause = new Clause();
      clause.setLevel(level);
      Operation op = new Operation();
      op.setSymbol(symbol);
      clause.setOperation(op);
      clause.setValue1(left);
      clause.setValue2(right);
      return clause;
   }

   private static Conjunction conj(int level, String relation, boolean not) {
      Conjunction conjunction = new Conjunction();
      conjunction.setLevel(level);
      conjunction.setConjunction(relation);
      conjunction.setIsNot(not);
      return conjunction;
   }

   private static ClauseValue field(String expression) {
      return new ClauseValue("Field", expression);
   }

   private static ClauseValue value(String expression) {
      return new ClauseValue("Value", expression);
   }

   /** What the Portal's own hidden-column picker sends (vpm-hidden-columns.component.ts). */
   private static DataRefModel hiddenRef(String table, String column) throws Exception {
      String json = "{\"classType\":\"AttributeRef\",\"name\":\"" + table + "." + column +
         "\",\"entity\":\"" + table + "\",\"attribute\":\"" + column + "\",\"caption\":\"" +
         table + "." + column + "\"}";
      return new ObjectMapper().readValue(json, DataRefModel.class);
   }

   private static HiddenColumnsModel hidden(List<String> roles, String script,
                                            DataRefModel... refs)
   {
      HiddenColumnsModel model = new HiddenColumnsModel();
      model.setRoles(new ArrayList<>(roles));
      model.setScript(script);
      model.setHiddens(new ArrayList<>(List.of(refs)));
      return model;
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
         throws Exception
      {
         if(failure != null) {
            throw failure;
         }

         this.user = user;
         this.groups = groups;
         this.roles = roles;
         this.vpm = vpm;
         return "SQL:" + vpm.getName();
      }

      IdentityID user;
      String[] groups;
      IdentityID[] roles;
      VirtualPrivateModel vpm;
      Exception failure;
   }
}
