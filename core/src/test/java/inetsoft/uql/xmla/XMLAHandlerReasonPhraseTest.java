/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.uql.xmla;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.reflect.Constructor;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82386: Mondrian on Tomcat 8.5+ answers "HTTP/1.1 200 " with no reason phrase (optional
 * per RFC 9112). XMLAHandler required the phrase "OK" and rejected every such response.
 * Uses a raw socket because the JDK HttpServer always writes a reason phrase.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  XMLAHandlerReasonPhraseTest.CredentialConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XMLAHandlerReasonPhraseTest {
   @Configuration
   static class CredentialConfig {
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }
   }

   private static final String BODY =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?><soap:Envelope " +
      "xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>" +
      "<DiscoverResponse xmlns=\"urn:schemas-microsoft-com:xml-analysis\"><return>" +
      "<root xmlns=\"urn:schemas-microsoft-com:xml-analysis:rowset\">" +
      "<row><CATALOG_NAME>Cat</CATALOG_NAME></row></root></return></DiscoverResponse>" +
      "</soap:Body></soap:Envelope>";

   private ServerSocket socket;
   private volatile String statusLine;
   private Thread thread;

   @BeforeEach
   void start() throws Exception {
      socket = new ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"));
      thread = new Thread(() -> {
         while(!socket.isClosed()) {
            try(Socket s = socket.accept()) {
               InputStream in = s.getInputStream();
               BufferedReader r = new BufferedReader(new InputStreamReader(in));
               int len = 0;
               String line;

               while((line = r.readLine()) != null && !line.isEmpty()) {
                  if(line.toLowerCase().startsWith("content-length:")) {
                     len = Integer.parseInt(line.substring(15).trim());
                  }
               }

               char[] buf = new char[len];
               int off = 0;

               while(off < len) {
                  int n = r.read(buf, off, len - off);

                  if(n < 0) {
                     break;
                  }

                  off += n;
               }

               byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
               String head = statusLine + "\r\nContent-Type: text/xml\r\nContent-Length: " +
                  (statusLine.startsWith("HTTP/1.1 200") ? body.length : 0) +
                  "\r\nConnection: close\r\n\r\n";
               OutputStream out = s.getOutputStream();
               out.write(head.getBytes(StandardCharsets.UTF_8));

               if(statusLine.startsWith("HTTP/1.1 200")) {
                  out.write(body);
               }

               out.flush();
            }
            catch(IOException ignore) {
            }
         }
      });
      thread.setDaemon(true);
      thread.start();
   }

   @AfterEach
   void stop() throws Exception {
      socket.close();
   }

   @Test
   void status200WithoutReasonPhraseIsAccepted() throws Exception {
      statusLine = "HTTP/1.1 200 ";
      assertDoesNotThrow(() -> new XMLAHandler().testDataSource(dataSource(), new VariableTable()));
   }

   @Test
   void status200WithReasonPhraseIsAccepted() throws Exception {
      statusLine = "HTTP/1.1 200 OK";
      assertDoesNotThrow(() -> new XMLAHandler().testDataSource(dataSource(), new VariableTable()));
   }

   @Test
   void nonOkStatusStillFailsAndMessageCarriesStatusCode() {
      statusLine = "HTTP/1.1 500 ";
      Exception e = assertThrows(Exception.class,
         () -> new XMLAHandler().testDataSource(dataSource(), new VariableTable()));
      assertTrue(e.getMessage().contains("500"), e.getMessage());
      assertFalse(e.getMessage().contains("null"), e.getMessage());
   }

   private XMLADataSource dataSource() {
      XMLADataSource ds = new XMLADataSource();
      ds.setURL("http://127.0.0.1:" + socket.getLocalPort() + "/");
      ds.setCatalogName("Cat");
      ds.setDatasourceInfo("Cat");
      return ds;
   }
}
