/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.uql.util.dtd;

import java.io.*;
import java.util.Iterator;
import java.util.Properties;

/**
 * DTDPreProcessor, pre-processes a dtd document for DTDParser.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class DTDPreProcessor {
   /**
    * Main entrance for testing.
    */
   // @by louis, pass the security scanning
   /*public static void main(String args[]) throws Exception {
      File file = new File(args[0]);
      InputStream in = new FileInputStream(file);
      BufferedReader reader = new BufferedReader(new InputStreamReader(in));
      DTDPreProcessor processor = new DTDPreProcessor();
      String line = null;

      while((line = reader.readLine()) != null) {
         System.err.println(processor.process(line));
      }

      in.close();
   }*/

   /**
    * Process the input stream reader.
    */
   public InputStream process(InputStream in) throws Exception {
      BufferedReader reader =
         new BufferedReader(new InputStreamReader(in, "utf-8"));
      String line = null;
      StringBuilder sb = new StringBuilder();

      while((line = reader.readLine()) != null) {
         line = process(line);

         if(sb.length() > 0) {
            sb.append('\n');
         }

         sb.append(line);
      }

      byte[] bytes = sb.toString().getBytes("utf-8");
      return new ByteArrayInputStream(bytes);
   }

   /**
    * Process line one by line.
    */
   public String process(String input) {
      if(input == null) {
         return input;
      }

      String[] pair = getParameterPair(input);

      if(pair != null) {
         pairs.setProperty("%" + pair[0] + ";", pair[1]);
      }

      return replace(input);
   }

   /**
    * Replace parameters.
    */
   private String replace(String input) {
      Iterator keys = pairs.keySet().iterator();

      while(keys.hasNext()) {
         String key = (String) keys.next();
         String val = pairs.getProperty(key);
         int index = -1;

         while((index = input.indexOf(key)) != -1) {
            input = input.substring(0, index) + val +
               input.substring(index + key.length());
         }
      }

      return input;
   }

   /**
    * Get the parameter pair.
    */
   private String[] getParameterPair(String input) {
      input = input.trim();
      int length = input.length();

      if(!input.startsWith(PARAMETER_PREFIX) ||
         input.charAt(input.length() - 1) != '>')
      {
         return null;
      }

      input = input.substring(PARAMETER_PREFIX.length(), length - 1).trim();
      int index = input.indexOf(' ');

      if(index <= 0) {
         return null;
      }

      String key = input.substring(0, index);
      String val = input.substring(index + 1);
      key = key.trim();
      val = val.trim();
      int klen = key.length();
      int vlen = val.length();

      if(klen == 0 || vlen <= 2 || val.charAt(0) != '"' ||
         val.charAt(vlen - 1) != '"')
      {
         return null;
      }

      return new String[] {key, val.substring(1, vlen - 1)};
   }

   private static final String PARAMETER_PREFIX = "<!ENTITY % ";
   private Properties pairs = new Properties();
}
