/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;

import java.util.*;

/**
 * Finds components whose formatting a script sets, which the modern reset does not touch. Only
 * writes count, as a missed case is acceptable and a warning on every script is not.
 */
public final class ScriptFormattingScanner {
   private ScriptFormattingScanner() {
   }

   /**
    * @param components names of own components with formatting set by a script or an expression.
    * @param dashboardScript true if a dashboard script sets formatting on a target it cannot name.
    */
   public record Report(SortedSet<String> components, boolean dashboardScript) {
      public boolean isEmpty() {
         return components.isEmpty() && !dashboardScript;
      }
   }

   public static Report scan(Viewsheet vs) {
      SortedSet<String> components = new TreeSet<>();
      boolean dashboard = false;

      for(VSAssemblyInfo info : ownInfos(vs)) {
         if(info.isScriptEnabled() && info.getScript() != null) {
            String self = info.getAbsoluteName();

            for(String target : writes(info.getScript())) {
               // the script's own scope, e.g. a chart's xAxis or axis
               if(target == null || "this".equals(target) || vs.getAssembly(target) == null) {
                  components.add(self);
               }
               else if(isOwn(vs, target)) {
                  components.add(target);
               }
            }
         }

         if(hasExpressionFormat(info)) {
            components.add(info.getAbsoluteName());
         }
      }

      ViewsheetInfo vinfo = vs.getViewsheetInfo();

      if(vinfo != null) {
         for(String script : new String[] { vinfo.getOnInit(), vinfo.getOnLoad() }) {
            for(String target : writes(script)) {
               if(target != null && isOwn(vs, target)) {
                  components.add(target);
               }
               else {
                  dashboard = true;
               }
            }
         }
      }

      return new Report(components, dashboard);
   }

   private static boolean hasExpressionFormat(VSAssemblyInfo info) {
      for(ChromeSlot slot : info.chromeSlots()) {
         if(slot.isExpression(info)) {
            return true;
         }
      }

      return false;
   }

   private static boolean isOwn(Viewsheet vs, String name) {
      Assembly assembly = vs.getAssembly(name);

      return assembly instanceof VSAssembly &&
         !((VSAssembly) assembly).getVSAssemblyInfo().isEmbedded();
   }

   /**
    * The infos of this sheet's own assemblies, as the reset collects them.
    */
   private static List<VSAssemblyInfo> ownInfos(Viewsheet vs) {
      List<VSAssemblyInfo> infos = new ArrayList<>();

      for(Assembly assembly : vs.getAssemblies(true)) {
         if(assembly instanceof VSAssembly) {
            VSAssemblyInfo info = ((VSAssembly) assembly).getVSAssemblyInfo();

            if(info != null && !info.isEmbedded()) {
               infos.add(info);
            }
         }
      }

      // embedded-viewsheet containers are flattened away above
      for(Assembly assembly : vs.getAssemblies(false)) {
         if(assembly instanceof Viewsheet) {
            VSAssemblyInfo info = ((Viewsheet) assembly).getVSAssemblyInfo();

            if(info != null && !info.isEmbedded()) {
               infos.add(info);
            }
         }
      }

      return infos;
   }

   /**
    * The target of each formatting write in a script; null when the write names none.
    */
   private static List<String> writes(String script) {
      List<String> writes = new ArrayList<>();

      if(script == null || script.isBlank()) {
         return writes;
      }

      List<Tok> toks = lex(script);

      for(int i = 0; i < toks.size(); i++) {
         Tok first = toks.get(i);

         if(first.kind != Kind.NAME || i > 0 && toks.get(i - 1).kind == Kind.DOT) {
            continue;
         }

         int last = i;

         while(last + 2 < toks.size() && toks.get(last + 1).kind == Kind.DOT &&
            toks.get(last + 2).kind == Kind.NAME)
         {
            last += 2;
         }

         Kind next = last + 1 < toks.size() ? toks.get(last + 1).kind : null;
         String prop = toks.get(last).text;
         boolean write = next == Kind.ASSIGN && isLookProperty(prop) ||
            next == Kind.LPAREN && SETTERS.contains(prop);

         if(write && last == i && i > 0 && DECLARATIONS.contains(toks.get(i - 1).text)) {
            write = false;
         }

         if(write) {
            writes.add(last == i ? null : first.text);
         }

         i = last;
      }

      return writes;
   }

   private static boolean isLookProperty(String name) {
      return LOOK_PROPERTIES.contains(name) || name.endsWith("Color") ||
         name.endsWith("Colors") || name.endsWith("Font");
   }

   private enum Kind { NAME, DOT, ASSIGN, LPAREN, LBRACKET, OTHER }

   private record Tok(Kind kind, String text) {
   }

   /**
    * Reduce a script to names, dots, a lone assignment and calls, dropping comments and strings.
    * A quoted name in brackets reads as a dotted name.
    */
   private static List<Tok> lex(String s) {
      List<Tok> toks = new ArrayList<>();
      int n = s.length();

      for(int i = 0; i < n; i++) {
         char c = s.charAt(i);

         if(c <= ' ') {
            continue;
         }

         if(c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
            while(i < n && s.charAt(i) != '\n') {
               i++;
            }
         }
         else if(c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
            int end = s.indexOf("*/", i + 2);
            i = end < 0 ? n : end + 1;
         }
         else if(c == '"' || c == '\'') {
            int end = i + 1;

            while(end < n && s.charAt(end) != c) {
               end += s.charAt(end) == '\\' ? 2 : 1;
            }

            int close = end + 1;

            while(close < n && s.charAt(close) <= ' ') {
               close++;
            }

            if(!toks.isEmpty() && toks.get(toks.size() - 1).kind == Kind.LBRACKET &&
               end < n && close < n && s.charAt(close) == ']')
            {
               toks.set(toks.size() - 1, new Tok(Kind.DOT, "."));
               toks.add(new Tok(Kind.NAME, s.substring(i + 1, end)));
               i = close;
            }
            else {
               toks.add(new Tok(Kind.OTHER, ""));
               i = end;
            }
         }
         else if(Character.isJavaIdentifierPart(c)) {
            int end = i;

            while(end < n && Character.isJavaIdentifierPart(s.charAt(end))) {
               end++;
            }

            toks.add(new Tok(Kind.NAME, s.substring(i, end)));
            i = end - 1;
         }
         else if(c == '.') {
            toks.add(new Tok(Kind.DOT, "."));
         }
         else if(c == '(') {
            toks.add(new Tok(Kind.LPAREN, "("));
         }
         else if(c == '[') {
            toks.add(new Tok(Kind.LBRACKET, "["));
         }
         else if(c == '=' || "!<>+-*/%&|^".indexOf(c) >= 0) {
            int end = i + 1;

            // an operator that ends in = or starts it is not a plain assignment
            while(end < n && (s.charAt(end) == '=' || c == '=' && s.charAt(end) == '>')) {
               end++;
            }

            boolean plain = c == '=' && end == i + 1;
            toks.add(new Tok(plain ? Kind.ASSIGN : Kind.OTHER, ""));
            i = end - 1;
         }
         else {
            toks.add(new Tok(Kind.OTHER, ""));
         }
      }

      return toks;
   }

   private static final Set<String> LOOK_PROPERTIES = Set.of(
      "foreground", "background", "font", "borders", "borderColors", "alpha", "roundCorner",
      "titleFont", "titleColor");
   private static final Set<String> SETTERS = Set.of(
      "setForeground", "setBackground", "setFont", "setColor", "setColors");
   private static final Set<String> DECLARATIONS = Set.of("var", "let", "const", "function");
}
