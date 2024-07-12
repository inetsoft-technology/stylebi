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
package inetsoft.report.gui.viewsheet;

import inetsoft.util.Tool;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * A simple parser for extracting embedded image information. This is a cruel
 * parser that assumes the file is structured as:
 * id {
 *   name:value;
 * }
 *
 * @version 9.5, 2/4/2008
 * @author InetSoft Technology Corp
 */
public class FlexCSSParser {
   /**
    * Parse a flex css file.
    */
   public void parse(InputStream input) throws Exception {
      BufferedReader reader = new BufferedReader(new InputStreamReader(input));
      String line;
      String id = null;
      String block = "";
      boolean inComment = false;

      while((line = reader.readLine()) != null) {
         line = line.trim();

         if(line.length() == 0) {
            continue;
         }

         if(line.startsWith("@namespace")) {
            continue;
         }

	      if(line.startsWith("/*")) {
            inComment = true;
         }

         if(inComment) {
            if(line.indexOf("*/") >= 0) {
               inComment = false;
            }
         }
         // looking for: id {
         else if(id == null) {
            if(!line.endsWith("{")) {
	            throw new RuntimeException("Unsupported format: " + line);
            }

            id = line.substring(0, line.length() - 1).trim();
         }
         // end of block
         else if(line.equals("}")) {
            addStyle(id, parseBlock(block));
            id = null;
            block = "";
         }
         // properties
         else {
            block += line;
         }
      }
   }

   /**
    * Add (or merge) a style block.
    */
   private void addStyle(String id, HashMap styles) {
      HashMap map = (HashMap) stylemap.get(id);

      if(map == null) {
         stylemap.put(id, map = new HashMap());
      }

      map.putAll(styles);
   }

   /**
    * Get the value of the style.
    */
   public Object getValue(String name, String prop) {
      Map map = (Map) stylemap.get(name);

      if(map == null) {
         return null;
      }

      return map.get(prop);
   }

   /**
    * Get the value of the style.
    * @param name the name of the style, e.g. global.
    * @param prop the style property name, e.g loadingLarge.
    * @param attr the embed tag attribute name, e.g source
    */
   public Object getValue(String name, String prop, String attr) {
      Map map = (Map) stylemap.get(name);

      if(map == null) {
         return null;
      }

      map = (Map) map.get(prop);

      if(map == null) {
         return null;
      }

      return map.get(attr);
   }

   /**
    * A block is a semi-colon separated list of name-value pairs.
    */
   private HashMap parseBlock(String block) throws Exception {
      String[] items = Tool.split(block, ';');
      HashMap map = new HashMap();

      for(int i = 0; i < items.length; i++) {
         int colon = items[i].indexOf(":");

         if("".equals(items[i])) {
            continue;
         }

         if(colon < 0) {
            throw new RuntimeException("Unsupported format: " + items[i]);
         }

         String name = items[i].substring(0, colon).trim();
         String value = items[i].substring(colon + 1).trim();

         if(value.startsWith("Embed(")) {
            map.put(name, parseEmbed(value));
         }
         else {
            map.put(name, value);
         }
      }

      return map;
   }

   /**
    * Parse e.g. Embed(source="abc.gif", scaleGridTop=3).
    */
   private Map parseEmbed(String value) {
      if(!value.startsWith("Embed(") || !value.endsWith(")")) {
         throw new RuntimeException("Invalid Embed tag: " + value);
      }

      value = value.substring(6, value.length() - 1);

      String[] attrs = Tool.split(value, ',');
      Map embedmap = new HashMap();

      for(int i = 0; i < attrs.length; i++) {
         String[] pair = Tool.split(attrs[i].trim(), '=');

         if(pair.length != 2) {
            throw new RuntimeException("Invalid attribute: " + attrs[i]);
         }

         embedmap.put(pair[0].trim(), trimQuote(pair[1]));
      }

      return embedmap;
   }

   /**
    * Trim surrounding quotes.
    */
   private String trimQuote(String str) {
      str = str.trim();

      if(str.startsWith("\"") && str.endsWith("\"")) {
         str = str.substring(1, str.length() - 1);
      }

      return str;
   }

   private HashMap stylemap = new HashMap(); // id -> styles (Map)
}

