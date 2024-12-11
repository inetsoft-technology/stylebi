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
package inetsoft.uql.datagov;

import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import inetsoft.util.credential.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

/**
 * Data source used to connect to the data.gov web services.
 */
@SuppressWarnings("unused")
@View(vertical = true, value = {
   @View1("URL"),
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(type = ViewType.LABEL,
          text = "authentication.required.text",
          col = 1,
          paddingLeft = 3),
   @View1(value = "user", visibleMethod = "useCredential"),
   @View1(value = "password", visibleMethod = "useCredential")
})
public class DatagovDataSource extends TabularDataSource<DatagovDataSource> {
   public static final String TYPE = "Datagov";

   /**
    * Creates a new instance of <tt>DatagovDataSource</tt>.
    */
   public DatagovDataSource() {
      super(TYPE, DatagovDataSource.class);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.PASSWORD;
   }

   /**
    * Gets the base URL of the data.gov web services.
    *
    * @return the base URL.
    */
   @Property(label="URL")
   public String getURL() {
      return url;
   }

   /**
    * Sets the base URL of the data.gov web services.
    *
    * @param url the base URL.
    */
   public void setURL(String url) {
      this.url = url;
   }

   /**
    * Gets the name of the user used to authenticate with the web services.
    *
    * @return the user name or <tt>null</tt> if not required.
    */
   @Property(label="User")
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getUser() {
      return getCredential() == null ? null : ((PasswordCredential) getCredential()).getUser();
   }

   /**
    * Sets the name of the user used to authenticate with the web services.
    *
    * @param user the user name or <tt>null</tt> if not required.
    */
   public void setUser(String user) {
      if(getCredential() instanceof PasswordCredential) {
         ((PasswordCredential) getCredential()).setUser(user);
      }
   }

   /**
    * Gets the password used to authenticate with the web services.
    *
    * @return the password or <tt>null</tt> if not required.
    */
   @Property(label="Password", password=true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getPassword() {
      return getCredential() == null ? null : ((PasswordCredential) getCredential()).getPassword();
   }

   /**
    * Sets the password used to authenticate with the web services.
    *
    * @param password the password or <tt>null</tt> if not required.
    */
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
         writer.format("<url><![CDATA[%s]]></url>%n", url);
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      url = Tool.getChildValueByTagName(root, "url");
   }

   @Override
   public boolean equals(Object obj) {
      try {
         DatagovDataSource ds = (DatagovDataSource) obj;

         return Objects.equals(url, ds.url) && Objects.equals(getCredential(), ds.getCredential());
      }
      catch(Exception ex) {
         return false;
      }
   }

   private String url;

   private static final Logger LOG =
      LoggerFactory.getLogger(DatagovDataSource.class.getName());
}
