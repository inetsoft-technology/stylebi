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
package inetsoft.report.composition.execution;

import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssemblyEntry;
import inetsoft.uql.asset.TableAssembly;

import java.io.*;
import java.security.Principal;

/**
 * DataKey, the key generated from a data execution request. By using the key,
 * two equivalent requests could share same data.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class DataKey implements Serializable, Cloneable {
   /**
    * Create a instance of DataKey.
    *
    * @param table      the specified table assembly.
    * @param vars       the specified variable table.
    * @param user       the specified user.
    * @param mode       the specified mode.
    * @param formatted  <tt>true</tt> if formatted, <tt>false</tt> otherwise.
    * @param inputmax   maxrows on the detail table.
    * @param ifiltering true to ignoring filtering.
    */
   public static DataKey create(TableAssembly table, VariableTable vars,
                                Principal user, int mode, boolean formatted,
                                int inputmax, int previewMax, boolean ifiltering, long timeout)
      throws Exception
   {
      String val = createKey(table, vars, user, mode, formatted, inputmax, previewMax, ifiltering);

      if(val == null) {
         return null;
      }

      return new DataKey(val, timeout, table);
   }

   /**
    * Create key content.
    */
   private static String createKey(TableAssembly table, VariableTable vars,
                                   Principal user, int mode, boolean formatted,
                                   int inputmax, int previewMax, boolean ifiltering)
      throws Exception
   {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);

      if(!table.printKey(writer)) {
         return null;
      }

      if(vars != null) {
         if(user instanceof XPrincipal) {
            vars.copyParameters((XPrincipal) user);
         }

         writer.print(",");
         vars.printKey(writer);
      }

      if(user != null) {
         writer.print(",");
         writer.print(user);
      }

      writer.print(",");
      writer.print(mode);
      writer.print(",");
      writer.print(formatted);
      writer.print(",");
      writer.print(inputmax);
      writer.print(",");
      writer.print(previewMax);
      writer.print(",");
      writer.print(ifiltering);
      writer.flush();
      String val = buf.toString();
      int len = val.length();

      // compact too long string
      if(len >= 2000) {
         int delta = Math.max(1, len / 1500);
         StringBuilder sb = new StringBuilder(1510);
         sb.append(val.hashCode());
         char[] arr = val.toCharArray();

         for(int i = 0; i < len; i += delta) {
            sb.append(arr[i]);
         }

         val = sb.toString();
      }

      return val;
   }

   /**
    * Create a instance of DataKey.
    *
    * @param val the specified key value.
    */
   private DataKey(String val, long timeout, TableAssembly table) {
      this.val = val;
      this.timeout = timeout;
      this.entry = table.getAssemblyEntry();
   }

   /**
    * Get the String value.
    */
   public String getValue() {
      return val;
   }

   /**
    * Get the time out.
    */
   public long getTimeout() {
      return timeout;
   }

   public AssemblyEntry getAssemblyEntry() {
      return entry;
   }

   /**
    * Get the hash code value.
    */
   @Override
   public int hashCode() {
      if(hash == -1) {
         hash = val.hashCode();
      }

      return hash;
   }

   /**
    * Check if equals another object.
    */
   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof DataKey)) {
         return false;
      }

      DataKey key = (DataKey) obj;
      return val.equals(key.val);
   }

   /**
    * Get the string representation.
    */
   @Override
   public String toString() {
      return val == null ? "" : val;
   }

   private int hash = -1;

   private AssemblyEntry entry;
   private final String val;
   private final long timeout;
}
