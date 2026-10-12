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
package inetsoft.web.wiz.worksheet;

import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.filter.ConditionGroup;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.ThreadContext;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.*;
import inetsoft.web.wiz.worksheet.model.WorksheetModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78394: the reported symptoms checked on rows. Every refusal leaves the table's rows as
 * they were, and every control (ONE_OF, explicit =, BETWEEN with two values or a literal + a
 * field, a field comparison, one DATE_IN per range OR'ed, a boolean column with no declared
 * type) still keeps the right rows of a mirror over embedded data, evaluated by a real
 * {@link AssetQuerySandbox}. The fixture's dates are relative to the current year, so
 * "Last year" / "This year" match the same rows in any year.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetConditionRows78394Test {
   private static final int Y = LocalDate.now().getYear();

   private final Principal agent = TestPrincipals.user("alice", "host-org");
   private Principal savedPrincipal;
   private Worksheet ws;
   private WorksheetEditService svc;

   @BeforeEach
   void setUp() throws Exception {
      savedPrincipal = ThreadContext.getContextPrincipal();
      ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(
         ws, "T", "State", "amt", "cap", "flag", "d");
      t.setEmbeddedData(new XEmbeddedTable(
         new String[]{ "string", "integer", "integer", "boolean", "date" },
         new Object[][]{
            { "State", "amt", "cap", "flag", "d" },
            { "NJ", 5, 10, true, date(Y - 1, 3) },
            { "NY", 10, 10, false, date(Y, 3) },
            { "NV", 15, 10, true, date(Y - 2, 3) },
            { "CA", 20, 10, false, date(Y - 1, 9) },
         }));
      ColumnSelection cs = t.getColumnSelection(false);
      String[] types = { XSchema.STRING, XSchema.INTEGER, XSchema.INTEGER, XSchema.BOOLEAN,
                         XSchema.DATE };

      for(int i = 0; i < types.length; i++) {
         ((ColumnRef) cs.getAttribute(i)).setDataType(types[i]);

         if(t.getColumnSelection(true).getAttribute(i) instanceof ColumnRef pub) {
            pub.setDataType(types[i]);
         }
      }

      ws.addAssembly(t);
      svc = service(ws);
      svc.apply("TOK", agent, ed -> ed.addMirror("M", "T"));
   }

   @AfterEach
   void restorePrincipal() {
      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   private WorksheetEditService service(Worksheet ws) throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      JoinSession s = new JoinSession("TOK", "Worksheet/ws1", "alice~;~host-org",
                                      SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = new AssetQuerySandbox(ws, null, new VariableTable());
      box.setActive(true);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.WORKSHEET), eq("Worksheet/ws1"),
                                            eq(agent))).thenReturn(rws);
      return new WorksheetEditService(sessions, runtimeAccess,
                                      mock(SheetAgentBroadcastService.class), securityEngine,
                                      mock(InnerJoinService.class));
   }

   private static java.sql.Date date(int year, int month) {
      return java.sql.Date.valueOf(LocalDate.of(year, month, 1));
   }

   /** The State values of M's rows, run through a fresh sandbox. */
   private List<String> rows() throws Exception {
      AssetQuerySandbox box = new AssetQuerySandbox(ws, null, new VariableTable());
      TableLens lens = box.getTableLens("M", AssetQuerySandbox.RUNTIME_MODE);
      int col = column(lens, "State");
      List<String> result = new ArrayList<>();

      for(int r = lens.getHeaderRowCount(); lens.moreRows(r); r++) {
         result.add((String) lens.getObject(r, col));
      }

      return result;
   }

   private static int column(TableLens lens, String header) {
      for(int c = 0; c < lens.getColCount(); c++) {
         if(header.equals(lens.getObject(0, c))) {
            return c;
         }
      }

      throw new AssertionError("no column " + header);
   }

   private static ConditionNode cond(String field, String op, String type, String... values) {
      return new ConditionNode(
         new ConditionSpec(field, op, Arrays.asList(values), false, type), null, 0);
   }

   private static ConditionNode specCond(String field, String op, List<String> values,
                                         ConditionValueSpec... specs)
   {
      return new ConditionNode(
         new ConditionSpec(field, op, values, false, null, List.of(specs)), null, 0);
   }

   private static ConditionNode or() {
      return new ConditionNode(null, new JunctionSpec("OR", 0), 0);
   }

   private void apply(ThrowingConsumer<WorksheetEditService.Editor> fn) throws Exception {
      svc.apply("TOK", agent, fn);
   }

   /** The call is refused naming {@code field}, and M's rows are unchanged. */
   private void refusedKeepsRows(ThrowingConsumer<WorksheetEditService.Editor> fn, String field)
      throws Exception
   {
      List<String> before = rows();
      Exception e = assertThrows(Exception.class, () -> apply(fn));
      assertTrue(e instanceof IllegalArgumentException || e instanceof PairingException,
                 e.toString());
      assertTrue(field == null || e.getMessage().contains("\"" + field + "\""), e.getMessage());
      assertEquals(before, rows(), "rows after the refusal of: " + e.getMessage());
   }

   @Test
   void fixtureSanity() throws Exception {
      assertEquals(List.of("NJ", "NY", "NV", "CA"), rows());
   }

   // item 1 -- DATE_IN takes one range; several ranges are one DATE_IN each, OR'ed

   @Test
   void dateInRefusalsKeepRowsAndOneRangePerConditionStillFilters() throws Exception {
      apply(ed -> ed.setConditions("M", List.of(cond("d", "DATE_IN", null, "Last year"))));
      assertEquals(List.of("NJ", "CA"), rows());

      refusedKeepsRows(ed -> ed.setConditions("M", List.of(
         cond("d", "DATE_IN", null, "Last year", "This year"))), "d");
      refusedKeepsRows(ed -> ed.setConditions("M", List.of(
         cond("d", "DATE_IN", null, "This year", "No such range"))), "d");
      refusedKeepsRows(ed -> ed.setConditions("M", List.of(specCond("d", "DATE_IN",
         List.of("This year"), new ConditionValueSpec("field", "d", null, null, null)))), "d");
      refusedKeepsRows(ed -> ed.editCondition("M", "d", "DATE_IN", "This year", "Last year"),
                       "d");
      refusedKeepsRows(ed -> ed.setMVConditions("M", List.of(
         cond("d", "DATE_IN", null, "Last year", "This year")), null, null, null, null), "d");

      apply(ed -> ed.setConditions("M", List.of(
         cond("d", "DATE_IN", null, "Last year"), or(),
         cond("d", "DATE_IN", null, "This year"))));
      assertEquals(List.of("NJ", "NY", "CA"), rows());

      apply(ed -> ed.setConditions("M", List.of()));
      refusedKeepsRows(ed -> ed.addFilter("M", "d", "DATE_IN", "Last year", "This year"), "d");
      apply(ed -> ed.addFilter("M", "d", "DATE_IN", "This year"));
      assertEquals(List.of("NY"), rows());
   }

   // item 2 -- add_filter / edit_condition value counts; set_conditions controls

   @Test
   void addFilterAndEditConditionCountsKeepRows() throws Exception {
      refusedKeepsRows(ed -> ed.addFilter("M", "amt", "BETWEEN", "10"), "amt");
      refusedKeepsRows(ed -> ed.addFilter("M", "amt", "BETWEEN", "5", "10", "20"), "amt");
      refusedKeepsRows(ed -> ed.addFilter("M", "State", "="), "State");
      assertEquals(4, rows().size());

      apply(ed -> ed.addFilter("M", "amt", "BETWEEN", "5", "10"));
      assertEquals(List.of("NJ", "NY"), rows());

      refusedKeepsRows(ed -> ed.editCondition("M", "amt", "BETWEEN", "15"), "amt");
      refusedKeepsRows(ed -> ed.editCondition("M", "amt", "BETWEEN", "10", "15", "20"), "amt");
      refusedKeepsRows(ed -> ed.editCondition("M", "amt", "="), "amt");

      apply(ed -> ed.editCondition("M", "amt", "BETWEEN", "10", "15"));
      assertEquals(List.of("NY", "NV"), rows());
   }

   @Test
   void setConditionsControlsAndValueSpecCounts() throws Exception {
      refusedKeepsRows(ed -> ed.setConditions("M", List.of(cond("amt", "BETWEEN", null, "10"))),
                       "amt");
      refusedKeepsRows(ed -> ed.setConditions("M", List.of(
         cond("amt", "BETWEEN", null, "5", "10", "20"))), "amt");
      refusedKeepsRows(ed -> ed.setConditions("M", List.of(cond("State", "=", null))), "State");

      ConditionValueSpec cap = new ConditionValueSpec("field", "cap", null, null, null);
      // literal low + field high: amt BETWEEN 5 AND cap(10)
      apply(ed -> ed.setConditions("M", List.of(specCond("amt", "BETWEEN", List.of("5"), cap))));
      assertEquals(List.of("NJ", "NY"), rows());
      // wsc-009-style field comparison: amt = cap
      apply(ed -> ed.setConditions("M", List.of(specCond("amt", "=", List.of(), cap))));
      assertEquals(List.of("NY"), rows());
      refusedKeepsRows(ed -> ed.setConditions("M", List.of(
         specCond("amt", "BETWEEN", List.of("1", "2"), cap))), "amt");
   }

   // item 3 -- a declared type cannot switch off the column type's checks

   @Test
   void declaredTypeOnBooleanColumn() throws Exception {
      refusedKeepsRows(ed -> ed.setConditions("M", List.of(cond("flag", "=", "string", "yes"))),
                       "flag");
      refusedKeepsRows(ed -> ed.setConditions("M", List.of(cond("flag", "=", "bool", "yes"))),
                       "flag");
      // control: no declared type (pre-existing refusal; its message does not name the field)
      refusedKeepsRows(ed -> ed.setConditions("M", List.of(cond("flag", "=", null, "yes"))),
                       null);
      refusedKeepsRows(ed -> ed.setMVConditions("M", List.of(cond("flag", "=", "string", "yes")),
                                                null, null, null, null), "flag");

      apply(ed -> ed.setConditions("M", List.of(cond("flag", "=", null, "true"))));
      assertEquals(List.of("NJ", "NV"), rows());
      apply(ed -> ed.setConditions("M", List.of(cond("flag", "=", "boolean", "false"))));
      assertEquals(List.of("NY", "CA"), rows());
   }

   // item 4 -- omitted / blank operation with two values; explicit = control

   @Test
   void omittedOperationWithTwoValues() throws Exception {
      refusedKeepsRows(ed -> ed.setConditions("M", List.of(cond("State", null, null, "NJ", "NY"))),
                       "State");
      refusedKeepsRows(ed -> ed.setConditions("M", List.of(cond("State", "", null, "NJ", "NY"))),
                       "State");
      refusedKeepsRows(ed -> ed.setConditions("M", List.of(cond("State", "=", null, "NJ", "NY"))),
                       "State");
      refusedKeepsRows(ed -> ed.setMVConditions("M", List.of(cond("State", null, null, "NJ", "NY")),
                                                null, null, null, null), "State");

      apply(ed -> ed.setConditions("M", List.of(cond("State", "ONE_OF", null, "NJ", "NY"))));
      assertEquals(List.of("NJ", "NY"), rows());
      apply(ed -> ed.setConditions("M", List.of(cond("State", "=", null, "NJ"))));
      assertEquals(List.of("NJ"), rows());
      apply(ed -> ed.setConditions("M", List.of(cond("State", null, null, "NY"))));
      assertEquals(List.of("NY"), rows());
   }

   // item 5 -- named groups (add_named_group / edit_named_group)

   /** The State values of M's rows that group {@code group} of named group {@code ng} keeps. */
   private List<String> groupRows(String ng, String group) throws Exception {
      NamedGroupInfo ngi = (NamedGroupInfo)
         ((NamedGroupAssembly) ws.getAssembly(ng)).getNamedGroupInfo();
      ConditionList cl = ngi.getGroupCondition(group);
      AssetQuerySandbox box = new AssetQuerySandbox(ws, null, new VariableTable());
      TableLens lens = box.getTableLens("M", AssetQuerySandbox.RUNTIME_MODE);
      ConditionGroup cg = new ConditionGroup(lens, cl);
      int col = column(lens, "State");
      List<String> result = new ArrayList<>();

      for(int r = lens.getHeaderRowCount(); lens.moreRows(r); r++) {
         if(cg.evaluate(lens, r)) {
            result.add((String) lens.getObject(r, col));
         }
      }

      return result;
   }

   private static GroupMapping group(String name, String op, String... values) {
      return new GroupMapping(name, List.of(values), op);
   }

   @Test
   void namedGroupLiteralsCountsAndControls() throws Exception {
      // boolean "yes" (was stored as false) refused; "true" kept and evaluated
      Exception e = assertThrows(Exception.class, () -> apply(ed -> ed.addNamedGroup(
         "BG", "M", "flag", null, List.of(group("Y", null, "yes")), false)));
      assertTrue(e.getMessage().contains("\"Y\"") && e.getMessage().contains("yes"),
                 e.getMessage());
      assertNull(ws.getAssembly("BG"));

      apply(ed -> ed.addNamedGroup("BG", "M", "flag", null,
                                   List.of(group("Y", null, "true")), false));
      assertEquals(List.of("NJ", "NV"), groupRows("BG", "Y"));

      e = assertThrows(Exception.class, () -> apply(ed -> ed.editNamedGroup(
         "BG", null, List.of(group("Y", null, "yes")), false)));
      assertTrue(e.getMessage().contains("yes"), e.getMessage());
      assertEquals(List.of("NJ", "NV"), groupRows("BG", "Y"));

      // DATE_IN group with two ranges refused; one range accepted and evaluated
      e = assertThrows(Exception.class, () -> apply(ed -> ed.addNamedGroup(
         "DG", "M", "d", null, List.of(group("LY", "DATE_IN", "Last year", "This year")),
         false)));
      assertTrue(e.getMessage().contains("\"LY\"") && e.getMessage().contains("got 2"),
                 e.getMessage());
      assertNull(ws.getAssembly("DG"));
      apply(ed -> ed.addNamedGroup("DG", "M", "d", null,
                                   List.of(group("LY", "DATE_IN", "Last year")), false));
      assertEquals(List.of("NJ", "CA"), groupRows("DG", "LY"));

      // groups are NOT held to filter arity: a 3-value = group ORs its values
      apply(ed -> ed.addNamedGroup("SG", "M", "State", null,
                                   List.of(group("East", "=", "NJ", "NY", "NV")), false));
      assertEquals(List.of("NJ", "NY", "NV"), groupRows("SG", "East"));
      apply(ed -> ed.editNamedGroup("SG", null,
                                    List.of(group("East", null, "NJ", "CA", "NV")), false));
      assertEquals(List.of("NJ", "NV", "CA"), groupRows("SG", "East"));

      // WSC-018 (5) shape: a standalone typed group (no table/column)
      e = assertThrows(Exception.class, () -> apply(ed -> ed.addNamedGroup(
         "NGY", null, null, "boolean", List.of(group("G", null, "yes")), false)));
      assertTrue(e.getMessage().contains("\"G\"") && e.getMessage().contains("yes"),
                 e.getMessage());
      e = assertThrows(Exception.class, () -> apply(ed -> ed.addNamedGroup(
         "NGD", null, null, "date", List.of(group("G", "DATE_IN", "Last year", "This year")),
         false)));
      assertTrue(e.getMessage().contains("got 2"), e.getMessage());
      apply(ed -> ed.addNamedGroup("NGT", null, null, "boolean",
                                   List.of(group("G", null, "true")), false));
      apply(ed -> ed.addNamedGroup("NGL", null, null, "date",
                                   List.of(group("G", "DATE_IN", "Last year")), false));
      assertNull(ws.getAssembly("NGY"));
      assertNull(ws.getAssembly("NGD"));
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      List<WorksheetModel.NamedGroupModel> groups = new WorksheetReadService().read(rws)
         .namedGroups();
      assertEquals(List.of("true"), groups.stream().filter(g -> "NGT".equals(g.name()))
         .findFirst().orElseThrow().groupMappings().get(0).values());
      WorksheetModel.GroupMappingModel lg = groups.stream()
         .filter(g -> "NGL".equals(g.name())).findFirst().orElseThrow().groupMappings().get(0);
      assertEquals(List.of("Last year"), lg.values());
      assertEquals("DATE_IN", lg.operation());

      // fixer's deviation: an empty per-value mapping (the "Others" fixture shape) is accepted
      apply(ed -> ed.addNamedGroup("OG", "M", "State", null,
                                   List.of(group("N", "STARTING_WITH", "N"),
                                           group("Others", null)), false));
      assertEquals(List.of("NJ", "NY", "NV"), groupRows("OG", "N"));
   }
}
