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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.EnumMap;

/**
 * Bridges the 16-entry {@link PresentationSubModel} catalog to the real sub-services
 * {@code PresentationSettingsController} itself wraps -- never calls the controller (same "wrap the
 * service layer, not the EM controller" decision C.4 made for providers, 01-spec.md section 4a).
 *
 * <p>Each of the 16 real services has a slightly different {@code getModel}/{@code setModel} shape
 * (some take a {@link Principal}, some don't; {@code fontMapping}/{@code ai} take no scope boolean at
 * all) -- normalized here, once, into one uniform {@code (Principal, boolean) -> Object} /
 * {@code (Object, Principal, boolean) -> void} pair per sub-model, so the plan/apply services never
 * need to know which of the 16 shapes they are calling.
 */
@Component
public class PresentationSettingsAccess {
   @Autowired
   public PresentationSettingsAccess(
      PresentationFormatsSettingsService formats,
      PresentationDashboardSettingsService dashboard,
      PresentationToolbarSettingsService toolbar,
      LookAndFeelService lookAndFeel,
      WelcomePageService welcomePage,
      PresentationLoginBannerSettingsService loginBanner,
      PortalIntegrationViewSettingsService portalIntegration,
      PresentationPdfGenerationSettingsService pdfGeneration,
      ExportMenuSettingsService exportMenu,
      PresentationFontMappingSettingsService fontMapping,
      ShareSettingsService share,
      PresentationComposerMessageSettingsService composerMessage,
      PresentationTimeSettingsService time,
      PresentationDataSourceVisibilitySettingsService dataSourceVisibility,
      WebMapSettingsService webMap,
      AISettingsService ai)
   {
      adapters = new EnumMap<>(PresentationSubModel.class);

      adapters.put(PresentationSubModel.FORMATS, new Adapter(
         (principal, global) -> formats.getModel(global),
         (model, principal, global) ->
            formats.setModel((PresentationFormatsSettingsModel) model, global)));

      adapters.put(PresentationSubModel.DASHBOARD, new Adapter(
         (principal, global) -> dashboard.getModel(global),
         (model, principal, global) ->
            dashboard.setModel((PresentationDashboardSettingsModel) model, global)));

      adapters.put(PresentationSubModel.VIEWSHEET_TOOLBAR, new Adapter(
         (principal, global) -> toolbar.getViewsheetOptions(global),
         (model, principal, global) ->
            toolbar.setViewsheetOptions((PresentationViewsheetToolbarOptionsModel) model, global)));

      adapters.put(PresentationSubModel.LOOK_AND_FEEL, new Adapter(
         lookAndFeel::getModel,
         (model, principal, global) ->
            lookAndFeel.setModel((LookAndFeelSettingsModel) model, principal, global)));

      adapters.put(PresentationSubModel.WELCOME_PAGE, new Adapter(
         (principal, global) -> welcomePage.getModel(global),
         (model, principal, global) ->
            welcomePage.setModel((WelcomePageSettingsModel) model, global)));

      adapters.put(PresentationSubModel.LOGIN_BANNER, new Adapter(
         (principal, global) -> loginBanner.getModel(global),
         (model, principal, global) ->
            loginBanner.setModel((PresentationLoginBannerSettingsModel) model, global)));

      adapters.put(PresentationSubModel.PORTAL_INTEGRATION, new Adapter(
         portalIntegration::getModel,
         (model, principal, global) ->
            portalIntegration.setModel((PortalIntegrationSettingsModel) model, principal, global)));

      adapters.put(PresentationSubModel.PDF_GENERATION, new Adapter(
         (principal, global) -> pdfGeneration.getModel(global),
         (model, principal, global) ->
            pdfGeneration.setModel((PresentationPdfGenerationSettingsModel) model, global)));

      adapters.put(PresentationSubModel.EXPORT_MENU, new Adapter(
         (principal, global) -> exportMenu.getExportMenuSettings(global),
         (model, principal, global) ->
            exportMenu.setExportMenuSettings((PresentationExportMenuSettingsModel) model, global)));

      adapters.put(PresentationSubModel.FONT_MAPPING, new Adapter(
         (principal, global) -> fontMapping.getModel(),
         (model, principal, global) ->
            fontMapping.setModel((PresentationFontMappingSettingsModel) model)));

      adapters.put(PresentationSubModel.SHARE, new Adapter(
         (principal, global) -> share.getModel(global),
         (model, principal, global) ->
            share.setModel((PresentationShareSettingsModel) model, global)));

      adapters.put(PresentationSubModel.COMPOSER_MESSAGE, new Adapter(
         (principal, global) -> composerMessage.getModel(global),
         (model, principal, global) ->
            composerMessage.setModel((PresentationComposerMessageSettingsModel) model, global)));

      adapters.put(PresentationSubModel.TIME, new Adapter(
         (principal, global) -> time.getModel(global),
         (model, principal, global) ->
            time.setModel((PresentationTimeSettingsModel) model, global)));

      adapters.put(PresentationSubModel.DATA_SOURCE_VISIBILITY, new Adapter(
         (principal, global) -> dataSourceVisibility.getModel(global),
         (model, principal, global) -> dataSourceVisibility
            .setModel((PresentationDataSourceVisibilitySettingsModel) model, global)));

      adapters.put(PresentationSubModel.WEB_MAP, new Adapter(
         (principal, global) -> webMap.getModel(global),
         (model, principal, global) ->
            webMap.setModel((inetsoft.web.admin.general.model.WebMapSettingsModel) model, principal,
                            global)));

      adapters.put(PresentationSubModel.AI, new Adapter(
         (principal, global) -> ai.getModel(),
         (model, principal, global) -> ai.setModel((PresentationAISettingsModel) model)));
   }

   /** Reads the current value of one sub-model, in whichever of the 16 real shapes it actually has. */
   public Object read(PresentationSubModel subModel, Principal principal, boolean global)
      throws Exception
   {
      return adapters.get(subModel).reader.read(principal, global);
   }

   /** Writes a fully-resolved (never partial) sub-model value -- callers must merge a partial
    * {@code spec} onto a fresh {@link #read} result themselves before calling this (the real
    * {@code setModel} methods all expect a complete object, none accept a patch). */
   public void write(PresentationSubModel subModel, Object model, Principal principal, boolean global)
      throws Exception
   {
      adapters.get(subModel).writer.write(model, principal, global);
   }

   @FunctionalInterface
   private interface Reader {
      Object read(Principal principal, boolean global) throws Exception;
   }

   @FunctionalInterface
   private interface Writer {
      void write(Object model, Principal principal, boolean global) throws Exception;
   }

   private record Adapter(Reader reader, Writer writer) {
   }

   private final EnumMap<PresentationSubModel, Adapter> adapters;
}
