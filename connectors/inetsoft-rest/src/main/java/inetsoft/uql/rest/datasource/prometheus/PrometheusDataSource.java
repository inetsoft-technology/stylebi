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
package inetsoft.uql.rest.datasource.prometheus;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1("host"),
   @View1("port"),
   @View1("URL")
})
public class PrometheusDataSource extends EndpointJsonDataSource<PrometheusDataSource> {
   static final String TYPE = "Rest.Prometheus";
   
   public PrometheusDataSource() {
      super(TYPE, PrometheusDataSource.class);
      setAuthType(AuthType.NONE);
   }

   @Override
   protected CredentialType getCredentialType() {
      return null;
   }

   @Property(label = "Host", required = true)
   public String getHost() {
      return host;
   }

   public void setHost(String host) {
      this.host = host;
   }

   @Property(label = "Port", required = true)
   public int getPort() {
      return port;
   }

   public void setPort(int port) {
      this.port = port;
   }

   @Property(label = "URL")
   @PropertyEditor(enabled = false)
   @Override
   public String getURL() {
      StringBuilder url = new StringBuilder("http://");

      if(host == null) {
         url.append("[host]");
      }
      else {
         url.append(host);
      }

      url.append(":");

      if(port == 0) {
         url.append("[port]");
      }
      else {
         url.append(port);
      }

      return url.append("/api").toString();
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(host != null) {
         writer.format("<host><![CDATA[%s]]></host>%n", host);
      }

      writer.format("<port>%d</port>%n", port);
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      host = Tool.getChildValueByTagName(root, "host");
      String prop = Tool.getChildValueByTagName(root, "port");

      if(prop != null) {
         try {
            port = Integer.parseInt(prop);
         }
         catch(NumberFormatException e) {
            LOG.warn("Invalid port number: {}", prop, e);
         }
      }
   }

   @Override
   protected String getTestSuffix() {
      return "/v1/status/buildinfo";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof PrometheusDataSource)) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      PrometheusDataSource that = (PrometheusDataSource) o;
      return port == that.port &&
         Objects.equals(host, that.host);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), host, port);
   }

   private String host;
   private int port;
   private static final Logger LOG = LoggerFactory.getLogger(PrometheusDataSource.class);
}
