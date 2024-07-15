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

import com.jayway.jsonpath.*;
import com.jayway.jsonpath.internal.JsonFormatter;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.tabular.*;
import inetsoft.uql.util.*;
import inetsoft.util.Tool;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.*;

public class ElasticRestRuntime extends TabularRuntime {
   public XTableNode runQuery(TabularQuery query0, VariableTable params) {
      ElasticRestQuery query = (ElasticRestQuery) query0;
      boolean expanded = query.isExpanded();
      InputStream input = null;

      try {
         URLConnection conn = getConnection(query);
         String jsonpath = query.getJsonPath();
         input = conn.getInputStream();

         if(jsonpath == null || jsonpath.isEmpty()) {
            jsonpath = "$";
         }

         Object rc = JsonPath.using(Configuration.builder()
                           .options(Option.DEFAULT_PATH_LEAF_TO_NULL)
                           .jsonProvider(jsonProvider)
                           .build())
            .parse(input)
            .read(jsonpath);
         final BaseJsonTable table;

         if(expanded) {
            table = new ExpandedJsonTable(query.getExpandedPath());
            ((ExpandedJsonTable) table).setAllowEmptyLists(true);
         }
         else {
            table = new JsonTable();
            table.setMaxRows(query.getMaxRows());
         }

         table.applyQueryColumnTypes(query);
         table.load(rc);

         LOG.debug("ElasticSearch Rest query JSON result: \n" +
                   JsonFormatter.prettyPrint(rc.toString()));

         return table;
      }
      catch(Exception ex) {
         LOG.warn("Error executing Elasticsearch Rest query: " + createURL(query), ex);
         Tool.addUserMessage("Error executing Elasticsearch Rest query: " +
              createURL(query) + "(" + ex.getMessage() + ")");
         handleError(params, ex, () -> null);
      }
      finally {
         IOUtils.closeQuietly(input);
      }

      return null;
   }

   public void testDataSource(TabularDataSource ds0,
                              VariableTable params) throws Exception
   {
      HttpURLConnection conn = null;

      try {
         ElasticRestDataSource restDS = (ElasticRestDataSource) ds0;
         URL url = new URL(restDS.getURL());
         conn = (HttpURLConnection) url.openConnection();

         String userpass = restDS.getUser() + ":" + restDS.getPassword();
         String basicAuth = "Basic " + Base64.encodeBase64String(userpass.getBytes());
         conn.setRequestProperty("Authorization", basicAuth);
         conn.getInputStream();
      }
      catch(Exception exc) {
         if(conn.getResponseCode() == 401) {
            throw exc;
         }
         else if(conn.getResponseCode() == 403) {
            conn.connect();
         }
      }
   }

   private URLConnection getConnection(ElasticRestQuery query) throws Exception {
      ElasticRestDataSource ds = (ElasticRestDataSource) query.getDataSource();
      String user = ds.getUser();
      String password = ds.getPassword();

      URL url = new URL(createURL(query));
      URLConnection conn = url.openConnection();

      if(user != null && password != null) {
         String credential = new String(
            Base64.encodeBase64((user + ":" + password).getBytes()));
         conn.setRequestProperty("Authorization", "Basic " + credential);
      }

      conn.setDoOutput(true);

      if(query.getFilter() != null) {
         conn.setRequestProperty("Content-Type", "application/json");
         conn.setRequestProperty("Accept", "application/json");

         OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
         wr.write(query.getFilter());
         wr.flush();
      }

      return conn;
   }

   private String createURL(ElasticRestQuery query) {
      ElasticRestDataSource ds = (ElasticRestDataSource) query.getDataSource();
      String url = ds.getURL();
      String suffix = query.getSuffix();

      if(suffix != null) {
          if(url.endsWith("/") && suffix.startsWith("/")) {
             url += suffix.substring(1);
          }
          else if(!url.endsWith("/") && !suffix.startsWith("/")) {
             url += "/" + suffix;
          }
          else {
             url += suffix;
          }
      }

      // only encode space (which is never allowed in url) but other characters
      // would need to be encoded explicitly to avoid confusion
      url = url.replaceAll(" ", "%20");
      return url;
   }

   private static final JsonProvider jsonProvider = new JacksonJsonProvider();
   private static final Logger LOG = LoggerFactory.getLogger(ElasticRestRuntime.class.getName());
}
