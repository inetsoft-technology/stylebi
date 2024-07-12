/*
 * inetsoft-elastic - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.elasticrest;

import inetsoft.uql.tabular.*;
import inetsoft.util.*;

import java.io.PrintWriter;
import java.util.Objects;
import java.util.logging.*;
import org.w3c.dom.*;

@View(vertical=true, value={
      @View1("URL"),
      @View1(type=ViewType.LABEL, text="authentication.required.text", col=1, paddingLeft=3),
      @View1("user"),
      @View1("password")
   })
public class ElasticRestDataSource extends TabularDataSource<ElasticRestDataSource> {
   public static final String TYPE = "Elastic";

   public ElasticRestDataSource() {
      super(TYPE, ElasticRestDataSource.class);
   }

   @Property(label="URL")
   public String getURL() {
      return url;
   }

   public void setURL(String url) {
      this.url = url;
   }

   @Property(label="User")
   public String getUser() {
      return user;
   }

   public void setUser(String user) {
      this.user = user;
   }

   @Property(label="Password", password=true)
   public String getPassword() {
      return password;
   }

   public void setPassword(String password) {
      this.password = password;
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

      if(user != null) {
         writer.println("<user><![CDATA[" + user + "]]></user>");
      }

      if(password != null) {
         writer.println("<password><![CDATA[" + Tool.encryptPassword(password) +
                        "]]></password>");
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      url = Tool.getChildValueByTagName(root, "url");
      user = Tool.getChildValueByTagName(root, "user");
      password = Tool.decryptPassword(Tool.getChildValueByTagName(root, "password"));
   }

   @Override
   public boolean equals(Object obj) {
      try {
         ElasticRestDataSource ds = (ElasticRestDataSource) obj;

         return Objects.equals(url, ds.url) &&
            Objects.equals(user, ds.user) &&
            Objects.equals(password, ds.password);
      }
      catch(Exception ex) {
         return false;
      }
   }

   private String url;
   private String user;
   private String password;

   private static final Logger LOG =
      Logger.getLogger(ElasticRestDataSource.class.getName());
}
