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
import inetsoft.test.*;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AggregateInfo;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// A real ExpressionRef (see calc()) loads AggregateFormula, whose static init reads SreeEnv and so
// needs the Spring context and a SREE home.
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
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

   /**
    * A session service whose mutate() runs the mutation immediately against runtime "rt1", whose
    * viewsheet already has one calc field, "NetTotal", on "ORDERS" (so edit/remove of it resolve).
    */
   private static ViewsheetSessionService sessionsRunningAgainstRt1() throws Exception {
      Viewsheet vs = mock(Viewsheet.class);
      CalculateRef netTotal = mock(CalculateRef.class);
      when(netTotal.getName()).thenReturn("NetTotal");
      when(vs.getCalcField(eq("ORDERS"), eq("NetTotal"))).thenReturn(netTotal);
      when(vs.getCalcFields(eq("ORDERS"))).thenReturn(new CalculateRef[] { netTotal });
      // a mode flip looks for the assemblies bound to the field; none here
      when(vs.getAssemblies()).thenReturn(new Assembly[0]);
      return sessionsRunningAgainstRt1(vs);
   }

   /** A session service whose mutate() runs the mutation immediately against the given viewsheet. */
   private static ViewsheetSessionService sessionsRunningAgainstRt1(Viewsheet vs) throws Exception {
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

   /**
    * Bug #79192: worksheet tables ORDERS and Orders are two tables, so a name matching them only
    * by case is refused naming both instead of storing the field under whichever sorted first.
    */
   @Test
   void refusesATableNameThatMatchesSeveralTablesOnlyByCase() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      BindableFieldsService fields = mock(BindableFieldsService.class);
      when(fields.list(eq("rt1"), any(), any(Principal.class))).thenReturn(List.of(
         new BindableTable("ORDERS", null, List.of(new BindableField("DISCOUNT", "double", null))),
         new BindableTable("Orders", null, List.of(new BindableField("TOTAL", "double", null)))));
      CalcFieldAgentService service = new CalcFieldAgentService(
         sessionsRunningAgainstRt1(), fields, proxy, allowingSecurityEngine());

      CalcFieldRequest req = new CalcFieldRequest(
         "orders", null, "NetTotal", null, "field['Total']", "double", false, true, false, true);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.modify("tok", principal(), req, ""));
      assertTrue(thrown.getMessage().contains("ORDERS") && thrown.getMessage().contains("Orders"),
                 thrown.getMessage());
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

   /**
    * Bug #77015 (DCG-020 a): nothing downstream infers a calc field's type from its expression --
    * a null dataType on create silently became a string dimension, even for numeric arithmetic.
    */
   @Test
   void createRequiresDataType() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldAgentService service = new CalcFieldAgentService(
         sessionsRunningAgainstRt1(), fieldsServiceWithOrdersTable(), proxy, allowingSecurityEngine());

      CalcFieldRequest req = new CalcFieldRequest(
         "ORDERS", null, "Doubled", null, "field['Total'] * 2", null, null, null, false, true);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.modify("tok", principal(), req, ""));
      assertTrue(thrown.getMessage().contains("dataType"), thrown.getMessage());
      verifyNoInteractions(proxy);
   }

   /**
    * Redmine #78437: ColumnRef.setDataType is case-sensitive and stores "string" for any
    * spelling it does not know, so "Double" became a string dimension that defaulted to Count.
    */
   @Test
   void refusesAnUnknownDataTypeSpelling() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      CalcFieldAgentService service = new CalcFieldAgentService(
         sessions, fieldsServiceWithOrdersTable(), proxy, allowingSecurityEngine());

      CalcFieldRequest req = new CalcFieldRequest(
         "ORDERS", null, "Doubled", null, "field['Total'] * 2", "Double", null, null, false, true);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.modify("tok", principal(), req, ""));
      assertTrue(thrown.getMessage().contains("dataType"), thrown.getMessage());
      verifyNoInteractions(proxy);
      verifyNoInteractions(sessions);
   }

   @Test
   void refusesAnUnknownDataTypeSpellingOnEditToo() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      CalcFieldAgentService service = new CalcFieldAgentService(
         sessions, fieldsServiceWithOrdersTable(), proxy, allowingSecurityEngine());

      CalcFieldRequest req = new CalcFieldRequest(
         "ORDERS", null, "Doubled", null, "field['Total'] * 2", "number", null, null, false,
         false);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.modify("tok", principal(), req, ""));
      assertTrue(thrown.getMessage().contains("dataType"), thrown.getMessage());
      verifyNoInteractions(proxy);
      verifyNoInteractions(sessions);
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
         "orders", null, "Fresh", null, "field['Total']", "double", false, true, false, true);

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

      CalcFieldRequest req = new CalcFieldRequest("ORDERS", "Chart1", "Fresh", null,
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

      CalcFieldRequest req = new CalcFieldRequest("ORDERS", "Chart1", "Fresh", null,
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
      assertEquals("Fresh", event.refName());

      CalculateRefModel calc = event.calculateRef();
      assertNotNull(calc);
      assertTrue(calc.isBaseOnDetail(), "baseOnDetail should default to true when omitted");
      assertFalse(calc.isSql());
      assertEquals("double", calc.getDataType());
      ExpressionRefModel expr = (ExpressionRefModel) calc.getDataRefModel();
      assertEquals("Fresh", expr.getName());
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
    * Bug #76952: the native remove path silently returns for a name not on the table, so a typo'd
    * remove ("Net Sale" for "Net Sales") used to report success while the real field stayed
    * bound. It must be refused, naming the missing field and listing the table's real ones.
    */
   @Test
   void removeRefusesACalcFieldNameTheTableDoesNotHave() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldAgentService service = new CalcFieldAgentService(
         sessionsRunningAgainstRt1(), fieldsServiceWithOrdersTable(), proxy, allowingSecurityEngine());

      CalcFieldRequest req = new CalcFieldRequest(
         "ORDERS", null, "NetTota", null, null, null, null, null, true, false);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.modify("tok", principal(), req, ""));
      assertTrue(thrown.getMessage().contains("'NetTota'"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("'NetTotal'"), thrown.getMessage());
      verifyNoInteractions(proxy);
   }

   @Test
   void removeOnATableWithNoCalcFieldsSaysSo() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldAgentService service = new CalcFieldAgentService(
         sessionsRunningAgainstRt1(mock(Viewsheet.class)), fieldsServiceWithOrdersTable(), proxy,
         allowingSecurityEngine());

      CalcFieldRequest req = new CalcFieldRequest(
         "ORDERS", null, "NoSuchField", null, null, null, null, null, true, false);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.modify("tok", principal(), req, ""));
      assertTrue(thrown.getMessage().contains("'NoSuchField'"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("(none)"), thrown.getMessage());
      verifyNoInteractions(proxy);
   }

   /**
    * Bug #76952 (edit path): with create:false the native service silently returns without
    * editing anything for a name not on the table, so a typo'd edit would be reported as a
    * success while the intended field kept its old formula.
    */
   @Test
   void editRefusesACalcFieldNameTheTableDoesNotHave() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldAgentService service = new CalcFieldAgentService(
         sessionsRunningAgainstRt1(), fieldsServiceWithOrdersTable(), proxy, allowingSecurityEngine());

      CalcFieldRequest req = new CalcFieldRequest("ORDERS", null, "NetTota", null,
         "field['Total']*0.9", null, null, null, false, false);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.modify("tok", principal(), req, ""));
      assertTrue(thrown.getMessage().contains("'NetTota'"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("'NetTotal'"), thrown.getMessage());
      verifyNoInteractions(proxy);
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

   /**
    * VBS-003 review round 1 (important, confidence 85): {@code sessions.mutate} now returns any
    * warnings its dispatcher captured (a caught post-write refresh failure inside
    * {@code ModifyCalculateFieldService}), but nothing forwarded them out of {@code modify} --
    * so the wiz agent got a clean, indistinguishable-from-success return for a call that
    * actually degraded. This asserts {@code modify} returns exactly what {@code mutate} reports,
    * not that {@code mutate} was merely called.
    */
   @Test
   void modifyReturnsTheWarningsMutateCaptured() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      Viewsheet vs = mock(Viewsheet.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      List<String> expectedWarnings = List.of(
         "Crosstab1: could not refresh the crosstab's aggregate info after the write; the " +
         "calc field was saved");

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.mutate(anyString(), any(Principal.class), any())).thenAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return expectedWarnings;
      });

      CalcFieldAgentService service = new CalcFieldAgentService(
         sessions, fieldsServiceWithOrdersTable(), proxy, allowingSecurityEngine());

      CalcFieldRequest req = new CalcFieldRequest(
         "ORDERS", "Crosstab1", "NetTotal", null, "field['Total']", "double", false, true, false,
         true);

      List<String> warnings = service.modify("tok", principal(), req, "").warnings();

      assertEquals(expectedWarnings, warnings);
   }

   @Test
   void baseOnDetailFalseIsPreserved() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldAgentService service = new CalcFieldAgentService(
         sessionsOver(realVsWithAggregates("Discount", "Total")), fieldsServiceWithOrdersTable(),
         proxy, allowingSecurityEngine());
      Principal agent = principal();

      // The documented aggregate form: the outer-call sum(field['Discount']) this test used to
      // send is refused since Bug #76480 (see refusesTheOuterCallFormWhenBaseOnDetailIsFalse).
      CalcFieldRequest req = new CalcFieldRequest("ORDERS", null, "AvgDiscountPct", null,
         "field['Sum(Discount)']/field['Sum(Total)']", "double", false, false, false, true);

      service.modify("tok", agent, req, "");

      ArgumentCaptor<ModifyCalculateFieldEvent> captor =
         ArgumentCaptor.forClass(ModifyCalculateFieldEvent.class);
      verify(proxy).modifyCalculateField(eq("rt1"), captor.capture(), eq(agent), any(), eq(""));

      assertFalse(captor.getValue().calculateRef().isBaseOnDetail());
   }

   private static CalculateRef calc(String name, String expression, boolean baseOnDetail) {
      ExpressionRef eref = new ExpressionRef(null, name);
      eref.setExpression(expression);
      CalculateRef calc = new CalculateRef(baseOnDetail);
      calc.setDataRef(eref);
      return calc;
   }

   private static ViewsheetSessionService sessionsOver(Viewsheet vs) throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.mutate(anyString(), any(Principal.class), any())).thenAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return List.of();
      });
      return sessions;
   }

   /**
    * Bug #76951 (VCF-002): a rename rewrites the other calc fields that reference the old name,
    * and the caller has no tool that reads a calc field's expression back -- so the response must
    * name them. Only the actual dependents are named, not every calc field on the table.
    */
   @Test
   void renameReportsTheDependentCalcFieldsThatReferenceTheOldName() throws Exception {
      Viewsheet vs = mock(Viewsheet.class);
      CalculateRef netSales = calc("Net Sales", "field['Total'] * (1 - field['Discount'])", true);
      CalculateRef share = calc("Discount Share",
         "(field['Sum(Total)'] - field['Sum(Net Sales)']) / field['Sum(Total)']", false);
      CalculateRef unrelated = calc("Tax", "field['Total'] * 0.08", true);
      when(vs.getCalcField("ORDERS", "Net Sales")).thenReturn(netSales);
      when(vs.getCalcFields("ORDERS")).thenReturn(new CalculateRef[] { netSales, share, unrelated });

      CalcFieldAgentService service = new CalcFieldAgentService(
         sessionsOver(vs), fieldsServiceWithOrdersTable(),
         mock(ModifyCalculateFieldServiceProxy.class), allowingSecurityEngine());

      CalcFieldRequest req = new CalcFieldRequest("ORDERS", null, "Net Sales", "Net Revenue",
         "field['Total'] * (1 - field['Discount'])", null, null, true, false, false);

      assertEquals(List.of("Discount Share"),
         service.modify("tok", principal(), req, "").rewrittenDependents());
   }

   @Test
   void editWithoutRenameReportsNoRewrittenDependents() throws Exception {
      Viewsheet vs = mock(Viewsheet.class);
      CalculateRef netSales = calc("Net Sales", "field['Total']", true);
      CalculateRef share = calc("Discount Share", "field['Sum(Net Sales)']", false);
      when(vs.getCalcField("ORDERS", "Net Sales")).thenReturn(netSales);
      when(vs.getCalcFields("ORDERS")).thenReturn(new CalculateRef[] { netSales, share });

      CalcFieldAgentService service = new CalcFieldAgentService(
         sessionsOver(vs), fieldsServiceWithOrdersTable(),
         mock(ModifyCalculateFieldServiceProxy.class), allowingSecurityEngine());

      CalcFieldRequest req = new CalcFieldRequest("ORDERS", null, "Net Sales", null,
         "field['Total'] * 0.9", null, null, null, false, false);

      assertTrue(service.modify("tok", principal(), req, "").rewrittenDependents().isEmpty());
   }

   /**
    * Bug #78193 (VCF-005): removing a calc field another calc field's formula references used to
    * commit and silently break the dependent. It must be refused before anything mutates, naming
    * every dependent and saying to remove or edit them first.
    */
   @Test
   void removeRefusesWhenAnAggregateCalcFieldReferencesIt() throws Exception {
      Viewsheet vs = mock(Viewsheet.class);
      CalculateRef netSales = calc("Net Sales", "field['Total'] * (1 - field['Discount'])", true);
      CalculateRef share = calc("Discount Share",
         "(field['Sum(Total)'] - field['Sum(Net Sales)']) / field['Sum(Total)']", false);
      CalculateRef margin = calc("Margin", "field['Net Sales'] * 0.1", true);
      CalculateRef tax = calc("Tax", "field['Total'] * 0.08", true);
      when(vs.getCalcField("ORDERS", "Net Sales")).thenReturn(netSales);
      when(vs.getCalcFields("ORDERS")).thenReturn(new CalculateRef[] { netSales, share, margin, tax });
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);

      CalcFieldRequest req = new CalcFieldRequest(
         "ORDERS", null, "Net Sales", null, null, null, null, null, true, false);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> serviceOver(vs, proxy).modify("tok", principal(), req, ""));
      String msg = thrown.getMessage();
      assertTrue(msg.contains("'Net Sales'"), msg);
      assertTrue(msg.contains("'Discount Share'"), msg);
      assertTrue(msg.contains("'Margin'"), msg);
      assertFalse(msg.contains("'Tax'"), msg);
      assertTrue(msg.contains("remove or edit"), msg);
      verify(proxy, never()).modifyCalculateField(any(), any(), any(), any(), any());
      verifyNoInteractions(proxy);
   }

   @Test
   void removeRefusesWhenADetailCalcFieldReferencesItByFieldAccessor() throws Exception {
      Viewsheet vs = mock(Viewsheet.class);
      CalculateRef netSales = calc("Net Sales", "field['Total']", true);
      CalculateRef margin = calc("Margin", "field['Net Sales'] * 0.1", true);
      when(vs.getCalcField("ORDERS", "Net Sales")).thenReturn(netSales);
      when(vs.getCalcFields("ORDERS")).thenReturn(new CalculateRef[] { netSales, margin });
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);

      CalcFieldRequest req = new CalcFieldRequest(
         "ORDERS", null, "Net Sales", null, null, null, null, null, true, false);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> serviceOver(vs, proxy).modify("tok", principal(), req, ""));
      assertTrue(thrown.getMessage().contains("'Margin'"), thrown.getMessage());
      verifyNoInteractions(proxy);
   }

   @Test
   void removeOfAnUnreferencedCalcFieldStillSucceeds() throws Exception {
      Viewsheet vs = mock(Viewsheet.class);
      CalculateRef netSales = calc("Net Sales", "field['Total']", true);
      CalculateRef share = calc("Discount Share", "field['Sum(Net Sales)']", false);
      CalculateRef tax = calc("Tax", "field['Total'] * 0.08", true);
      when(vs.getCalcField("ORDERS", "Tax")).thenReturn(tax);
      when(vs.getCalcFields("ORDERS")).thenReturn(new CalculateRef[] { netSales, share, tax });
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);

      CalcFieldRequest req = new CalcFieldRequest(
         "ORDERS", null, "Tax", null, null, null, null, null, true, false);

      serviceOver(vs, proxy).modify("tok", principal(), req, "");

      verify(proxy).modifyCalculateField(eq("rt1"), any(), any(), any(), eq(""));
   }

   @Test
   void removeIsNotBlockedBySimilarlyNamedCalcField() throws Exception {
      Viewsheet vs = mock(Viewsheet.class);
      CalculateRef netSales = calc("Net Sales", "field['Total']", true);
      CalculateRef netSales2 = calc("Net Sales 2", "field['Total'] * 2", true);
      CalculateRef other = calc("Other", "field['Sum(Net Sales 2)']", false);
      when(vs.getCalcField("ORDERS", "Net Sales")).thenReturn(netSales);
      when(vs.getCalcFields("ORDERS")).thenReturn(new CalculateRef[] { netSales, netSales2, other });
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);

      CalcFieldRequest req = new CalcFieldRequest(
         "ORDERS", null, "Net Sales", null, null, null, null, null, true, false);

      serviceOver(vs, proxy).modify("tok", principal(), req, "");

      verify(proxy).modifyCalculateField(eq("rt1"), any(), any(), any(), eq(""));
   }

   /** Bug #78441: a dot-form or case-different reference is a dependent on remove. */
   @Test
   void removeRefusesWhenADotFormOrCaseDifferentReferenceReadsIt() throws Exception {
      Viewsheet vs = mock(Viewsheet.class);
      CalculateRef duty = calc("Duty", "field['Total']", true);
      CalculateRef dot = calc("Dot", "field.Duty * 2", true);
      CalculateRef lower = calc("Lower", "field['duty'] * 3", true);
      CalculateRef other = calc("Other", "field.Total", true);
      when(vs.getCalcField("ORDERS", "Duty")).thenReturn(duty);
      when(vs.getCalcFields("ORDERS")).thenReturn(new CalculateRef[] { duty, dot, lower, other });
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);

      CalcFieldRequest req = new CalcFieldRequest(
         "ORDERS", null, "Duty", null, null, null, null, null, true, false);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> serviceOver(vs, proxy).modify("tok", principal(), req, ""));
      String msg = thrown.getMessage();
      assertTrue(msg.contains("'Dot'"), msg);
      assertTrue(msg.contains("'Lower'"), msg);
      assertFalse(msg.contains("'Other'"), msg);
      verifyNoInteractions(proxy);
   }

   @Test
   void removeRefusesWhenACaseDifferentReferenceIsAmbiguous() throws Exception {
      Viewsheet vs = mock(Viewsheet.class);
      CalculateRef duty = calc("Duty", "field['Total']", true);
      CalculateRef twin = calc("duty", "field['Total']", true);
      CalculateRef amb = calc("Amb", "field['DUTY']", true);
      when(vs.getCalcField("ORDERS", "Duty")).thenReturn(duty);
      when(vs.getCalcFields("ORDERS")).thenReturn(new CalculateRef[] { duty, twin, amb });
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);

      CalcFieldRequest req = new CalcFieldRequest(
         "ORDERS", null, "Duty", null, null, null, null, null, true, false);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> serviceOver(vs, proxy).modify("tok", principal(), req, ""));
      assertTrue(thrown.getMessage().contains("'Amb'"), thrown.getMessage());
      verifyNoInteractions(proxy);
   }

   @Test
   void removeIsNotBlockedByAReferenceThatNamesAnotherFieldExactly() throws Exception {
      Viewsheet vs = mock(Viewsheet.class);
      CalculateRef duty = calc("Duty", "field['Total']", true);
      CalculateRef twin = calc("duty", "field['Total']", true);
      CalculateRef reader = calc("Reader", "field['duty']", true);
      when(vs.getCalcField("ORDERS", "Duty")).thenReturn(duty);
      when(vs.getCalcFields("ORDERS")).thenReturn(new CalculateRef[] { duty, twin, reader });
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);

      CalcFieldRequest req = new CalcFieldRequest(
         "ORDERS", null, "Duty", null, null, null, null, null, true, false);

      serviceOver(vs, proxy).modify("tok", principal(), req, "");

      verify(proxy).modifyCalculateField(eq("rt1"), any(), any(), any(), eq(""));
   }

   /** Bug #78441: a rename reports the dot-form, case-different and ambiguous dependents. */
   @Test
   void renameReportsDotFormCaseDifferentAndAmbiguousDependents() throws Exception {
      Viewsheet vs = mock(Viewsheet.class);
      CalculateRef fee = calc("Fee", "field['Total']", true);
      CalculateRef dot = calc("Dot", "field.Fee * 2", true);
      CalculateRef lower = calc("Lower", "field['fee']", true);
      CalculateRef unrelated = calc("Unrelated", "field.Total", true);
      when(vs.getCalcField("ORDERS", "Fee")).thenReturn(fee);
      when(vs.getCalcFields("ORDERS")).thenReturn(new CalculateRef[] { fee, dot, lower, unrelated });

      CalcFieldRequest req = new CalcFieldRequest("ORDERS", null, "Fee", "FeeR",
         "field['Total']", null, null, true, false, false);

      assertEquals(List.of("Dot", "Lower"),
         serviceOver(vs, mock(ModifyCalculateFieldServiceProxy.class))
            .modify("tok", principal(), req, "").rewrittenDependents());

      CalculateRef twin = calc("FEE", "field['Total']", true);
      CalculateRef amb = calc("Amb", "field['fEe']", true);
      when(vs.getCalcFields("ORDERS")).thenReturn(new CalculateRef[] { fee, twin, amb });

      assertEquals(List.of("Amb"),
         serviceOver(vs, mock(ModifyCalculateFieldServiceProxy.class))
            .modify("tok", principal(), req, "").rewrittenDependents());
   }

   private CalcFieldAgentService serviceOver(Viewsheet vs, ModifyCalculateFieldServiceProxy proxy)
      throws Exception
   {
      return new CalcFieldAgentService(
         sessionsOver(vs), fieldsServiceWithOrdersTable(), proxy, allowingSecurityEngine());
   }

   private Viewsheet vsWith(CalculateRef... calcs) {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getCalcFields("ORDERS")).thenReturn(calcs);

      for(CalculateRef c : calcs) {
         when(vs.getCalcField("ORDERS", c.getName())).thenReturn(c);
      }

      return vs;
   }

   /** Bug #77044 (7): a create with a different definition must not replace an existing field. */
   @Test
   void createWithAnExistingNameAndADifferentDefinitionIsRefused() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldAgentService service =
         serviceOver(vsWith(calc("DupCalc", "1", true)), proxy);

      CalcFieldRequest req = new CalcFieldRequest(
         "ORDERS", null, "DupCalc", null, "2", "integer", false, true, false, true);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.modify("tok", principal(), req, ""));
      assertTrue(thrown.getMessage().contains("DupCalc"), thrown.getMessage());
      verifyNoInteractions(proxy);
   }

   @Test
   void createWithAnExistingNameDifferingOnlyInCaseIsRefused() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldAgentService service =
         serviceOver(vsWith(calc("DupCalc", "1", true)), proxy);

      CalcFieldRequest req = new CalcFieldRequest(
         "ORDERS", null, "dupcalc", null, "1", "string", false, true, false, true);

      assertThrows(IllegalArgumentException.class,
         () -> service.modify("tok", principal(), req, ""));
      verifyNoInteractions(proxy);
   }

   @Test
   void createWithTheIdenticalDefinitionStaysAnIdempotentNoOp() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalculateRef existing = calc("DupCalc", "1", true);
      existing.setDataType("integer");
      existing.setSQL(false);
      CalcFieldAgentService service = serviceOver(vsWith(existing), proxy);

      CalcFieldRequest req = new CalcFieldRequest(
         "ORDERS", null, "DupCalc", null, "1", "integer", false, true, false, true);

      assertDoesNotThrow(() -> service.modify("tok", principal(), req, ""));
   }

   @Test
   void renameOntoAnotherCalcFieldIsRefused() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldAgentService service = serviceOver(
         vsWith(calc("DupCalc", "1", true), calc("DupCalc2", "3", true)), proxy);

      CalcFieldRequest req = new CalcFieldRequest(
         "ORDERS", null, "DupCalc2", "DupCalc", "3", null, null, true, false, false);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.modify("tok", principal(), req, ""));
      assertTrue(thrown.getMessage().contains("DupCalc"), thrown.getMessage());
      verifyNoInteractions(proxy);
   }

   @Test
   void caseOnlyRenameOfTheFieldItselfIsAllowed() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldAgentService service = serviceOver(vsWith(calc("DupCalc", "1", true)), proxy);

      CalcFieldRequest req = new CalcFieldRequest(
         "ORDERS", null, "DupCalc", "DUPCALC", "1", null, null, true, false, false);

      assertDoesNotThrow(() -> service.modify("tok", principal(), req, ""));
   }

   /** Bug #77044 (7): a name matching a real column, case-insensitively, is refused. */
   @Test
   void createNamedLikeAPhysicalColumnIsRefusedCaseInsensitively() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldAgentService service = serviceOver(vsWith(), proxy);

      CalcFieldRequest req = new CalcFieldRequest(
         "ORDERS", null, "discount", null, "1", "integer", false, true, false, true);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.modify("tok", principal(), req, ""));
      assertTrue(thrown.getMessage().contains("DISCOUNT"), thrown.getMessage());
      verifyNoInteractions(proxy);
   }

   @Test
   void createNamedLikeAnEntityQualifiedColumnsAttributeIsRefused() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      BindableFieldsService fields = mock(BindableFieldsService.class);
      when(fields.list(eq("rt1"), any(), any(Principal.class))).thenReturn(List.of(
         new BindableTable("ORDERS", null,
            List.of(new BindableField("Customer:Region", "string", null)))));
      CalcFieldAgentService service = new CalcFieldAgentService(
         sessionsOver(vsWith()), fields, proxy, allowingSecurityEngine());

      for(String name : List.of("region", "Customer:Region")) {
         CalcFieldRequest req = new CalcFieldRequest(
            "ORDERS", null, name, null, "1", "integer", false, true, false, true);
         assertThrows(IllegalArgumentException.class,
            () -> service.modify("tok", principal(), req, ""));
      }

      verifyNoInteractions(proxy);
   }

   @Test
   void createWithAnUnrelatedNameIsNotMistakenForAColumnCollision() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldAgentService service = serviceOver(vsWith(), proxy);

      CalcFieldRequest req = new CalcFieldRequest(
         "ORDERS", null, "DiscountRate", null, "1", "integer", false, true, false, true);

      assertDoesNotThrow(() -> service.modify("tok", principal(), req, ""));
   }

   /** The listing also carries calc fields; a case-only self rename must not trip on its own entry. */
   @Test
   void caseOnlyRenameIsNotRefusedWhenTheListingAlsoCarriesTheCalcField() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      BindableFieldsService fields = mock(BindableFieldsService.class);
      when(fields.list(eq("rt1"), any(), any(Principal.class))).thenReturn(List.of(
         new BindableTable("ORDERS", null, List.of(
            new BindableField("DISCOUNT", "double", null),
            new BindableField("DupCalc", "integer", null)))));
      CalcFieldAgentService service = new CalcFieldAgentService(
         sessionsOver(vsWith(calc("DupCalc", "1", true))), fields, proxy,
         allowingSecurityEngine());

      CalcFieldRequest req = new CalcFieldRequest(
         "ORDERS", null, "DupCalc", "DUPCALC", "1", null, null, true, false, false);

      assertDoesNotThrow(() -> service.modify("tok", principal(), req, ""));
   }

   // =========================================================================
   // Bug #76480 (VBS-004): an aggregate calc field (baseOnDetail:false, non-SQL) whose
   // field['...'] references are not detected aggregate tags throws at render time, and the
   // engine swallows that into a crosstab with no data rows, after the write reported ok:true.
   // =========================================================================

   /** A real viewsheet whose "ORDERS" table has a Sum(col) user aggregate field per column. */
   private static Viewsheet realVsWithAggregates(String... columns) {
      Viewsheet vs = new Viewsheet();

      for(String column : columns) {
         vs.addAggrField("ORDERS", new AggregateRef(
            new AttributeRef(null, column), AggregateFormula.SUM));
      }

      return vs;
   }

   /** A JavaScript (non-SQL) calc field; a bare ColumnRef defaults to sql:true. */
   private static CalculateRef scriptCalc(String name, String expression, boolean baseOnDetail) {
      CalculateRef calc = calc(name, expression, baseOnDetail);
      calc.setSQL(false);
      return calc;
   }

   private static IllegalArgumentException assertRefusedBeforeWrite(
      Viewsheet vs, CalcFieldRequest req) throws Exception
   {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldAgentService service = new CalcFieldAgentService(
         sessionsOver(vs), fieldsServiceWithOrdersTable(), proxy, allowingSecurityEngine());

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service.modify("tok", principal(), req, ""));
      assertTrue(thrown.getMessage().contains("field['Sum("), thrown.getMessage());
      verifyNoInteractions(proxy);
      return thrown;
   }

   private static List<String> modifyAndCaptureWarnings(
      Viewsheet vs, CalcFieldRequest req, ModifyCalculateFieldServiceProxy proxy) throws Exception
   {
      CalcFieldAgentService service = new CalcFieldAgentService(
         sessionsOver(vs), fieldsServiceWithOrdersTable(), proxy, allowingSecurityEngine());
      return service.modify("tok", principal(), req, "").warnings();
   }

   /** The reported input: the outer-call form, refused even with both Sums detected. */
   @Test
   void refusesTheOuterCallFormWhenBaseOnDetailIsFalse() throws Exception {
      CalcFieldRequest req = new CalcFieldRequest("ORDERS", null, "RateBad", null,
         "sum(field['Discount'])/sum(field['Total'])", "double", false, false, false, true);

      IllegalArgumentException thrown =
         assertRefusedBeforeWrite(realVsWithAggregates("Discount", "Total"), req);
      assertTrue(thrown.getMessage().contains("'RateBad'"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("field['Discount'], field['Total']"),
         thrown.getMessage());
   }

   /** A bare reference, including another calc field's name (03-repro v4p-O6: never legal). */
   @Test
   void refusesABareReferenceWhenBaseOnDetailIsFalse() throws Exception {
      CalcFieldRequest req = new CalcFieldRequest("ORDERS", null, "RateRef", null,
         "field['RateBase']", "double", false, false, false, true);

      assertRefusedBeforeWrite(realVsWithAggregates("Discount", "Total"), req);
   }

   /** The mixed form: one detected tag does not make the bare reference beside it computable. */
   @Test
   void refusesTheMixedFormWhenBaseOnDetailIsFalse() throws Exception {
      CalcFieldRequest req = new CalcFieldRequest("ORDERS", null, "RateMixed", null,
         "field['Sum(Discount)']/field['Total']", "double", false, false, false, true);

      IllegalArgumentException thrown =
         assertRefusedBeforeWrite(realVsWithAggregates("Discount", "Total"), req);
      assertTrue(thrown.getMessage().contains("not: field['Total']."), thrown.getMessage());
   }

   /** An edit that only flips an existing detail field to baseOnDetail:false is checked too. */
   @Test
   void editThatOnlyFlipsBaseOnDetailToFalseIsChecked() throws Exception {
      Viewsheet vs = realVsWithAggregates("Discount", "Total");
      vs.addCalcField("ORDERS",
         scriptCalc("Rate", "sum(field['Discount'])/sum(field['Total'])", true));

      CalcFieldRequest req = new CalcFieldRequest("ORDERS", null, "Rate", null,
         "sum(field['Discount'])/sum(field['Total'])", null, null, false, false, false);

      assertRefusedBeforeWrite(vs, req);
   }

   /** An expression-only edit of an aggregate field (baseOnDetail omitted) inherits its mode. */
   @Test
   void expressionOnlyEditOfAnAggregateFieldIsChecked() throws Exception {
      Viewsheet vs = realVsWithAggregates("Discount", "Total");
      vs.addCalcField("ORDERS",
         scriptCalc("Rate", "field['Sum(Discount)']/field['Sum(Total)']", false));

      CalcFieldRequest req = new CalcFieldRequest("ORDERS", null, "Rate", null,
         "sum(field['Discount'])/sum(field['Total'])", null, null, null, false, false);

      assertRefusedBeforeWrite(vs, req);
   }

   /** Detail mode is untouched: the outer-call form is valid there (03-repro v4p-O3). */
   @Test
   void theOuterCallFormIsAcceptedInDetailMode() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldRequest req = new CalcFieldRequest("ORDERS", null, "RateDetail", null,
         "sum(field['Discount'])/sum(field['Total'])", "double", false, true, false, true);

      List<String> warnings = modifyAndCaptureWarnings(new Viewsheet(), req, proxy);

      assertTrue(warnings.isEmpty(), warnings.toString());
      verify(proxy).modifyCalculateField(eq("rt1"), any(), any(), any(), eq(""));
   }

   /** A SQL expression is not a script, so the field['...'] rule does not apply to it. */
   @Test
   void aSqlAggregateExpressionIsNotShapeChecked() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldRequest req = new CalcFieldRequest("ORDERS", null, "RateSql", null,
         "field['Discount']", "double", true, false, false, true);

      List<String> warnings = modifyAndCaptureWarnings(new Viewsheet(), req, proxy);

      assertTrue(warnings.isEmpty(), warnings.toString());
      verify(proxy).modifyCalculateField(eq("rt1"), any(), any(), any(), eq(""));
   }

   /**
    * The documented form with nothing detected renders no data (03-repro v4p-O2) but becomes
    * valid once the Sums are bound (O2b), so it is written and warned about, not refused.
    */
   @Test
   void warnsButWritesWhenAWellFormedAggregateIsNotDetected() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldRequest req = new CalcFieldRequest("ORDERS", null, "RateDoc", null,
         "field['Sum(Discount)']/field['Sum(Total)']", "double", false, false, false, true);

      List<String> warnings = modifyAndCaptureWarnings(realVsWithAggregates("Discount"), req, proxy);

      verify(proxy).modifyCalculateField(eq("rt1"), any(), any(), any(), eq(""));
      assertEquals(1, warnings.size(), warnings.toString());
      assertTrue(warnings.get(0).contains("field['Sum(Total)']"), warnings.get(0));
      assertTrue(warnings.get(0).contains("Bind Sum(Total)"), warnings.get(0));
      assertTrue(warnings.get(0).contains("'RateDoc'"), warnings.get(0));
   }

   @Test
   void noWarningWhenEveryAggregateReferenceIsDetected() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldRequest req = new CalcFieldRequest("ORDERS", null, "RateOk", null,
         "field['sum(Discount)']/field['Sum([Total])']", "double", false, false, false, true);

      List<String> warnings =
         modifyAndCaptureWarnings(realVsWithAggregates("Discount", "Total"), req, proxy);

      assertTrue(warnings.isEmpty(), warnings.toString());
   }

   /** Every tag form VSUtil.addUsedAggregateRef can match has the <FormulaName>(<args>) shape. */
   @Test
   void theShapeRuleAcceptsEveryAggregateTagForm() {
      for(String expression : List.of(
         "field['Sum(X)']", "field['Sum([X])']", "field['Correlation([X], [Y])']",
         "field['Correlation(X, Y)']", "field['NthLargest([X], 3)']", "field['DistinctCount(X)']",
         "field['sum(X)'] * 100", "1 + 2"))
      {
         assertDoesNotThrow(
            () -> CalcFieldAgentService.checkAggregateReferences("C", expression), expression);
      }
   }

   /** A name that is no formula's can never be a detected tag (tags use getFormulaName()). */
   @Test
   void theShapeRuleRefusesAnUnknownFormulaName() {
      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> CalcFieldAgentService.checkAggregateReferences("C", "field['Avg(X)']"));
      assertTrue(thrown.getMessage().contains("field['Avg(X)']"), thrown.getMessage());
   }

   /**
    * Review r1 (R1-1): detection only matches the literal field['Name(...)'] text, so a
    * double-quoted or spaced variant can never be detected, whatever is bound. It is refused, not
    * warned "bind Sum(X)" (binding would never help).
    */
   @Test
   void refusesUndetectableSpellingsOfAWellFormedTagInsteadOfWarning() throws Exception {
      for(String ref : List.of(
         "field[\"Sum(Discount)\"]", "field[ 'Sum(Discount)' ]", "field['Sum (Discount)']",
         "field['Sum(Discount) ']", "field ['Sum(Discount)']"))
      {
         CalcFieldRequest req = new CalcFieldRequest("ORDERS", null, "RateSpelled", null,
            ref + "/field['Sum(Total)']", "double", false, false, false, true);

         IllegalArgumentException thrown =
            assertRefusedBeforeWrite(realVsWithAggregates("Discount", "Total"), req);
         assertTrue(thrown.getMessage().contains("spelled exactly like"), thrown.getMessage());
         assertFalse(thrown.getMessage().contains("not: field['Sum(Total)']"), thrown.getMessage());
      }
   }

   /**
    * Review r1 (R1-2): no field object exists in an aggregate calc field's script scope, so a dot
    * or computed-key access throws just like a bare field['X'] and must be refused too.
    */
   @Test
   void refusesDotAndComputedKeyFieldAccess() throws Exception {
      for(String[] expressionAndBad : new String[][] {
         { "sum(field.Discount)/sum(field.Total)", "field.Discount, field.Total" },
         { "field[col] * 2", "field[col]" },
         { "field['Sum(Discount)'] + field[0]", "field[0]" } })
      {
         CalcFieldRequest req = new CalcFieldRequest("ORDERS", null, "RateDot", null,
            expressionAndBad[0], "double", false, false, false, true);

         IllegalArgumentException thrown =
            assertRefusedBeforeWrite(realVsWithAggregates("Discount", "Total"), req);
         assertTrue(thrown.getMessage().contains("not: " + expressionAndBad[1] + "."),
            thrown.getMessage());
      }
   }

   // =========================================================================
   // Bug #79289 (VCF-037): a valid per-row <-> aggregate flip of a BOUND calc field made
   // ModifyCalculateFieldService drop it from the crosstab/table binding, and the agent got
   // ok:true with no word of it.
   // =========================================================================

   /** A crosstab "AovCat" on ORDERS whose aggregates shelf holds the calc field "Avg Order Value". */
   private static Viewsheet vsWithBoundAggregateCalc() {
      Viewsheet vs = new Viewsheet();
      vs.addCalcField("ORDERS", scriptCalc("Avg Order Value",
         "field['Sum(Total)'] / field['Sum(Discount)']", false));

      CrosstabVSAssembly cross = new CrosstabVSAssembly(vs, "AovCat");
      cross.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "ORDERS"));
      VSAggregateRef aggr = new VSAggregateRef();
      aggr.setColumnValue("Avg Order Value");
      aggr.setFormulaValue("None");
      VSCrosstabInfo cinfo = new VSCrosstabInfo();
      cinfo.setDesignAggregates(new DataRef[] { aggr });
      // an executed crosstab also carries runtime refs, which a design-time removal leaves alone
      cinfo.setRuntimeAggregates(new DataRef[] { (DataRef) aggr.clone() });
      cinfo.setAggregateInfo(new AggregateInfo());
      cross.setVSCrosstabInfo(cinfo);
      vs.addAssembly(cross);
      return vs;
   }

   @Test
   void refusesAFlipThatWouldDropTheFieldFromABoundCrosstab() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldRequest req = new CalcFieldRequest("ORDERS", null, "Avg Order Value", null,
         "field['Total']", "double", false, true, false, false);

      Viewsheet vs = vsWithBoundAggregateCalc();
      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> serviceOver(vs, proxy).modify("tok", principal(), req, ""));

      assertTrue(thrown.getMessage().contains("'Avg Order Value'") &&
                 thrown.getMessage().contains("'AovCat'"), thrown.getMessage());
      verifyNoInteractions(proxy);
      // the dry run must not have touched the real binding
      assertEquals(1, ((CrosstabVSAssembly) vs.getAssembly("AovCat")).getVSCrosstabInfo()
         .getDesignAggregates().length);
   }

   @Test
   void refusesTheFlipInTheOtherDirectionToo() throws Exception {
      Viewsheet vs = vsWithBoundAggregateCalc();
      vs.addCalcField("ORDERS", scriptCalc("Avg Order Value", "field['Total']", true));
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldRequest req = new CalcFieldRequest("ORDERS", null, "Avg Order Value", null,
         "field['Sum(Total)'] / field['Sum(Discount)']", "double", false, false, false, false);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> serviceOver(vs, proxy).modify("tok", principal(), req, ""));

      assertTrue(thrown.getMessage().contains("'AovCat'"), thrown.getMessage());
      verifyNoInteractions(proxy);
   }

   /** A flip is only a problem for a field something is bound to. */
   @Test
   void allowsTheFlipOfAFieldNothingIsBoundTo() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addCalcField("ORDERS", scriptCalc("Avg Order Value",
         "field['Sum(Total)'] / field['Sum(Discount)']", false));
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldRequest req = new CalcFieldRequest("ORDERS", null, "Avg Order Value", null,
         "field['Total']", "double", false, true, false, false);

      serviceOver(vs, proxy).modify("tok", principal(), req, "");

      verify(proxy).modifyCalculateField(eq("rt1"), any(), any(), any(), eq(""));
   }

   /**
    * A crosstab's AggregateInfo lists every column of its table, not just the bound ones, so a
    * field that is only listed there is not bound and its flip must not be refused.
    */
   @Test
   void allowsTheFlipOfAFieldThatIsOnlyListedInACrosstabsAggregateInfo() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addCalcField("ORDERS", scriptCalc("Avg Order Value",
         "field['Sum(Total)'] / field['Sum(Discount)']", false));
      CrosstabVSAssembly cross = new CrosstabVSAssembly(vs, "Other");
      cross.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "ORDERS"));
      VSCrosstabInfo cinfo = new VSCrosstabInfo();
      cinfo.setDesignAggregates(new DataRef[0]);
      AggregateInfo ainfo = new AggregateInfo();
      ainfo.addAggregate(new AggregateRef(
         new AttributeRef(null, "Avg Order Value"), AggregateFormula.SUM));
      cinfo.setAggregateInfo(ainfo);
      cross.setVSCrosstabInfo(cinfo);
      vs.addAssembly(cross);
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldRequest req = new CalcFieldRequest("ORDERS", null, "Avg Order Value", null,
         "field['Total']", "double", false, true, false, false);

      serviceOver(vs, proxy).modify("tok", principal(), req, "");

      verify(proxy).modifyCalculateField(eq("rt1"), any(), any(), any(), eq(""));
   }

   /** Editing the expression or renaming a bound field, without flipping its mode, is untouched. */
   @Test
   void allowsAnEditOfABoundFieldThatKeepsItsMode() throws Exception {
      ModifyCalculateFieldServiceProxy proxy = mock(ModifyCalculateFieldServiceProxy.class);
      CalcFieldRequest req = new CalcFieldRequest("ORDERS", null, "Avg Order Value",
         "Avg Order Value 2", "field['Sum(Total)'] / field['Sum(Discount)'] * 2", "double", false,
         null, false, false);

      serviceOver(vsWithBoundAggregateCalc(), proxy).modify("tok", principal(), req, "");

      verify(proxy).modifyCalculateField(eq("rt1"), any(), any(), any(), eq(""));
   }

   /** A word boundary: an identifier that merely ends in "field" is not a field access. */
   @Test
   void anIdentifierEndingInFieldIsNotAFieldAccess() {
      assertDoesNotThrow(() -> CalcFieldAgentService.checkAggregateReferences(
         "C", "subfield['X'] + field['Sum(X)']"));
   }
}
