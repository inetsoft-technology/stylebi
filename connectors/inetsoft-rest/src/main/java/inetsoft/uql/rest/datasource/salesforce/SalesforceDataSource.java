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
package inetsoft.uql.rest.datasource.salesforce;

import com.github.benmanes.caffeine.cache.*;
import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import inetsoft.util.credential.*;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.xpath.*;
import java.io.*;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public abstract class SalesforceDataSource<SELF extends SalesforceDataSource<SELF>>
   extends EndpointJsonDataSource<SELF>
{
   protected SalesforceDataSource(String type, Class<SELF> selfClass) {
      super(type, selfClass);
      setAuthType(AuthType.NONE);
   }

   @Property(label = "Security Token", required = true, password = true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getSecurityToken() {
      return ((SecurityTokenCredential) getCredential()).getSecurityToken();
   }

   public void setSecurityToken(String securityToken) {
      ((SecurityTokenCredential) getCredential()).setSecurityToken(securityToken);
   }

   @Override
   public String getURL() {
      return getSession().getUrl() + getUrlSuffix();
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      String sessionId = getSession().getSessionId();
      return new HttpParameter[] {
         HttpParameter.builder()
            .type(HttpParameter.ParameterType.HEADER)
            .name("Authorization")
            .value("Bearer " + sessionId)
            .build()
      };
   }

   @Override
   public void setQueryHttpParameters(HttpParameter[] parameters) {
      // no-op
   }

   protected abstract String getUrlSuffix();

   protected String getLoginUrl() {
      return "https://login.salesforce.com/services/Soap/u/35.0";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      return super.equals(o);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), getSecurityToken());
   }

   private SalesforceSession getSession() {
      try {
         return SessionCache.INSTANCE.cache.get(this);
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to log into Salesforce.com", e);
      }
   }

   private static SalesforceSession createSession(SalesforceDataSource dataSource) {
      byte[] content;

      try {
         ByteArrayOutputStream buffer = new ByteArrayOutputStream();
         XMLStreamWriter writer =
            XMLOutputFactory.newInstance().createXMLStreamWriter(buffer, "UTF-8");
         writer.writeStartDocument("UTF-8", "1.0");
         writer.writeStartElement("soapenv", "Envelope", "http://schemas.xmlsoap.org/soap/envelope/");
         writer.writeDefaultNamespace("urn:partner.soap.sforce.com");
         writer.writeNamespace("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
         writer.writeStartElement("soapenv", "Header", "http://schemas.xmlsoap.org/soap/envelope/");
         writer.writeEndElement(); // Header
         writer.writeStartElement("soapenv", "Body", "http://schemas.xmlsoap.org/soap/envelope/");
         writer.writeStartElement("", "login", "urn:partner.soap.sforce.com");
         writer.writeStartElement("", "username", "urn:partner.soap.sforce.com");
         writer.writeCharacters(dataSource.getUser());
         writer.writeEndElement(); // username
         writer.writeStartElement("", "password", "urn:partner.soap.sforce.com");
         writer.writeCharacters(dataSource.getPassword() + dataSource.getSecurityToken());
         writer.writeEndElement(); // password
         writer.writeEndElement(); // login
         writer.writeEndElement(); // Body
         writer.writeEndElement(); // Envelope
         writer.writeEndDocument();
         writer.close();
         content = buffer.toByteArray();
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to write login request", e);
      }

      Document document;

      try(CloseableHttpClient client = HttpClients.createDefault()) {
         HttpPost post = new HttpPost(dataSource.getLoginUrl());
         ByteArrayEntity entity = new ByteArrayEntity(content, ContentType.TEXT_XML);
         post.setEntity(entity);
         post.addHeader("SOAPAction", "urn:partner.soap.sforce.com#login");

         try(CloseableHttpResponse response = client.execute(post)) {
            if(response.getStatusLine().getStatusCode() < 200 || response.getStatusLine().getStatusCode() >= 300) {
               throw new Exception(
                  "Login failed [" + response.getStatusLine() + "]: " +
                     response.getStatusLine().getStatusCode());
            }

            try(InputStream input = response.getEntity().getContent()) {
               document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input);
            }
         }
      }
      catch(Exception e) {
         throw new RuntimeException("Login to Salesforce.com failed", e);
      }

      try {
         XPath xpath = XPathFactory.newInstance().newXPath();
         String serverUrl = (String) xpath.evaluate(
            "//*[local-name()='serverUrl']/text()", document, XPathConstants.STRING);
         String sessionId = (String) xpath.evaluate(
            "//*[local-name()='sessionId']/text()", document, XPathConstants.STRING);

         int index = serverUrl.indexOf("/services/");

         if(index < 0) {
            serverUrl = URI.create(serverUrl).resolve("/services").toString();
         }
         else {
            serverUrl = serverUrl.substring(0, index + 9);
         }

         return new SalesforceSession(serverUrl, sessionId);
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to parse login response", e);
      }
   }

   private static final class SalesforceSession {
      SalesforceSession(String url, String sessionId) {
         this.url = url;
         this.sessionId = sessionId;
      }

      public String getUrl() {
         return url;
      }

      public String getSessionId() {
         return sessionId;
      }

      private final String url;
      private final String sessionId;
   }

   private enum SessionCache {
      INSTANCE;

      private final LoadingCache<SalesforceDataSource, SalesforceSession> cache =
         Caffeine.newBuilder()
         .expireAfterAccess(2L, TimeUnit.HOURS)
         .maximumSize(20)
         .build(SalesforceDataSource::createSession);
   }
}
