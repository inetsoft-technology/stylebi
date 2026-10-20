/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.model.vs.HideColumnsDialogModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Bug #78852: hiding a Table column and showing it again must put it back where it was.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class HideColumnsDialogServicePositionTest {
   @BeforeEach
   void setUp() throws Exception {
      Viewsheet vs = new Viewsheet();
      table = new TableVSAssembly(vs, "Table1");
      vs.addAssembly(table);
      info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      ColumnSelection cols = new ColumnSelection();

      for(String name : List.of("A", "B", "C", "D", "E")) {
         cols.addAttribute(new ColumnRef(new AttributeRef(null, name)));
      }

      info.setColumnSelection(cols);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      ViewsheetService engine = mock(ViewsheetService.class);
      when(engine.getViewsheet(any(), any())).thenReturn(rvs);
      VSAssemblyInfoHandler handler = mock(VSAssemblyInfoHandler.class);

      doAnswer(inv -> {
         TableVSAssemblyInfo applied = inv.getArgument(1);
         info.setColumnSelection(applied.getColumnSelection());
         info.setHiddenColumns(applied.getHiddenColumns());
         return null;
      }).when(handler).apply(any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(),
                             anyBoolean(), any(), any(), any(), any(), any());

      service = new HideColumnsDialogService(engine, handler);
   }

   @Test
   void hideThenShowRestoresOriginalPosition() throws Exception {
      setVisible("C", false);
      assertEquals(List.of("A", "B", "D", "E"), shown());
      assertEquals(List.of("C"), hidden());
      assertEquals(List.of("A", "B", "C", "D", "E"), ordered());

      setVisible("C", true);
      assertEquals(List.of("A", "B", "C", "D", "E"), shown());
      assertTrue(hidden().isEmpty());
   }

   @Test
   void hideSeveralAndShowInAnyOrder() throws Exception {
      setVisible("C", false);
      setVisible("A", false);
      setVisible("E", false);
      assertEquals(List.of("A", "B", "C", "D", "E"), ordered());

      setVisible("C", true);
      assertEquals(List.of("B", "C", "D"), shown());
      setVisible("E", true);
      setVisible("A", true);
      assertEquals(List.of("A", "B", "C", "D", "E"), shown());
   }

   @Test
   void positionIsClampedWhenColumnsWereRemoved() throws Exception {
      setVisible("E", false);
      ColumnSelection cols = info.getColumnSelection();
      cols.removeAttribute(cols.getAttribute(0));
      cols.removeAttribute(cols.getAttribute(0));
      cols.removeAttribute(cols.getAttribute(0));
      assertEquals(List.of("D"), shown());

      setVisible("E", true);
      assertEquals(List.of("D", "E"), shown());
   }

   @Test
   void hiddenColumnWithoutRecordedPositionGoesLast() throws Exception {
      ColumnSelection cols = info.getColumnSelection();
      DataRef b = cols.getAttribute(1);
      cols.removeAttribute(b);
      ColumnSelection hide = new ColumnSelection();
      hide.addAttribute(b);
      info.setHiddenColumns(hide);

      assertEquals(List.of("A", "C", "D", "E", "B"), ordered());
      setVisible("B", true);
      assertEquals(List.of("A", "C", "D", "E", "B"), shown());
   }

   @Test
   void hiddenPositionRoundTripsThroughXml() throws Exception {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, "C"));
      ref.setHiddenPosition(2);
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      ref.writeXML(pw);
      pw.flush();

      Element tag = Tool.parseXML(new java.io.StringReader(sw.toString())).getDocumentElement();
      ColumnRef parsed = new ColumnRef();
      parsed.parseXML(tag);
      assertEquals(2, parsed.getHiddenPosition());
      assertEquals(2, ((ColumnRef) ref.clone()).getHiddenPosition());
   }

   @Test
   void xmlWithoutPositionKeepsDefault() throws Exception {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, "C"));
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      ref.writeXML(pw);
      pw.flush();
      assertFalse(sw.toString().contains("hiddenpos"));

      Element tag = Tool.parseXML(new java.io.StringReader(sw.toString())).getDocumentElement();
      ColumnRef parsed = new ColumnRef();
      parsed.parseXML(tag);
      assertEquals(-1, parsed.getHiddenPosition());
   }

   private void setVisible(String column, boolean visible) throws Exception {
      List<String> shown = new ArrayList<>(shown());
      List<String> hidden = new ArrayList<>(hidden());
      shown.remove(column);
      hidden.remove(column);
      (visible ? shown : hidden).add(column);
      service.setColumnOptionDialogModel(
         "rt", "Table1",
         HideColumnsDialogModel.builder().availableColumns(shown).hiddenColumns(hidden).build(),
         mock(Principal.class), null, null);
   }

   private List<String> shown() {
      return names(info.getColumnSelection());
   }

   private List<String> hidden() {
      return names(info.getHiddenColumns());
   }

   private List<String> ordered() {
      List<String> out = new ArrayList<>();

      for(DataRef ref : HideColumnsDialogService.getOrderedColumns(
         info.getColumnSelection(), info.getHiddenColumns()))
      {
         out.add(ref.getAttribute());
      }

      return out;
   }

   private static List<String> names(ColumnSelection cols) {
      List<String> out = new ArrayList<>();
      Enumeration<DataRef> e = cols.getAttributes();

      while(e.hasMoreElements()) {
         out.add(e.nextElement().getAttribute());
      }

      return out;
   }

   private TableVSAssembly table;
   private TableVSAssemblyInfo info;
   private HideColumnsDialogService service;
}
