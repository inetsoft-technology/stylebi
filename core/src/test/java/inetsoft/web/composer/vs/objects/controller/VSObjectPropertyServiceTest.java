/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.*;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.GaugeVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.composer.model.vs.OutputColumnRefModel;
import inetsoft.web.composer.model.vs.VSDimensionMemberModel;
import inetsoft.web.composer.model.vs.VSDimensionModel;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith(MockitoExtension.class)
@Tag("core")
class VSObjectPropertyServiceTest {

   @BeforeEach
   void setup() {
      controller = new VSObjectPropertyService(coreLifecycleService,
                                               vsColumnHandler,
                                               vsObjectTreeService,
                                               infoHandler,
                                               temporaryInfoService,
                                               vsCompositionService,
                                               sharedFilterService,
                                               dataSourceRegistry);
   }

   @Test
   void popComponentListTest() {
      String textAssemblyName = "TextAssembly";
      String selectionListAssemblyName = "SelectionListAssembly";
      TextVSAssembly textVSAssembly = new TextVSAssembly(viewsheet, textAssemblyName);
      Assembly[] assemblies = new Assembly[] {
         textVSAssembly,
         new SelectionListVSAssembly(viewsheet, selectionListAssemblyName)
      };

      when(viewsheet.getAssemblies()).thenReturn(assemblies);
      when(viewsheet.getAssemblies(anyBoolean())).thenReturn(assemblies);
      when(viewsheet.getAssembly(textAssemblyName)).thenReturn(textVSAssembly);
      String[] popComponents = controller.getSupportedPopComponents(viewsheet, textAssemblyName);
      assertEquals(1, popComponents.length);
      assertEquals(selectionListAssemblyName, popComponents[0]);
   }

   /**
    * Write coordination (2026-08-17-write-coordination-design.md / -implementation.md): a stale
    * commit -- one whose expectedRevision no longer matches the live viewsheet's -- must be
    * refused before any assembly is even resolved, not silently applied over whatever changed in
    * between. The marker exception proves reachability past the check without needing to mock the
    * rest of this method's body (that is other tests' job, unaffected by this change).
    */
   @Test
   void refusesAStaleCommitAndNeverResolvesTheAssembly() throws Exception {
      when(rvs.isDisposed()).thenReturn(false);
      when(rvs.getWriteRevision()).thenReturn(5);

      boolean applied = controller.editObjectProperty(rvs, new GaugeVSAssemblyInfo(), "Gauge1",
                                                      "Gauge1", "", null, commandDispatcher, true, 4);

      // Callers that do follow-up work keyed on the (possibly renamed) assembly -- e.g.
      // SelectionTreePropertyDialogService re-resolving by newName -- must see this false and
      // skip that work, or they NPE looking up a rename that never happened.
      assertFalse(applied);
      verify(rvs, never()).getViewsheet();
      verify(coreLifecycleService).sendMessage(
         contains("Gauge1"), eq(MessageCommand.Type.ERROR), eq(commandDispatcher));
   }

   @Test
   void proceedsWhenTheRevisionMatches() {
      when(rvs.isDisposed()).thenReturn(false);
      when(rvs.getWriteRevision()).thenReturn(5);
      when(rvs.getViewsheet()).thenThrow(new RuntimeException("reached past the check"));

      RuntimeException thrown = assertThrows(RuntimeException.class, () ->
         controller.editObjectProperty(rvs, new GaugeVSAssemblyInfo(), "Gauge1", "Gauge1", "",
                                       null, commandDispatcher, true, 5));

      assertEquals("reached past the check", thrown.getMessage());
   }

   @Test
   void proceedsWhenNoRevisionIsSupplied() {
      when(rvs.isDisposed()).thenReturn(false);
      when(rvs.getViewsheet()).thenThrow(new RuntimeException("reached past the check"));

      // null means "this caller does not participate" -- every caller of editObjectProperty
      // before this change, and any future one that isn't a dialog commit.
      RuntimeException thrown = assertThrows(RuntimeException.class, () ->
         controller.editObjectProperty(rvs, new GaugeVSAssemblyInfo(), "Gauge1", "Gauge1", "",
                                       null, commandDispatcher, true, null));

      assertEquals("reached past the check", thrown.getMessage());
   }

