/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { NameLabelTuple } from "../../../../../../shared/util/name-label-tuple";
import { SSOType } from "./sso-settings-page/sso-settings-page.component";

export interface SSOSettingsModel {
   samlAttributesModel: SamlAttributesModel;
   openIdAttributesModel: OpenIdAttributesModel;
   customAttributesModel: CustomSSOAttributesModel;
   roles: NameLabelTuple[];
   selectedRoles: string[];
   activeFilterType: SSOType;
   logoutUrl?: string;
   logoutPath?: string;
   fallbackLogin: boolean;
}

export type PickSSOSettingsModel<K extends keyof T, T = SSOSettingsModel> = {
   [P in K]: T[P];
} & Partial<Pick<SSOSettingsModel, "activeFilterType">> &
   Partial<Pick<SSOSettingsModel, "roles">> &
   Partial<Pick<SSOSettingsModel, "selectedRoles">> &
   Partial<Pick<SSOSettingsModel, "logoutUrl">> &
   Partial<Pick<SSOSettingsModel, "logoutPath">> &
   Partial<Pick<SSOSettingsModel, "fallbackLogin">>;

export type SamlFormModel = PickSSOSettingsModel<"samlAttributesModel">;
export type OpenIdFormModel = PickSSOSettingsModel<"openIdAttributesModel">;
export type CustomFormModel = PickSSOSettingsModel<"customAttributesModel">;
export type SSOFormModel = SamlFormModel | OpenIdFormModel | CustomFormModel;

export interface SSOAttributesModel {
}

export interface SamlAttributesModel extends SSOAttributesModel {
   spEntityId: string;
   assertionUrl: string;
   idpEntityId: string;
   idpSignOnUrl: string;
   idpLogoutUrl: string;
   idpPublicKey: string;
   roleClaim: string;
   groupClaim: string;
   orgIDClaim: string;
}

export interface OpenIdAttributesModel extends SSOAttributesModel {
   clientId: string;
   clientSecret: string;
   scopes?: string;
   issuer: string;
   audience?: string;
   tokenEndpoint: string;
   authorizationEndpoint: string;
   jwksUri?: string;
   jwkCertificate?: string;
   nameClaim: string;
   roleClaim: string;
   groupClaim: string;
   orgIDClaim: string;
   openIdPropertyProvider: string;
   openIdPostprocessor: string;
}

export interface CustomSSOAttributesModel extends SSOAttributesModel {
   useJavaClass: boolean;
   javaClassName?: string;
   useInlineGroovy: boolean;
   inlineGroovyClass?: string;
}