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
package inetsoft.web.admin.general;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.web.admin.general.model.*;
import inetsoft.web.admin.general.model.model.OAuthParams;
import inetsoft.web.admin.general.model.model.OAuthParamsRequest;
import inetsoft.web.admin.security.ConnectionStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.OutputStream;
import java.net.SocketException;
import java.security.Principal;

@RestController
public class GeneralSettingsPageController {
   @Autowired
   public GeneralSettingsPageController(LicenseKeySettingsService licenseKeySettingsService,
                                        LocalizationSettingsService localizationSettingsService,
                                        MVSettingsService mvSettingsService,
                                        CacheSettingsService cacheSettingsService,
                                        DataSpaceSettingsService dataSpaceSettingsService,
                                        EmailSettingsService emailSettingsService,
                                        PerformanceSettingsService performanceSettingsService,
                                        SecurityEngine securityEngine)
   {
      this.licenseKeySettingsService = licenseKeySettingsService;
      this.localizationSettingsService = localizationSettingsService;
      this.mvSettingsService = mvSettingsService;
      this.cacheSettingsService = cacheSettingsService;
      this.dataSpaceSettingsService = dataSpaceSettingsService;
      this.emailSettingsService = emailSettingsService;
      this.performanceSettingsService = performanceSettingsService;
      this.securityEngine = securityEngine;
   }

   @GetMapping("/api/em/general/settings/model")
   public GeneralSettingsPageModel getPageModel(Principal principal) throws Exception {
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      if (SecurityEngine.getSecurity().isSecurityEnabled() &&
              SUtil.isMultiTenant() && !OrganizationManager.getInstance().isSiteAdmin(principal)) {
         return GeneralSettingsPageModel.builder()
                 .mvSettingsModel(mvSettingsService.getModel(principal))
                 .build();
      }

      return GeneralSettingsPageModel.builder()
         .dataSpaceSettingsModel(dataSpaceSettingsService.getModel(principal))
         .licenseKeySettingsModel(licenseKeySettingsService.getModel())
         .localizationSettingsModel(localizationSettingsService.getModel())
         .mvSettingsModel(mvSettingsService.getModel(principal))
         .cacheSettingsModel(cacheSettingsService.getModel())
         .emailSettingsModel(emailSettingsService.getModel())
         .performanceSettingsModel(performanceSettingsService.getModel())
         .securityEnabled(!securityEngine.getSecurityProvider().isVirtual())
         .build();
   }

   @PostMapping("/api/em/general/settings/model")
   public GeneralSettingsPageModel setPageModel(@RequestBody GeneralSettingsPageModel model,
                                                Principal principal,  HttpServletRequest request)
      throws Exception
   {
      if(SecurityEngine.getSecurity().isSecurityEnabled() && SUtil.isMultiTenant() &&
         !OrganizationManager.getInstance().isSiteAdmin(principal))
      {
         if(model.mvSettingsModel() != null) {
            mvSettingsService.setModel(model.mvSettingsModel(), principal);
         }
      }
      else {
         if(model.licenseKeySettingsModel() != null) {
            licenseKeySettingsService.setModel(model.licenseKeySettingsModel(), principal);
         }

         if(model.mvSettingsModel() != null) {
            mvSettingsService.setModel(model.mvSettingsModel(), principal);
         }

         if(model.localizationSettingsModel() != null) {
            localizationSettingsService.setModel(model.localizationSettingsModel(), principal);
         }

         if(model.cacheSettingsModel() != null) {
            cacheSettingsService.setModel(model.cacheSettingsModel(), principal);
         }

         if(model.emailSettingsModel() != null) {
            emailSettingsService.setModel(model.emailSettingsModel(), principal);
         }

         if(model.performanceSettingsModel() != null) {
            performanceSettingsService.setModel(model.performanceSettingsModel(), principal);
         }
      }

      return getPageModel(principal);
   }

   @PostMapping("/api/em/general/settings/data-space/backup")
   public ConnectionStatus backup(@RequestBody BackupDataModel model, Principal principal) {
      return new ConnectionStatus(DataSpaceSettingsService.backup(model));
   }

   @GetMapping("/api/em/general/settings/license-key/single-server-key")
   public LicenseKeyModel getSingleServerLicenseKey(@RequestParam("key") String key) {
      return this.licenseKeySettingsService.getSingleServerLicenseKey(key);
   }

   @GetMapping("/api/em/general/settings/localization/locale")
   public void reloadLocales() throws Exception {
      this.localizationSettingsService.reloadLocales();
   }

   @GetMapping("/em/general/settings/localization/generateBundle")
   public void generateBundle(Principal principal, HttpServletResponse response) throws Exception {
      response.setHeader("Content-disposition",
                         "attachment; filename*=utf-8''user_bundle.properties");
      response.setHeader("extension", "properties");
      response.setHeader("Cache-Control", "");
      response.setHeader("Pragma", "");
      response.setContentType("text/properties");

      try(OutputStream output = response.getOutputStream()) {
         this.localizationSettingsService.generateBundle(principal, output);
      }
      catch(SocketException ignore) {
      }
   }

   @GetMapping("/api/em/general/settings/cache/cleanup")
   public void cleanUpCache() {
      cacheSettingsService.cleanUpCache();
   }

   @PostMapping("/api/em/general/settings/email/oauth-params")
   public OAuthParams getOAuthParams(@RequestBody OAuthParamsRequest request) throws Exception {
      return emailSettingsService.getOAuthParams(request);
   }

   private final LicenseKeySettingsService licenseKeySettingsService;
   private final LocalizationSettingsService localizationSettingsService;
   private final MVSettingsService mvSettingsService;
   private final CacheSettingsService cacheSettingsService;
   private final DataSpaceSettingsService dataSpaceSettingsService;
   private final EmailSettingsService emailSettingsService;
   private final PerformanceSettingsService performanceSettingsService;
   private final SecurityEngine securityEngine;
}
