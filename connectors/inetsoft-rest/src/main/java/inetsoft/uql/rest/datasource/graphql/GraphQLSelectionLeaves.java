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
package inetsoft.uql.rest.datasource.graphql;

import java.util.*;

/**
 * Reads the leaf fields a GraphQL query selects under a nested object, so a table can build the
 * leaf columns of an object that comes back null on every row (the response then carries no keys
 * for it). Parsing is deliberately conservative: anything not understood with confidence (fragment
 * spreads, inline fragments, directives, unbalanced input, a JSON path that does not resolve
 * unambiguously to a selection) yields no declarations, which keeps the data-driven columns.
 */
final class GraphQLSelectionLeaves {
   private GraphQLSelectionLeaves() {
   }

   /**
    * @param queryString the GraphQL query.
    * @param jsonPath    the query's JSON path, which selects the rows.
    *
    * @return object path to recursive leaf paths, both relative to the row root and named the way
    *         the table names columns (response keys, so aliases, dot joined). Empty on any doubt.
    */
   /**
    * @return the root query field of a {@code $.data.<field>...} path, or null.
    */
   static String rootField(String jsonPath) {
      final List<String> steps = pathSteps(jsonPath);
      return steps != null && steps.size() >= 2 && "data".equals(steps.get(0)) ?
         steps.get(1) : null;
   }

   static Map<String, List<String>> declare(String queryString, String jsonPath) {
      try {
         final List<String> steps = pathSteps(jsonPath);
         final Node root = parse(queryString);

         if(steps == null || root == null || steps.size() < 2 || !"data".equals(steps.get(0))) {
            return Collections.emptyMap();
         }

         Node node = root;

         for(int i = 1; i < steps.size(); i++) {
            node = node.children.get(steps.get(i));

            if(node == null || node.children.isEmpty()) {
               return Collections.emptyMap();
            }
         }

         final Map<String, List<String>> result = new LinkedHashMap<>();

         for(Map.Entry<String, Node> e : node.children.entrySet()) {
            collectObjects(e.getKey(), e.getValue(), result);
         }

         return result;
      }
      catch(RuntimeException e) {
         return Collections.emptyMap();
      }
   }

   private static void collectObjects(String path, Node node, Map<String, List<String>> result) {
      if(node.children.isEmpty()) {
         return;
      }

      final List<String> leaves = new ArrayList<>();
      collectLeaves(path, node, leaves);
      result.put(path, leaves);

      for(Map.Entry<String, Node> e : node.children.entrySet()) {
         collectObjects(path + "." + e.getKey(), e.getValue(), result);
      }
   }

   private static void collectLeaves(String path, Node node, List<String> leaves) {
      for(Map.Entry<String, Node> e : node.children.entrySet()) {
         final String name = path + "." + e.getKey();

         if(e.getValue().children.isEmpty()) {
            leaves.add(name);
         }
         else {
            collectLeaves(name, e.getValue(), leaves);
         }
      }
   }

   /**
    * @return the field names a plain {@code $.a.b[*].c[0]} path walks, or null if the path is
    *         anything else (filters, recursive descent, wildcards, quoted keys).
    */
   private static List<String> pathSteps(String jsonPath) {
      if(jsonPath == null || !jsonPath.startsWith("$")) {
         return null;
      }

      final List<String> steps = new ArrayList<>();
      final int len = jsonPath.length();
      int i = 1;

      while(i < len) {
         final char c = jsonPath.charAt(i);

         if(c == '.') {
            int j = i + 1;

            while(j < len && (Character.isLetterOrDigit(jsonPath.charAt(j)) ||
                              jsonPath.charAt(j) == '_'))
            {
               j++;
            }

            if(j == i + 1) {
               return null;
            }

            steps.add(jsonPath.substring(i + 1, j));
            i = j;
         }
         else if(c == '[') {
            final int end = jsonPath.indexOf(']', i);

            if(end < 0) {
               return null;
            }

            final String inner = jsonPath.substring(i + 1, end);

            if(!inner.equals("*") && !inner.matches("\\d+")) {
               return null;
            }

            i = end + 1;
         }
         else {
            return null;
         }
      }

      return steps;
   }

   /**
    * @return the selection tree of the query's one operation, or null if it cannot be read with
    *         confidence.
    */
   private static Node parse(String query) {
      if(query == null) {
         return null;
      }

      final List<String> tokens = tokenize(query);
      return tokens == null ? null : new Parser(tokens).parseDocument();
   }

