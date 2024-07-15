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
package org.mozilla.javascript;

public class JSMethod extends NativeJavaMethod {
   public JSMethod(NativeJavaMethod func) {
      super(func.methods, func.getFunctionName());
   }

   @Override
   int findCachedFunction(Context cx, Object[] args) {
      try {
         return super.findCachedFunction(cx, args);
      }
      catch(Exception ex) {
         // this is to handle the case where a method is varargs (e.g. init(Object ...args))
         // and with individual arguments (e.g. init(Object[] vals, GShape[] shapes))
         // rhino fails to differentiate the two and will throw an exception (ambiguity).
         // we only handle this case here. if more cases need to be handled, we can
         // enhance the matching logic.
         // the current case is for CategoricalShapeFrame.init()
         for(int i = 0; i < methods.length; i++) {
            MemberBox member = methods[i];

            if(member.isMethod() && member.argTypes.length == args.length) {
               return i;
            }
         }

         return -1;
      }
   }
}
