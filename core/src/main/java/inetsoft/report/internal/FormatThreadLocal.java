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
package inetsoft.report.internal;

import java.text.Format;
import java.util.Objects;

/**
 * Provides thread-local copies of a <tt>Format</tt>. The original
 * <tt>Format</tt> is cloned for each requesting thread. This prevents thread
 * safety issues with the use of a shared format.
 *
 * @author InetSoft Technology
 * @since  9.1
 */
public class FormatThreadLocal extends ThreadLocal<Format> {
   /**
    * Creates a new instance of FormatThreadLocal.
    *
    * @param format the format to be shared across threads.
    */
   public FormatThreadLocal(Format format) {
      this.format = format;
   }
   
   /**
    * Returns the current thread's initial value for this thread-local variable.
    * This method will be invoked at most once per accessing thread for each
    * thread-local, the first time the thread accesses the variable with the
    * <tt>get()</tt> method.
    * 
    * @return the initial value for this thread-local.
    */
   @Override
   protected Format initialValue() {
      return format == null ? null : (Format) format.clone();
   }
   
   public int hashCode() {
      return format == null ? 0 : format.hashCode();
   }

   public boolean equals(Object o) {
      if(!(o instanceof FormatThreadLocal)) {
         return false;
      }

      FormatThreadLocal other = (FormatThreadLocal) o;
      return Objects.equals(format, other.format);
   }

   private final Format format;
}