   /**
    * Regression test for Redmine #76861 VCX-002: without an explicit member name, {@code
    * VSDimensionMember.getName()} falls back to {@code dataRef.getName()}, which is
    * entity-qualified (e.g. "Customer.Region"). The runtime drill-down lookup
    * (VSUtil.getCubeNextLevelRef) matches a member's name against a row-shelf ref's bare
    * attribute (e.g. "Region"), so the fallback name can never match.
    */
   @Test
   void convertModelToVSDimensionGivesEachMemberAnExplicitBareName() {
      OutputColumnRefModel column = new OutputColumnRefModel();
      column.setEntity("Customer");
      column.setAttribute("Region");
      VSDimensionMemberModel memberModel = new VSDimensionMemberModel();
      memberModel.setDataRef(column);
      VSDimensionModel model = new VSDimensionModel();
      model.setMembers(new VSDimensionMemberModel[] { memberModel });

      VSDimension dimension = controller.convertModelToVSDimension(model);

      assertEquals("Region", dimension.getLevelAt(0).getName(),
                   "member's own name must be the bare attribute, not the entity-qualified " +
                   "dataRef fallback (\"Customer.Region\") a plain row-shelf ref's bare " +
                   "attribute can never match");
   }

   /**
    * The end-to-end version of the same regression: the dimension {@code
    * convertModelToVSDimension} builds must actually be findable, by the exact lookup
    * {@code CrosstabDrillHandler.drillDownChild} uses at runtime, from the row-shelf ref's own
    * bare attribute -- not just "the member has some name now".
    */
   @Test
   void convertModelToVSDimensionProducesADimensionTheRuntimeDrillLookupCanFind() {
      OutputColumnRefModel region = new OutputColumnRefModel();
      region.setEntity("Customer");
      region.setAttribute("Region");
      VSDimensionMemberModel regionMember = new VSDimensionMemberModel();
      regionMember.setDataRef(region);
      // Matches the real caller, HierarchyDimensionService.add(), which always calls
      // setOption(...) explicitly -- defaulting to NONE_INTERVAL for a non-date column, never
      // leaving VSDimensionMemberModel's own YEAR_INTERVAL default.
      regionMember.setOption(DateRangeRef.NONE_INTERVAL);

      OutputColumnRefModel city = new OutputColumnRefModel();
      city.setEntity("Customer");
      city.setAttribute("City");
      VSDimensionMemberModel cityMember = new VSDimensionMemberModel();
      cityMember.setDataRef(city);
      cityMember.setOption(DateRangeRef.NONE_INTERVAL);

      VSDimensionModel model = new VSDimensionModel();
      model.setMembers(new VSDimensionMemberModel[] { regionMember, cityMember });

      VSDimension dimension = controller.convertModelToVSDimension(model);
      VSCube cube = new VSCube();
      cube.setDimensions(List.of(dimension));

      // ref.getAttribute() for a plain relational row-shelf binding is the bare attribute,
      // never entity-qualified (confirmed via AttributeRef.getAttribute()).
      assertSame(dimension, VSUtil.findDimension(cube, "Region"),
                 "the 'user created hierarchy in vs' fallback (VSUtil.getCubeNextLevelRef) must " +
                 "find the dimension by the row-shelf ref's bare attribute");
      assertEquals(0, VSUtil.getScope("Region", dimension, DateRangeRef.NONE_INTERVAL),
                   "the current member's scope/index must resolve so the next level (City) can " +
                   "be looked up");
   }

   @Mock CoreLifecycleService coreLifecycleService;
   @Mock VSColumnHandler vsColumnHandler;
   @Mock VSObjectTreeService vsObjectTreeService;
   @Mock VSAssemblyInfoHandler infoHandler;
   @Mock Viewsheet viewsheet;
   @Mock VSWizardTemporaryInfoService temporaryInfoService;
   @Mock VSCompositionService vsCompositionService;
   @Mock SharedFilterService sharedFilterService;
   @Mock DataSourceRegistry dataSourceRegistry;
   @Mock RuntimeViewsheet rvs;
   @Mock CommandDispatcher commandDispatcher;

   private VSObjectPropertyService controller;
}
