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
package inetsoft.util;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.CollationKey;
import java.text.Collator;
import java.util.HashMap;
import java.util.Map;

/*
 * Collator_CN, basing on file, it can compare polyphone words properly. For a
 * polyphone word, for proper comparison, user may replace the word with another
 * word which is not a polyphone word, but has the same prononcation with the
 * polyphone word.
 * <p>
 * The file, in utf-8 format, stores the key value pairs.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class Collator_CN extends Collator {
   /**
    * Initialize this collator.
    */
   private static void init() {
      if(!inited) {
         synchronized(Collator_CN.class) {
            if(!inited) {
               init0();
            }
         }
      }
   }

   /**
    * Initialize this collator internally.
    */
   private static void init0() {
      InputStream input =
         Collator_CN.class.getResourceAsStream("/inetsoft/util/collator_map.properties");

      if(input != null) {
         try {
            BufferedReader reader =
               new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            map = new HashMap<>();
            String line;

            while((line = reader.readLine()) != null) {
               line = line.trim();
               int index = line.indexOf('=');

               if(index >= 0) {
                  map.put(line.substring(0, index), line.substring(index + 1));
               }
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to read collator map file", ex);
         }
         finally {
            inited = true;
            IOUtils.closeQuietly(input);
         }
      }
   }

   /**
    * Get the collator.
    */
   public static Collator getCollator() {
      init();

      return map == null ? Collator.getInstance() :
         new Collator_CN();
   }

   /**
    * Constructor.
    */
   private Collator_CN() {
      this.base = Collator.getInstance();
   }

   /**
    * Get the collation key.
    */
   @Override
   public CollationKey getCollationKey(String source) {
      return base.getCollationKey(source);
   }

   /**
    * Compare two strings.
    */
   @Override
   public int compare(String source, String target) {
      String val = map == null ? null : map.get(source);
      source = val == null ? source : val;
      val = map == null ? null : map.get(target);
      target = val == null ? target : val;
      return base.compare(source, target);
   }

   /**
    * Get the hash code.
    */
   public int hashCode() {
      return base.hashCode();
   }

   private static final Logger LOG = LoggerFactory.getLogger(Collator_CN.class);
   private static Map<String, String> map;
   private static volatile boolean inited;
   private final Collator base;
}
