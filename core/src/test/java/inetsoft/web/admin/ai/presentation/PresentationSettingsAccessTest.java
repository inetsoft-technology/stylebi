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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

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

   @Test
   void fontMappingOrganizationScopedReadIsNeverNulled() throws Exception {
      PresentationFontMappingSettingsModel model =
         PresentationFontMappingSettingsModel.builder().fontMappings(List.of()).build();
      when(fontMapping.getModel()).thenReturn(model);

      assertSame(model, access().read(PresentationSubModel.FONT_MAPPING, PRINCIPAL, true));
      assertSame(model, access().read(PresentationSubModel.FONT_MAPPING, PRINCIPAL, false));
   }
}
