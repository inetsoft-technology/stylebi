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
package inetsoft.uql.elasticrest;

import inetsoft.uql.tabular.*;
import inetsoft.util.*;

import java.io.PrintWriter;
import java.util.Objects;
import java.util.logging.*;

import inetsoft.util.credential.*;
import org.w3c.dom.*;

@View(vertical=true, value={
      @View1("URL"),
      @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
      @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
      @View1(type=ViewType.LABEL, text="authentication.required.text", col=1, paddingLeft=3),
      @View1(value = "user", visibleMethod = "useCredential"),
      @View1(value = "password", visibleMethod = "useCredential")
   })
public class ElasticRestDataSource extends TabularDataSource<ElasticRestDataSource> {
   public static final String TYPE = "Elastic";

   public ElasticRestDataSource() {
      super(TYPE, ElasticRestDataSource.class);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.PASSWORD;
   }

   @Property(label="URL")
   public String getURL() {
      return url;
   }

   public void setURL(String url) {
      this.url = url;
   }

   @Property(label="User")
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getUser() {
      return getCredential() == null ? null : ((PasswordCredential) getCredential()).getUser();
   }

   public void setUser(String user) {
      if(getCredential() instanceof PasswordCredential) {
         ((PasswordCredential) getCredential()).setUser(user);
      }
   }

   @Property(label="Password", password=true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getPassword() {
      return getCredential() == null ? null : ((PasswordCredential) getCredential()).getPassword();
   }

   public void setPassword(String password) {
      if(getCredential() instanceof PasswordCredential) {
         ((PasswordCredential) getCredential()).setPassword(password);
      }
   }

   @Override
   public boolean isTypeConversionSupported() {
      return true;
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(url != null) {
         writer.println("<url><![CDATA[" + url + "]]></url>");
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      url = Tool.getChildValueByTagName(root, "url");
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      try {
         ElasticRestDataSource ds = (ElasticRestDataSource) obj;

         return Objects.equals(url, ds.url);
      }
      catch(Exception ex) {
         return false;
      }
   }

   private String url;

   private static final Logger LOG =
      Logger.getLogger(ElasticRestDataSource.class.getName());
}
