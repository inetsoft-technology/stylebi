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
package inetsoft.web.admin.presentation;

import inetsoft.graph.geo.service.MapboxStyle;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.*;
import inetsoft.sree.security.*;
import inetsoft.uql.viewsheet.graph.aesthetic.ImageShapes;
import inetsoft.web.admin.content.dataspace.DataSpaceContentSettingsService;
import inetsoft.web.admin.general.WebMapSettingsService;
import inetsoft.web.admin.presentation.model.*;

import java.security.Principal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class PresentationSettingsController {
   @Autowired
   public PresentationSettingsController(
      PresentationFormatsSettingsService formatsSettingsService,
      LookAndFeelService lookAndFeelService,
      WelcomePageService welcomePageService,
      PresentationLoginBannerSettingsService loginBannerSettingsService,
      PresentationToolbarSettingsService toolbarSettingsService,
      PortalIntegrationViewSettingsService portalIntegrationViewSettingsService,
      PresentationDashboardSettingsService dashboardSettingsService,
      PresentationPdfGenerationSettingsService pdfGenerationSettingsService,
      ExportMenuSettingsService exportMenuSettingsService,
      PresentationFontMappingSettingsService fontMappingSettingsService,
      ShareSettingsService shareSettingsService,
      SecurityEngine securityEngine,
      PresentationComposerMessageSettingsService composerMessageSettingsService,
      PresentationTimeSettingsService timeSettingsService,
      PresentationDataSourceVisibilitySettingsService dataSourceVisibilitySettingsService,
      WebMapSettingsService webMapSettingsService,
      DataSpaceContentSettingsService dataSpaceContentSettingsService)
   {
      this.lookAndFeelService = lookAndFeelService;
      this.welcomePageService = welcomePageService;
      this.formatsSettingsService = formatsSettingsService;
      this.loginBannerSettingsService = loginBannerSettingsService;
      this.toolbarSettingsService = toolbarSettingsService;
      this.portalIntegrationViewSettingsService = portalIntegrationViewSettingsService;
      this.dashboardSettingsService = dashboardSettingsService;
      this.pdfGenerationSettingsService = pdfGenerationSettingsService;
      this.exportMenuSettingsService = exportMenuSettingsService;
      this.fontMappingSettingsService = fontMappingSettingsService;
      this.shareSettingsService = shareSettingsService;
      this.securityEngine = securityEngine;
      this.composerMessageSettingsService = composerMessageSettingsService;
      this.timeSettingsService = timeSettingsService;
      this.dataSourceVisibilitySettingsService = dataSourceVisibilitySettingsService;
      this.webMapSettingsService = webMapSettingsService;
      this.dataSpaceContentSettingsService = dataSpaceContentSettingsService;
   }

   @GetMapping("/api/em/settings/presentation/model")
   public PresentationSettingsModel getSettings(Principal principal,
      @RequestParam(name = "orgSettings", defaultValue = "false") boolean orgSettings)
   {
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      boolean securityEnabled = !SecurityEngine.getSecurity().getSecurityProvider().isVirtual();
      boolean globalProperty = (OrganizationManager.getInstance().isSiteAdmin(principal) && !orgSettings)
         || !securityEnabled || securityEnabled && !SUtil.isMultiTenant();
      return PresentationSettingsModel.builder()
         .lookAndFeelSettingsModel(lookAndFeelService.getModel(principal, globalProperty))
         .welcomePageSettingsModel(welcomePageService.getModel(globalProperty))
         .formatsSettingsModel(formatsSettingsService.getModel(globalProperty))
         .loginBannerSettingsModel(loginBannerSettingsService.getModel(globalProperty))
         .viewsheetToolbarOptionsModel(toolbarSettingsService.getViewsheetOptions(globalProperty))
         .portalIntegrationSettingsModel(portalIntegrationViewSettingsService.getModel(principal, globalProperty))
         .dashboardSettingsModel(dashboardSettingsService.getModel(globalProperty))
         .pdfGenerationSettingsModel(pdfGenerationSettingsService.getModel(globalProperty))
         .exportMenuSettingsModel(exportMenuSettingsService.getExportMenuSettings(globalProperty))
         .fontMappingSettingsModel(fontMappingSettingsService.getModel())
         .shareSettingsModel(shareSettingsService.getModel(globalProperty))
         .composerSettingMessageModel(composerMessageSettingsService.getModel(globalProperty))
         .timeSettingsModel(timeSettingsService.getModel(globalProperty))
         .dataSourceVisibilitySettingsModel(dataSourceVisibilitySettingsService.getModel(globalProperty))
         .webMapSettingsModel(webMapSettingsService.getModel(globalProperty))
         .securityEnabled(!securityEngine.getSecurityProvider().isVirtual())
         .orgSettings(!globalProperty)
         .build();
   }

   @GetMapping("/api/em/presentation/settings/mapstyles/{mapboxUser}/{mapboxToken}")
   public List<MapboxStyle> getMapStyles(Principal principal,
                                         @PathVariable("mapboxUser") String mapboxUser,
                                         @PathVariable("mapboxToken") String mapboxToken)
   {
      return webMapSettingsService.getMapStyles(mapboxUser, mapboxToken);
   }

   @PostMapping("/api/em/settings/presentation/model")
   public PresentationSettingsModel applySettings(@RequestBody() PresentationSettingsModel model,
                                                  Principal principal) throws Exception
   {
      boolean securityEnabled = !SecurityEngine.getSecurity().getSecurityProvider().isVirtual();
      boolean globalSettings = OrganizationManager.getInstance().isSiteAdmin(principal) &&
         (model.orgSettings() != null && !model.orgSettings()) || !securityEnabled ||
         !SUtil.isMultiTenant();

      if(model.formatsSettingsModel() != null) {
         formatsSettingsService.setModel(model.formatsSettingsModel(), globalSettings);
      }

      if(model.lookAndFeelSettingsModel() != null) {
         lookAndFeelService.setModel(model.lookAndFeelSettingsModel(), globalSettings);
      }

      if(model.loginBannerSettingsModel() != null) {
         loginBannerSettingsService.setModel(model.loginBannerSettingsModel(), globalSettings);
      }

      if(model.portalIntegrationSettingsModel() != null) {
         portalIntegrationViewSettingsService
            .setModel(model.portalIntegrationSettingsModel(), principal, globalSettings);
      }

      if(model.viewsheetToolbarOptionsModel() != null) {
         toolbarSettingsService.setViewsheetOptions(model.viewsheetToolbarOptionsModel(), globalSettings);
      }

      if(model.dashboardSettingsModel() != null) {
         dashboardSettingsService.setModel(model.dashboardSettingsModel(), globalSettings);
      }

      if(model.welcomePageSettingsModel() != null) {
         welcomePageService.setModel(model.welcomePageSettingsModel(), globalSettings);
      }

      if(model.pdfGenerationSettingsModel() != null) {
         pdfGenerationSettingsService.setModel(model.pdfGenerationSettingsModel(), globalSettings);
      }

      if(model.exportMenuSettingsModel() != null) {
         exportMenuSettingsService.setExportMenuSettings(model.exportMenuSettingsModel(), globalSettings);
      }

      if(model.fontMappingSettingsModel() != null) {
         fontMappingSettingsService.setModel(model.fontMappingSettingsModel());
      }

      if(model.shareSettingsModel() != null) {
         shareSettingsService.setModel(model.shareSettingsModel(), globalSettings);
      }

      if(model.composerSettingMessageModel() != null) {
         composerMessageSettingsService.setModel(model.composerSettingMessageModel(), globalSettings);
      }

      if(model.timeSettingsModel() != null) {
         timeSettingsService.setModel(model.timeSettingsModel(), globalSettings);
      }

      if(model.dataSourceVisibilitySettingsModel() != null) {
         dataSourceVisibilitySettingsService.setModel(model.dataSourceVisibilitySettingsModel(), globalSettings);
      }

      if(model.webMapSettingsModel() != null) {
         webMapSettingsService.setModel(model.webMapSettingsModel(), principal, globalSettings);
      }

      return getSettings(principal, !globalSettings);
   }

   @PostMapping("/api/em/settings/presentation/model/reset")
   public PresentationSettingsModel resetSettings(@RequestBody() PresentationSettingsModel model,
                                                  Principal principal) throws Exception
   {
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      boolean securityEnabled = !SecurityEngine.getSecurity().getSecurityProvider().isVirtual();
      boolean globalSettings = OrganizationManager.getInstance().isSiteAdmin(principal) &&
         (model.orgSettings() != null && !model.orgSettings()) || !securityEnabled;

      formatsSettingsService.resetSettings(globalSettings);
      lookAndFeelService.resetSettings(globalSettings);
      portalIntegrationViewSettingsService.resetSettings(globalSettings);
      toolbarSettingsService.resetSettings(globalSettings);
      dashboardSettingsService.resetSettings(globalSettings);
      pdfGenerationSettingsService.resetSettings(globalSettings);
      exportMenuSettingsService.resetSettings(globalSettings);
      shareSettingsService.resetSettings(globalSettings);
      composerMessageSettingsService.resetSettings(globalSettings);
      timeSettingsService.resetSettings(globalSettings);
      dataSourceVisibilitySettingsService.resetSettings(globalSettings);
      webMapSettingsService.resetSettings(principal, globalSettings);
      dataSpaceContentSettingsService.deleteDataSpaceNode(ImageShapes.getShapesDirectory(), false);
      welcomePageService.resetSettings(globalSettings);
      loginBannerSettingsService.resetSettings(globalSettings);

      if(globalSettings) {
         fontMappingSettingsService.resetSettings();
      }

      return getSettings(principal, !globalSettings);
   }

   private final LookAndFeelService lookAndFeelService;
   private final WelcomePageService welcomePageService;
   private final PresentationFormatsSettingsService formatsSettingsService;
   private final PresentationLoginBannerSettingsService loginBannerSettingsService;
   private final PresentationToolbarSettingsService toolbarSettingsService;
   private final PortalIntegrationViewSettingsService portalIntegrationViewSettingsService;
   private final PresentationDashboardSettingsService dashboardSettingsService;
   private final PresentationPdfGenerationSettingsService pdfGenerationSettingsService;
   private final ExportMenuSettingsService exportMenuSettingsService;
   private final PresentationFontMappingSettingsService fontMappingSettingsService;
   private final ShareSettingsService shareSettingsService;
   private final SecurityEngine securityEngine;
   private final PresentationComposerMessageSettingsService composerMessageSettingsService;
   private final PresentationTimeSettingsService timeSettingsService;
   private final PresentationDataSourceVisibilitySettingsService dataSourceVisibilitySettingsService;
   private final WebMapSettingsService webMapSettingsService;
   private final DataSpaceContentSettingsService dataSpaceContentSettingsService;
}
