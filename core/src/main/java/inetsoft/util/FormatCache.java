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
package inetsoft.util;

import java.io.Serializable;
import java.text.Format;
import java.text.ParseException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Format is not thread-safe, but to add a lock when format an object is
 * expensive, especially when there are many queued threads. Here we will
 * create 128 copies of the specified format to support at least 128 threads
 * concurrently. If the concurrent thread count is greater than 128,
 * we may consider 256, 512, etc.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public final class FormatCache implements Serializable, Cloneable {
   /**
    * Create a format cache with the specified size.
    */
   public FormatCache(Format fmt) {
      super();
      this.fmts = new Format[N + 1];

      for(int i = 0; i < fmts.length; i++) {
         fmts[i] = (Format) fmt.clone();
      }
   }

   /**
    * Format an object to get the text.
    */
   public String format(Object obj) {
      int i = idx.getAndIncrement();
      i &= N; // '&' is faster than '%', that's why we use 127, 255, etc.

      synchronized(fmts[i]) {
         return fmts[i].format(obj);
      }
   }

   /**
    * Parse a text to get the object.
    */
   public Object parse(String text) throws ParseException {
      int i = idx.getAndIncrement();
      i &= N; // '&' is faster than '%', that's why we use 127, 255, etc.

      synchronized(fmts[i]) {
         return fmts[i].parseObject(text);
      }
   }

   private static final int N = 0xff;
   private final Format[] fmts;
   private final AtomicInteger idx = new AtomicInteger(0);
}
