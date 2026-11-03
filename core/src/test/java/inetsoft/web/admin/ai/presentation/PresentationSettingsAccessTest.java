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
   private static class FakeOrgStore implements PresentationSettingsAccess.OrgPropertyStore {
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

      PresentationSettingsAccess access = access();
      access.setOrgStore(store);
      return access;
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

      assertTrue(access.write(PresentationSubModel.TIME, timeModel("Sunday", true), PRINCIPAL, true)
                    .isEmpty());

      assertEquals(Map.of(WEEK, "Monday"), store.props);
      assertEquals(0, store.saves);
   }

   @Test
   void rollbackPreImageWriteDropsKeysThatDidNotExistBeforeTheApply() throws Exception {
      FakeOrgStore store = new FakeOrgStore();
      PresentationSettingsAccess access = timeAccess(store);
      // apply: the change is kept as a pin
      Set<String> kept =
         access.write(PresentationSubModel.TIME, timeModel("Monday", false), PRINCIPAL, false);
      assertEquals(Map.of(WEEK, "Monday", HOURS, "false"), store.props);
      assertEquals(Set.of(WEEK, HOURS), kept);

      // rollback re-writes the pre-image (inherited values), which re-pins both keys
      access.write(PresentationSubModel.TIME, timeModel("Sunday", true), PRINCIPAL, false);
      access.unpinOrgKeys(PresentationSubModel.TIME, PRINCIPAL, false, kept);

      assertTrue(store.props.isEmpty(), store.props.toString());
   }

   @Test
   void unpinOnlyTouchesTheCandidateKeysNotKeysAnotherWriteCreated() throws Exception {
      FakeOrgStore store = new FakeOrgStore();
      PresentationSettingsAccess access = timeAccess(store);
      Set<String> kept =
         access.write(PresentationSubModel.TIME, timeModel("Sunday", false), PRINCIPAL, false);
      // a later, unrelated write (another entry or a native save) created this key
      store.props.put("inetsoft.org.o.repository.tree.sort", "false");

      access.unpinOrgKeys(PresentationSubModel.TIME, PRINCIPAL, false, kept);

      assertEquals("false", store.props.get("inetsoft.org.o.repository.tree.sort"));
      assertEquals(Set.of(HOURS), kept);
   }

   @Test
   void aWriteThatThrowsAfterPinningStillUnpinsKeysThatCarryNoChange() throws Exception {
      FakeOrgStore store = new FakeOrgStore();
      PresentationSettingsAccess access = timeAccess(store);
      doAnswer(inv -> {
         store.props.put(WEEK, "Sunday");
         store.props.put(HOURS, "true");
         throw new IllegalStateException("boom");
      }).when(time).setModel(any(), eq(false));

      assertThrows(IllegalStateException.class,
                   () -> access.write(PresentationSubModel.TIME, timeModel("Sunday", true),
                                      PRINCIPAL, false));

      assertTrue(store.props.isEmpty(), store.props.toString());
   }

   @Test
   void unpinRestoresAKeyWhoseRemovalWouldChangeTheRead() throws Exception {
      FakeOrgStore store = new FakeOrgStore();
      PresentationSettingsAccess access = timeAccess(store);
      store.props.put(WEEK, "Monday");
      store.props.put(HOURS, "false");

      Set<String> kept = access.unpinOrgKeys(PresentationSubModel.TIME, PRINCIPAL, false,
                                             Set.of(WEEK, HOURS));
      assertEquals(Set.of(WEEK, HOURS), kept);

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

   // ---------------------------------------------------------------- Bug #80087: organization reset

   private static final String TOOLBAR_HIDDEN = "inetsoft.org.o.viewsheet toolbar hidden";
   private static final String SLACK_URL = "inetsoft.org.o.share.slack.url";

   private PresentationSettingsAccess resetAccess(FakeOrgStore store) {
      PresentationSettingsAccess access = access();
      access.setOrgStore(store);
      access.setWelcomeEntryProbe(() -> false);
      return access;
   }

   @Test
   void resetRemovesTheOrgKeysAndTheOutcomeRestoresThem() throws Exception {
      FakeOrgStore store = new FakeOrgStore();
      store.props.put(WEEK, "");
      store.props.put(HOURS, "false");
      store.props.put("inetsoft.org.o.dashboard.enabled", "false");
      doAnswer(inv -> {
         store.props.remove(WEEK);
         store.props.remove(HOURS);
         return null;
      }).when(time).resetSettings(false);
      PresentationSettingsAccess access = resetAccess(store);

      PresentationSettingsAccess.ResetOutcome outcome =
         access.resetOrg(PresentationSubModel.TIME, PRINCIPAL);

      assertEquals(Map.of("inetsoft.org.o.dashboard.enabled", "false"), store.props);
      assertEquals(Map.of(WEEK, "", HOURS, "false"), outcome.restore());
      assertTrue(outcome.added().isEmpty());
      assertNull(access.checkReset(PresentationSubModel.TIME, outcome));

      assertNull(access.undoReset(outcome));
      assertEquals(Map.of(WEEK, "", HOURS, "false", "inetsoft.org.o.dashboard.enabled", "false"),
                   store.props);
   }

   @Test
   void resetOfAnOrgWithNoOverridesIsAVerifiedNoOp() throws Exception {
      FakeOrgStore store = new FakeOrgStore();
      PresentationSettingsAccess access = resetAccess(store);

      PresentationSettingsAccess.ResetOutcome outcome =
         access.resetOrg(PresentationSubModel.TIME, PRINCIPAL);

      assertTrue(outcome.restore().isEmpty());
      assertNull(access.checkReset(PresentationSubModel.TIME, outcome));
   }

   @Test
   void resetSavesExplicitlyBecauseExportMenuResetDoesNot() throws Exception {
      FakeOrgStore store = new FakeOrgStore();
      store.props.put("inetsoft.org.o.vsexport.menu.options", "pdf");
      doAnswer(inv -> {
         store.props.remove("inetsoft.org.o.vsexport.menu.options");
         return null;
      }).when(exportMenu).resetSettings(false);
      PresentationSettingsAccess access = resetAccess(store);

      access.resetOrg(PresentationSubModel.EXPORT_MENU, PRINCIPAL);

      assertTrue(store.saves > 0, "the removal must be persisted");
      assertTrue(store.props.isEmpty());
   }

   @Test
   void toolbarResetDoesNotLeaveTheHiddenKeyPinned() throws Exception {
      FakeOrgStore store = new FakeOrgStore();
      doAnswer(inv -> {
         store.props.put(TOOLBAR_HIDDEN, "false");
         return null;
      }).when(toolbar).resetSettings(false);
      PresentationSettingsAccess access = resetAccess(store);

      // an org that never had the key ends without it
      PresentationSettingsAccess.ResetOutcome outcome =
         access.resetOrg(PresentationSubModel.VIEWSHEET_TOOLBAR, PRINCIPAL);
      assertTrue(store.props.isEmpty(), store.props.toString());
      assertTrue(outcome.added().isEmpty());
      assertNull(access.checkReset(PresentationSubModel.VIEWSHEET_TOOLBAR, outcome));

      // an org pinned to true ends inheriting, and rollback brings the pin back
      store.props.put(TOOLBAR_HIDDEN, "true");
      outcome = access.resetOrg(PresentationSubModel.VIEWSHEET_TOOLBAR, PRINCIPAL);
      assertTrue(store.props.isEmpty(), store.props.toString());
      assertNull(access.checkReset(PresentationSubModel.VIEWSHEET_TOOLBAR, outcome));
      assertNull(access.undoReset(outcome));
      assertEquals(Map.of(TOOLBAR_HIDDEN, "true"), store.props);
   }

   @Test
   void checkResetFlagsKeysLeftBehindCreatedOrRewritten() throws Exception {
      FakeOrgStore store = new FakeOrgStore();
      store.props.put(WEEK, "Monday");
      store.props.put(HOURS, "false");
      doAnswer(inv -> {
         store.props.put(HOURS, "true");
         store.props.put("inetsoft.org.o.new.key", "x");
         return null;
      }).when(time).resetSettings(false);
      PresentationSettingsAccess access = resetAccess(store);

      PresentationSettingsAccess.ResetOutcome outcome =
         access.resetOrg(PresentationSubModel.TIME, PRINCIPAL);
      String problem = access.checkReset(PresentationSubModel.TIME, outcome);

      assertNotNull(problem);
      assertTrue(problem.contains("created"), problem);
      assertTrue(problem.contains("rewrote"), problem);
      assertTrue(problem.contains("still present"), problem);

      // the rollback restores the old values and deletes the created key
      assertNull(access.undoReset(outcome));
      assertEquals(Map.of(WEEK, "Monday", HOURS, "false"), store.props);
   }

   @Test
   void aResetThatThrowsMidwayPutsTheKeysBack() throws Exception {
      FakeOrgStore store = new FakeOrgStore();
      store.props.put(WEEK, "Monday");
      store.props.put(HOURS, "false");
      doAnswer(inv -> {
         store.props.remove(WEEK);
         throw new java.io.IOException("disk full");
      }).when(time).resetSettings(false);
      PresentationSettingsAccess access = resetAccess(store);

      assertThrows(java.io.IOException.class,
                   () -> access.resetOrg(PresentationSubModel.TIME, PRINCIPAL));

      assertEquals(Map.of(WEEK, "Monday", HOURS, "false"), store.props);
   }

   @Test
   void aResetWhoseKeysCannotBeRestoredThrowsStateUnknown() throws Exception {
      FakeOrgStore store = new FakeOrgStore() {
         @Override
         public void put(String key, String value) {
            throw new IllegalStateException("store unavailable");
         }
      };
      store.props.put(WEEK, "Monday");
      doAnswer(inv -> {
         store.props.remove(WEEK);
         throw new java.io.IOException("disk full");
      }).when(time).resetSettings(false);
      PresentationSettingsAccess access = resetAccess(store);

      assertThrows(PresentationSettingsAccess.ResetStateUnknownException.class,
                   () -> access.resetOrg(PresentationSubModel.TIME, PRINCIPAL));
   }

   @Test
   void welcomePageAndLoginBannerResetsAreCheckedAgainstTheSharedEntry() throws Exception {
      FakeOrgStore store = new FakeOrgStore();
      PresentationSettingsAccess access = access();
      access.setOrgStore(store);
      boolean[] entryPresent = {true};
      access.setWelcomeEntryProbe(() -> entryPresent[0]);
      doAnswer(inv -> {
         entryPresent[0] = false;
         return null;
      }).when(welcomePage).resetSettings(false);
      doAnswer(inv -> null).when(loginBanner).resetSettings(false);

      assertNull(access.checkReset(PresentationSubModel.WELCOME_PAGE,
         access.resetOrg(PresentationSubModel.WELCOME_PAGE, PRINCIPAL)));

      entryPresent[0] = true;
      String problem = access.checkReset(PresentationSubModel.LOGIN_BANNER,
         access.resetOrg(PresentationSubModel.LOGIN_BANNER, PRINCIPAL));
      assertNotNull(problem);
      assertTrue(problem.contains("welcome page/login banner entry still exists"), problem);
   }

   @Test
   void everyOrganizationCapableSubModelHasAResetAndTheGlobalOnlyOnesDoNot() throws Exception {
      FakeOrgStore store = new FakeOrgStore();
      PresentationSettingsAccess access = resetAccess(store);

      for(PresentationSubModel subModel : PresentationSubModel.values()) {
         if(subModel.globalOnly()) {
            assertThrows(IllegalArgumentException.class,
                         () -> access.resetOrg(subModel, PRINCIPAL), subModel.key());
         }
         else {
            assertNotNull(access.resetOrg(subModel, PRINCIPAL), subModel.key());
         }
      }

      verify(time).resetSettings(false);
      verify(share).resetSettings(false);
      verify(webMap).resetSettings(PRINCIPAL, false);
      verify(lookAndFeel).resetSettings(PRINCIPAL, false);
      verify(fontMapping, never()).resetSettings();
      verify(ai, never()).resetSettings();
   }

   @Test
   void orgStateDigestTracksValuesWithoutRevealingThem() {
      FakeOrgStore store = new FakeOrgStore();
      store.props.put(SLACK_URL, "https://hooks.example/secret-token");
      PresentationSettingsAccess access = resetAccess(store);

      String first = access.orgStateDigest();
      assertFalse(first.contains("secret-token"));
      assertEquals(first, access.orgStateDigest());

      store.props.put(SLACK_URL, "https://hooks.example/other");
      assertNotEquals(first, access.orgStateDigest());

      store.props.remove(SLACK_URL);
      assertNotEquals(first, access.orgStateDigest());
   }

   @Test
   void resetKeysPresentNamesOnlyTheSubModelsOwnKeys() {
      FakeOrgStore store = new FakeOrgStore();
      store.props.put(WEEK, "Monday");
      store.props.put("inetsoft.org.o.dashboard.enabled", "false");
      PresentationSettingsAccess access = resetAccess(store);

      assertEquals(List.of(WEEK), access.resetKeysPresent(PresentationSubModel.TIME));
      assertEquals(List.of("inetsoft.org.o.dashboard.enabled"),
                   access.resetKeysPresent(PresentationSubModel.DASHBOARD));
      assertTrue(access.resetKeysPresent(PresentationSubModel.SHARE).isEmpty());
   }
}
