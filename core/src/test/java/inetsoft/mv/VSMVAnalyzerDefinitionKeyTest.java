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
package inetsoft.mv;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.DefaultIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Bug #81366, the fingerprint of the table definition recorded by the real analysis. The risk
 * of the check is a fresh MV that reads as changed because the analysis transforms the table it
 * holds, so these run VSMVAnalyzer over a viewsheet (a crosstab on a worksheet table, which the
 * transformers move the aggregate down to the table of) and read the fingerprint of the MV the
 * way the status, the viewsheet and the worksheet query read it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  VSMVAnalyzerDefinitionKeyTest.RepositoryConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSMVAnalyzerDefinitionKeyTest {
   @Configuration
   static class RepositoryConfig {
      @Bean
      XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   @BeforeEach
   void setUp() {
      mvManager = mockStatic(MVManager.class);
      mvManager.when(MVManager::getManager).thenReturn(mock(MVManager.class));
   }

   @AfterEach
   void tearDown() {
      mvManager.close();
   }

   /**
    * The fingerprint is taken from the table before the analysis, not from the snapshot the MV
    * stores, as the analysis works on (and can move the aggregate, max rows and conditions of)
    * the tables of that snapshot. A worksheet table with its own aggregate is read unchanged
    * after the analysis, and the analysis leaves the viewsheet's table as it was.
    */
   @Test
   void worksheetAggregateIsReadUnchangedAfterTheAnalysis() throws Exception {
      aggregated = true;
      Viewsheet vs = viewsheet(null, null);
      String before = MVDefinitionKey.create(
         (TableAssembly) vs.getBaseWorksheet().getAssembly("ORDERS_O"));
      MVDef[] defs = analyze(vs);

      assertTrue(defs.length > 0, "the analysis created no MV");
      assertEquals(before, MVDefinitionKey.create(
         (TableAssembly) vs.getBaseWorksheet().getAssembly("ORDERS_O")),
                   "the analysis changed the viewsheet's table");

      for(MVDef def : defs) {
         assertEquals(before, def.getMetaData().getDefinitionKey(), def.getName());
         assertFalse(def.isStale(vs.getBaseWorksheet()), def.getName());
         assertFalse(def.isStale(roundTrip(worksheet(null, null))), def.getName());
      }

      // an edited aggregate is a change
      aggregated = false;
      assertTrue(defs[0].isStale(roundTrip(worksheet(null, null))));
   }

   @Test
   void freshBuildReadsUnchanged() throws Exception {
      Viewsheet vs = viewsheet(null, null);
      MVDef[] defs = analyze(vs);

      assertTrue(defs.length > 0, "the analysis created no MV");

      for(MVDef def : defs) {
         assertNotNull(def.getMetaData().getDefinitionKey(), def.getName());
         // the viewsheet's own worksheet (use time), the worksheet as saved (status)
         assertFalse(def.isDefinitionChanged(vs.getBaseWorksheet()), def.getName());
         assertFalse(def.isDefinitionChanged(roundTrip(worksheet(null, null))), def.getName());
         assertFalse(def.isStale(vs.getBaseWorksheet()), def.getName());
         // and across the storage of the MV
         MVMetaData stored = roundTrip(def.getMetaData());
         assertEquals(def.getMetaData().getDefinitionKey(), stored.getDefinitionKey());
         assertEquals(def.getMetaData().getDefinitionTable(), stored.getDefinitionTable());
      }
   }

   @Test
   void freshBuildReadsUnchangedWithAnExpressionColumn() throws Exception {
      Viewsheet vs = viewsheet(null, "1");

      for(MVDef def : analyze(vs)) {
         assertFalse(def.isStale(vs.getBaseWorksheet()), def.getName());
         assertFalse(def.isStale(roundTrip(worksheet(null, "1"))), def.getName());
      }
   }

   @Test
   void addedFilterReadsChanged() throws Exception {
      MVDef[] defs = analyze(viewsheet(null, null));
      Viewsheet edited = viewsheet("12100", null);

      for(MVDef def : defs) {
         assertTrue(def.isDefinitionChanged(edited.getBaseWorksheet()), def.getName());
         assertTrue(def.isStale(roundTrip(worksheet("12100", null))), def.getName());
      }
   }

   @Test
   void editedExpressionReadsChanged() throws Exception {
      MVDef[] defs = analyze(viewsheet(null, "1"));

      for(MVDef def : defs) {
         assertTrue(def.isStale(viewsheet(null, "2").getBaseWorksheet()), def.getName());
      }
   }

   @Test
   void unrelatedChangeReadsUnchanged() throws Exception {
      MVDef[] defs = analyze(viewsheet(null, null));
      Worksheet current = roundTrip(worksheet(null, null));
      TableAssembly table = (TableAssembly) current.getAssembly("ORDERS");
      table.setPixelOffset(new java.awt.Point(400, 300));
      ((WSAssembly) table).setDescription("moved and described");

      for(MVDef def : defs) {
         assertFalse(def.isStale(current), def.getName());
      }
   }

   @Test
   void filterChangeIsNotSharedWithTheAnalyzedMV() throws Exception {
      MVDef[] stored = analyze(viewsheet(null, null));
      MVDef[] same = analyze(viewsheet(null, null));
      MVDef[] filtered = analyze(viewsheet("12100", null));

      assertEquals(stored.length, filtered.length);
      assertTrue(stored[0].isSharedBy(same[0]));
      assertFalse(stored[0].isSharedBy(filtered[0]));
   }

   @Test
   void expressionChangeIsNotSharedWithTheAnalyzedMV() throws Exception {
      MVDef[] stored = analyze(viewsheet(null, "1"));
      MVDef[] same = analyze(viewsheet(null, "1"));
      MVDef[] edited = analyze(viewsheet(null, "2"));

      assertTrue(stored[0].isSharedBy(same[0]));
      assertFalse(stored[0].isSharedBy(edited[0]));
   }

   @Test
   void legacyStoredMVWithoutTheFingerprintIsStillShared() throws Exception {
      MVDef[] stored = analyze(viewsheet(null, null));
      MVDef[] edited = analyze(viewsheet("12100", null));
      assertFalse(stored[0].isSharedBy(edited[0]));

      stored[0].setDefinitionKey(null, null);

      assertTrue(stored[0].isSharedBy(edited[0]));
      assertFalse(stored[0].isStale(viewsheet("12100", null).getBaseWorksheet()));
   }

   private MVDef[] analyze(Viewsheet vs) throws Exception {
      Identity user = new DefaultIdentity(
         new IdentityID("u1", Organization.getDefaultOrganizationID()), Identity.USER);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getAllVariables()).thenReturn(new VariableTable());
      return new VSMVAnalyzer("1^128^__NULL__^vs1", vs, user, box, false, null).analyze();
   }

   private Worksheet worksheet(String filter, String flag) {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, "ORDERS");
      table.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "ds1", "ORDERS"));
      ColumnSelection cols = new ColumnSelection();

      for(String name : new String[] { "STATE", "ORDER_ID", "DISCOUNT" }) {
         ColumnRef col = new ColumnRef(new AttributeRef("ORDERS", name));
         col.setDataType("DISCOUNT".equals(name) ? XSchema.DOUBLE : XSchema.STRING);
         cols.addAttribute(col);
      }

      table.setColumnSelection(cols, false);
      table.setColumnSelection(cols, true);

      if(aggregated) {
         AggregateInfo ainfo = new AggregateInfo();
         ainfo.addGroup(new GroupRef(cols.getAttribute(0)));
         ainfo.addAggregate(new AggregateRef(cols.getAttribute(2), AggregateFormula.SUM));
         table.setAggregateInfo(ainfo);
         table.setMaxRows(1000);
      }

      if(flag != null) {
         ExpressionRef eref = new ExpressionRef(null, "Flag");
         eref.setExpression(flag);
         ColumnRef col = new ColumnRef(eref);
         col.setSQL(false);
         col.setDataType(XSchema.STRING);
         cols.addAttribute(col);
         table.setColumnSelection(cols, false);
         table.setColumnSelection(cols, true);
      }

      if(filter != null) {
         AssetCondition cond = new AssetCondition();
         cond.setOperation(XCondition.EQUAL_TO);
         cond.addValue(filter);
         ConditionList list = new ConditionList();
         list.append(new ConditionItem(new ColumnRef(new AttributeRef("ORDERS", "ORDER_ID")),
                                       cond, 0));
         table.setPreConditionList(list);
      }

      ws.addAssembly(table);
      return ws;
   }

   private Viewsheet viewsheet(String filter, String flag) throws Exception {
      Viewsheet vs = new Viewsheet(new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                                  AssetEntry.Type.WORKSHEET, "ws1", null));
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, "Crosstab1");
      crosstab.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "ORDERS"));
      vs.addAssembly(crosstab);

      VSDimensionRef state = new VSDimensionRef();
      String dim = flag == null ? "STATE" : "Flag";
      state.setDataRef(new AttributeRef("ORDERS", dim));
      state.setGroupColumnValue(dim);
      VSAggregateRef sum = new VSAggregateRef();
      sum.setDataRef(new AttributeRef("ORDERS", "DISCOUNT"));
      sum.setColumnValue("DISCOUNT");
      sum.setFormulaValue(AggregateFormula.SUM.getFormulaName());

      VSCrosstabInfo cinfo = new VSCrosstabInfo();
      cinfo.setDesignRowHeaders(new VSDimensionRef[] { state });
      cinfo.setDesignColHeaders(new VSDimensionRef[0]);
      cinfo.setDesignAggregates(new VSAggregateRef[] { sum });
      crosstab.setVSCrosstabInfo(cinfo);

      Method setBase = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBase.setAccessible(true);
      Worksheet ws = worksheet(filter, flag);
      setBase.invoke(vs, ws);
      cinfo.update(vs, ws.getAssembly("ORDERS") instanceof TableAssembly t ?
                   t.getColumnSelection(false) : null, null, false, "ORDERS", null);
      return vs;
   }

   private MVMetaData roundTrip(MVMetaData data) throws Exception {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      data.writeXML(writer);
      writer.flush();

      MVMetaData result = new MVMetaData();
      result.parseXML(inetsoft.util.Tool.parseXML(new ByteArrayInputStream(
         buf.toString().getBytes(StandardCharsets.UTF_8))).getDocumentElement());
      return result;
   }

   private Worksheet roundTrip(Worksheet ws) throws Exception {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      ws.writeXML(writer);
      writer.flush();

      Worksheet result = new Worksheet();
      result.parseXML(inetsoft.util.Tool.parseXML(new ByteArrayInputStream(
         buf.toString().getBytes(StandardCharsets.UTF_8))).getDocumentElement());
      return result;
   }

   private boolean aggregated;
   private MockedStatic<MVManager> mvManager;
}
