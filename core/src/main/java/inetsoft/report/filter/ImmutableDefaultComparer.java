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
package inetsoft.report.filter;

import inetsoft.util.Tool;

public class ImmutableDefaultComparer extends DefaultComparer {
   public ImmutableDefaultComparer() {
      super();
   }

   private ImmutableDefaultComparer(boolean negate, boolean caseSensitive) {
      super(caseSensitive);
      super.setNegate(negate);
   }

   public static DefaultComparer getInstance() {
      return getInstance(false, Tool.isCaseSensitive()); // Matches default constructor
   }

   public static DefaultComparer getInstance(boolean caseSensitive) {
      return getInstance(false, caseSensitive);
   }

   public static DefaultComparer getInstance(boolean negate, boolean caseSensitive) {
      return comparers[negate ? 1 : 0][caseSensitive ? 1 : 0];
   }

   @Override
   public void setNegate(boolean neg) {
      throw new UnsupportedOperationException("Cannot modify ImmutableDefaultComparer.");
   }

   @Override
   public void setCaseSensitive(boolean caseSensitive) {
      throw new UnsupportedOperationException("Cannot modify ImmutableDefaultComparer.");
   }

   private static final ImmutableDefaultComparer[][] comparers = new ImmutableDefaultComparer[][] {
      {new ImmutableDefaultComparer(false, false),
         new ImmutableDefaultComparer(false, true)},
      {new ImmutableDefaultComparer(true, false),
         new ImmutableDefaultComparer(true, true)}
   };
}
