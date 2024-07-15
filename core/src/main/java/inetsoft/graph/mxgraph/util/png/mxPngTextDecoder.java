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
package inetsoft.graph.mxgraph.util.png;

import java.io.*;
import java.util.Hashtable;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Utility class to extract the compression text portion of a PNG
 */
public class mxPngTextDecoder {
   /**
    *
    */
   public static final int PNG_CHUNK_ZTXT = 2052348020;
   /**
    *
    */
   public static final int PNG_CHUNK_IEND = 1229278788;
   private static final Logger log = Logger.getLogger(mxPngTextDecoder.class.getName());

   /**
    * Decodes the zTXt chunk of the given PNG image stream.
    */
   public static Map<String, String> decodeCompressedText(InputStream stream)
   {
      Map<String, String> result = new Hashtable<String, String>();

      if(!stream.markSupported()) {
         stream = new BufferedInputStream(stream);
      }
      DataInputStream distream = new DataInputStream(stream);

      try {
         long magic = distream.readLong();
         if(magic != 0x89504e470d0a1a0aL) {
            throw new RuntimeException("PNGImageDecoder0");
         }
      }
      catch(Exception e) {
         throw new RuntimeException("PNGImageDecoder1", e);
      }

      do {
         try {
            int length = distream.readInt();
            int type = distream.readInt();
            byte[] data = new byte[length];
            distream.readFully(data);
            distream.readInt(); // Move past the crc

            if(type == PNG_CHUNK_IEND) {
               return result;
            }
            else if(type == PNG_CHUNK_ZTXT) {
               int currentIndex = 0;
               while((data[currentIndex++]) != 0) {
               }

               String key = new String(data, 0, currentIndex - 1);

               // LATER Add option to decode uncompressed text
               // NOTE Do not comment this line out as the
               // increment of the currentIndex is required
               byte compressType = data[currentIndex++];

               StringBuffer value = new StringBuffer();
               try {
                  InputStream is = new ByteArrayInputStream(data,
                                                            currentIndex, length);
                  InputStream iis = new InflaterInputStream(is,
                                                            new Inflater(true));

                  int c;
                  while((c = iis.read()) != -1) {
                     value.append((char) c);
                  }

                  result.put(key, String.valueOf(value));
               }
               catch(Exception e) {
                  log.log(Level.SEVERE, "Failed to decode PNG text", e);
               }
            }
         }
         catch(Exception e) {
            log.log(Level.SEVERE, "Failed to decode PNG text", e);
            return null;
         }
      }
      while(true);
   }
}
