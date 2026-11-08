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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.FormTableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.ThreadContext;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.viewsheet.event.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80768: a Submit click whose onClick is {@code Form.applyChanges(); saveWorksheet();}
 * persisted the edit, but the first runtime operation after the click still read the
 * pre-click table, because the sandbox kept serving the worksheet table's cached lens.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
class VSInputServiceSubmitSaveWorksheetTest {
   @BeforeEach
   void savePrincipal() {
      oldPrincipal = ThreadContext.getContextPrincipal();
   }

   @AfterEach
   void restorePrincipal() {
      ThreadContext.setContextPrincipal(oldPrincipal);
   }

   @Test
   void editSavedByTheSubmitIsReadByTheNextOperation() throws Exception {
      assertEquals("zzz1", clickSave("zzz1").getObject(1, 0),
                   "form lens right after the click");
   }

   /**
    * Warm the form lens and the worksheet table cache like a live session, click a Submit that
    * edits the form's first data row, applies and saves, and return the form lens the next
    * operation gets.
    */
   private FormTableLens clickSave(String value) throws Exception {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      // a small embedded table the form binds to, in the original and in the base worksheet
      String[] types = { XSchema.STRING, XSchema.STRING };
      Object[][] rows = { { "Name", "Qty" }, { "Router", "20" }, { "Stand", "12" } };

      for(Worksheet ws : new Worksheet[] { vs.getOriginalWorksheet(), vs.getBaseWorksheet() }) {
         EmbeddedTableAssembly mini = new EmbeddedTableAssembly(ws, EMBEDDED_TABLE);
         mini.setEmbeddedData(new XEmbeddedTable(types, rows));
         ws.addAssembly(mini);
      }

      TableVSAssembly table = new TableVSAssembly(vs, FORM);
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getInfo();
      info.setFormValue(true);
      info.setWriteBackValue(true);
      info.setSourceInfo(new SourceInfo(XSourceInfo.ASSET, null, EMBEDDED_TABLE));
      ColumnSelection cols = new ColumnSelection();

      for(int i = 0; i < types.length; i++) {
         ColumnRef col = new ColumnRef(new AttributeRef(null, (String) rows[0][i]));
         col.setDataType(types[i]);
         cols.addAttribute(col);
      }

      info.setColumnSelection(cols);
      vs.addAssembly(table);

      SubmitVSAssembly button = new SubmitVSAssembly(vs, "ApplyButton");
      SubmitVSAssemblyInfo binfo = (SubmitVSAssemblyInfo) button.getVSAssemblyInfo();
      binfo.setRefresh(true);
      binfo.setScriptEnabled(true);
      binfo.setOnClick(FORM + ".setObject(1, 0, '" + value + "'); " + FORM +
                       ".applyChanges(); saveWorksheet();");
      vs.addAssembly(button);

      box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);

      // warm the caches the way earlier operations of a live session do
      assertNotEquals(value, box.getFormTableLens(FORM).getObject(1, 0));

      VSInputService inputService = new VSInputService(
         objectService, coreLifecycleService, viewsheetService, mock(VSObjectPropertyService.class),
         null, null, null, null, null, null);
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);
      when(dispatcher.detach()).thenReturn(dispatcher);
      when(dispatcher.iterator()).thenAnswer(inv -> Collections.emptyIterator());
      inputService.onClick(vsResource.getRuntimeId(), "ApplyButton", "1", "1", "", false, null,
                           ThreadContext.getPrincipal(), dispatcher);

      assertEquals(value, ((EmbeddedTableAssembly) vs.getOriginalWorksheet()
         .getAssembly(EMBEDDED_TABLE)).getEmbeddedData().getObject(1, 0), "saved worksheet");
      return rvs.getViewsheetSandbox().orElseThrow().getFormTableLens(FORM);
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   private static final String EMBEDDED_TABLE = "Mini";
   private static final String FORM = "FormTable";

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   @Autowired CoreLifecycleService coreLifecycleService;
   @Autowired ViewsheetService viewsheetService;
   @Autowired VSObjectService objectService;

   private Principal oldPrincipal;
}
