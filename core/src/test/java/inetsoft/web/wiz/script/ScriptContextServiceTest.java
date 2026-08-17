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
package inetsoft.web.wiz.script;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.web.binding.VSScriptableService;
import inetsoft.web.wiz.script.model.AssemblyContext;
import inetsoft.web.wiz.script.model.ScriptContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
// CONTROLLER NOTE: keep @Tag("core") if it works — this test mocks RuntimeViewsheet and overrides
// describeAssemblies, so it may never touch Spring. But Task 2 hit exactly this and had to switch:
// if these tests error with "ShutdownException: Spring application context is not available",
// replace @Tag("core") with @WizAgentTestSupport (import inetsoft.web.wiz.pairing.WizAgentTestSupport),
// which is what ScriptEditServiceTest, ScriptExecuteServiceTest, ScriptImageServiceTest and
// ScriptReadServiceTest all already use. Report which one you ended up needing.
class ScriptContextServiceTest {
   /**
    * Stubs the expensive live lookup so these tests exercise the projection, not VSScriptable.
    *
    * <p>The mocked {@code VSScriptableService} is required, not decorative: this class has only
    * the {@code @Autowired} constructor, no no-arg one, so an anonymous subclass must pass it up.
    * It is never touched, because {@code describeAssemblies} is exactly what the override replaces.
    */
   private static ScriptContextService serviceReturning(List<AssemblyContext> full) {
      return new ScriptContextService(mock(VSScriptableService.class)) {
         @Override
         List<AssemblyContext> describeAssemblies(RuntimeViewsheet rvs) {
            return full;
         }
      };
   }

   private static final List<AssemblyContext> FULL = List.of(
      new AssemblyContext("Chart1", "chart", true, List.of("graph"), "CHART_TREE"),
      new AssemblyContext("Table1", "table", true, List.of("table"), "TABLE_TREE"));

   @Test
   void withATargetReturnsOnlyThatAssembly() throws Exception {
      ScriptContext ctx = serviceReturning(FULL).context(
         mock(RuntimeViewsheet.class), ScriptTarget.of(ScriptTarget.Kind.ASSEMBLY_MAIN, "Table1"));

      assertEquals(1, ctx.assemblies().size());
      assertEquals("Table1", ctx.assemblies().get(0).name());
      assertEquals("TABLE_TREE", ctx.assemblies().get(0).apiTree());
   }

   @Test
   void withNoTargetListsEveryAssemblyWithoutTheApiTree() throws Exception {
      ScriptContext ctx = serviceReturning(FULL).context(mock(RuntimeViewsheet.class), null);

      assertEquals(2, ctx.assemblies().size(), "the list is what makes a target choosable");
      assertTrue(ctx.assemblies().stream().allMatch(a -> a.apiTree() == null),
                 "the whole-viewsheet dump is what makes this attractive for non-script work");
      assertEquals(List.of("graph"), ctx.assemblies().get(0).scriptableMembers(),
                   "the short curated list stays -- it is cheap and aids the choice");
   }

   @Test
   void aViewsheetLevelTargetGetsEveryAssemblyBecauseItsScriptReachesThemAll() throws Exception {
      ScriptContext ctx = serviceReturning(FULL).context(
         mock(RuntimeViewsheet.class), ScriptTarget.of(ScriptTarget.Kind.VIEWSHEET_ON_INIT, null));

      assertEquals(2, ctx.assemblies().size());
      assertTrue(ctx.assemblies().stream().allMatch(a -> a.apiTree() != null),
                 "onInit coordinates across assemblies, so it needs all their trees");
   }

   /**
    * The context VARS differ by kind, not just the assembly list — which is the substance of
    * "scope the returned surface to a target's kind".
    *
    * <p>{@code event} resolves from {@code ViewsheetScope}'s vmap
    * ({@code ViewsheetScope.java:368}) and is only ever populated while an onClick is executing.
    * Listing it for onInit tells the caller a variable is available that is always null there —
    * the same always-null-field defect #4618 fixed by removing {@code BindableField.role}.
    */
   @Test
   void reportsEventOnlyForTheKindThatActuallyReceivesOne() throws Exception {
      ScriptContextService service = serviceReturning(FULL);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);

      ScriptContext onClick = service.context(
         rvs, ScriptTarget.of(ScriptTarget.Kind.ASSEMBLY_ON_CLICK, "Table1"));
      assertTrue(onClick.contextVars().contains("event"));

      for(ScriptTarget.Kind kind : List.of(ScriptTarget.Kind.VIEWSHEET_ON_INIT,
                                           ScriptTarget.Kind.VIEWSHEET_ON_LOAD))
      {
         ScriptContext ctx = service.context(rvs, ScriptTarget.of(kind, null));
         assertFalse(ctx.contextVars().contains("event"),
                     kind + " never receives an event; listing it promises a null");
         assertTrue(ctx.contextVars().contains("thisViewsheet"), kind.toString());
      }

      ScriptContext main = service.context(
         rvs, ScriptTarget.of(ScriptTarget.Kind.ASSEMBLY_MAIN, "Table1"));
      assertFalse(main.contextVars().contains("event"));
   }

   /** With no target the caller has not said which context, so report the union, not a guess. */
   @Test
   void withNoTargetReportsEveryContextVarAnyKindCanSee() throws Exception {
      assertTrue(serviceReturning(FULL).context(mock(RuntimeViewsheet.class), null)
                    .contextVars().contains("event"));
   }

   @Test
   void anUnknownAssemblyFailsLoudRatherThanReturningAnEmptySurface() throws Exception {
      ScriptContextService service = serviceReturning(FULL);
      ScriptTarget target = ScriptTarget.of(ScriptTarget.Kind.ASSEMBLY_MAIN, "Nope");

      assertThrows(inetsoft.web.wiz.pairing.PairingException.class,
                   () -> service.context(mock(RuntimeViewsheet.class), target));
   }
}
