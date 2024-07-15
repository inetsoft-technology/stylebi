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
package inetsoft.report.pdf;

import inetsoft.sree.SreeEnv;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * CMap of a CID font.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class CMap {
   /**
    * Create a cmap object for an unicode cmap.
    */
   public CMap(String name) throws IOException {
      this.name = name;

      try(InputStream ins = getCMapData(name)) {
        if(ins == null) {
           return;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(ins));
        String line;
        boolean inrange = false;
        Vector rv = new Vector();

        while((line = reader.readLine()) != null) {
           String[] fields = Tool.split(line, " <>\t\n", true);

           if(!inrange && fields.length == 2 &&
              fields[1].equals("begincidrange")) {
              inrange = true;
           }
           else if(inrange && fields.length == 1 &&
              fields[0].equals("endcidrange")) {
              inrange = false;
           }
           else if(inrange && fields.length == 4) {
              Range range = new Range(Integer.parseInt(fields[1], 16),
                 Integer.parseInt(fields[2], 16), Integer.parseInt(fields[3]));
              // keep the array sorted
              int idx = rv.size();

              for(; idx > 0; idx--) {
                 if(range.compare((Range) rv.elementAt(idx - 1)) >= 0) {
                    break;
                 }
              }

              rv.insertElementAt(range, idx);
           }
        }

        ranges = new Range[rv.size()];
        rv.copyInto(ranges);
     }
   }

   /**
    * Map a character to glyph index.
    */
   public int map(char ch) {
      return map(ch, 0, ranges.length - 1);
   }

   /**
    * Return the cmap data as a stream.
    */
   public static InputStream getCMapData(String name) {
      String path = SreeEnv.getProperty("font.cmap.path");
      String cmapdir = ""; // suffix to add to end of path

      if(path == null) {
         path = SreeEnv.getProperty("font.truetype.path");
         cmapdir = "/../CMap";
      }

      if(path != null) {
         try {
            StringTokenizer tok = new StringTokenizer(path, ";");

            while(tok.hasMoreTokens()) {
               String dir = tok.nextToken();
               File file = FileSystemService.getInstance().getFile(dir + cmapdir, name);

               if(file.exists()) {
                  return new FileInputStream(file);
               }
            }

            DataSpace space = DataSpace.getDataSpace();

            while(tok.hasMoreTokens()) {
               String dir = tok.nextToken();
               InputStream in = space.getInputStream(dir + cmapdir, name);

               if(in != null) {
                  return in;
               }
            }
         }
         catch(Exception ex) {
            LOG.warn("Failed to open CMap data stream: " + name, ex);
         }
      }

      return CMap.class.getResourceAsStream("/" + name);
   }

   /**
    * Find a range.
    */
   private int map(char ch, int lo, int hi) {
      int mid = (lo + hi) / 2;
      int rc = ranges[mid].compare(ch);

      if(rc == 0) {
         return ranges[mid].map(ch);
      }

      if(lo == mid) {
         lo++;
      }

      if(hi == mid) {
         hi--;
      }

      if(hi < lo) {
         return -1;
      }
      else if(hi == lo) {
         return ranges[hi].map(ch);
      }

      if(rc > 0) {
         hi = mid;
      }
      else {
         lo = mid;
      }

      return map(ch, lo, hi);
   }

   /**
    * Get the maximum value in the character ranges.
    */
   public int getMax() {
      return max;
   }

   /**
    * Character range to glyph mapping.
    */
   class Range {
      public Range(int lo, int hi, int base) {
         this.lo = lo;
         this.hi = hi;
         this.base = base;
         max = Math.max(max, hi);
      }

      public boolean contains(int val) {
         return val >= lo && val <= hi;
      }

      public int map(int val) {
         return contains(val) ? base + val - lo : -1;
      }

      public int compare(Range range) {
         return lo - range.lo;
      }

      public int compare(int val) {
         return (val < lo) ? 1 : ((val > hi) ? -1 : 0);
      }

      private int lo, hi, base;
   }

   String name;
   Range[] ranges = null;
   int max;

   private static final Logger LOG =
      LoggerFactory.getLogger(CMap.class);
}

