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
package inetsoft.uql.asset.internal;

import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.internal.ScriptIterator.ScriptListener;
import inetsoft.uql.asset.internal.ScriptIterator.Token;
import inetsoft.util.Tool;

/**
 * Rewrites the references to a worksheet table or variable in a script when that assembly is
 * renamed. Shared by expression columns and JavaScript condition values so every script-bearing
 * location follows a rename the same way.
 */
public final class ScriptAssemblyRenamer {
   private ScriptAssemblyRenamer() {
   }

   /**
    * Rename the references to an assembly in a script.
    * @param exp the script.
    * @param oname the old assembly name.
    * @param nname the new assembly name.
    * @param tableReadsOnly true to match only the references that name a worksheet assembly: a
    *                       bare identifier, the quoted key of worksheet[...] / parameter[...], or
    *                       the dot form worksheet.T / parameter.T. Entity-qualified
    *                       field['Entity.col'] and any other quoted key are left untouched.
    * @param js true if the script is JavaScript. A new name that is not an identifier is then
    *           written as worksheet['New Name'] (the dot form is replaced by ['New Name']);
    *           SQL text is never given JavaScript quoting.
    */
   public static String rename(String exp, final String oname, final String nname,
                               final boolean tableReadsOnly, final boolean js)
   {
      ScriptIterator iterator = new ScriptIterator(exp);
      final StringBuilder sb = new StringBuilder();

      ScriptListener listener = new ScriptListener() {
         @Override
         public void nextElement(Token token, Token pref, Token cref) {
            boolean fullMatch = !Assembly.FIELD.equals(token.val) && oname.equals(token.val);
            boolean entityMatch = false;
            int idx = token.val != null ? token.val.lastIndexOf(".") : -1;
            boolean assemblyParent = pref != null && pref.type == Token.NORMAL_REF &&
               ("worksheet".equals(pref.val) || "parameter".equals(pref.val));

            if(tableReadsOnly) {
               boolean assemblyKey = token.type != Token.NORMAL_REF && assemblyParent;
               fullMatch = fullMatch && (token.type == Token.NORMAL_REF || assemblyKey);
            }
            else if(pref != null && Assembly.FIELD.equals(pref.val) && idx != -1) {
               entityMatch = Tool.equals(oname, token.val.substring(0, idx));
            }

            // a name after a dot is a property of some other object, except worksheet.T and
            // parameter.V which read the assembly itself
            boolean afterDot = sb.toString().endsWith(".");
            boolean dotForm = afterDot && js && token.type == Token.NORMAL_REF && assemblyParent;

            if(token.isRef() && (!afterDot || dotForm) && (fullMatch || entityMatch)) {
               String nval = entityMatch ? nname + token.val.substring(idx) : nname;

               if(js && token.type == Token.NORMAL_REF && !entityMatch &&
                  !Tool.isValidIdentifier(nval))
               {
                  if(afterDot) {
                     sb.deleteCharAt(sb.length() - 1);
                  }
                  else {
                     sb.append("worksheet");
                  }

                  sb.append(new Token(Token.SINGLE_REF, nval, token.length));
               }
               else {
                  sb.append(new Token(token.type, nval, token.length));
               }
            }
            else {
               sb.append(token);
            }
         }
      };

      iterator.addScriptListener(listener);
      iterator.iterate();

      return sb.toString();
   }
}
