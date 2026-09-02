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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.controller.ModifyCalculateFieldServiceProxy;
import inetsoft.web.binding.drm.CalculateRefModel;
import inetsoft.web.binding.event.ModifyCalculateFieldEvent;
import inetsoft.web.binding.model.ExpressionRefModel;
import inetsoft.web.wiz.binding.CalcFieldAgentService.CalcFieldRequest;
import inetsoft.web.wiz.binding.model.BindableField;
import inetsoft.web.wiz.binding.model.BindableTable;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class CalcFieldAgentServiceTest {
   private static Principal principal() {
      return () -> "admin";
   }

   /** A table listing with exactly one bindable table, "ORDERS". */
   private static BindableFieldsService fieldsServiceWithOrdersTable() throws Exception {
      BindableFieldsService fields = mock(BindableFieldsService.class);
      BindableTable orders = new BindableTable("ORDERS", null,
         List.of(new BindableField("DISCOUNT", "double", null)));
      when(fields.list(eq("rt1"), any(), any(Principal.class))).thenReturn(List.of(orders));
      return fields;
   }

   /** A SecurityEngine that grants every permission check -- the default for tests not exercising
   * the VIEWSHEET_CALCULATED_FIELD gate itself. */
   private static SecurityEngine allowingSecurityEngine() throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), any(ResourceType.class), anyString(),
         any(ResourceAction.class))).thenReturn(true);
      return securityEngine;
   }

   /** A session service whose mutate() runs the mutation immediately against runtime "rt1". */
   private static ViewsheetSessionService sessionsRunningAgainstRt1() throws Exception {
      Viewsheet vs = mock(Viewsheet.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());

      return sessions;
   }

   /**
    * {@code ModifyCalculateFieldService.modifyCalculateField} performs no permission check of its
    * own -- {@code ModifyCalculateFieldController} is the sole enforcement point for the native
    * Formula Editor dialog, gating on {@code VIEWSHEET_CALCULATED_FIELD}/{@code ACCESS}. Calling
    * the service directly, as this class does, must re-apply that same gate up front, before the
    * table/name validation, model building, or {@code sessions.mutate} -- an admin-restricted
    * agent must be refused before any of that runs, not merely before the proxy call.
    */
   @Test
   void refusesWhenCalculatedFieldPermissionIsDenied() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      BindableFieldsService fieldsService = mock(BindableFieldsService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), any(ResourceType.class), anyString(),
         any(ResourceAction.class))).thenReturn(false);

      CalcFieldAgentService service = new CalcFieldAgentService(
         sessions, fieldsService, proxy, securityEngine);

      CalcFieldRequest req = new CalcFieldRequest(
         "ORDERS", null, "NetTotal", null, "field['Total']", "double", false, true, false, true);

      assertThrows(SecurityException.class,
         () -> service.modify("tok", principal(), req, ""));

      verifyNoInteractions(proxy);
      verifyNoInteractions(sessions);
      verifyNoInteractions(fieldsService);
   }

   @Test
   void requiresTable() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldAgentService service = new CalcFieldAgentService(
         sessionsRunningAgainstRt1(), fieldsServiceWithOrdersTable(), proxy, allowingSecurityEngine());

      CalcFieldRequest req = new CalcFieldRequest(
         null, null, "NetTotal", null, "field['Total']", "double", false, true, false, true);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.modify("tok", principal(), req, ""));
      assertTrue(thrown.getMessage().contains("table"), thrown.getMessage());
      verifyNoInteractions(proxy);
   }

   @Test
   void requiresName() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldAgentService service = new CalcFieldAgentService(
         sessionsRunningAgainstRt1(), fieldsServiceWithOrdersTable(), proxy, allowingSecurityEngine());

      CalcFieldRequest req = new CalcFieldRequest(
         "ORDERS", null, "", null, "field['Total']", "double", false, true, false, true);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.modify("tok", principal(), req, ""));
      assertTrue(thrown.getMessage().contains("name"), thrown.getMessage());
      verifyNoInteractions(proxy);
   }

   @Test
   void requiresExpressionUnlessRemoving() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldAgentService service = new CalcFieldAgentService(
         sessionsRunningAgainstRt1(), fieldsServiceWithOrdersTable(), proxy, allowingSecurityEngine());

      CalcFieldRequest req = new CalcFieldRequest(
         "ORDERS", null, "NetTotal", null, null, null, null, null, false, true);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.modify("tok", principal(), req, ""));
      assertTrue(thrown.getMessage().contains("expression"), thrown.getMessage());
      verifyNoInteractions(proxy);
   }

   @Test
   void refusesATableTheListingDoesNotHave() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldAgentService service = new CalcFieldAgentService(
         sessionsRunningAgainstRt1(), fieldsServiceWithOrdersTable(), proxy, allowingSecurityEngine());

      CalcFieldRequest req = new CalcFieldRequest(
         "NO_SUCH_TABLE", null, "NetTotal", null, "field['Total']", "double", false, true, false,
         true);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.modify("tok", principal(), req, ""));
      assertTrue(thrown.getMessage().contains("NO_SUCH_TABLE"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("ORDERS"), thrown.getMessage());
      verifyNoInteractions(proxy);
   }

   /**
    * {@code Viewsheet.calcmap} is a plain, case-sensitive map keyed by table name. Matching
    * "orders" against the listed "ORDERS" case-insensitively and then storing the calc field
    * under the caller's own casing ("orders") would create it under a key no chart bound to the
    * real "ORDERS" table ever looks up -- silently orphaned, not merely a cosmetic mismatch. The
    * event sent downstream must always carry the LISTING's canonical casing.
    */
   @Test
   void resolvesTableNameToTheListingsCanonicalCasing() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldAgentService service = new CalcFieldAgentService(
         sessionsRunningAgainstRt1(), fieldsServiceWithOrdersTable(), proxy, allowingSecurityEngine());
      Principal agent = principal();

      CalcFieldRequest req = new CalcFieldRequest(
         "orders", null, "NetTotal", null, "field['Total']", "double", false, true, false, true);

      service.modify("tok", agent, req, "");

      ArgumentCaptor<ModifyCalculateFieldEvent> captor =
         ArgumentCaptor.forClass(ModifyCalculateFieldEvent.class);
      verify(proxy).modifyCalculateField(eq("rt1"), captor.capture(), eq(agent), any(), eq(""));
      assertEquals("ORDERS", captor.getValue().tableName(),
         "must store under the listing's canonical casing, not the caller's raw 'orders'");
   }

   /**
    * {@code table} and {@code assembly} are independent by this class's own contract -- passing
    * an {@code assembly} must not narrow the bindable-table check to THAT assembly's own current
    * source, which would wrongly refuse a real worksheet table whenever the assembly happens to
    * be bound elsewhere. Mirrors the reasoning already applied in
    * {@code SelectionBindingService.resolveTable}.
    */
   @Test
   void tableValidationIsNotScopedToTheGivenAssembly() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      BindableFieldsService fields = mock(BindableFieldsService.class);
      BindableTable orders = new BindableTable("ORDERS", null,
         List.of(new BindableField("DISCOUNT", "double", null)));
      // Scoped to "Chart1" (a different, already-bound assembly) the listing would be narrowed to
      // whatever THAT chart is bound to -- stubbed empty here so a scoped call fails loudly.
      when(fields.list(eq("rt1"), eq("Chart1"), any(Principal.class))).thenReturn(List.of());
      // Unscoped (assembly: null), the listing is worksheet-wide and finds ORDERS.
      when(fields.list(eq("rt1"), isNull(), any(Principal.class))).thenReturn(List.of(orders));

      CalcFieldAgentService service = new CalcFieldAgentService(
         sessionsRunningAgainstRt1(), fields, proxy, allowingSecurityEngine());
      Principal agent = principal();

      CalcFieldRequest req = new CalcFieldRequest("ORDERS", "Chart1", "NetTotal", null,
         "field['Total']", "double", false, true, false, true);

      service.modify("tok", agent, req, "");

      verify(proxy).modifyCalculateField(eq("rt1"), any(), eq(agent), any(), eq(""));
   }

   @Test
   void createBuildsAnEventWithTheGivenNameAndSensibleDefaults() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldAgentService service = new CalcFieldAgentService(
         sessionsRunningAgainstRt1(), fieldsServiceWithOrdersTable(), proxy, allowingSecurityEngine());
      Principal agent = principal();

      CalcFieldRequest req = new CalcFieldRequest("ORDERS", "Chart1", "NetTotal", null,
         "field['Total']*(1-field['Discount'])", "double", false, null, false, true);

      service.modify("tok", agent, req, "link");

      ArgumentCaptor<ModifyCalculateFieldEvent> captor =
         ArgumentCaptor.forClass(ModifyCalculateFieldEvent.class);
      verify(proxy).modifyCalculateField(eq("rt1"), captor.capture(), eq(agent), any(), eq("link"));

      ModifyCalculateFieldEvent event = captor.getValue();
      assertTrue(event.create());
      assertFalse(event.remove());
      assertEquals("ORDERS", event.tableName());
      assertEquals("Chart1", event.name());
      assertEquals("NetTotal", event.refName());

      CalculateRefModel calc = event.calculateRef();
      assertNotNull(calc);
      assertTrue(calc.isBaseOnDetail(), "baseOnDetail should default to true when omitted");
      assertFalse(calc.isSql());
      assertEquals("double", calc.getDataType());
      ExpressionRefModel expr = (ExpressionRefModel) calc.getDataRefModel();
      assertEquals("NetTotal", expr.getName());
      assertEquals("field['Total']*(1-field['Discount'])", expr.getExp());
   }

   @Test
   void editWithoutNewNameKeepsTheSameName() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldAgentService service = new CalcFieldAgentService(
         sessionsRunningAgainstRt1(), fieldsServiceWithOrdersTable(), proxy, allowingSecurityEngine());
      Principal agent = principal();

      CalcFieldRequest req = new CalcFieldRequest("ORDERS", null, "NetTotal", null,
         "field['Total']*0.9", "double", false, true, false, false);

      service.modify("tok", agent, req, "");

      ArgumentCaptor<ModifyCalculateFieldEvent> captor =
         ArgumentCaptor.forClass(ModifyCalculateFieldEvent.class);
      verify(proxy).modifyCalculateField(eq("rt1"), captor.capture(), eq(agent), any(), eq(""));

      ModifyCalculateFieldEvent event = captor.getValue();
      assertFalse(event.create());
      assertEquals("NetTotal", event.refName());
      ExpressionRefModel expr = (ExpressionRefModel) event.calculateRef().getDataRefModel();
      assertEquals("NetTotal", expr.getName());
   }

   @Test
   void editWithNewNameRenames() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldAgentService service = new CalcFieldAgentService(
         sessionsRunningAgainstRt1(), fieldsServiceWithOrdersTable(), proxy, allowingSecurityEngine());
      Principal agent = principal();

      CalcFieldRequest req = new CalcFieldRequest("ORDERS", null, "NetTotal", "NetAfterDiscount",
         "field['Total']*0.9", "double", false, true, false, false);

      service.modify("tok", agent, req, "");

      ArgumentCaptor<ModifyCalculateFieldEvent> captor =
         ArgumentCaptor.forClass(ModifyCalculateFieldEvent.class);
      verify(proxy).modifyCalculateField(eq("rt1"), captor.capture(), eq(agent), any(), eq(""));

      ModifyCalculateFieldEvent event = captor.getValue();
      // refName carries the OLD name, so the service can find the existing calc field...
      assertEquals("NetTotal", event.refName());
      // ...and the new CalculateRef carries the NEW name, so the rename is actually requested.
      ExpressionRefModel expr = (ExpressionRefModel) event.calculateRef().getDataRefModel();
      assertEquals("NetAfterDiscount", expr.getName());
   }

   @Test
   void removeSendsNoCalculateRefAndSkipsExpressionValidation() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldAgentService service = new CalcFieldAgentService(
         sessionsRunningAgainstRt1(), fieldsServiceWithOrdersTable(), proxy, allowingSecurityEngine());
      Principal agent = principal();

      CalcFieldRequest req = new CalcFieldRequest(
         "ORDERS", null, "NetTotal", null, null, null, null, null, true, false);

      service.modify("tok", agent, req, "");

      ArgumentCaptor<ModifyCalculateFieldEvent> captor =
         ArgumentCaptor.forClass(ModifyCalculateFieldEvent.class);
      verify(proxy).modifyCalculateField(eq("rt1"), captor.capture(), eq(agent), any(), eq(""));

      ModifyCalculateFieldEvent event = captor.getValue();
      assertTrue(event.remove());
      assertEquals("NetTotal", event.refName());
      assertNull(event.calculateRef());
   }

   /**
    * A caller editing a field previously created with {@code sql: true}/{@code baseOnDetail:
    * false} that omits both (a plain rename or expression-only tweak) must NOT have them
    * silently reset to the create-time defaults ({@code sql: false}/{@code baseOnDetail: true}) --
    * the omitted fields mean "leave as-is" on edit, not "reset".
    */
   @Test
   void editOmittingSqlAndBaseOnDetailPreservesTheExistingCalcFieldsSettings() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      Viewsheet vs = mock(Viewsheet.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      CalculateRef existing = mock(CalculateRef.class);
      when(existing.isSQL()).thenReturn(true);
      when(existing.isBaseOnDetail()).thenReturn(false);
      when(existing.getDataType()).thenReturn("double");
      when(vs.getCalcField(eq("ORDERS"), eq("NetTotal"))).thenReturn(existing);

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());

      CalcFieldAgentService service = new CalcFieldAgentService(
         sessions, fieldsServiceWithOrdersTable(), proxy, allowingSecurityEngine());
      Principal agent = principal();

      // Edit: rename only, sql/baseOnDetail/dataType all omitted (null).
      CalcFieldRequest req = new CalcFieldRequest("ORDERS", null, "NetTotal", "NetAfterDiscount",
         "field['Total']*0.9", null, null, null, false, false);

      service.modify("tok", agent, req, "");

      ArgumentCaptor<ModifyCalculateFieldEvent> captor =
         ArgumentCaptor.forClass(ModifyCalculateFieldEvent.class);
      verify(proxy).modifyCalculateField(eq("rt1"), captor.capture(), eq(agent), any(), eq(""));

      CalculateRefModel calc = captor.getValue().calculateRef();
      assertNotNull(calc);
      assertTrue(calc.isSql(), "sql:true must be preserved, not reset to the create default");
      assertFalse(calc.isBaseOnDetail(),
         "baseOnDetail:false must be preserved, not reset to the create default");
      assertEquals("double", calc.getDataType(), "dataType must be preserved when omitted");
   }

   @Test
   void baseOnDetailFalseIsPreserved() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldAgentService service = new CalcFieldAgentService(
         sessionsRunningAgainstRt1(), fieldsServiceWithOrdersTable(), proxy, allowingSecurityEngine());
      Principal agent = principal();

      CalcFieldRequest req = new CalcFieldRequest("ORDERS", null, "AvgDiscountPct", null,
         "sum(field['Discount'])/sum(field['Total'])", "double", false, false, false, true);

      service.modify("tok", agent, req, "");

      ArgumentCaptor<ModifyCalculateFieldEvent> captor =
         ArgumentCaptor.forClass(ModifyCalculateFieldEvent.class);
      verify(proxy).modifyCalculateField(eq("rt1"), captor.capture(), eq(agent), any(), eq(""));

      assertFalse(captor.getValue().calculateRef().isBaseOnDetail());
   }
}
