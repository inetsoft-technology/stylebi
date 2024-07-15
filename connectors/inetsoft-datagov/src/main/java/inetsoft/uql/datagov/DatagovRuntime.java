/*
 * inetsoft-datagov - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.datagov;

import inetsoft.uql.*;

import inetsoft.uql.tabular.*;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.*;
import java.net.*;
import java.io.*;
import java.util.Base64;

/**
 * Runtime implementation for the data.gov data source.
 */
@SuppressWarnings("unused")
public class DatagovRuntime extends TabularRuntime {
   @Override
   public XTableNode runQuery(TabularQuery query0, VariableTable params) {
      DatagovQuery query = (DatagovQuery) query0;
      JsonReader jsonReader = null;
      XTableNode table = null;

      try {
         URLConnection conn = getConnection(query);
         BufferedReader connReader = new BufferedReader(
            new InputStreamReader(conn.getInputStream(), "UTF-8"));

         // TODO: Might need to switch this to a streaming based implementation.
         jsonReader = Json.createReader(connReader);
         JsonObject jObj = jsonReader.readObject();
         table = new DatagovTable(jObj, query0.getMaxRows(), query);
      }
      catch(Exception ex) {
         LOG.warn("Error executing data.gov query: " +
            createURL(query), ex);
         handleError(params, ex, () -> null);
      }
      finally {
         IOUtils.closeQuietly(jsonReader);
      }

      return table;
   }

   @Override
   public void testDataSource(TabularDataSource ds0,
                              VariableTable params) throws Exception
   {
      URL url = new URL(((DatagovDataSource) ds0).getURL());
      url.openConnection();
   }

   /**
    * Opens a connection to the web service specified by a query.
    *
    * @param query the query definition.
    *
    * @return the URL connection.
    *
    * @throws Exception if the connection could not be established.
    */
   private URLConnection getConnection(DatagovQuery query) throws Exception {
      DatagovDataSource ds = (DatagovDataSource) query.getDataSource();
      String user = ds.getUser();
      String password = ds.getPassword();

      URL url = new URL(createURL(query));
      URLConnection conn = url.openConnection();

      if(user != null && password != null) {
         String credential =
            Base64.getEncoder().encodeToString((user + ":" + password).getBytes());
         conn.setRequestProperty("Authorization", "Basic " + credential);
      }

      conn.setDoOutput(true);
      return conn;
   }

   /**
    * Creates the URL used to connect to the web service specified by a query.
    *
    * @param query the query definition.
    *
    * @return the URL.
    */
   private String createURL(DatagovQuery query) {
      DatagovDataSource ds = (DatagovDataSource) query.getDataSource();
      URI uri = URI.create(ds.getURL().trim());
      String suffix = query.getSuffix();

      if(suffix != null) {
         uri = uri.resolve(suffix.trim());
      }

      return uri.toString();
   }

   private static final Logger LOG = LoggerFactory.getLogger(DatagovRuntime.class.getName());
}
