/*
 * Copyright (c) 2018, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
 */
package inetsoft.web.admin.general;

import inetsoft.sree.SreeEnv;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.general.model.*;
import inetsoft.web.admin.general.model.model.*;
import inetsoft.web.viewsheet.AuditUser;
import inetsoft.web.viewsheet.Audited;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
public class EmailSettingsService {
   public EmailSettingsModel getModel() {
      boolean historyEnabled = "true".equalsIgnoreCase(SreeEnv.getProperty("mail.history.enabled"));

      return EmailSettingsModel.builder()
         .smtpHost(SreeEnv.getProperty("mail.smtp.host"))
         .ssl(SreeEnv.getBooleanProperty("mail.ssl"))
         .tls(SreeEnv.getBooleanProperty("mail.tls"))
         .jndiUrl(SreeEnv.getProperty("mail.jndi.url"))
         .smtpAuthentication("true".equals(SreeEnv.getProperty("mail.smtp.authbox")))
         .smtpAuthenticationType(SMTPAuthType.forValue(SreeEnv.getProperty("mail.smtp.auth")))
         .smtpUser(SreeEnv.getProperty("mail.smtp.user"))
         .smtpSecretId(Tool.isCloudSecrets() ? SreeEnv.getProperty("mail.smtp.pass") : null)
         .smtpPassword(!Tool.isCloudSecrets() ? SreeEnv.getPassword("mail.smtp.pass") : null)
         .fromAddress(SreeEnv.getProperty("mail.from.address"))
         .deliveryMailSubjectFormat(SreeEnv.getProperty("mail.subject.format"))
         .notificationMailSubjectFormat(SreeEnv.getProperty("mail.notification.subject.format"))
         .historyEnabled(historyEnabled)
         .secretIdVisible(Tool.isCloudSecrets())
         .smtpClientId(SreeEnv.getProperty("mail.smtp.clientId"))
         .smtpClientSecret(SreeEnv.getPassword("mail.smtp.clientSecret"))
         .smtpAuthUri(SreeEnv.getProperty("mail.smtp.authUri"))
         .smtpTokenUri(SreeEnv.getPassword("mail.smtp.tokenUri"))
         .smtpOAuthScopes(SreeEnv.getProperty("mail.smtp.oauthScopes"))
         .smtpOAuthFlags(SreeEnv.getPassword("mail.smtp.outhFlags"))
         .smtpAccessToken(SreeEnv.getPassword("mail.smtp.accessToken"))
         .smtpRefreshToken(SreeEnv.getPassword("mail.smtp.refreshToken"))
         .tokenExpiration(SreeEnv.getProperty("mail.smtp.tokenExpiration"))
         .build();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "General-Mail",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void setModel(EmailSettingsModel model,
                        @SuppressWarnings("unused") @AuditUser Principal principal)
      throws Exception
   {
      SreeEnv.setProperty("mail.smtp.authbox", model.smtpAuthentication() + "");
      SreeEnv.setProperty("mail.smtp.auth", model.smtpAuthenticationType().value());

      if(model.smtpAuthenticationType() == SMTPAuthType.SMTP_AUTH) {
         SreeEnv.setProperty("mail.smtp.user", model.smtpUser());SreeEnv.setPassword(
            "mail.smtp.pass", Tool.isCloudSecrets() ? model.smtpSecretId() :model.smtpPassword());
      }
      else if(model.smtpAuthenticationType() == SMTPAuthType.SASL_XOAUTH2 || model.smtpAuthenticationType() == SMTPAuthType.GOOGLE_AUTH) {
         SreeEnv.setProperty("mail.smtp.user", model.smtpUser());

         SreeEnv.setProperty("mail.smtp.clientId", model.smtpClientId());
         SreeEnv.setPassword("mail.smtp.clientSecret", model.smtpClientSecret());
         SreeEnv.setPassword("mail.smtp.accessToken", model.smtpAccessToken());
         SreeEnv.setPassword("mail.smtp.refreshToken", model.smtpRefreshToken());
         SreeEnv.setProperty("mail.smtp.tokenExpiration", model.tokenExpiration());

         if(model.smtpAuthenticationType() == SMTPAuthType.SASL_XOAUTH2) {
            SreeEnv.setProperty("mail.smtp.authUri", model.smtpAuthUri());
            SreeEnv.setProperty("mail.smtp.tokenUri", model.smtpTokenUri());
            SreeEnv.setProperty("mail.smtp.oauthScopes", model.smtpOAuthScopes());
            SreeEnv.setProperty("mail.smtp.oauthFlags", model.smtpOAuthFlags());
         }
      }

      SreeEnv.setProperty("mail.smtp.host", model.smtpHost());
      SreeEnv.setProperty("mail.ssl", model.ssl() + "");
      SreeEnv.setProperty("mail.tls", model.tls() + "");
      SreeEnv.setProperty("mail.jndi.url", model.jndiUrl());
      SreeEnv.setProperty("mail.from.address", model.fromAddress());
      SreeEnv.setProperty("mail.subject.format", model.deliveryMailSubjectFormat());
      SreeEnv.setProperty("mail.notification.subject.format", model.notificationMailSubjectFormat());
      SreeEnv.setProperty("mail.history.enabled", model.historyEnabled() + "");
      SreeEnv.save();
   }

   public OAuthParams getOAuthParams(OAuthParamsRequest request) {
      String license = SreeEnv.getProperty("license.key");
      int index = license.indexOf(',');

      if(index >= 0) {
         license = license.substring(0, index);
      }

      OAuthParams.Builder builder = OAuthParams.builder()
         .license(license)
         .user(request.user())
         .password(request.password())
         .clientId(request.clientId())
         .clientSecret(request.clientSecret())
         .authorizationUri(request.authorizationUri())
         .tokenUri(request.tokenUri())
         .method("updateTokens");

      if(request.scope() != null) {
         builder.addScope(request.scope().split(" "));
      }

      if(request.flags() != null) {
         builder.addScope(request.flags().split(" "));
      }

      return builder.build();
   }
}
