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

package inetsoft.sree.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.*;

/**
 * The schedule task permission policy (Testing #79042): evaluates the rules of
 * {@code schedule-task-permission-policy.json} for an actor's attributes and the two settings.
 * An expression uses attribute names, the settings {@code share} and {@code deleteOnlyByOwner},
 * other rule ids, {@code true} / {@code false}, and {@code && || ! ( )}.
 */
public final class SchedulePermissionPolicy {
   private SchedulePermissionPolicy(Map<String, String> rules, Set<String> attributes) {
      this.rules = rules;
      this.attributes = attributes;
   }

   public static SchedulePermissionPolicy load() {
      try(InputStream in = SchedulePermissionPolicy.class
         .getResourceAsStream("schedule-task-permission-policy.json"))
      {
         JsonNode root = new ObjectMapper().readTree(Objects.requireNonNull(in, "policy file"));
         Map<String, String> rules = new LinkedHashMap<>();
         root.get("rules").forEach(r -> rules.put(r.get("id").asText(), r.get("expr").asText()));
         Set<String> attributes = new HashSet<>();
         root.get("attributes").fieldNames().forEachRemaining(attributes::add);
         attributes.add("share");
         attributes.add("deleteOnlyByOwner");
         return new SchedulePermissionPolicy(rules, attributes);
      }
      catch(Exception ex) {
         throw new IllegalStateException("Failed to read the schedule permission policy", ex);
      }
   }

   /**
    * @param ruleId     a rule id of the policy file.
    * @param attributes the actor's attributes and the settings; a missing name is false.
    *
    * @return whether the rule allows the actor.
    */
   public boolean allows(String ruleId, Map<String, Boolean> attributes) {
      return evaluate(ruleId, attributes, new HashSet<>());
   }

   private boolean evaluate(String ruleId, Map<String, Boolean> values, Set<String> seen) {
      String expr = rules.get(ruleId);

      if(expr == null) {
         throw new IllegalArgumentException("Unknown rule " + ruleId);
      }

      if(!seen.add(ruleId)) {
         throw new IllegalStateException("Rule cycle at " + ruleId);
      }

      return new Parser(expr, values, seen).parse();
   }

   /** Recursive descent over: or := and ('||' and)*; and := not ('&&' not)*; not := '!' not | atom. */
   private final class Parser {
      Parser(String expr, Map<String, Boolean> values, Set<String> seen) {
         this.tokens = tokenize(expr);
         this.values = values;
         this.seen = seen;
         this.expr = expr;
      }

      boolean parse() {
         boolean result = or();

         if(pos != tokens.size()) {
            throw new IllegalArgumentException("Unexpected " + tokens.get(pos) + " in " + expr);
         }

         return result;
      }

      private boolean or() {
         boolean result = and();

         while(accept("||")) {
            result = and() || result;
         }

         return result;
      }

      private boolean and() {
         boolean result = not();

         while(accept("&&")) {
            result = not() && result;
         }

         return result;
      }

      private boolean not() {
         if(accept("!")) {
            return !not();
         }

         if(accept("(")) {
            boolean result = or();
            expect(")");
            return result;
         }

         String name = next();

         if("true".equals(name) || "false".equals(name)) {
            return Boolean.parseBoolean(name);
         }

         if(rules.containsKey(name)) {
            return evaluate(name, values, new HashSet<>(seen));
         }

         if(!attributes.contains(name)) {
            throw new IllegalArgumentException("Unknown name " + name + " in " + expr);
         }

         return Boolean.TRUE.equals(values.get(name));
      }

      private boolean accept(String token) {
         if(pos < tokens.size() && tokens.get(pos).equals(token)) {
            pos++;
            return true;
         }

         return false;
      }

      private void expect(String token) {
         if(!accept(token)) {
            throw new IllegalArgumentException("Expected " + token + " in " + expr);
         }
      }

      private String next() {
         if(pos >= tokens.size()) {
            throw new IllegalArgumentException("Unexpected end of " + expr);
         }

         return tokens.get(pos++);
      }

      private final List<String> tokens;
      private final Map<String, Boolean> values;
      private final Set<String> seen;
      private final String expr;
      private int pos;
   }

   private static List<String> tokenize(String expr) {
      List<String> tokens = new ArrayList<>();
      int i = 0;

      while(i < expr.length()) {
         char c = expr.charAt(i);

         if(Character.isWhitespace(c)) {
            i++;
         }
         else if(expr.startsWith("&&", i) || expr.startsWith("||", i)) {
            tokens.add(expr.substring(i, i + 2));
            i += 2;
         }
         else if(c == '!' || c == '(' || c == ')') {
            tokens.add(String.valueOf(c));
            i++;
         }
         else if(Character.isLetter(c)) {
            int start = i;

            while(i < expr.length() && Character.isLetterOrDigit(expr.charAt(i))) {
               i++;
            }

            tokens.add(expr.substring(start, i));
         }
         else {
            throw new IllegalArgumentException("Unexpected character " + c + " in " + expr);
         }
      }

      return tokens;
   }

   private final Map<String, String> rules;
   private final Set<String> attributes;
}
