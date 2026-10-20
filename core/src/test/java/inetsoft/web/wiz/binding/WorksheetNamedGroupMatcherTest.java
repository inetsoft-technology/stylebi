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
package inetsoft.web.wiz.binding;

import inetsoft.uql.asset.AttachedAssembly;
import inetsoft.uql.asset.BoundTableAssembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.DefaultNamedGroupAssembly;
import inetsoft.uql.asset.MirrorTableAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.sree.SreeEnv;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

/**
 * Bug #78932 / #76311: the matcher must resolve the table a viewsheet assembly is bound to
 * through the {@code MirrorTableAssembly} that {@code Viewsheet#createMirrorTable} always puts
 * under the bound name. Built from real worksheet assemblies on purpose: the #76311 unit tests
 * mocked an {@code AbstractTableAssembly} under that name, a shape a live runtime never has.
 */
@Tag("core")
class WorksheetNamedGroupMatcherTest {
   private MockedStatic<SreeEnv> sreeEnv;

   @BeforeEach
   void setUp() {
      // Worksheet's constructor reads a server property; no Spring context in a unit test
      sreeEnv = mockStatic(SreeEnv.class);
      sreeEnv.when(() -> SreeEnv.getProperty(org.mockito.ArgumentMatchers.anyString()))
         .thenReturn("");
      sreeEnv.when(() -> SreeEnv.getProperty(org.mockito.ArgumentMatchers.anyString(),
                                             org.mockito.ArgumentMatchers.anyString()))
         .thenAnswer(inv -> inv.getArgument(1));
   }

   @AfterEach
   void tearDown() {
      sreeEnv.close();
   }

   private static final SourceInfo MODEL = new SourceInfo(SourceInfo.MODEL, "ds", "Order Model");
   private static final SourceInfo PHYSICAL =
      new SourceInfo(SourceInfo.PHYSICAL_TABLE, "ds", "SA.CUSTOMERS");

   private static BoundTableAssembly raw(Worksheet ws, String name, SourceInfo source,
                                         String entity, String... columns)
   {
      BoundTableAssembly table = new BoundTableAssembly(ws, name);
      table.setSourceInfo(source);
      ColumnSelection selection = new ColumnSelection();

      for(String column : columns) {
         selection.addAttribute(new ColumnRef(new AttributeRef(entity, column)));
      }

      table.setColumnSelection(selection, true);
      table.setColumnSelection(selection, false);
      ws.addAssembly(table);
      return table;
   }

