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
package inetsoft.sree.internal;

import inetsoft.util.GroupedThread;
import inetsoft.util.Tool;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLHandshakeException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Status thread class.
 */
public class StatusThread extends GroupedThread {
   public StatusThread(String servletUrl) {
      super();
      this.servletUrl = servletUrl;
   }

   public int getResponseCode() {
      try {
         this.start();
         this.join(JOIN_INTERVAL);

         if(this.isAlive()) {
            this.interrupt();
         }
      }
      catch(Exception e) {
         LOG.error("Exception occurred while waiting for status to be available", e);
      }

      return result;
   }

   public String getResponse() {
      return response;
   }

   @Override
   protected void doRun() {
      String uri = servletUrl + (servletUrl.endsWith("/") ? "ping" : "/ping");
      run0(uri);
   }

   private void run0(String uri) {
      try {
         URL url = new URL(uri); // check status
         HttpURLConnection conn = (HttpURLConnection) url.openConnection();
         conn.setRequestMethod("GET");
         conn.connect();
         result = conn.getResponseCode();

         if(result == HttpURLConnection.HTTP_MOVED_PERM ||
            result == HttpURLConnection.HTTP_MOVED_TEMP)
         {
            run0(conn.getHeaderField("Location"));
         }
         else if(result == HttpURLConnection.HTTP_UNAUTHORIZED) {
            response = "ok";
            result = HttpURLConnection.HTTP_OK;
         }
         else {
            try(InputStream input = conn.getInputStream()) {
               response = String.join("\n", IOUtils.readLines(input));
            }
         }
      }
      catch(SSLHandshakeException e) {
         if(Tool.isLogValid(servletUrl)) {
            LOG.error("Error pinging server " + servletUrl, e);
         }

         response = "ssl";
      }
      catch(Exception e) {
         if(Tool.isLogValid(servletUrl)) {
            LOG.error("Error pinging server " + servletUrl, e);
         }
      }
   }

   private int result = -1;
   private static final long JOIN_INTERVAL = 10000;
   private String servletUrl;
   private String response = "";

   private static final Logger LOG = LoggerFactory.getLogger(StatusThread.class);
}
