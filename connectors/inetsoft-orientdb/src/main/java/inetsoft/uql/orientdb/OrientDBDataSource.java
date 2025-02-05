/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.uql.orientdb;

import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import inetsoft.util.credential.*;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

/**
 * DataSource that connects to a OrientDB server
 */

@View(vertical=true, value={
   @View1("url"),
   @View1(type=ViewType.LABEL, text="authentication.required.text", col=1, paddingLeft=3),
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "user", visibleMethod = "useCredential"),
   @View1(value = "password", visibleMethod = "useCredential")
})
public class OrientDBDataSource extends TabularDataSource<OrientDBDataSource> {
   public static final String TYPE = "OrientDB";

   /**
    * Constructor
    */
   public OrientDBDataSource() {
      super(TYPE, OrientDBDataSource.class);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.PASSWORD;
   }

   /**
    * get the JDBC Url
    *
    * @return the url
    */
   @Property(label="URL", required=true)
   public String getUrl() {
      return url;
   }

   /**
    * set the JDBC Url address
    *
    * @param url the url address
    */
   public void setUrl(String url) {
      this.url = url;
   }

   /**
    * get user name
    *
    * @return user name
    */
   @Property(label="User")
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getUser() {
      return ((PasswordCredential) getCredential()).getUser();
   }

   /**
    * set user name
    *
    * @param user user name
    */
   public void setUser(String user) {
      ((PasswordCredential) getCredential()).setUser(user);
   }

   /**
    * get password
    *
    * @return password
    */
   @Property(label="Password", password=true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getPassword() {
      return ((PasswordCredential) getCredential()).getPassword();
   }

   /**
    * set password
    * @param password password
    */
   public void setPassword(String password) {
      ((PasswordCredential) getCredential()).setPassword(password);
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
         OrientDBDataSource ds = (OrientDBDataSource) obj;

         return Objects.equals(url, ds.url);
      }
      catch(Exception ex) {
         return false;
      }
   }

   @Override
   public Object clone() {
      OrientDBDataSource source = (OrientDBDataSource) super.clone();

      try {
         source.setCredential((Credential) getCredential().clone());
      }
      catch(CloneNotSupportedException ignore) {
      }

      return source;
   }

   private String url = "jdbc:orient:remote:localhost/<databaseName>";
}
