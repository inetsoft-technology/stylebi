/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { WebMapSettingsModel } from "../webmap-settings-view/webmap-settings-model";
import { LookAndFeelSettingsModel } from "../look-and-feel-settings-view/look-and-feel-settings-model";
import { PresentationDataSourceVisibilitySettingsModel } from "../presentation-data-source-visibility-settings/presentation-data-source-visibility-settings-model";
import { PresentationFormatsSettingsModel } from "../presentation-formats-settings-view/presentation-formats-settings-model";
import { PortalIntegrationSettingsModel } from "../portal-integration-view/portal-integration-settings-model";
import { PresentationTimeSettingsModel } from "../presentation-time-settings-view/presentation-time-settings-model";
import { PresentationViewsheetToolbarOptionsModel } from "../presentation-viewsheet-toolbar-options-view/presentation-viewsheet-toolbar-options-model";
import { PresentationLoginBannerSettingsModel } from "../presentation-login-banner-settings-view/presentation-login-banner-settings-model";
import { PresentationPdfGenerationSettingsModel } from "../presentation-pdf-generation-settings-view/presentation-pdf-generation-settings-model";
import { PresentationShareSettingsModel } from "../presentation-share-settings-view/presentation-share-settings-model";
import { WelcomePageSettingsModel } from "../welcome-page-settings-view/welcome-page-settings-model";
import { PresentationDashboardSettingsModel } from "../presentation-dashboard-settings-view/presentation-dashboard-settings-model";
import { PresentationExportMenuSettingsModel } from "../presentation-export-menu-settings-view/presentation-export-menu-settings-model";
import { PresentationFontMappingSettingsModel } from "../presentation-font-mapping-settings-view/presentation-font-mapping-settings-model";
import { PresentationComposerMessageSettingsModel } from "../presentation-composer-message-settings-view/presentation-composer-message-settings-model";

export interface PresentationSettingsModel {
   formatsSettingsModel?: PresentationFormatsSettingsModel;
   exportMenuSettingsModel?: PresentationExportMenuSettingsModel;
   viewsheetToolbarOptionsModel?: PresentationViewsheetToolbarOptionsModel;
   lookAndFeelSettingsModel?: LookAndFeelSettingsModel;
   welcomePageSettingsModel?: WelcomePageSettingsModel;
   loginBannerSettingsModel?: PresentationLoginBannerSettingsModel;
   portalIntegrationSettingsModel?: PortalIntegrationSettingsModel;
   dashboardSettingsModel?: PresentationDashboardSettingsModel;
   pdfGenerationSettingsModel?: PresentationPdfGenerationSettingsModel;
   fontMappingSettingsModel?: PresentationFontMappingSettingsModel;
   shareSettingsModel?: PresentationShareSettingsModel;
   composerSettingMessageModel?: PresentationComposerMessageSettingsModel;
   timeSettingsModel?: PresentationTimeSettingsModel;
   dataSourceVisibilitySettingsModel?: PresentationDataSourceVisibilitySettingsModel;
   webMapSettingsModel?: WebMapSettingsModel;
   securityEnabled?: boolean;
   orgSettings?: boolean;
}