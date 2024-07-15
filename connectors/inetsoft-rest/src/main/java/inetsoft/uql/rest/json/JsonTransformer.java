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
package inetsoft.uql.rest.json;

import com.jayway.jsonpath.*;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.rest.InputTransformer;
import inetsoft.util.CoreTool;
import inetsoft.util.FileSystemService;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.zip.*;

/**
 * Class which uses JsonPath to transform JSON sources into java objects.
 */
public class JsonTransformer implements InputTransformer {
   public JsonTransformer() {
      this.parseContext = createJsonPath();
   }

   public static JsonProvider getJsonProvider() {
      return jsonProvider;
   }

   @Override
   public Object transform(InputStream input, String path) {
      try {
         return parseContext.parse(fixInput(input)).read(path);
      }
      catch(IllegalArgumentException e) {
         // Server response returned a null value
         return null;
      }
   }

   @Override
   public Object transform(Object obj, String path) {
      try {
         return parseContext.parse(obj).read(path);
      }
      catch(IllegalArgumentException e) {
         // Server response returned a null value
         return null;
      }
   }

   @Override
   public Object transform(InputStream input) {
      try {
         return parseContext.parse(fixInput(input)).json();
      }
      catch(IllegalArgumentException e) {
         // Server response returned a null value
         return null;
      }
   }

   @Override
   public String updateOutputString(String output, String path, Object value) {
      output = createParents(output, path);
      return parseContext.parse(output).set(path, value).jsonString();
   }

   private ParseContext createJsonPath() {
      final Configuration conf = Configuration.builder()
         .options(Option.DEFAULT_PATH_LEAF_TO_NULL)
         .jsonProvider(jsonProvider)
         .build();

      return JsonPath.using(conf);
   }

   private InputStream fixInput(InputStream input) {
      try {
         if("true".equals(SreeEnv.getProperty("debug.json"))) {
            File outfile = File.createTempFile("debug", ".json");
            FileOutputStream out = new FileOutputStream(outfile);
            CoreTool.copyTo(input, out);
            out.close();
            input = new FileInputStream(outfile);
            System.err.println("Json saved in: " + outfile);
         }

         input = ignoreInvalidInputPrefix(input);
         return fixJiveInput(input);
      }
      catch(IOException e) {
         throw new RuntimeException("Failed to check for invalid JSON", e);
      }
   }

   private InputStream ignoreInvalidInputPrefix(InputStream input) throws IOException {
      BufferedInputStream buffered = new BufferedInputStream(input, 100);
      buffered.mark(100);
      int readByte = buffered.read();
      long skipCount = 0;

      while(readByte > 128) {
         skipCount ++;
         readByte = buffered.read();
      }

      buffered.reset();
      buffered.skip(skipCount);

      return buffered;
   }

   private InputStream fixJiveInput(InputStream input) throws IOException {
      BufferedInputStream buffered = new BufferedInputStream(input, 100);

      buffered.mark(100);
      final boolean zip = isPrefix(buffered, new byte[] {0x50, 0x4b, 0x03, 0x04});
      buffered.reset();

      if(zip) {
         File outfile = File.createTempFile("json", ".zip");
         FileOutputStream out = new FileOutputStream(outfile);
         CoreTool.copyTo(buffered, out);
         out.close();
         ZipFile zfile = new ZipFile(outfile);
         ZipEntry entry = zfile.stream().findFirst().orElse(null);
         FileSystemService.getInstance().remove(outfile, 60000);

         if(entry != null) {
            return zfile.getInputStream(entry);
         }
      }

      byte[] jivePrefix = "throw 'allowIllegalResourceCall is false.';\n"
         .getBytes(StandardCharsets.UTF_8);
      boolean fixJive = isPrefix(buffered, jivePrefix);

      if(!fixJive) {
         buffered.reset();
      }

      return buffered;
   }

   private static boolean isPrefix(BufferedInputStream buffered, byte[] prefix) throws IOException {
      boolean found = true;

      for(int i = 0; i < prefix.length; i++) {
         int n = buffered.read();

         if(n != prefix[i] || n < 0) {
            found = false;
            break;
         }
      }

      return found;
   }

   private String createParents(String json, String path) {
      int parentLength = path.lastIndexOf('.');

      if(parentLength == -1) {
         return json;
      }

      String parentPath = path.substring(0, parentLength);

      try {
         if(parseContext.parse(json).read(parentPath) == null) {
            throw new PathNotFoundException();
         }
      }
      catch(PathNotFoundException e) {
         json = createParents(json, parentPath);
         json = parseContext.parse(json).set(parentPath, new LinkedHashMap()).jsonString();
      }

      return json;
   }

   private final ParseContext parseContext;

   private static final JsonProvider jsonProvider = new JacksonJsonProvider();
}
