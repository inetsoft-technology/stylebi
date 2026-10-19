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

import inetsoft.mv.MVManager;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.*;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.AggregateSpec;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.GroupSpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78790 item 3 (WBS-140): an identical {@code set_group_aggregate} resend with two
 * aggregates on one source column was refused because the generated secondary column
 * ({@code qty_1}) was regenerated under a new name ({@code qty_2}, ...) on every apply, so the
 * downstream-loss guards saw the column a dependent consumes as lost. An unchanged secondary now
 * keeps its column, found through the {@code wiz.aggregate.secondary.columns} provenance record.
 *
 * <p>Applies run through the real {@code apply} path (so every successful op also runs the real
 * refresh), and "preview" is the real {@link WorksheetPreviewService}, which installs a deep clone
 * of every table's AggregateInfo. A refusal is checked by comparing the whole table state,
 * including the provenance record, before and after.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  WorksheetSecondaryAggregateStable78790Test.CacheConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetSecondaryAggregateStable78790Test {
   @Configuration
   static class CacheConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }

      /** Needed by the real preview path. */
      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }
   }

   private static final List<AggregateSpec> AB =
      List.of(agg("qty", "MIN", "a"), agg("qty", "MAX", "b"));
   private static final List<AggregateSpec> UNALIASED =
      List.of(agg("qty", "MIN", null), agg("qty", "MAX", null));

   private final Principal agent = TestPrincipals.user("alice", "host-org");
   private final WorksheetPreviewService previews = new WorksheetPreviewService();
   private Worksheet ws;
   private RuntimeWorksheet rws;
   private WorksheetEditService svc;

   private void build() throws Exception {
      ws = new Worksheet();
      ws.addAssembly(orders(ws, "U"));
      ws.addAssembly(orders(ws, "Z"));
      svc = service(ws);
   }

   // ---- identical resend ------------------------------------------------------------------

   @Test
   void aliasedIdenticalResendKeepsTheColumn() throws Exception {
      build();
      set("U", AB);
      dep("DEP", "U", "b");
      String initial = state("U");

      assertAccepted(() -> set("U", AB));
      assertAccepted(() -> set("U", AB));
      assertEquals(initial, state("U"));
      assertEquals("U.b", depInput("DEP"));
      assertTrue(initial.contains("qty_1{b}"), initial);
   }

   @Test
   void aliasedIdenticalResendAfterRealPreviewIsAccepted() throws Exception {
      build();
      set("U", AB);
      dep("DEP", "U", "b");
      String initial = state("U");

      for(int i = 0; i < 2; i++) {
         previewAll();
         assertAccepted(() -> set("U", AB));
         assertEquals(initial, state("U"));
      }

      assertEquals("U.b", depInput("DEP"));
   }

   @Test
   void unaliasedIdenticalResendWithDependentAfterRealPreviewIsAccepted() throws Exception {
      build();
      set("U", UNALIASED);
      dep("DEP", "U", "qty_1");
      String initial = state("U");

      assertAccepted(() -> set("U", UNALIASED));
      previewAll();
      assertAccepted(() -> set("U", UNALIASED));
      assertEquals(initial, state("U"));
      assertEquals("U.qty_1", depInput("DEP"));
   }

   @Test
   void joinKeyedIdenticalResendAfterRealPreviewIsAccepted() throws Exception {
      joinSetup();
      String initial = state("U");

      assertAccepted(() -> set("U", AB));
      previewAll();
      assertAccepted(() -> set("U", AB));
      assertEquals(initial, state("U"));
   }

   @Test
   void joinKeyRealiasIsStillHardRefusedAndMutatesNothing() throws Exception {
      joinSetup();
      String before = state("U");
      List<AggregateSpec> realias = List.of(agg("qty", "MIN", "a"), agg("qty", "MAX", "c"));

      String refused = refusal(() -> set("U", realias));
      assertTrue(refused != null && refused.contains("join key"), refused);
      refused = refusal(() -> set("U", realias, true));
      assertTrue(refused != null && refused.contains("join key"), refused);
      assertEquals(before, state("U"));
   }

   @Test
   void provenanceRecordSurvivesSaveAndReopen() throws Exception {
      build();
      set("U", AB);
      dep("DEP", "U", "b");
      String initial = state("U");

      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      ws.writeXML(writer);
      writer.flush();
      Worksheet reopened = new Worksheet();
      reopened.parseXML(parseXmlString(out.toString()));
      ws = reopened;
      svc = service(ws);

      assertTrue(initial.contains("prop=qty_1"), initial);
      assertEquals(initial, state("U"));
      assertAccepted(() -> set("U", AB));
      assertEquals(initial, state("U"));
   }

   // ---- what is never reused --------------------------------------------------------------

   @Test
   void userColumnNamedByTheCallIsNotTaken() throws Exception {
      build();
      addUserColumn("X");
      set("U", List.of(agg("X", "SUM", "xs")));
      set("U", List.of(agg("X", "SUM", "xs"), agg("qty", "MIN", null), agg("qty", "MAX", null)));

      String s = state("U");
      assertTrue(s.contains("Sum:X{xs}"), s);
      assertTrue(s.contains("Min:qty"), s);
      assertTrue(s.contains("Max:qty_1"), s);
   }

   @Test
   void userColumnNotNamedByTheCallIsNotTaken() throws Exception {
      build();
      addUserColumn("X");
      set("U", List.of(agg("X", "SUM", "xs"), agg("qty", "MIN", null)));
      set("U", UNALIASED);

      String s = state("U");
      assertTrue(s.contains("Max:qty_1"), s);
      assertFalse(s.contains("Max:X"), s);
   }

   @Test
   void unaliasedUserColumnWithTheSameFormulaIsNotTaken() throws Exception {
      build();
      addUserColumn("X");
      set("U", List.of(agg("X", "MAX", null), agg("qty", "MIN", null)));
      set("U", UNALIASED);

      String s = state("U");
      assertTrue(s.contains("Max:qty_1"), s);
      assertFalse(s.contains("Max:X"), s);
   }

   @Test
   void callNamingTheGeneratedColumnExcludesItFromReuse() throws Exception {
      build();
      set("U", UNALIASED);
      set("U", List.of(agg("qty", "MIN", null), agg("qty", "MAX", null),
                       agg("qty_1", "SUM", null)));

      String s = state("U");
      assertFalse(s.contains("Max:qty_1"), s);
      assertTrue(s.contains("Sum:qty_1"), s);
   }

   // ---- identity changes are refused, not silently re-bound -------------------------------

   @Test
   void unaliasedFormulaSwapIsRefusedNamingTheDependent() throws Exception {
      build();
      set("U", UNALIASED);
      dep("DEP", "U", "qty_1");
      String before = state("U");
      List<AggregateSpec> swapped = List.of(agg("qty", "MAX", null), agg("qty", "MIN", null));

      assertRefusedNaming("DEP", refusal(() -> set("U", swapped)));
      assertEquals(before, state("U"));
      assertEquals("U.qty_1", depInput("DEP"));

      // Only an explicit confirmation applies it, and the name then follows the formula.
      assertAccepted(() -> set("U", swapped, true));
      assertTrue(state("U").contains("Max:qty,Min:qty_2"), state("U"));
   }

   @Test
   void aliasedSwapIsRefusedAndMutatesNothing() throws Exception {
      build();
      set("U", AB);
      dep("DEP", "U", "b");
      String before = state("U");

      assertRefusedNaming("DEP", refusal(() -> set(
         "U", List.of(agg("qty", "MAX", "b"), agg("qty", "MIN", "a")))));
      assertEquals(before, state("U"));
   }

   @Test
   void lossyResendIsRefusedNamingTheDependentThenIdenticalResendIsAccepted() throws Exception {
      build();
      set("U", AB);
      dep("DEP", "U", "b");
      previewAll();
      String before = state("U");

      assertRefusedNaming("DEP", refusal(() -> set("U", List.of(agg("qty", "MIN", "a")))));
      assertEquals(before, state("U"));

      assertRefusedNaming("DEP", refusal(() -> set(
         "U", List.of(agg("qty", "MIN", "a"), agg("qty", "MAX", "c")))));
      assertEquals(before, state("U"));

      assertAccepted(() -> set("U", AB));
      assertEquals(before, state("U"));
   }

   @Test
   void renamingTheAggregatedColumnToItsGeneratedNameStaysRefused() throws Exception {
      // WBS-098: TOTAL -> qty must not be waved through by the reuse path.
      build();
      set("U", List.of(agg("qty", "SUM", "TOTAL")));
      dep("DEP", "U", "TOTAL");
      String before = state("U");

      assertRefusedNaming("DEP", refusal(() -> set("U", List.of(agg("qty", "SUM", "qty")))));
      assertEquals(before, state("U"));
   }

   @Test
   void omittedPercentageOfOnTheSecondaryCountsAsAChange() throws Exception {
      build();
      List<AggregateSpec> withPercent = List.of(
         agg("qty", "SUM", null),
         new AggregateSpec("qty", "SUM", null, null, null, "group"));
      set("U", withPercent);
      dep("DEP", "U", "qty_1");

      assertAccepted(() -> set("U", withPercent));
      String before = state("U");

      assertRefusedNaming("DEP", refusal(() -> set(
         "U", List.of(agg("qty", "SUM", null), agg("qty", "SUM", null)))));
      assertEquals(before, state("U"));
   }

   // ---- pre-fix tables and refusal bookkeeping --------------------------------------------

   @Test
   void tableWithoutARecordRefusesOnceThenConfirmedApplyWritesIt() throws Exception {
      build();
      set("U", AB);
      dep("DEP", "U", "b");
      table("U").setProperty(WorksheetMutationSupport.SECONDARY_AGGREGATE_COLUMNS, null);
      String before = state("U");

      assertRefusedNaming("DEP", refusal(() -> set("U", AB)));
      assertEquals(before, state("U"));
      assertNull(table("U").getProperty(WorksheetMutationSupport.SECONDARY_AGGREGATE_COLUMNS));

      assertAccepted(() -> set("U", AB, true));
      assertNotNull(table("U").getProperty(WorksheetMutationSupport.SECONDARY_AGGREGATE_COLUMNS));
      String confirmed = state("U");
      assertAccepted(() -> set("U", AB));
      assertEquals(confirmed, state("U"));
      assertEquals("U.b", depInput("DEP"));
   }

   @Test
   void ownStateRefusalLeavesTheRecordUnchanged() throws Exception {
      build();
      svc.apply("TOK", agent, ed -> ed.addMirror("Q", "U"));
      svc.apply("TOK", agent, ed -> ed.setGroupAggregate(
         "Q", List.of(new GroupSpec("od", "QUARTER")), AB));
      String quarter = null;
      ColumnSelection pub = table("Q").getColumnSelection(true);

      for(int i = 0; i < pub.getAttributeCount(); i++) {
         if(pub.getAttribute(i).getName().contains("Quarter")) {
            quarter = pub.getAttribute(i).getName();
         }
      }

      assertNotNull(quarter, "sanity: the Quarter range column exists");
      String filtered = quarter;
      svc.apply("TOK", agent, ed -> ed.addFilter("Q", filtered, "NOT_NULL"));
      String before = state("Q");

      assertNotNull(refusal(() -> svc.apply("TOK", agent, ed -> ed.setGroupAggregate(
         "Q", List.of(new GroupSpec("od", "MONTH")), AB))));
      assertEquals(before, state("Q"));
      assertAccepted(() -> svc.apply("TOK", agent, ed -> ed.setGroupAggregate(
         "Q", List.of(new GroupSpec("od", "QUARTER")), AB)));
      assertEquals(before, state("Q"));
   }

   // ---- rename / remove / visibility keep the record accurate -----------------------------

   @Test
   void identicalResendAfterRenamingTheGeneratedColumnIsAccepted() throws Exception {
      build();
      set("U", AB);
      dep("DEP", "U", "b");
      svc.apply("TOK", agent, ed -> ed.renameColumn("U", "b", "bb"));
      String renamed = state("U");

      assertTrue(renamed.contains("bb{b}"), renamed);
      assertAccepted(() -> set("U", AB));
      assertEquals(renamed, state("U"));
      assertEquals("U.b", depInput("DEP"));
   }

   @Test
   void identicalResendAfterRenamingAnUnaliasedGeneratedColumnIsAccepted() throws Exception {
      build();
      set("U", UNALIASED);
      svc.apply("TOK", agent, ed -> ed.renameColumn("U", "qty_1", "foo"));
      dep("DEP", "U", "foo");
      String renamed = state("U");

      assertAccepted(() -> set("U", UNALIASED));
      assertEquals(renamed, state("U"));
      assertEquals("U.foo", depInput("DEP"));
   }

   @Test
   void removingTheGeneratedColumnDropsItsRecordEntry() throws Exception {
      build();
      set("U", AB);
      svc.apply("TOK", agent, ed -> ed.removeColumn("U", "b", true));

      String record = table("U").getProperty(WorksheetMutationSupport.SECONDARY_AGGREGATE_COLUMNS);
      assertTrue(record == null || record.isEmpty(), record);
      assertAccepted(() -> set("U", AB));
      String after = state("U");
      assertAccepted(() -> set("U", AB));
      assertEquals(after, state("U"));
   }

   @Test
   void hiddenGeneratedColumnStaysHiddenOnIdenticalResend() throws Exception {
      build();
      set("U", AB);
      svc.apply("TOK", agent, ed -> ed.setColumnVisibility("U", "b", false));
      String hidden = state("U");
      assertTrue(hidden.contains("pub=[pid, a]"), hidden);

      assertAccepted(() -> set("U", AB));
      assertAccepted(() -> set("U", AB));
      assertEquals(hidden, state("U"));
   }

   // ---- wider shapes ----------------------------------------------------------------------

   @Test
   void thirdAggregateKeepsTheExistingSecondaryAndAddsAFreshOne() throws Exception {
      build();
      set("U", AB);
      dep("DEP", "U", "b");

      assertAccepted(() -> set("U", List.of(agg("qty", "MIN", "a"), agg("qty", "MAX", "b"),
                                            agg("qty", "AVG", "c"))));
      String s = state("U");
      assertTrue(s.contains("qty_1{b}"), s);
      assertTrue(s.contains("qty_2{c}"), s);
      assertEquals("U.b", depInput("DEP"));
   }

   @Test
   void formulaChangeUnderAStableAliasKeepsTheColumn() throws Exception {
      build();
      set("U", AB);
      dep("DEP", "U", "b");

      assertAccepted(() -> set("U", List.of(agg("qty", "MIN", "a"), agg("qty", "AVG", "b"))));
      assertTrue(state("U").contains("Average:qty_1{b}"), state("U"));
      assertEquals("U.b", depInput("DEP"));
   }

   @Test
   void twoSourceColumnsKeepTheirOwnSecondaries() throws Exception {
      build();
      List<AggregateSpec> aggs = List.of(
         agg("qty", "MIN", null), agg("qty", "MAX", null),
         agg("oid", "MIN", null), agg("oid", "MAX", null));
      set("U", aggs);
      dep("DEP", "U", "oid_1");
      String initial = state("U");

      assertAccepted(() -> set("U", aggs));
      previewAll();
      assertAccepted(() -> set("U", aggs));
      assertEquals(initial, state("U"));
      assertEquals("U.oid_1", depInput("DEP"));
   }

   @Test
   void threeAggregatesOnOneColumnResendIsStable() throws Exception {
      build();
      List<AggregateSpec> aggs = List.of(
         agg("qty", "MIN", null), agg("qty", "MAX", null), agg("qty", "AVG", null));
      set("U", aggs);
      dep("DEP", "U", "qty_2");
      String initial = state("U");

      assertAccepted(() -> set("U", aggs));
      assertEquals(initial, state("U"));
      assertEquals("U.qty_2", depInput("DEP"));
   }

   @Test
   void expressionSourceResendIsStable() throws Exception {
      build();
      svc.apply("TOK", agent, ed -> ed.addExpressionColumn(
         "U", "X", "field['qty'] * 2", "integer", false));
      List<AggregateSpec> aggs = List.of(agg("X", "MIN", null), agg("X", "MAX", null));
      set("U", aggs);
      dep("DEP", "U", "X_1");
      String initial = state("U");

      assertAccepted(() -> set("U", aggs));
      assertEquals(initial, state("U"));
   }

   @Test
   void crosstabResendIsStable() throws Exception {
      build();
      List<GroupSpec> groups = List.of(new GroupSpec("pid"), new GroupSpec("oid"));
      svc.apply("TOK", agent, ed -> ed.setGroupAggregate("U", groups, AB, true));
      String initial = state("U");

      assertAccepted(() -> svc.apply("TOK", agent, ed -> ed.setGroupAggregate(
         "U", groups, AB, true)));
      assertEquals(initial, state("U"));
      assertTrue(table("U").getAggregateInfo().isCrosstab());
   }

   @Test
   void dateLevelGroupResendIsStable() throws Exception {
      build();
      List<GroupSpec> groups = List.of(new GroupSpec("od", "MONTH"));
      svc.apply("TOK", agent, ed -> ed.setGroupAggregate("U", groups, AB));
      dep("DEP", "U", "b");
      String initial = state("U");

      assertAccepted(() -> svc.apply("TOK", agent, ed -> ed.setGroupAggregate("U", groups, AB)));
      assertEquals(initial, state("U"));
   }

   @Test
   void groupColumnNamedLikeAGeneratedNameIsNeverAggregatedOrTaken() throws Exception {
      build();
      svc.apply("TOK", agent, ed -> ed.addExpressionColumn(
         "U", "qty_1", "field['oid']", "integer", false));
      List<GroupSpec> groups = List.of(new GroupSpec("pid"), new GroupSpec("qty_1"));
      svc.apply("TOK", agent, ed -> ed.setGroupAggregate("U", groups, UNALIASED));
      String initial = state("U");

      assertTrue(initial.contains("Max:qty_2"), initial);
      assertAccepted(() -> svc.apply("TOK", agent, ed -> ed.setGroupAggregate(
         "U", groups, UNALIASED)));
      assertEquals(initial, state("U"));
   }

   // ---- helpers ---------------------------------------------------------------------------

   private static AggregateSpec agg(String field, String formula, String alias) {
      return new AggregateSpec(field, formula, alias);
   }

   private void set(String table, List<AggregateSpec> aggregates) throws Exception {
      set(table, aggregates, false);
   }

   private void set(String table, List<AggregateSpec> aggregates, boolean confirmed)
      throws Exception
   {
      svc.apply("TOK", agent, ed -> ed.setGroupAggregate(
         table, List.of(new GroupSpec("pid")), aggregates, false, confirmed));
   }

   /** A mirror of {@code source} summing {@code field}: a downstream consumer of that column. */
   private void dep(String name, String source, String field) throws Exception {
      svc.apply("TOK", agent, ed -> ed.addMirror(name, source));
      svc.apply("TOK", agent, ed -> ed.setGroupAggregate(
         name, List.of(), List.of(agg(field, "SUM", null))));
   }

   private void joinSetup() throws Exception {
      build();
      set("U", AB);
      set("Z", List.of(agg("qty", "MAX", "tot")));
      svc.apply("TOK", agent, ed -> ed.addJoin("J", "U", "b", "Z", "tot", "INNER", null, null));
   }

   private void addUserColumn(String name) throws Exception {
      svc.apply("TOK", agent, ed -> ed.addExpressionColumn(
         "U", name, "field['qty']", "integer", false));
   }

   /** Runs the real preview of every table, which re-installs a clone of each AggregateInfo. */
   private void previewAll() throws Exception {
      for(String name : List.of("U", "Z", "DEP", "J")) {
         if(ws.getAssembly(name) != null) {
            assertNotNull(previews.preview(rws, name, 0, 10));
         }
      }
   }

   private void assertAccepted(ThrowingRunnable r) {
      String result = refusal(r);
      assertNull(result, result);
   }

   private static void assertRefusedNaming(String dependent, String refusal) {
      assertNotNull(refusal, "expected a refusal naming " + dependent);
      assertTrue(refusal.contains(dependent), refusal);
   }

   /** The refusal message, or {@code null} when the call was accepted. */
   private String refusal(ThrowingRunnable r) {
      try {
         r.run();
         return null;
      }
      catch(PairingException ex) {
         return ex.getMessage();
      }
      catch(Exception ex) {
         throw new AssertionError("unexpected " + ex, ex);
      }
   }

   private interface ThrowingRunnable {
      void run() throws Exception;
   }

   private TableAssembly table(String name) {
      return (TableAssembly) ws.getAssembly(name);
   }

   /** The input column name of the dependent's first aggregate. */
   private String depInput(String dep) {
      return table(dep).getAggregateInfo().getAggregate(0).getDataRef().getName();
   }

   /** Private selection, aggregates, record and public names: everything a resend may change. */
   private String state(String name) {
      TableAssembly t = table(name);
      ColumnSelection cs = t.getColumnSelection(false);
      StringBuilder sb = new StringBuilder("sel=[");

      for(int i = 0; i < cs.getAttributeCount(); i++) {
         ColumnRef c = (ColumnRef) cs.getAttribute(i);
         sb.append(i > 0 ? "," : "").append(c.getAttribute())
            .append(c.getAlias() == null ? "" : "{" + c.getAlias() + "}");
      }

      sb.append("] agg=[");
      AggregateInfo ai = t.getAggregateInfo();

      for(int i = 0; i < ai.getAggregateCount(); i++) {
         AggregateRef r = ai.getAggregate(i);
         ColumnRef c = (ColumnRef) r.getDataRef();
         sb.append(i > 0 ? "," : "").append(r.getFormula()).append(':').append(c.getAttribute())
            .append(c.getAlias() == null ? "" : "{" + c.getAlias() + "}");
      }

      ColumnSelection pub = t.getColumnSelection(true);
      List<String> names = new ArrayList<>();

      for(int i = 0; i < pub.getAttributeCount(); i++) {
         names.add(pub.getAttribute(i).getName());
      }

      return sb.append("] prop=").append(String.valueOf(
         t.getProperty(WorksheetMutationSupport.SECONDARY_AGGREGATE_COLUMNS)).replace("\n", "|"))
         .append(" pub=").append(names).toString();
   }

   private static Element parseXmlString(String xml) throws Exception {
      javax.xml.parsers.DocumentBuilderFactory factory =
         javax.xml.parsers.DocumentBuilderFactory.newInstance();
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      return factory.newDocumentBuilder()
         .parse(new java.io.ByteArrayInputStream(
            xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
         .getDocumentElement();
   }

   private static EmbeddedTableAssembly orders(Worksheet ws, String name) {
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, name, "pid", "oid", "qty", "od");
      t.setEmbeddedData(new XEmbeddedTable(
         new String[]{ "integer", "integer", "integer", "date" },
         new Object[][]{
            { "pid", "oid", "qty", "od" },
            { 1, 10501, 5, java.sql.Date.valueOf("2024-01-15") },
            { 1, 10502, 7, java.sql.Date.valueOf("2024-04-15") },
            { 2, 10501, 3, java.sql.Date.valueOf("2024-07-15") },
            { 2, 10503, 9, java.sql.Date.valueOf("2024-10-15") },
         }));
      ColumnSelection cs = t.getColumnSelection(false);

      for(String c : List.of("pid", "oid", "qty")) {
         ((ColumnRef) cs.getAttribute(c)).setDataType(XSchema.INTEGER);
      }

      ((ColumnRef) cs.getAttribute("od")).setDataType(XSchema.DATE);
      return t;
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

      rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = new AssetQuerySandbox(ws, null, new VariableTable());
      box.setActive(true);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.WORKSHEET), eq("Worksheet/ws1"), any()))
         .thenReturn(rws);

      return new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), securityEngine, mock(InnerJoinService.class));
   }
}
