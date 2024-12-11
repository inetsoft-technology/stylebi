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
package inetsoft.uql.rest.datasource.firebase;

import inetsoft.uql.rest.json.OAuthEndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialType;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1("projectId"),
   @View1(
      type = ViewType.BUTTON,
      text = "Authorize",
      button = @Button(
         type = ButtonType.OAUTH,
         method = "updateTokens",
         oauth = @Button.OAuth(serviceName = FirebaseDataSource.SERVICE_NAME))),
   @View1("accessToken"),
   @View1("refreshToken"),
   @View1("tokenExpiration")
})
public class FirebaseDataSource extends OAuthEndpointJsonDataSource<FirebaseDataSource> {
   static final String TYPE = "Rest.Firebase";

   public FirebaseDataSource() {
      super(TYPE, FirebaseDataSource.class);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.AUTH_TOKENS;
   }

   @Override
   protected boolean supportCredentialId() {
      return false;
   }

   @Property(label = "Project ID", required = true)
   public String getProjectId() {
      return projectId;
   }

   public void setProjectId(String projectId) {
      this.projectId = projectId;
   }

   @Override
   protected String getTestSuffix() {
      return "/v1/projects/" + projectId + "/databases/(default)/operations";
   }

   @Override
   public String getURL() {
      return "https://firestore.googleapis.com";
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      refreshTokens();
      return new HttpParameter[]{
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name("Authorization")
            .value("Bearer " + getAccessToken())
            .build()
      };
   }

   @Override
   public void setQueryHttpParameters(HttpParameter[] parameters) {
      // no-op
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(projectId != null) {
         writer.format("<projectId><![CDATA[%s]]></projectId>%n", projectId);
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      projectId = Tool.getChildValueByTagName(root, "projectId");
   }

   @Override
   public String getServiceName() {
      return SERVICE_NAME;
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

      FirebaseDataSource that = (FirebaseDataSource) o;
      return Objects.equals(projectId, that.projectId);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), projectId);
   }

   public static final String SERVICE_NAME = "firebase";
   private String projectId;
}
