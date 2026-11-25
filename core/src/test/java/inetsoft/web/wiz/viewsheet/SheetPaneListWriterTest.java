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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.vs.FilterModel;
import inetsoft.web.composer.model.vs.FiltersPaneModel;
import inetsoft.web.composer.model.vs.LocalizationComponent;
import inetsoft.web.composer.model.vs.LocalizationPaneModel;
import inetsoft.web.composer.model.vs.ViewsheetPropertyDialogModel;
import inetsoft.web.composer.vs.dialog.ViewsheetPropertyDialogService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82317: the Filters tab's shared filters and the Localization tab's text ids are written
 * through {@code set_viewsheet_properties} as complete lists, validated against the sheet's own
 * selection/input assemblies and component tree.
 */
@Tag("core")
class SheetPaneListWriterTest {
   @Test
   void sharedFiltersAreReplacedAndDefaultTheirIdToTheAssemblyName() throws Exception {
      Fixture fixture = new Fixture(model(true));

      fixture.service.set("tok", principal(), Map.of(
         "filtersPane.sharedFilters",
         List.of(map("column", "Sel1", "filterId", "cat"), map("assemblyName", "Sel2"))), "");

      FiltersPaneModel pane = fixture.written().filtersPane();
      assertEquals(2, pane.getSharedFilters().size());
      assertEquals("Sel1", pane.getSharedFilters().get(0).getColumn());
      assertEquals("cat", pane.getSharedFilters().get(0).getFilterId());
      assertEquals("Sel2", pane.getSharedFilters().get(1).getColumn());
      assertEquals("Sel2", pane.getSharedFilters().get(1).getFilterId());
      // The candidate list is what setViewsheetInfo clears before applying the shared list, so it
      // must stay whole: that is what makes the write a full replace.
      assertEquals(3, pane.getFilters().size());
   }

   @Test
   void emptySharedFiltersListClearsTheAssignments() throws Exception {
      Fixture fixture = new Fixture(model(true));

      fixture.service.set("tok", principal(), Map.of("filtersPane.sharedFilters", List.of()), "");

      assertTrue(fixture.written().filtersPane().getSharedFilters().isEmpty());
   }

   @Test
   void aDataColumnIsRefusedAsASharedFilterAndNothingIsWritten() throws Exception {
      Fixture fixture = new Fixture(model(true));

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> fixture.service.set("tok", principal(), Map.of(
            "filtersPane.sharedFilters",
            List.of(map("assemblyName", "B5Chart", "column", "Product:Category",
                        "filterId", "cat"))), ""));

