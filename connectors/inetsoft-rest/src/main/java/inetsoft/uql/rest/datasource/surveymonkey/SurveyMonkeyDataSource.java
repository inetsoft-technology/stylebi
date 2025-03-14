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
package inetsoft.uql.rest.datasource.surveymonkey;

import inetsoft.uql.rest.json.OAuthEndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.CoreTool;
import inetsoft.util.credential.CredentialType;
import org.apache.http.HttpHeaders;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1(
      type = ViewType.BUTTON,
      text = "Authorize",
      button = @Button(
         type = ButtonType.OAUTH,
         method = "updateTokens",
         oauth = @Button.OAuth(serviceName = "survey-monkey"))
   ),
   @View1(type = ViewType.LABEL, text = "em.license.communityAPIKeyRequired", align = ViewAlign.FILL,
      wrap = true, colspan = 2, visibleMethod ="displayAPIKeyTip"),
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "accessToken", visibleMethod = "useCredential"),
   @View1("rowLimit")
})
public class SurveyMonkeyDataSource extends OAuthEndpointJsonDataSource<SurveyMonkeyDataSource> {
   static final String TYPE = "Rest.SurveyMonkey";

   public SurveyMonkeyDataSource() {
      super(TYPE, SurveyMonkeyDataSource.class);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.ACCESS_TOKEN;
   }

   @Property(label = "Row Limit")
   public int getRowLimit() {
      return rowLimit;
   }

   public void setRowLimit(int rowLimit) {
      this.rowLimit = rowLimit;
   }

   @Override
   protected String getTestSuffix() {
      return "/users/me";
   }

   @Override
   public String getURL() {
      return "https://api.surveymonkey.com/v3";
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      return new HttpParameter[]{
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name(HttpHeaders.AUTHORIZATION)
            .value("Bearer " + getAccessToken())
            .build()
      };
   }

   @Override
   public double getRequestsPerSecond() {
      return 2;
   }

   @Override
   public long getTokenExpiration() {
      return 0;
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      writer.format("<rowLimit>%s</rowLimit>\n", rowLimit);
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      final String limit = CoreTool.getChildValueByTagName(root, "rowLimit");

      if(limit != null) {
         rowLimit = Integer.parseInt(limit);
      }
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      SurveyMonkeyDataSource that = (SurveyMonkeyDataSource) o;
      return rowLimit == that.rowLimit;
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), rowLimit);
   }

   private int rowLimit = 0;
}
