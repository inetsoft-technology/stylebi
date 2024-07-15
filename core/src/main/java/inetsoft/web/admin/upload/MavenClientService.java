/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.web.admin.upload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import groovy.grape.Grape;
import groovy.lang.GroovyClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;


@Component
public class MavenClientService {
   public List<String> search(String query) {
      try {
         StringBuilder urlText = new StringBuilder()
            .append("https://search.maven.org/solrsearch/select?q=");
         String[] terms = query.split(":");

         if(terms.length == 1) {
            urlText.append(URLEncoder.encode(terms[0], "UTF-8"));
         }
         else {
            urlText.append("g:")
               .append(URLEncoder.encode(terms[0], "UTF-8"))
               .append("%20AND%20a:")
               .append(URLEncoder.encode(terms[1], "UTF-8"));

            if(terms.length > 2) {
               urlText
                  .append("%20AND%20v:")
                  .append(URLEncoder.encode(terms[2], "UTF-8"));

               if(terms.length > 3) {
                  urlText
                     .append("%20AND%20l:")
                     .append(URLEncoder.encode(terms[3], "UTF-8"));
               }
            }
         }

         urlText.append("&rows=20");
         URL url = new URL(urlText.toString());

         ObjectNode root = (ObjectNode) new ObjectMapper().readTree(url);
         ArrayNode docs = (ArrayNode) root.get("response").get("docs");
         List<String> coords = new ArrayList<>();

         for(JsonNode doc : docs) {
            String id = doc.get("id").asText();
            String version = doc.get("latestVersion").asText();
            coords.add(id + ":" + version);
         }

         return coords;
      }
      catch(Exception e) {
         LOG.debug("Maven search failed for query \"%s\"", query, e);
      }

      return Collections.emptyList();
   }

   public List<UploadedFile> resolve(String gav) throws Exception {
      String[] coords = gav.split(":");
      Map<String, Object> args = new HashMap<>();
      args.put("group", coords[0]);
      args.put("module", coords[1]);
      args.put("version", coords[2]);
      args.put("classLoader", new GroovyClassLoader());

      return Arrays.stream(Grape.resolve(args, args))
         .map(File::new)
         .map(f -> UploadedFile.builder()
            .file(f)
            .fileName(f.getName())
            .build())
         .collect(Collectors.toList());
   }

   private static final Logger LOG = LoggerFactory.getLogger(MavenClientService.class);
}
