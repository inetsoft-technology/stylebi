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
package inetsoft.web.composer.ws.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.MessageException;
import inetsoft.web.composer.model.ws.TableUnpivotDialogModel;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.composer.ws.event.WSAssemblyEvent;
import inetsoft.web.composer.ws.event.WSUnpivotDialogEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for PR #5070 round-1 review feedback (bug 76517/WBS-036):
 * {@code changeUnpivotTableRowHeaders} -- the Composer UI's own call site for the header-shrink
 * type-conflict guard -- had no bound check on the incoming {@code level} at all, unlike the
 * agent API's {@code WorksheetEditService.editUnpivot}. An out-of-range level fell straight
 * through to {@code AssetUtil.checkUnpivotShrinkTypeConflict} and threw an uncaught
 * {@code ArrayIndexOutOfBoundsException} instead of the friendly {@link MessageException} this
 * call site's own shrink-type-conflict guard already gives for other validation failures.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableUnpivotDialogServiceTest {
   private static ColumnRef col(String name, String dataType) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, name));
      ref.setDataType(dataType);
      ref.setVisible(true);
      return ref;
   }

   private static EmbeddedTableAssembly table(Worksheet ws, String name, ColumnRef... cols) {
      EmbeddedTableAssembly t = new EmbeddedTableAssembly(ws, name);
      ColumnSelection cs = new ColumnSelection();

      for(ColumnRef c : cols) {
         cs.addAttribute(c);
      }

      t.setColumnSelection(cs, false);
      return t;
   }

   private static void setField(Object target, Class<?> declaringClass, String name, Object value)
      throws Exception
   {
      Field field = declaringClass.getDeclaredField(name);
      field.setAccessible(true);
      field.set(target, value);
   }

   /** {@link WSUnpivotDialogEvent}/{@link TableUnpivotDialogModel} have no setters (Jackson
    * populates their private fields directly off the wire), so tests do the same via reflection.
    */
   private static WSUnpivotDialogEvent event(String assemblyName, int level) throws Exception {
      TableUnpivotDialogModel model = new TableUnpivotDialogModel();
      setField(model, TableUnpivotDialogModel.class, "level", level);

      WSUnpivotDialogEvent event = new WSUnpivotDialogEvent();
      setField(event, WSUnpivotDialogEvent.class, "model", model);
      setField(event, WSAssemblyEvent.class, "assemblyName", assemblyName);
      return event;
   }

   private static RuntimeWorksheet mockRws(Worksheet ws) {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      return rws;
   }

   private TableUnpivotDialogService service(RuntimeWorksheet rws) throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getWorksheet(anyString(), any())).thenReturn(rws);

      return new TableUnpivotDialogService(
         viewsheetService, mock(VSLayoutService.class), mock(DataSourceRegistry.class));
   }

   @Test
   void changeUnpivotTableRowHeadersRejectsNegativeLevelWithoutCrashing() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly src = table(ws, "S",
         col("region", XSchema.STRING), col("category", XSchema.STRING),
         col("q1", XSchema.DOUBLE), col("q2", XSchema.DOUBLE));
      ws.addAssembly(src);

      UnpivotTableAssembly table = new UnpivotTableAssembly(ws, "U", src);
      table.setLiveData(true);
      table.setHeaderColumns(2);
      ws.addAssembly(table);

      TableUnpivotDialogService svc = service(mockRws(ws));
      Principal principal = mock(Principal.class);
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);

      // This is the reviewer's exact repro shape: a negative level reaching the same helper that
      // editUnpivot's own headerColumns < 0 check rejects before ever calling it. Before this
      // fix, changeUnpivotTableRowHeaders had no such guard and this threw
      // ArrayIndexOutOfBoundsException instead.
      MessageException ex = assertThrows(MessageException.class, () ->
         svc.changeUnpivotTableRowHeaders("Worksheet/ws1", event("U", -1), principal, dispatcher));
      assertTrue(ex.getMessage().contains("-1"), ex.getMessage());

      assertEquals(2, table.getHeaderColumns(),
         "a rejected headerColumns change must not be applied");
   }

   @Test
   void changeUnpivotTableRowHeadersRejectsLevelAtOrPastColumnCount() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly src = table(ws, "S",
         col("region", XSchema.STRING), col("q1", XSchema.DOUBLE), col("q2", XSchema.DOUBLE));
      ws.addAssembly(src);

      UnpivotTableAssembly table = new UnpivotTableAssembly(ws, "U", src);
      table.setLiveData(true);
      table.setHeaderColumns(1);
      ws.addAssembly(table);

      TableUnpivotDialogService svc = service(mockRws(ws));
      Principal principal = mock(Principal.class);
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);

      MessageException ex = assertThrows(MessageException.class, () ->
         svc.changeUnpivotTableRowHeaders("Worksheet/ws1", event("U", 3), principal, dispatcher));
      assertTrue(ex.getMessage().contains("3"), ex.getMessage());

      assertEquals(1, table.getHeaderColumns(),
         "a rejected headerColumns change must not be applied");
   }
}
