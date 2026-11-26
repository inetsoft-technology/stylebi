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
package inetsoft.web.wiz.worksheet;

import inetsoft.uql.asset.AssetVariable;
import inetsoft.uql.asset.ExpressionValue;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XValueNode;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.viewsheet.WizStrictValueParser;

/**
 * The one place a variable's default value is built, shared by {@code add_variable} (both entry
 * points, via {@code WorksheetAgentController#createVariable}) and {@code edit_variable}
 * ({@code WorksheetEditService.Editor#editVariable}) so add and edit cannot diverge.
 *
 * <p>Mirrors the Composer's Variable Properties dialog
 * ({@code VariableAssemblyDialogService#convertModelToAssetVariable}): a literal default is parsed
 * against the variable's type, an expression default is stored as a JavaScript
 * {@link ExpressionValue} on a node of the variable's type, and "None" is an absent default.
 */
final class WorksheetVariableDefault {
   static final String VALUE = "value";
   static final String EXPRESSION = "expression";

   /** Disclosed when a string variable's default starts with {@code =} but was not typed. */
   static final String LITERAL_EQUALS_WARNING =
      "stored as a literal text default, not an expression; pass defaultValueType:'expression' " +
      "for a script";

   private WorksheetVariableDefault() {
   }

   /**
    * Normalizes {@code defaultValueType}: {@code null}/blank means {@link #VALUE}; anything other
    * than {@code "value"}/{@code "expression"} (case-insensitive) is refused.
    *
    * @return {@code null} when the caller did not give a type, else {@link #VALUE} or
    *         {@link #EXPRESSION}
    */
   static String normalizeType(String defaultValueType) throws PairingException {
      if(defaultValueType == null || defaultValueType.isBlank()) {
         return null;
      }

      String t = defaultValueType.trim().toLowerCase();

      if(VALUE.equals(t) || EXPRESSION.equals(t)) {
         return t;
      }

      throw new PairingException(
         "defaultValueType must be \"value\" or \"expression\" -- got '" + defaultValueType + "'.");
   }

   /**
    * Applies the default described by the three request fields to {@code var}. The caller must
    * already have applied any {@code setTypeNode}: {@code none} clears the node, and
    * {@code setTypeNode} re-creates an empty one when the node is {@code null}.
    *
    * @param name         the variable name (the value node's name)
    * @param type         the type the request set, or {@code null} to use the variable's own
    * @param defaultValue the literal text or JavaScript source; {@code null} leaves the default
    *                     unchanged unless {@code none}
    * @param defaultValueType {@code "value"} (or {@code null}) / {@code "expression"}
    * @param none         {@code true} clears the default
    * @return a warning to surface to the caller, or {@code null}
    */
   static String apply(AssetVariable var, String name, String type, String defaultValue,
                       String defaultValueType, Boolean none)
      throws PairingException
   {
      String kind = normalizeType(defaultValueType);
      boolean isNone = Boolean.TRUE.equals(none);
      boolean isExpression = EXPRESSION.equals(kind);

      if(isNone) {
         if(defaultValue != null || isExpression) {
            throw new PairingException(
               "none:true and " + (defaultValue != null ? "defaultValue" : "defaultValueType") +
               " conflict -- none:true stores no default at all; drop one of them.");
         }

         var.setValueNode(null);
         return null;
      }

      if(isExpression && (defaultValue == null || stripExpression(defaultValue).isEmpty())) {
         throw new PairingException(
            "defaultValueType 'expression' needs a non-blank defaultValue holding the script " +
            "(for example \"new Date()\"); use none:true for no default.");
      }

      if(defaultValue == null) {
         return null;
      }

      String effectiveType = type != null
         ? type : (var.getTypeNode() != null ? var.getTypeNode().getType() : null);

      if(isExpression) {
         ExpressionValue expVal = new ExpressionValue();
         expVal.setType(ExpressionValue.JAVASCRIPT);
         expVal.setExpression(stripExpression(defaultValue));
         XValueNode node = XValueNode.createValueNodeByType(name, effectiveType, expVal);

         if(node == null) {
            throw new PairingException(
               "Could not store the expression default for variable '" + name + "'.");
         }

         var.setValueNode(node);
         return null;
      }

      XValueNode valueNode = XValueNode.createValueNode(name, effectiveType);

      if(valueNode != null) {
         try {
            WizStrictValueParser.parse(defaultValue, effectiveType);
            valueNode.parse0(defaultValue);
         }
         catch(Exception e) {
            // L2-Group7: previously fell back to storing the raw, unparsed string (e.g.
            // "not_a_number" on a declared-integer variable) instead of failing loud -- the
            // variable ended up with a default value inconsistent with its own declared type.
            throw new PairingException(
               "Default value '" + defaultValue + "' is not a valid " +
               (effectiveType != null ? effectiveType : "string") +
               " value for variable '" + name + "'.");
         }

         var.setValueNode(valueNode);
      }

      boolean stringType = effectiveType == null || XSchema.STRING.equals(effectiveType);
      return kind == null && stringType && defaultValue.startsWith("=")
         ? LITERAL_EQUALS_WARNING : null;
   }

   /** Trims, drops ONE leading {@code =} (callers write {@code =new Date()}), trims again. */
   static String stripExpression(String text) {
      String s = text.trim();

      if(s.startsWith("=")) {
         s = s.substring(1).trim();
      }

      return s;
   }
}
