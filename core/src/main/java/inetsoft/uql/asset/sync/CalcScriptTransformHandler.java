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
package inetsoft.uql.asset.sync;

import inetsoft.uql.asset.internal.ScriptIterator;
import inetsoft.util.Tool;
import org.apache.commons.lang.StringUtils;

public class CalcScriptTransformHandler {
   public CalcScriptTransformHandler() {
      super();
   }

   public String transformCalcScript(String exp, RenameInfo info) {
      if(exp == null) {
         return exp;
      }

      exp = exp.replace("\\;", ESC_SEMICOLON);
      StringBuilder builder = new StringBuilder();
      String oname = info.getOldName();
      String nname = info.getNewName();
      String oprefix = info.isTable() ? oname + "." : oname;
      String nprefx = info.isTable() ? nname + "." : nname;
      String nexp = tryTransform(exp, info);

      if(!Tool.equals(nexp, exp)) {
         return nexp;
      }

      ScriptIterator iterator = new ScriptIterator(exp);
      final int[] sourceIdx = new int[1];
      sourceIdx[0] = 0;

      ScriptIterator.ScriptListener listener = (token, pref, cref) -> {
         String val = token.toString();

         // for example, val-> "['Customer.State']", token.val-> Customer.State
         if(token.isRef() && token.val != null && info.isTable() &&
            sourceIdx[0] == info.getSourceIndex() &&
            !Tool.equals(tryTransform(token.val, info), token.val))
         {
            builder.append(val.replace(oprefix, nprefx));
         }
         else if(token.isRef() && info.isColumn() && sourceIdx[0] == info.getSourceIndex() &&
            val.equals(info.getOldName()))
         {
            builder.append(nname);
         }
         else if(val.indexOf("[") != -1 && val.length() != 1) {
            String[] arr = val.split("\\[");

            for(int i = 0; i < arr.length; i++) {
               String str = arr[i];
               int idx = str.indexOf("[");
               String prefix = str.substring(0, idx + 1);
               str = str.substring(idx + 1);
               idx = str.lastIndexOf("]");
               String sufix = idx == -1 ? null : str.substring(idx);
               str = idx == -1 ? str : str.substring(0, idx);
               String content = transformCalcScript(str, info);

               StringBuilder builder2 = new StringBuilder();
               builder2.append(prefix);
               builder2.append(content);

               if(sufix != null) {
                  builder2.append(sufix);
               }

               arr[i] = builder2.toString();
            }

            String result = StringUtils.join(arr, "[");
            builder.append(result);
         }
         else if(hasSpliter(val) && val.length() != 1) {
            builder.append(transformCalcScriptPart(val, info));
         }
         else {
            if("data".equals(val)) {
               sourceIdx[0] = 0;
            }
            if(val.startsWith("data")) {
               String sub = val.substring("data".length());

               try {
                  sourceIdx[0] = Integer.parseInt(sub);
               }
               catch(Exception ignore) {
               }
            }

            builder.append(val);
         }
      };

      iterator.addScriptListener(listener);
      iterator.iterate();

      nexp = builder.toString();

      nexp = nexp.replace(ESC_SEMICOLON, "\\;");
      return nexp;
   }

   public String transformCalcScriptPart(String exp, RenameInfo info) {
      return transformCalcScriptPart(exp, 0, info);
   }

   public String transformCalcScriptPart(String exp, int spliterIdx, RenameInfo info) {
      if(exp == null || "".equals(exp) || spliterIdx >= SPLITER_ARR.length) {
         return exp;
      }

      String nexp = tryTransform(exp, info);

      if(!Tool.equals(nexp, exp)) {
         return nexp;
      }

      spliterIdx = spliterIdx == -1 ? 0 : spliterIdx;
      String spliter = SPLITER_ARR[spliterIdx];
      String[] arr = exp.split(spliter);

      for(int i = 0; i < arr.length; i++) {
         if(info.isTable() && !Tool.equals(tryTransform(arr[i], info), arr[i])) {
            arr[i] = tryTransform(arr[i], info);
         }
         else if(info.isColumn() && arr[i].equals( info.getOldName())) {
            arr[i] = info.getNewName();
         }
         else {
            arr[i] = transformCalcScriptPart(arr[i], ++spliterIdx, info);
         }
      }

      return StringUtils.join(arr, spliter);
   }

   private String tryTransform(String exp, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();
      String oprefix = info.isTable() ? oname + "." : oname;
      String nprefx = info.isTable() ? nname + "." : nname;

      if(info.isTable() && (exp.startsWith(oprefix)) || exp.startsWith("\"" + oprefix)) {
         return exp.replace(oprefix, nprefx);
      }
      else if(info.isColumn() && exp.equals(info.getOldName())) {
         return info.getNewName();
      }

      return exp;
   }

   private boolean hasSpliter(String exp) {
      for(int i = 0; i < SPLITER_ARR.length; i++) {
         if(exp.indexOf(SPLITER_ARR[i]) != -1) {
            return true;
         }
      }

      return false;
   }

   private static final String[] SPLITER_ARR = {"@", ";", ":", ","};
   private static final String ESC_SEMICOLON = "#&^_ESCAPED_SEMI_COLON_^&#";
}