   /**
    * Splits into names and single character punctuators. Strings, block strings and comments are
    * consumed so braces inside them are not seen; a string becomes the token {@code "}. Returns
    * null for an unterminated string.
    */
   private static List<String> tokenize(String q) {
      final List<String> tokens = new ArrayList<>();
      final int len = q.length();
      int i = 0;

      while(i < len) {
         final char c = q.charAt(i);

         if(Character.isWhitespace(c) || c == ',' || c == (char) 0xFEFF) {
            i++;
         }
         else if(c == '#') {
            while(i < len && q.charAt(i) != '\n' && q.charAt(i) != '\r') {
               i++;
            }
         }
         else if(q.startsWith("\"\"\"", i)) {
            int j = i + 3;

            while(j < len && !(q.startsWith("\"\"\"", j) && q.charAt(j - 1) != '\\')) {
               j++;
            }

            if(j >= len) {
               return null;
            }

            tokens.add("\"");
            i = j + 3;
         }
         else if(c == '"') {
            int j = i + 1;

            while(j < len && q.charAt(j) != '"') {
               j += q.charAt(j) == '\\' ? 2 : 1;
            }

            if(j >= len) {
               return null;
            }

            tokens.add("\"");
            i = j + 1;
         }
         else if(q.startsWith("...", i)) {
            tokens.add("...");
            i += 3;
         }
         else if(isWordChar(c)) {
            int j = i + 1;

            while(j < len && isWordChar(q.charAt(j))) {
               j++;
            }

            tokens.add(q.substring(i, j));
            i = j;
         }
         else {
            tokens.add(String.valueOf(c));
            i++;
         }
      }

      return tokens;
   }

   private static boolean isWordChar(char c) {
      return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '+';
   }

   private static final class Node {
      final Map<String, Node> children = new LinkedHashMap<>();
   }

   private static final class Parser {
      Parser(List<String> tokens) {
         this.tokens = tokens;
      }

      Node parseDocument() {
         final String first = peek();

         if(first == null) {
            return null;
         }

         if(first.equals("query")) {
            pos++;

            if(peek() != null && !peek().equals("(") && !peek().equals("{")) {
               pos++; // operation name
            }

            if("(".equals(peek()) && !skipBalanced("(", ")")) {
               return null;
            }
         }
         else if(!first.equals("{")) {
            // mutation, subscription, fragment definition...
            return null;
         }

         final Node root = parseSelectionSet();

         // a second operation or a fragment definition is out of scope
         return root == null || pos != tokens.size() ? null : root;
      }

      private Node parseSelectionSet() {
         if(!"{".equals(peek())) {
            return null;
         }

         pos++;
         final Node node = new Node();

         while(!"}".equals(peek())) {
            final String tok = peek();

            if(tok == null || tok.equals("...") || !isName(tok)) {
               return null;
            }

            pos++;
            final String key = tok;

            if(":".equals(peek())) {
               pos++;
               final String field = peek();

               if(field == null || !isName(field)) {
                  return null;
               }

               pos++;
            }

            if("(".equals(peek()) && !skipBalanced("(", ")")) {
               return null;
            }

            if("@".equals(peek())) {
               // @include/@skip can drop the field, so its columns are not certain
               return null;
            }

            Node child = new Node();

            if("{".equals(peek())) {
               child = parseSelectionSet();

               if(child == null) {
                  return null;
               }
            }

            final Node existing = node.children.get(key);

            if(existing == null) {
               node.children.put(key, child);
            }
            else {
               merge(existing, child);
            }
         }

         if(node.children.isEmpty()) {
            return null;
         }

         pos++; // }
         return node;
      }

      private static void merge(Node target, Node source) {
         for(Map.Entry<String, Node> e : source.children.entrySet()) {
            final Node existing = target.children.get(e.getKey());

            if(existing == null) {
               target.children.put(e.getKey(), e.getValue());
            }
            else {
               merge(existing, e.getValue());
            }
         }
      }

      private boolean skipBalanced(String open, String close) {
         int depth = 0;

         while(pos < tokens.size()) {
            final String tok = tokens.get(pos++);

            if(tok.equals(open)) {
               depth++;
            }
            else if(tok.equals(close) && --depth == 0) {
               return true;
            }
         }

         return false;
      }

      private boolean isName(String tok) {
         final char c = tok.charAt(0);
         return Character.isLetter(c) || c == '_';
      }

      private String peek() {
         return pos < tokens.size() ? tokens.get(pos) : null;
      }

      private final List<String> tokens;
      private int pos;
   }
}
