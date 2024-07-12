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
package inetsoft.mv.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Cache query result, BitSet. Data is written out to swap file if necessary.
 *
 * @version 12.1
 * @author InetSoft Technology Corp
 */
public class BitSetCache extends TwoLevelCache<String, BitSet> {
   public BitSetCache() {
      // 5 minutes cache
      super(200, 5 * 60000);
   }
   
   /**
    * Write the object to output.
    */
   @Override
   protected void writeObject(OutputStream output, BitSet obj) {
      try {
         obj.serialize(output);
      }
      catch(Exception ex) {
         LOG.error("Failed to serialize object", ex);
      }
   }

   /**
    * Read the object from input.
    */
   @Override
   protected BitSet readObject(InputStream input) {
      BitSet bitset = new BitSet();

      try {
         bitset.deserialize(input);
      }
      catch(Exception ex) {
         LOG.error("Failed to deserialize object", ex);
      }

      return bitset;
   }

   private static final Logger LOG = LoggerFactory.getLogger(BitSetCache.class);
}
