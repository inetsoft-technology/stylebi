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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSModelContext;
import inetsoft.report.composition.VSModelTrapContext;
import inetsoft.report.filter.ColumnHighlight;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AbstractModelTrapContext;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.composer.model.condition.ConditionModel;
import inetsoft.web.composer.model.vs.HighlightDialogModel;
import inetsoft.web.composer.model.vs.HighlightModel;
import inetsoft.web.composer.vs.dialog.HighlightDialogService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.service.HighlightService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80476: a highlight written through the agent path reaches
 * {@code VSObjectPropertyService.editObjectProperty}, whose trap check must see the field the
 * highlight condition reads and return a "Data-model trap: " warning. The condition field here is
 * one the table's column lookup does not list, which is the case the check used to miss. The
 * trap detector's verdict is derived from the fields the real {@code VSModelContext} collects
 * for the written info, so the test fails if that collection drops the highlight's field.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class AssemblyHighlightServiceTrapTest {
   private static final String TRAPPED_FIELD = "FIRST_NAME";

   @Test
   void aHighlightOnAFieldTheColumnLookupMissesWarnsAboutTheTrap() throws Exception {
      Fixture f = new Fixture();

      try(MockedConstruction<VSModelTrapContext> ctx = trapContext(f)) {
         AssemblyHighlightService.SetResult result = f.set(TRAPPED_FIELD);

         assertFalse(ctx.constructed().isEmpty(), "the trap check must have run");
         assertEquals(1, result.warnings().size(), result.warnings().toString());
         assertTrue(result.warnings().get(0).startsWith("Data-model trap: "),
                    result.warnings().toString());
         assertTrue(result.warnings().get(0).contains("Chasm Trap is on Orders, Returns"),
                    result.warnings().toString());
         assertNotNull(((TableVSAssemblyInfo) f.assembly.getVSAssemblyInfo()).getHighlightAttr(),
                       "the write is applied even when it warns");
      }
   }

   @Test
   void aHighlightOnAnUntrappedFieldIsSilent() throws Exception {
      Fixture f = new Fixture();

      try(MockedConstruction<VSModelTrapContext> ctx = trapContext(f)) {
         AssemblyHighlightService.SetResult result = f.set("STATE");

         assertFalse(ctx.constructed().isEmpty(), "the trap check must have run");
         assertTrue(result.warnings().isEmpty(), result.warnings().toString());
         assertNotNull(((TableVSAssemblyInfo) f.assembly.getVSAssemblyInfo()).getHighlightAttr());
      }
   }

   /**
    * A detector that reports a trap exactly when the fields the real {@code VSModelContext}
    * collects for the new info include {@link #TRAPPED_FIELD}.
    */
   private static MockedConstruction<VSModelTrapContext> trapContext(Fixture f) {
      return Mockito.mockConstruction(VSModelTrapContext.class, (context, c) -> {
         when(context.isCheckTrap()).thenReturn(true);
         when(context.getTrapCondition()).thenReturn("Chasm Trap is on Orders, Returns");
         when(context.checkTrap(any(), any())).thenAnswer(invocation -> {
            HashSet<DataRef> all = new HashSet<>();
            new VSModelContext(f.rvs, null).getAllAttributes(
               invocation.getArgument(1), all, new HashSet<>());
            boolean trap = all.stream().anyMatch(r -> TRAPPED_FIELD.equals(r.getAttribute()));
            AbstractModelTrapContext.TrapInfo info =
               mock(AbstractModelTrapContext.TrapInfo.class);
            when(info.showWarning()).thenReturn(trap);
            return info;
         });
      });
   }

   private static DataRefModel field(String name) {
      DataRefModel field = mock(DataRefModel.class);
      when(field.getName()).thenReturn(name);
      return field;
   }

   private static final class Fixture {
      Fixture() throws Exception {
         Viewsheet vs = new Viewsheet();
         assembly = new TableVSAssembly(vs, "Table1");
         vs.addAssembly(assembly);
         rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenReturn(vs);
         when(rvs.getID()).thenReturn("rt1");

         VSObjectPropertyService props = new VSObjectPropertyService(
            mock(CoreLifecycleService.class), null, null, null, null, null, null,
            mock(DataSourceRegistry.class), mock(QueryManagerService.class));

         ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
         doAnswer(invocation -> {
            Principal user = invocation.getArgument(1);
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            return CapturingCommandDispatcher.withCapturingDispatcher(user, dispatcher -> {
               mutation.run(rvs, "rt1", dispatcher);
               return dispatcher.getWarnings();
            });
         }).when(sessions).mutate(anyString(), any(Principal.class), any());

         HighlightDialogService highlights = mock(HighlightDialogService.class);
         HighlightDialogModel model = new HighlightDialogModel();
         model.setFields(new DataRefModel[]{ field(TRAPPED_FIELD), field("STATE") });
         model.setTableName("Contact");
         model.setHighlights(new HighlightModel[0]);
         when(highlights.getHighlightDialogModel(anyString(), anyString(), any(), any(), any(),
                                                 anyBoolean(), anyBoolean(),
                                                 any(Principal.class)))
            .thenReturn(model);
         when(highlights.getFirstDataCell(anyString(), anyString(), any(Principal.class)))
            .thenReturn(new int[]{ 1, 1 });
         when(highlights.getDateComparisonFields(anyString(), anyString(), any(), any(),
                                                 any(Principal.class)))
            .thenReturn(HighlightService.DateComparisonFields.NONE);

         // HighlightDialogService.setHighlightDialogModel stores the highlight on the table info
         // and hands the result to editObjectProperty; this does the same with the posted model's
         // condition field, so the real property service and its trap hook are the code under test.
         doAnswer(invocation -> {
            HighlightDialogModel posted = invocation.getArgument(2);
            TableVSAssemblyInfo info =
               (TableVSAssemblyInfo) assembly.getVSAssemblyInfo().clone();
            ConditionModel condition = (ConditionModel) posted.getHighlights()[0]
               .getVsConditionDialogModel().getConditionList()[0];
            ColumnRef ref = new ColumnRef(
               new AttributeRef("Contact", condition.getField().getName()));
            Condition cond = new Condition();
            cond.addValue("a");
            cond.setOperation(XCondition.EQUAL_TO);
            ConditionList list = new ConditionList();
            list.append(new ConditionItem(ref, cond, 0));
            ColumnHighlight highlight = new ColumnHighlight();
            highlight.setConditionGroup(list);
            HighlightGroup group = new HighlightGroup();
            group.addHighlight(posted.getHighlights()[0].getName(), highlight);
            TableHighlightAttr attr = new TableHighlightAttr();
            TableDataPath path = new TableDataPath("Contact:" + ref.getAttribute());
            path.setType(TableDataPath.DETAIL);
            attr.setHighlight(path, group);
            info.setHighlightAttr(attr);
            props.editObjectProperty(rvs, info, "Table1", "Table1", invocation.getArgument(3),
                                     invocation.getArgument(4), invocation.getArgument(5), true,
                                     posted.getRevision());
            return null;
         }).when(highlights).setHighlightDialogModel(anyString(), anyString(), any(), any(),
                                                      any(Principal.class), any());

         service = new AssemblyHighlightService(sessions, highlights);
      }

      AssemblyHighlightService.SetResult set(String field) throws Exception {
         return service.set("tok", () -> "admin", "Table1", null,
            new AssemblyHighlightService.Highlight(
               "H1", null, "#ff0000", null,
               List.of(new ConditionVocabulary.Clause(field, "=", List.of("a"), null, false,
                                                      false, 0)),
               false),
            false, "");
      }

      final TableVSAssembly assembly;
      final RuntimeViewsheet rvs;
      final AssemblyHighlightService service;
   }
}
