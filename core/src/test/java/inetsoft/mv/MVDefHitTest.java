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

import inetsoft.mv.fs.*;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Whether a table can be answered from an MV definition: {@link MVDef#isValidMV} (the version
 * and the count-distinct rule) and {@link MVDef#canHitMV} (the columns the query needs).
 * An MV supports at most one count distinct, and only on the column it breaks its blocks at,
 * unless the MV is a worksheet MV or the server is a desktop one, where the data is not
 * split into blocks.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVDefHitTest {
   @BeforeEach
   void serverIsNotDesktop() {
      fsService = mockStatic(FSService.class);
      fsService.when(FSService::getServer).thenReturn(null);
   }

   @AfterEach
   void closeServer() {
      fsService.close();
   }

   // ---------------------------------------------------------------- isValidMV

   @Test
   void definitionWithoutVersionIsInvalid() throws Exception {
      // a definition saved before 11.1 has no version
      MVDef def = definition(null, "vs1", null);

      assertFalse(def.isValidMV());
      assertFalse(def.isValidMV(table(info(group("Region"), sum("Amount")))));
   }

   @Test
   void noCountDistinctIsValid() throws Exception {
      MVDef def = definition("1", "vs1", null);

      assertTrue(def.isValidMV());
      assertTrue(def.isValidMV(info(group("Region"), sum("Amount"))));
      assertTrue(def.isValidMV((AggregateInfo) null));
   }

   @Test
   void countDistinctOnTheBreakColumnIsValid() throws Exception {
      MVDef def = definition("1", "vs1", "Customer");
      assertTrue(def.isValidMV(info(group("Region"), distinct("Customer"), sum("Amount"))));
   }

   @Test
   void countDistinctOnAnotherColumnIsInvalid() throws Exception {
      MVDef def = definition("1", "vs1", "Customer");
      assertFalse(def.isValidMV(info(group("Region"), distinct("Product"))));
   }

   @Test
   void countDistinctWithoutBreakColumnIsInvalid() throws Exception {
      MVDef def = definition("1", "vs1", null);
      assertFalse(def.isValidMV(info(group("Region"), distinct("Customer"))));
   }

   @Test
   void twoCountDistinctsAreInvalidEvenWithABreakColumn() throws Exception {
      MVDef def = definition("1", "vs1", "Customer");
      assertFalse(def.isValidMV(info(null, distinct("Customer"), distinct("Product"))));
   }

   @Test
   void countDistinctOnAnAliasUsesTheBaseColumn() throws Exception {
      MVDef def = definition("1", "vs1", "Customer");
      AliasDataRef alias = new AliasDataRef("Buyer", new AttributeRef(null, "Customer"));
      ColumnRef ref = new ColumnRef(alias);
      ref.setDataType(XSchema.STRING);
      AggregateInfo info = info(null, new AggregateRef(ref, AggregateFormula.COUNT_DISTINCT));

      assertTrue(def.isValidMV(info));
   }

   @Test
   void worksheetMVAllowsAnyCountDistinct() throws Exception {
      // a worksheet MV has no viewsheet id
      MVDef def = definition("1", null, null);

      assertTrue(def.isWSMV());
      assertTrue(def.isValidMV(info(null, distinct("Customer"), distinct("Product"))));
   }

   @Test
   void desktopServerAllowsAnyCountDistinct() throws Exception {
      MVDef def = definition("1", "vs1", null);
      FSConfig config = mock(FSConfig.class);
      when(config.isDesktop()).thenReturn(true);
      XServerNode server = mock(XServerNode.class);
      when(server.getConfig()).thenReturn(config);
      fsService.when(FSService::getServer).thenReturn(server);

      assertTrue(def.isValidMV(info(null, distinct("Customer"), distinct("Product"))));

      when(config.isDesktop()).thenReturn(false);
      assertFalse(def.isValidMV(info(null, distinct("Customer"), distinct("Product"))));
   }

   // ---------------------------------------------------------------- canHitMV

   @Test
   void hitWhenTheMVHasTheGroupAndAggregateColumns() throws Exception {
      MVDef def = withColumns(definition("1", "vs1", null), dim("Region"), measure("Amount"));
      assertTrue(def.canHitMV(table(info(group("Region"), sum("Amount")))));
   }

   @Test
   void missWhenTheGroupColumnIsNotInTheMV() throws Exception {
      MVDef def = withColumns(definition("1", "vs1", null), dim("State"), measure("Amount"));
      assertFalse(def.canHitMV(table(info(group("Region"), sum("Amount")))));
   }

   @Test
   void missWhenTheAggregateColumnIsNotInTheMV() throws Exception {
      MVDef def = withColumns(definition("1", "vs1", null), dim("Region"), measure("Price"));
      assertFalse(def.canHitMV(table(info(group("Region"), sum("Amount")))));
   }

   @Test
   void aGroupColumnMayBeAMeasureOfTheMV() throws Exception {
      // the column is looked up as a dimension first, then as a measure
      MVDef def = withColumns(definition("1", "vs1", null), measure("Region"),
                              measure("Amount"));
      assertTrue(def.canHitMV(table(info(group("Region"), sum("Amount")))));
   }

   @Test
   void invalidDefinitionIsNotHitEvenWithAllColumns() throws Exception {
      MVDef def = withColumns(definition("1", "vs1", null), dim("Region"), dim("Customer"),
                              measure("Amount"));
      assertFalse(def.canHitMV(table(info(group("Region"), distinct("Customer")))));

      MVDef old = withColumns(definition(null, "vs1", null), dim("Region"), measure("Amount"));
      assertFalse(old.canHitMV(table(info(group("Region"), sum("Amount")))));
   }

   // ---------------------------------------------------------------- helpers

   /**
    * Read a definition from its XML, the only way to set the version and break column.
    */
   private static MVDef definition(String version, String vsId, String breakcol)
      throws Exception
   {
      StringBuilder xml = new StringBuilder("<mvdef><name>mv1</name>");
      append(xml, "version", version);
      append(xml, "vsId", vsId);
      append(xml, "breakcol", breakcol);
      xml.append("</mvdef>");
      Document doc = Tool.parseXML(
         new ByteArrayInputStream(xml.toString().getBytes(StandardCharsets.UTF_8)));
      MVDef def = new MVDef();
      def.parseXML(doc.getDocumentElement(), false, false);
      return def;
   }

   private static void append(StringBuilder xml, String tag, String value) {
      if(value != null) {
         xml.append('<').append(tag).append('>').append(value)
            .append("</").append(tag).append('>');
      }
   }

   private static MVDef withColumns(MVDef def, MVColumn... columns) {
      MVDef spy = spy(def);
      doReturn(new ArrayList<>(Arrays.asList(columns))).when(spy).getColumns();
      return spy;
   }

   private static MVColumn dim(String name) {
      return new MVColumn(column(name), true);
   }

   private static MVColumn measure(String name) {
      ColumnRef ref = column(name);
      ref.setDataType(XSchema.DOUBLE);
      return new MVColumn(ref, false);
   }

   private static ColumnRef column(String name) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, name));
      ref.setDataType(XSchema.STRING);
      return ref;
   }

   private static GroupRef group(String name) {
      return new GroupRef(column(name));
   }

   private static AggregateRef sum(String name) {
      ColumnRef ref = column(name);
      ref.setDataType(XSchema.DOUBLE);
      return new AggregateRef(ref, AggregateFormula.SUM);
   }

   private static AggregateRef distinct(String name) {
      return new AggregateRef(column(name), AggregateFormula.COUNT_DISTINCT);
   }

   private static AggregateInfo info(GroupRef group, AggregateRef... aggregates) {
      AggregateInfo info = new AggregateInfo();

      if(group != null) {
         info.addGroup(group);
      }

      for(AggregateRef aggregate : aggregates) {
         info.addAggregate(aggregate);
      }

      return info;
   }

   /** A worksheet table on a (non-sub) runtime MV, with the columns of its aggregate. */
   private static TableAssembly table(AggregateInfo info) {
      BoundTableAssembly table = new BoundTableAssembly(new Worksheet(), "T");
      ColumnSelection cols = new ColumnSelection();

      for(GroupRef group : info.getGroups()) {
         cols.addAttribute(group.getDataRef());
      }

      for(AggregateRef aggregate : info.getAggregates()) {
         if(!cols.containsAttribute(aggregate.getDataRef())) {
            cols.addAttribute(aggregate.getDataRef());
         }
      }

      table.setColumnSelection(cols);
      table.setAggregateInfo(info);
      RuntimeMV rmv = mock(RuntimeMV.class);
      when(rmv.isSub()).thenReturn(false);
      table.setRuntimeMV(rmv);
      return table;
   }

   private MockedStatic<FSService> fsService;
}
