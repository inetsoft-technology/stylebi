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
package inetsoft.uql.rest.datasource;

import inetsoft.test.EndpointsSpringContext;
import inetsoft.test.SreeHome;
import inetsoft.uql.rest.json.EndpointJsonQuery;
import inetsoft.uql.tabular.RestParameter;
import inetsoft.uql.tabular.RestParameters;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Saves and loads a query of every endpoint of every data source: the endpoint, its parameter
 * values, the JSON path and the request body of a POST endpoint must come back.
 */
@EndpointsSpringContext
@SreeHome
@Tag("core")
class QueryRoundTripAllEndpointsTest {
   private static final String BODY = "{\"a\":\"b&c<d\"}";

   private static Map<String, String> values(EndpointJsonQuery<?> q) {
      Map<String, String> map = new TreeMap<>();

      for(RestParameter p : q.getParameters().getParameters()) {
         map.put(p.getName(), p.getValue());
      }

      return map;
   }

   @Test
   void everyEndpointQueryKeepsItsSettingsThroughSaveAndLoad() throws Exception {
      List<String> problems = new ArrayList<>();
      int checked = 0;

      for(Class<?> type : EndpointDefinitionsTest.queryTypes()) {
         EndpointJsonQuery<?> probe = EndpointDefinitionsTest.newQuery(type);

         for(String name : new ArrayList<>(probe.getEndpointMap().keySet())) {
            String where = type.getSimpleName() + "/" + name;

            try {
               EndpointJsonQuery<?> q = EndpointDefinitionsTest.newQuery(type);
               q.setEndpoint(name);
               q.setJsonPath("$.items");
               RestParameters parameters = q.getParameters();

               for(RestParameter p : parameters.getParameters()) {
                  p.setValue("v&" + p.getName().hashCode());
               }

               q.setParameters(parameters);

               if(q.isPostRequest()) {
                  q.setRequestBody(BODY);
               }

               StringWriter text = new StringWriter();
               PrintWriter writer = new PrintWriter(text);
               q.writeXML(writer);
               writer.flush();
               Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                  .parse(new ByteArrayInputStream(text.toString().getBytes(StandardCharsets.UTF_8)))
                  .getDocumentElement();
               EndpointJsonQuery<?> copy = EndpointDefinitionsTest.newQuery(type);
               copy.parseXML(root);
               checked++;

               if(!Objects.equals(name, copy.getEndpoint())) {
                  problems.add(where + ": endpoint came back as " + copy.getEndpoint());
               }

               if(!Objects.equals("$.items", copy.getJsonPath())) {
                  problems.add(where + ": json path came back as " + copy.getJsonPath());
               }

               if(!values(q).equals(values(copy))) {
                  problems.add(where + ": parameters " + values(q) + " came back as " +
                               values(copy));
               }

               if(q.isPostRequest() && !BODY.equals(copy.getRequestBody())) {
                  problems.add(where + ": request body came back as " + copy.getRequestBody());
               }
            }
            catch(Throwable e) {
               String message = String.valueOf(e);
               problems.add(where + ": " + message.substring(0, Math.min(160, message.length())));
            }
         }
      }

      assertTrue(checked > 1000, "only " + checked + " endpoints were checked");
      assertTrue(problems.isEmpty(), problems.size() + " problem(s):\n" +
                 String.join("\n", problems.subList(0, Math.min(60, problems.size()))));
   }
}
