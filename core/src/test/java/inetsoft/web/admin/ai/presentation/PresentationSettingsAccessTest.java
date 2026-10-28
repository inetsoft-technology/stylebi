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
package inetsoft.web.admin.ai.presentation;

import inetsoft.web.admin.general.WebMapSettingsService;
import inetsoft.web.admin.presentation.*;
import inetsoft.web.admin.presentation.model.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pins the ai/fontMapping org-scope read asymmetry (Redmine #76639): an organization-scoped read
 * of {@code ai} must come back {@code null} (there is no per-org AI config, unlike the legacy
 * {@code PresentationSettingsController.getSettings}'s {@code globalProperty ? aiSettingsService
 * .getModel() : null}, which the new adapter never carried over), while {@code fontMapping}'s
 * organization-scoped read is never nulled at either scope -- both today and in the legacy
 * controller.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class PresentationSettingsAccessTest {
   @Mock private PresentationFormatsSettingsService formats;
   @Mock private PresentationDashboardSettingsService dashboard;
   @Mock private PresentationToolbarSettingsService toolbar;
   @Mock private LookAndFeelService lookAndFeel;
   @Mock private WelcomePageService welcomePage;
   @Mock private PresentationLoginBannerSettingsService loginBanner;
   @Mock private PortalIntegrationViewSettingsService portalIntegration;
   @Mock private PresentationPdfGenerationSettingsService pdfGeneration;
   @Mock private ExportMenuSettingsService exportMenu;
   @Mock private PresentationFontMappingSettingsService fontMapping;
   @Mock private ShareSettingsService share;
   @Mock private PresentationComposerMessageSettingsService composerMessage;
   @Mock private PresentationTimeSettingsService time;
   @Mock private PresentationDataSourceVisibilitySettingsService dataSourceVisibility;
   @Mock private WebMapSettingsService webMap;
   @Mock private AISettingsService ai;

   private static final Principal PRINCIPAL = () -> "admin";

   private PresentationSettingsAccess access() {
      return new PresentationSettingsAccess(formats, dashboard, toolbar, lookAndFeel, welcomePage,
                                             loginBanner, portalIntegration, pdfGeneration,
                                             exportMenu, fontMapping, share, composerMessage, time,
                                             dataSourceVisibility, webMap, ai);
   }

   @Test
   void aiOrganizationScopedReadReturnsNull() throws Exception {
      PresentationAISettingsModel model = PresentationAISettingsModel.builder()
         .aiAssistantVisible(true).chatAppServerUrl("https://example.com").build();
      when(ai.getModel()).thenReturn(model);

      assertSame(model, access().read(PresentationSubModel.AI, PRINCIPAL, true));
      assertNull(access().read(PresentationSubModel.AI, PRINCIPAL, false));
   }

   // ---------------------------------------------------------------- Bug #79434: org key unpinning

   /** In-memory stand-in for the organization's property overrides plus a time sub-model whose
    * read falls back to the inherited global values (Sunday / true) for every key that is not
    * overridden -- the shape of every value-scope sub-model. */
   private static final class FakeOrgStore implements PresentationSettingsAccess.OrgPropertyStore {
      final Map<String, String> props = new HashMap<>();
      int saves;

      @Override
      public Set<String> keys() {
         return new HashSet<>(props.keySet());
      }

      @Override
      public String get(String key) {
         return props.get(key);
      }

      @Override
      public void put(String key, String value) {
         props.put(key, value);
      }

      @Override
      public void remove(String key) {
         props.remove(key);
      }

      @Override
      public void save() {
         saves++;
      }
   }

   private static final String WEEK = "inetsoft.org.o.week.start";
   private static final String HOURS = "inetsoft.org.o.schedule.time.12hours";

   private PresentationSettingsAccess timeAccess(FakeOrgStore store) throws Exception {
      lenient().when(time.getModel(false)).thenAnswer(inv -> PresentationTimeSettingsModel.builder()
         .weekStart(store.props.getOrDefault(WEEK, "Sunday"))
         .scheduleTime12Hours(Boolean.parseBoolean(store.props.getOrDefault(HOURS, "true")))
         .build());
      // like the real setModel, write every property whatever the model changed
      lenient().doAnswer(inv -> {
         PresentationTimeSettingsModel m = inv.getArgument(0);
         store.props.put(WEEK, m.weekStart());
         store.props.put(HOURS, String.valueOf(m.scheduleTime12Hours()));
         return null;
      }).when(time).setModel(any(), eq(false));

      return new PresentationSettingsAccess(formats, dashboard, toolbar, lookAndFeel, welcomePage,
                                             loginBanner, portalIntegration, pdfGeneration,
                                             exportMenu, fontMapping, share, composerMessage, time,
                                             dataSourceVisibility, webMap, ai, store);
   }

   private static PresentationTimeSettingsModel timeModel(String weekStart, boolean hours12) {
      return PresentationTimeSettingsModel.builder().weekStart(weekStart)
         .scheduleTime12Hours(hours12).build();
   }

   @Test
   void orgWriteDropsTheKeysTheWriteCreatedThatCarryNoChange() throws Exception {
      FakeOrgStore store = new FakeOrgStore();
      PresentationSettingsAccess access = timeAccess(store);

      access.write(PresentationSubModel.TIME, timeModel("Monday", true), PRINCIPAL, false);

      // week.start carries the change and stays; the 12-hour pin merely froze the inherited value
      assertEquals(Map.of(WEEK, "Monday"), store.props);
      assertEquals(1, store.saves);
   }

   @Test
   void orgWriteOfUnchangedValuesLeavesNoPins() throws Exception {
      FakeOrgStore store = new FakeOrgStore();
      PresentationSettingsAccess access = timeAccess(store);

      access.write(PresentationSubModel.TIME, timeModel("Sunday", true), PRINCIPAL, false);

      assertTrue(store.props.isEmpty(), store.props.toString());
   }

   @Test
   void orgWriteKeepsKeysThatAlreadyExisted() throws Exception {
      FakeOrgStore store = new FakeOrgStore();
      store.props.put(HOURS, "true");
      PresentationSettingsAccess access = timeAccess(store);

      access.write(PresentationSubModel.TIME, timeModel("Sunday", true), PRINCIPAL, false);

      assertEquals(Map.of(HOURS, "true"), store.props);
   }

   @Test
   void globalWriteNeverTouchesOrgKeys() throws Exception {
      FakeOrgStore store = new FakeOrgStore();
      store.props.put(WEEK, "Monday");
      PresentationSettingsAccess access = timeAccess(store);
      lenient().doNothing().when(time).setModel(any(), eq(true));

      access.write(PresentationSubModel.TIME, timeModel("Sunday", true), PRINCIPAL, true);

      assertEquals(Map.of(WEEK, "Monday"), store.props);
      assertEquals(0, store.saves);
      assertTrue(access.snapshotOrgKeys(true).isEmpty());
   }

   @Test
   void rollbackPreImageWriteDropsKeysThatDidNotExistBeforeTheApply() throws Exception {
      FakeOrgStore store = new FakeOrgStore();
      PresentationSettingsAccess access = timeAccess(store);
      Set<String> beforeApply = access.snapshotOrgKeys(false);

      // apply: the change is kept as a pin
      access.write(PresentationSubModel.TIME, timeModel("Monday", false), PRINCIPAL, false);
      assertEquals(Map.of(WEEK, "Monday", HOURS, "false"), store.props);

      // rollback re-writes the pre-image (inherited values), which re-pins both keys
      access.write(PresentationSubModel.TIME, timeModel("Sunday", true), PRINCIPAL, false);
      access.unpinOrgKeysSince(PresentationSubModel.TIME, PRINCIPAL, false, beforeApply);

      assertTrue(store.props.isEmpty(), store.props.toString());
   }

   @Test
   void unpinRestoresAKeyWhoseRemovalWouldChangeTheRead() throws Exception {
      FakeOrgStore store = new FakeOrgStore();
      PresentationSettingsAccess access = timeAccess(store);
      store.props.put(WEEK, "Monday");
      store.props.put(HOURS, "false");

      access.unpinOrgKeysSince(PresentationSubModel.TIME, PRINCIPAL, false, Set.of());

      // both carry a non-inherited value, so neither can be dropped
      assertEquals(Map.of(WEEK, "Monday", HOURS, "false"), store.props);
      assertEquals(0, store.saves);
   }

   @Test
   void fontMappingOrganizationScopedReadIsNeverNulled() throws Exception {
      PresentationFontMappingSettingsModel model =
         PresentationFontMappingSettingsModel.builder().fontMappings(List.of()).build();
      when(fontMapping.getModel()).thenReturn(model);

      assertSame(model, access().read(PresentationSubModel.FONT_MAPPING, PRINCIPAL, true));
      assertSame(model, access().read(PresentationSubModel.FONT_MAPPING, PRINCIPAL, false));
   }
}