      assertTrue(thrown.getMessage().contains("Product:Category"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("Sel1"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("Nothing was changed"), thrown.getMessage());
      fixture.assertNothingWritten();
   }

   @Test
   void duplicateColumnsAndBlankIdsAreRefused() throws Exception {
      Fixture fixture = new Fixture(model(true));

      assertThrows(IllegalArgumentException.class, () -> fixture.service.set("tok", principal(),
         Map.of("filtersPane.sharedFilters", List.of(map("column", "Sel1"), map("column", "Sel1"))),
         ""));
      IllegalArgumentException blank = assertThrows(IllegalArgumentException.class,
         () -> fixture.service.set("tok", principal(), Map.of(
            "filtersPane.sharedFilters", List.of(map("column", "Sel1", "filterId", "  "))), ""));
      assertTrue(blank.getMessage().contains("filterId"), blank.getMessage());
      assertThrows(IllegalArgumentException.class, () -> fixture.service.set("tok", principal(),
         Map.of("filtersPane.sharedFilters", "Sel1"), ""));
      fixture.assertNothingWritten();
   }

   @Test
   void twoAssembliesMayShareOneFilterId() throws Exception {
      Fixture fixture = new Fixture(model(true));

      fixture.service.set("tok", principal(), Map.of(
         "filtersPane.sharedFilters",
         List.of(map("column", "Sel1", "filterId", "region"),
                 map("column", "Sel2", "filterId", "region"))), "");

      assertEquals("region",
                   fixture.written().filtersPane().getSharedFilters().get(1).getFilterId());
   }

   @Test
   void filtersListAndComponentsTreeAreRefusedWithAPointerToTheWritableKeys() throws Exception {
      Fixture fixture = new Fixture(model(true));

      IllegalArgumentException filters = assertThrows(IllegalArgumentException.class,
         () -> fixture.service.set("tok", principal(), Map.of(
            "filtersPane.filters", List.of(map("column", "Sel1", "filterId", "x"))), ""));
      assertTrue(filters.getMessage().contains("filtersPane.sharedFilters"), filters.getMessage());

      IllegalArgumentException components = assertThrows(IllegalArgumentException.class,
         () -> fixture.service.set("tok", principal(), Map.of(
            "localizationPane.components", Map.of()), ""));
      assertTrue(components.getMessage().contains("localizationPane.localized"),
                 components.getMessage());
      fixture.assertNothingWritten();
   }

   @Test
   void localizedEntriesAreReplacedAndValidatedAgainstTheComponentTree() throws Exception {
      Fixture fixture = new Fixture(model(true));

      fixture.service.set("tok", principal(), Map.of(
         "localizationPane.localized",
         List.of(map("name", "B5Chart^_^Title", "textId", "b5.title"))), "");

      List<LocalizationComponent> localized = fixture.written().localizationPane().getLocalized();
      assertEquals(1, localized.size());
      assertEquals("B5Chart^_^Title", localized.get(0).getName());
      assertEquals("b5.title", localized.get(0).getTextId());
   }

   @Test
   void localizedRefusesUnknownNamesMissingIdsAndDuplicates() throws Exception {
      Fixture fixture = new Fixture(model(true));

      IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
         () -> fixture.service.set("tok", principal(), Map.of(
            "localizationPane.localized", List.of(map("name", "Nope^_^Title", "textId", "t"))),
            ""));
      assertTrue(unknown.getMessage().contains("B5Chart^_^Title"), unknown.getMessage());

      IllegalArgumentException noId = assertThrows(IllegalArgumentException.class,
         () -> fixture.service.set("tok", principal(), Map.of(
            "localizationPane.localized", List.of(map("name", "B5Chart^_^Title"))), ""));
      assertTrue(noId.getMessage().contains("textId"), noId.getMessage());

      assertThrows(IllegalArgumentException.class, () -> fixture.service.set("tok", principal(),
         Map.of("localizationPane.localized",
                List.of(map("name", "B5Chart^_^Title", "textId", ""))), ""));
      assertThrows(IllegalArgumentException.class, () -> fixture.service.set("tok", principal(),
         Map.of("localizationPane.localized",
                List.of(map("name", "B5Chart^_^Title", "textId", "a"),
                        map("name", "B5Chart^_^Title", "textId", "b"))), ""));
      fixture.assertNothingWritten();
   }

   @Test
   void localizedIsRefusedLoudWhenThePaneIsNotOfferedToTheUser() throws Exception {
      Fixture fixture = new Fixture(model(false));

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> fixture.service.set("tok", principal(), Map.of(
            "localizationPane.localized",
            List.of(map("name", "B5Chart^_^Title", "textId", "t"))), ""));

