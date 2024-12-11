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
package inetsoft.web.portal.controller;

import inetsoft.report.io.Builder;
import inetsoft.report.io.ExportType;
import inetsoft.sree.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.web.portal.model.PreferencesDialogModel;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

/**
 * Controller that provides a REST endpoint for the preferences dialog
 *
 * @since 12.3
 */
@RestController
public class PreferencesDialogController {
   /**
    * Creates a new instance of <tt>PreferencesDialogController</tt>.
    *
    * @param analyticRepository the analytic repository.
    */
   @Autowired
   public PreferencesDialogController(AnalyticRepository analyticRepository) {
      this.analyticRepository = analyticRepository;
   }

   @GetMapping("/api/portal/get-history-bar-status")
   public boolean getHistoryBarStatus(Principal principal) {
      Object userSetting = UserEnv.getProperty(principal, "historyBarEnable", null);

      // If the user does not manually set it in the Preference, the default value
      // in properties is used. If the user manually sets it, the value set by
      // the current user is used first.
      return userSetting != null ? Boolean.parseBoolean((String) userSetting)
         : SreeEnv.getBooleanProperty("portal.history.bar");
   }

   @GetMapping(value = "/api/portal/preferences-dialog-model")
   public PreferencesDialogModel getPreferencesDialogModel(
      Principal principal) throws Exception
   {
      PreferencesDialogModel model = new PreferencesDialogModel();
      SecurityEngine engine = SecurityEngine.getSecurity();
      SecurityProvider provider = engine.getSecurityProvider();
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      User user = provider.getUser(pId);

      if(user instanceof FSUser) {
         FSUser fsUser = (FSUser) user;
         String[] emails = fsUser.getEmails();
         model.setEmail(emails == null ? null : StringUtils.join(emails, ","));
      }
      else if(user instanceof User) {
         String[] emails = user.getEmails();
         model.setEmail(emails == null ? null : StringUtils.join(emails, ","));
         model.setdisable(true);
      }
      else {
         String SSOName = IdentityID.getIdentityIDFromKey(principal.getName()).name; //SSO users email claim stored as name
         model.setEmail(SSOName);
         model.setdisable(true);
      }

      model.setChangePasswordAvailable(canChangePWD(principal));
      model.setHistoryBarEnabled(this.getHistoryBarStatus(principal));
      return model;
   }

   @PostMapping(value = "/api/portal/preferences-dialog-model")
   public PreferencesDialogModel setPreferencesDialogModel(
      @RequestBody PreferencesDialogModel model, Principal principal)
   {
      String email = model.getEmail();
      email = email == null ? null : email.trim();
      String[] emails = StringUtils.isEmpty(email) ? new String[0] : email.split(",");

      SecurityEngine engine = SecurityEngine.getSecurity();
      SecurityProvider provider = engine.getSecurityProvider();
      EditableAuthenticationProvider eprovider = SUtil.getEditableAuthenticationProvider(provider);
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      User user = eprovider.getUser(pId);

      if(user == null && SUtil.isInternalUser(principal)) {
         throw new MessageException("The authentication provider is not editable!");
      }

      if(user instanceof FSUser) {
         FSUser fsUser = (FSUser) user;
         fsUser.setEmails(emails);
         eprovider.setUser(user.getIdentityID(), fsUser);
      }

      UserEnv.setProperty(principal, "historyBarEnable", model.isHistoryBarEnabled() + "");

      return model;
   }

   private boolean checkDashboardPermissionWithBC(AnalyticRepository engine,
      Principal principal, ResourceType type, String source, ResourceAction access) throws Exception
   {
      //maybe caused by BC
      return engine.checkPermission(principal, type, source, access) ||
         engine.checkPermission(principal, ResourceType.DASHBOARD, "*", access);

   }

   /**
    * Check if change password is allowed.
    */
   private boolean canChangePWD(Principal principal) throws Exception {
      boolean securityEnabled = SecurityEngine.getSecurity().isSecurityEnabled();

      return securityEnabled &&
         "true".equals(SreeEnv.getProperty("enable.changePassword")) &&
         !"anonymous".equals(principal.getName()) &&
         userExistsInEditableSecurityProvider(principal) && SUtil.isInternalUser(principal);
   }

   private List<Integer> getInvisibleFormats() {
      String globalSetting = SreeEnv.getProperty("export.menu.options");
      List<String> formatsList = Arrays.asList(Tool.split(globalSetting, ','));
      int[] formats = Builder.getSupportedExportTypes();
      List<Integer> invisibleFormats = new ArrayList<>();

      if(formatsList.size() > 0) {
         for(int format : formats) {
            ExportType type = Builder.getExportType(format);

            if(!formatsList.contains(type.getFormatOption())) {
               invisibleFormats.add(type.getFormatId());
            }
         }
      }

      return invisibleFormats;
   }

   private boolean userExistsInEditableSecurityProvider(Principal principal) {
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      SecurityProvider securityProvider = SecurityEngine.getSecurity().getSecurityProvider();
      AuthenticationProvider authc = securityProvider.getAuthenticationProvider();

      if(authc instanceof AuthenticationChain) {
         AuthenticationChain chain = (AuthenticationChain) authc;
         authc = chain.stream()
            .filter(p -> p instanceof EditableAuthenticationProvider)
            .filter(p -> p.getUser(pId) != null)
            .findFirst()
            .orElse(null);
      }

      return authc != null;
   }

   private final AnalyticRepository analyticRepository;
   private static final String EMAIL_REGEX =
      "^[\\w\\d\\-_]+(\\.[\\w\\d\\-_]+)*@[\\w\\d\\-_]+(\\.[\\w\\d\\-_]+)*$";
}