   /** What {@code Viewsheet#createMirrorTable} does: rename the raw table to {@code <n>_O}. */
   private static MirrorTableAssembly mirror(Worksheet ws, String name) {
      inetsoft.uql.asset.AbstractTableAssembly table =
         (inetsoft.uql.asset.AbstractTableAssembly) ws.getAssembly(name);
      ws.removeAssembly(name);
      table.getInfo().setName(name + "_O");
      ws.addAssembly(table);
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, name, table);
      ws.addAssembly(mirror);
      return mirror;
   }

   private static DefaultNamedGroupAssembly group(Worksheet ws, String name, SourceInfo source,
                                                  String entity, String attribute)
   {
      DefaultNamedGroupAssembly ng = new DefaultNamedGroupAssembly(ws, name);
      ng.setAttachedType(AttachedAssembly.COLUMN_ATTACHED);
      ng.setAttachedSource(source);
      ng.setAttachedAttribute(new ColumnRef(new AttributeRef(entity, attribute)));
      ws.addAssembly(ng);
      return ng;
   }

   private static SourceInfo asset(String name) {
      return new SourceInfo(SourceInfo.ASSET, null, name);
   }

   @Test
   void attributeScopedPhysicalGroupMatchesThroughTheViewsheetMirror() {
      Worksheet ws = new Worksheet();
      raw(ws, "Customers", PHYSICAL, null, "STATE", "NAME");
      mirror(ws, "Customers");
      DefaultNamedGroupAssembly ng = group(ws, "M_States", PHYSICAL, null, "STATE");

      List<DefaultNamedGroupAssembly> found =
         WorksheetNamedGroupMatcher.worksheetNamedGroups(ws, asset("Customers"), "STATE");

      assertEquals(List.of(ng), found);
   }

   @Test
   void logicalModelGroupMatchesThroughTheViewsheetMirror() {
      Worksheet ws = new Worksheet();
      raw(ws, "Customer", MODEL, "Customer", "State");
      mirror(ws, "Customer");
      DefaultNamedGroupAssembly ng = group(ws, "LM_States", MODEL, "Customer", "State");

      assertEquals(List.of(ng), WorksheetNamedGroupMatcher.worksheetNamedGroups(
         ws, asset("Customer"), "State"));
   }

   @Test
   void groupMatchesThroughAUserMirrorUnderTheViewsheetMirror() {
      Worksheet ws = new Worksheet();
      raw(ws, "Customers", PHYSICAL, null, "STATE");
      MirrorTableAssembly user = new MirrorTableAssembly(
         ws, "Customers_M", (inetsoft.uql.asset.WSAssembly) ws.getAssembly("Customers"));
      ws.addAssembly(user);
      mirror(ws, "Customers_M");
      DefaultNamedGroupAssembly ng = group(ws, "M_States", PHYSICAL, null, "STATE");

      assertEquals(List.of(ng), WorksheetNamedGroupMatcher.worksheetNamedGroups(
         ws, asset("Customers_M"), "STATE"));
   }

   @Test
   void aDifferentEntityOfTheSameLogicalModelDoesNotMatch() {
      Worksheet ws = new Worksheet();
      raw(ws, "Orders", MODEL, "Order", "State");
      mirror(ws, "Orders");
      group(ws, "LM_States", MODEL, "Customer", "State");

      assertTrue(WorksheetNamedGroupMatcher.worksheetNamedGroups(
         ws, asset("Orders"), "State").isEmpty());
   }

   @Test
   void aGroupOnAColumnTheBoundTableDoesNotExposeDoesNotMatch() {
      Worksheet ws = new Worksheet();
      raw(ws, "Customers", PHYSICAL, null, "NAME");
      mirror(ws, "Customers");
      group(ws, "M_States", PHYSICAL, null, "STATE");

      assertTrue(WorksheetNamedGroupMatcher.worksheetNamedGroups(
         ws, asset("Customers"), "STATE").isEmpty());
   }

   @Test
   void aDifferentPhysicalTableDoesNotMatch() {
      Worksheet ws = new Worksheet();
      raw(ws, "Stores", new SourceInfo(SourceInfo.PHYSICAL_TABLE, "ds", "SA.STORES"), null,
          "STATE");
      mirror(ws, "Stores");
      group(ws, "M_States", PHYSICAL, null, "STATE");

      assertTrue(WorksheetNamedGroupMatcher.worksheetNamedGroups(
         ws, asset("Stores"), "STATE").isEmpty());
   }

   @Test
   void aMirrorWithoutAResolvableSourceStaysUnmatched() {
      Worksheet ws = new Worksheet();
      BoundTableAssembly table = new BoundTableAssembly(ws, "Missing_O");
      ws.addAssembly(table);
      ws.addAssembly(new MirrorTableAssembly(ws, "Missing", table));
      group(ws, "M_States", PHYSICAL, null, "STATE");

      assertTrue(WorksheetNamedGroupMatcher.worksheetNamedGroups(
         ws, asset("Missing"), "STATE").isEmpty());
   }

   @Test
   void unmatchedReasonNamesADataTypeOnlyGroupAndTheRegistrationRoute() {
      Worksheet ws = new Worksheet();
      DefaultNamedGroupAssembly ng = new DefaultNamedGroupAssembly(ws, "T_States");
      ng.setAttachedType(AttachedAssembly.DATA_TYPE_ATTACHED);
      ng.setAttachedDataType("string");
      ws.addAssembly(ng);

      String reason = WorksheetNamedGroupMatcher.unmatchedReason(ws, "T_States");

      assertTrue(reason.contains("T_States") && reason.contains("data type only") &&
                 reason.contains("set_primary_assembly") && reason.contains("save_worksheet"),
                 reason);
      assertEquals("", WorksheetNamedGroupMatcher.unmatchedReason(ws, "unknown"));
   }
}
