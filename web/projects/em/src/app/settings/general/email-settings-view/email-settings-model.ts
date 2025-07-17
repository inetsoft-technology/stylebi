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
export interface EmailSettingsModel {
   smtpHost: string;
   ssl: boolean;
   tls: boolean;
   jndiUrl: string;
   smtpAuthentication: boolean;
   smtpAuthenticationType: SMTPAuthType;
   smtpUser: string;
   smtpPassword: string;
   smtpSecretId: string;
   confirmSmtpPassword?: string;
   fromAddress: string;
   fromAddressEnabled: boolean;
   deliveryMailSubjectFormat: string;
   notificationMailSubjectFormat: string;
   historyEnabled: boolean;
   secretIdVisible: boolean;
   smtpClientId: string;
   smtpClientSecret: string;
   smtpAccessToken: string;
   smtpAuthUri: string;
   smtpTokenUri: string;
   smtpOAuthScopes: string;
   smtpOAuthFlags: string;
   smtpRefreshToken: string;
   tokenExpiration: string;
}

export enum SMTPAuthType {
   NONE = <any> "NONE",
   SMTP_AUTH = <any> "SMTP_AUTH",
   SASL_XOAUTH2 = <any> "SASL_XOAUTH2",
   GOOGLE_AUTH = <any> "GOOGLE_AUTH"
}