      assertTrue(thrown.getMessage().contains("not available"), thrown.getMessage());
      fixture.assertNothingWritten();
   }

   /**
    * An assembly with no sub-components (a Text) is a childless leaf with no data, keyed by the
    * assembly name -- the same key rule the Composer dialog uses -- so it is writable, and a list
    * read from the sheet (which can hold such an entry) writes back unchanged.
    */
   @Test
   void aChildlessLeafIsKeyedByItsLabelAndReadModifyWriteRoundTrips() throws Exception {
      Fixture fixture = new Fixture(model(true));

      fixture.service.set("tok", principal(), Map.of(
         "localizationPane.localized",
         List.of(map("name", "Text1", "textId", "t1.id"),
                 map("name", "B5Chart^_^Title", "textId", "old.id"))), "");

      List<LocalizationComponent> localized = fixture.written().localizationPane().getLocalized();
      assertEquals(2, localized.size());
      assertEquals("Text1", localized.get(0).getName());
      assertEquals("t1.id", localized.get(0).getTextId());

      // The sheet already holds a Text1 entry; echoing the list it reads is accepted.
      Fixture roundTrip = new Fixture(model(true));
      List<Object> echoed = new ArrayList<>();

      for(LocalizationComponent existing : roundTrip.currentLocalized()) {
         echoed.add(map("name", existing.getName(), "textId", existing.getTextId()));
      }

      roundTrip.service.set("tok", principal(), Map.of("localizationPane.localized", echoed), "");
      assertEquals(2, roundTrip.written().localizationPane().getLocalized().size());
   }

   @Test
   void anUnrelatedPatchLeavesBothListsAsRead() throws Exception {
      Fixture fixture = new Fixture(model(true));

      fixture.service.set("tok", principal(), Map.of("desc", "new"), "");

      ViewsheetPropertyDialogModel written = fixture.written();
      assertEquals("existing", written.filtersPane().getSharedFilters().get(0).getFilterId());
      assertEquals("old.id", written.localizationPane().getLocalized().get(0).getTextId());
   }

   private static ViewsheetPropertyDialogModel model(boolean withLocalizationPane) {
      FiltersPaneModel filters = new FiltersPaneModel();
      filters.setFilters(new ArrayList<>(List.of(filter("Sel1", null), filter("Sel2", null),
                                                   filter("Sel3", null))));
      filters.setSharedFilters(new ArrayList<>(List.of(filter("Sel3", "existing"))));

      ViewsheetPropertyDialogModel.Builder builder =
         ViewsheetPropertyDialogModel.builder().filtersPane(filters);

      if(withLocalizationPane) {
         TreeNodeModel title = TreeNodeModel.builder().label("Title").leaf(true)
            .data("B5Chart^_^Title").build();
         TreeNodeModel chart = TreeNodeModel.builder().label("B5Chart").children(List.of(title))
            .build();
         // An assembly with no sub-components: a childless leaf, no data, keyed by its label.
         TreeNodeModel text = TreeNodeModel.builder().label("Text1").leaf(true).build();
         LocalizationPaneModel pane = new LocalizationPaneModel();
         pane.setComponents(TreeNodeModel.builder().label("Components")
                               .children(List.of(chart, text)).build());
         LocalizationComponent existing = new LocalizationComponent();
         existing.setName("B5Chart^_^Title");
         existing.setTextId("old.id");
         LocalizationComponent existingText = new LocalizationComponent();
         existingText.setName("Text1");
         existingText.setTextId("Text1");
         pane.setLocalized(new ArrayList<>(List.of(existing, existingText)));
         builder.localizationPane(pane);
      }

      return builder.build();
   }

   private static FilterModel filter(String column, String id) {
      FilterModel filter = new FilterModel();
      filter.setColumn(column);
      filter.setFilterId(id);
      return filter;
   }

   private static Map<String, Object> map(Object... kv) {
      Map<String, Object> map = new LinkedHashMap<>();

      for(int i = 0; i < kv.length; i += 2) {
         map.put((String) kv[i], kv[i + 1]);
      }

      return map;
   }

   private static Principal principal() {
      return () -> "admin";
   }

   private static final class Fixture {
      final ViewsheetPropertyDialogService dialog = mock(ViewsheetPropertyDialogService.class);
      final SheetPropertyService service;
      final ViewsheetPropertyDialogModel model;

      List<LocalizationComponent> currentLocalized() {
         return new ArrayList<>(model.localizationPane().getLocalized());
      }

      Fixture(ViewsheetPropertyDialogModel model) throws Exception {
         this.model = model;
         when(dialog.getViewsheetInfo(anyString(), any(Principal.class))).thenReturn(model);
         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(rvs.getID()).thenReturn("rt1");
         ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
         service = new SheetPropertyService(sessions, dialog);
      }

      ViewsheetPropertyDialogModel written() throws Exception {
         ArgumentCaptor<ViewsheetPropertyDialogModel> captor =
            ArgumentCaptor.forClass(ViewsheetPropertyDialogModel.class);
         verify(dialog).setViewsheetInfo(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                         anyString(), any());
         return captor.getValue();
      }

      void assertNothingWritten() throws Exception {
         verify(dialog, never()).setViewsheetInfo(anyString(), any(), any(Principal.class), any(),
                                                  anyString(), any());
      }
   }
}
