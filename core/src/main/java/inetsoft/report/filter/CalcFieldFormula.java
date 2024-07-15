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

import inetsoft.report.StyleConstants;
import inetsoft.uql.XConstants;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import inetsoft.util.script.*;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * This calc field formula to encapsulate many formula with script.
 *
 * @version 11.1, 2/28/2011
 * @author InetSoft Technology Corp
 */
public class CalcFieldFormula implements PercentageFormula, Formula2 {
   /**
    * Create a CalcFieldFormula instance.
    */
   public CalcFieldFormula(String expression, String[] aggs,
                           Formula[] sub, int[] secondColumns,
                           ScriptEnv senv, Scriptable scope)
   {
      this.expression = expression;
      this.used = aggs;
      this.child = sub;

      if(expression == null || child == null || used == null ||
         secondColumns == null || child.length != used.length)
      {
         throw new RuntimeException("Invalid calc formula " + expression +
            ", aggs are: " + (aggs == null ? null : Arrays.asList(aggs)) +
            ", formula: " + (sub == null ? null : Arrays.asList(sub)) +
            ", columns length: " +
            (secondColumns == null ? null : secondColumns.length));
      }

      this.secondColumns = secondColumns;
      startPos = new int[child.length];
      isformula2 = new boolean[child.length];
      // this formula occupied the one column index
      int position = 1;

      for(int i = 0; i < startPos.length; i++) {
         startPos[i] = position;
         isformula2[i] = child[i] instanceof Formula2;
         position += (isformula2[i] ? 2 : 1);
      }

      if(position != secondColumns.length + 1) {
         throw new RuntimeException("Invalid calc formula, second columns is wrong");
      }

      // send from caller?
      this.senv = senv;
      this.scope = scope;
      initScipt();
   }

   // Needed to avoid Kyro error "Class cannot be created (missing no-arg constructor):"
   private CalcFieldFormula() { }

   /**
    * Reset the formula to start over.
    */
   @Override
   public void reset() {
      for(Formula formula : child) {
         formula.reset();
      }

      cnt = 0;
      clearResult();
   }

   /**
    * Add a value to the formula.
    */
   @Override
   public void addValue(Object v) {
      clearResult();

      if(v == null) {
         return;
      }

      // is array
      if(v instanceof Object[]) {
         Object[] vv = (Object[]) v;

         for(int i = 0; i < child.length; i++) {
            if(isformula2[i]) {
               child[i].addValue(new Object[] {vv[startPos[i]], vv[startPos[i] + 1]});
            }
            else {
               child[i].addValue(vv[startPos[i]]);
            }
         }
      }
      else {
         child[0].addValue(v);
      }

      cnt++;
   }

   /**
    * Add a double value to the formula.
    */
   @Override
   public void addValue(double v) {
      if(v == Tool.NULL_DOUBLE) {
         return;
      }

      child[0].addValue(v);
      cnt++;
      clearResult();
   }

   /**
    * Add double values to the formula.
    */
   @Override
   public void addValue(double[] v) {
      if(v[0] == Tool.NULL_DOUBLE) {
         return;
      }

      for(int i = 0; i < child.length; i++) {
         if(isformula2[i]) {
            child[i].addValue(new double[] {v[startPos[i]], v[startPos[i] + 1]});
         }
         else {
            child[i].addValue(v[startPos[i]]);
         }
      }

      cnt++;
      clearResult();
   }

   /**
    * Add a float value to the formula.
    */
   @Override
   public void addValue(float v) {
      if(v == Tool.NULL_FLOAT) {
         return;
      }

      child[0].addValue(v);
      cnt++;
      clearResult();
   }

   /**
    * Add a long value to the formula.
    */
   @Override
   public void addValue(long v) {
      if(v == Tool.NULL_LONG) {
         return;
      }

      child[0].addValue(v);
      cnt++;
      clearResult();
   }

   /**
    * Add an int value to the formula.
    */
   @Override
   public void addValue(int v) {
      if(v == Tool.NULL_INTEGER) {
         return;
      }

      child[0].addValue(v);
      cnt++;
      clearResult();
   }

   /**
    * Add a short value to the formula.
    */
   @Override
   public void addValue(short v) {
      if(v == Tool.NULL_SHORT) {
         return;
      }

      child[0].addValue(v);
      cnt++;
      clearResult();
   }

   /**
    * Set the default result option of this formula.
    * @param def <tt>true</tt> to use the default value of a formula if no
    * result, <tt>false</tt> to just return null.
    */
   @Override
   public void setDefaultResult(boolean def) {
      this.def = def;
   }

   /**
    * Get the default result option of this formula.
    * @return <tt>true</tt> to use the default value of a formula if no
    * result, <tt>false</tt> to just return null.
    */
   @Override
   public boolean isDefaultResult() {
      return def;
   }

   /**
    * Clear the result to avoid reading wrong cache result.
    */
   public void clearResult() {
      result = null;
   }

   /**
    * Get the formula result.
    */
   @Override
   public Object getResult() {
      if(cnt == 0) {
         return def ? 0D : null;
      }

      Object[] cachedResult = this.result;

      if(cachedResult == null) {
         cachedResult = result = new Object[] { getResult0() };
      }

      return cachedResult[0];
   }

   private Object getResult0() {
      Object result;
      Scriptable scope = null;

      // execute the script object
      try {
         result = senv.exec(script, scope = updateParameter(), null, null);
      }
      catch(Exception ex) {
         String suggestion = senv.getSuggestion(ex, "field", scope);
         String msg = "Script error: " + ex.getMessage() +
            (suggestion != null ? "\nTo fix: " + suggestion : "") +
            "\nScript failed:\n" + XUtil.numbering(runtimeFormula);

         if(LOG.isDebugEnabled()) {
            LOG.debug(msg, ex);
         }
         else {
            LOG.warn(msg);
         }

         throw new ScriptException(msg, ex);
      }

      if(result == null) {
         return null;
      }

      try {
         double douResult = getDouble(result);

         if(percentageType != 0 && total instanceof Number) {
            double totalNum = getDouble(total);

            if(totalNum == 0) {
               return def ? 0D : null;
            }

            return douResult / totalNum;
         }

         return douResult;
      }
      catch(NumberFormatException ne) {
         return result;
      }
   }

   /**
    * Get the formula double result.
    */
   @Override
   public double getDoubleResult() {
      return getDouble(getResult());
   }

   private double getDouble(Object value) {
      return (value instanceof Number) ? ((Number) value).doubleValue() :
         NumberParserWrapper.getDouble(value.toString());
   }

   /**
    * Check if the result is null.
    */
   @Override
   public boolean isNull() {
      return cnt == 0;
   }

   @Override
   public Object clone() {
      try {
         Object obj = super.clone();
         CalcFieldFormula cformula = (CalcFieldFormula) obj;
         cformula.child = new Formula[child.length];

         for(int i = 0; i < child.length; i++) {
            cformula.child[i] = (Formula) child[i].clone();
         }

         return obj;
      }
      catch(CloneNotSupportedException ex) {
         return this;
      }
   }

   /**
    * Get percentage type.
    */
   @Override
   public int getPercentageType() {
      return percentageType;
   }

   /**
    * Set percentage type.
    * three types: StyleConstants.PERCENTAGE_NONE,
    *              StyleConstants.PERCENTAGE_OF_GROUP,
    *              StyleConstants.PERCENTAGE_OF_GRANDTOTAL.
    */
   @Override
   public void setPercentageType(int percentageType) {
      this.percentageType = (short) percentageType;
   }

   /**
    * Set the total used to calculate percentage.
    * if percentage type is PERCENTAGE_NONE, it is ineffective to
    * invoke the method.
    */
   @Override
   public void setTotal(Object total) {
      this.total = total;
   }

   /**
    * Get the original formula result without percentage.
    */
   @Override
   public Object getOriginalResult() {
      int perType = getPercentageType();
      setPercentageType(StyleConstants.PERCENTAGE_NONE);
      Object oresult = getResult();
      setPercentageType(perType);

      return oresult;
   }

   /**
    * Get formula display name.
    */
   @Override
   public String getDisplayName() {
      return Catalog.getCatalog().getString("Calc");
   }

   /**
    * Get formula name.
    */
   @Override
   public String getName() {
      return XConstants.CALC_FORMULA;
   }

   /**
    * (non-Javadoc)
    * @see inetsoft.report.filter.Formula2#getSecondaryColumns()
    */
   @Override
   public int[] getSecondaryColumns() {
      return secondColumns;
   }

   /**
    * Update the children formula value to parameter.
    */
   private Scriptable updateParameter() {
      if(values == null) {
         values = new ConcurrentHashMap<>();
      }

      for(int i = 0; i < child.length; i++) {
         Object value = child[i].getResult();
         value = value == null ? NULL_VALUE_VAL : value;
         String key = used[i] == null ? NULL_VALUE_KEY : used[i];
         values.put(key, value);
      }

      if(valuesScriptable == null) {
         valuesScriptable = new ValuesScriptable();
         valuesScriptable.setParentScope(scope);
      }

      return valuesScriptable;
   }

   /**
    * Generate runtime scirpt.
    */
   private void generateRuntimeScript() {
      runtimeFormula = expression.toUpperCase();
      char[] runtimes = expression.toCharArray();

      for(int i = 0; i < used.length; i++) {
         String key = used[i].toUpperCase();
         String nname = key;

         for(char illegalChar : illegalChars) {
            nname = nname.replace(illegalChar, '_');
         }

         replaceExpression(runtimeFormula, runtimes, key, nname, 0);
         used[i] = nname;
      }

      runtimeFormula = new String(runtimes);
   }

   /*
    * replace expession string.
    */
   private void replaceExpression(String runtimeFormula, char[] runtimes,
                                  String key, String value, int index)
   {
      int idx = runtimeFormula.indexOf(key, index);

      if(idx >= 0) {
         int nextidx = idx + key.length();

         for(int i = idx; i < nextidx; i++) {
            value.getChars(0, value.length(), runtimes, idx);
         }

         // need to replace again if exp has next substring.
         replaceExpression(runtimeFormula, runtimes, key, value, nextidx);
      }
   }

   /**
    * Init script object.
    */
   private void initScipt() {
      generateRuntimeScript();

      Consumer<Exception> handler = (Exception ex) -> {
         String suggestion = senv.getSuggestion(ex, "field", scope);
         String msg = "Script error: " + ex.getMessage() +
            (suggestion != null ? "\nTo fix: " + suggestion : "") +
            "\nScript failed:\n" + XUtil.numbering(runtimeFormula);


         if(LOG.isDebugEnabled()) {
            LOG.debug(msg, ex);
         }
         else {
            LOG.warn(msg);
         }
      };

      script = scriptCache.get(runtimeFormula, senv, handler);
   }

   /**
    * Get the expression.
    */
   public String getExpression() {
      return expression;
   }

   public String toString() {
      return "CalcFieldFormula[" + expression +
             ", aggs: " + (used == null ? null : Arrays.asList(used)) +
             ", formula: " + (child == null ? null : Arrays.asList(child)) +
             ", columns length: " +
             (secondColumns == null ? null : secondColumns.length) +
             ", percentage: " + percentageType + "]";
   }

   private void readObject(ObjectInputStream in) throws ClassNotFoundException, IOException {
      in.defaultReadObject();
   }

   /**
    * Write object.
    */
   private void writeObject(ObjectOutputStream out) throws IOException {
      // make sure result is calculated and stored before script env is lost since we can't
      // serialize scriptable, which may have reference far and beyond.
      try {
         getResult();
      }
      catch(Exception ex) {
         // already logged in getResult0 so no need to repeat it here (45486).
      }

      out.defaultWriteObject();
   }

   private class ValuesScriptable extends ScriptableObject implements DynamicScope {
      @Override
      public Object get(String name, Scriptable scope) {
         String key = name == null ? NULL_VALUE_KEY : name;

         if(values != null && values.containsKey(key)) {
            Object value = values.get(key);
            return value == NULL_VALUE_VAL ? null : value;
         }

         return super.get(name, scope);
      }

      @Override
      public String getClassName() {
         return "fieldValues";
      }
   }

   private String expression;
   private String[] used;
   private Formula[] child;
   private int[] secondColumns;

   private short percentageType = (short) StyleConstants.PERCENTAGE_NONE;
   private Object total = null;
   private int cnt = 0;
   private boolean def;
   private transient Map<String, Object> values;
   private static final String NULL_VALUE_KEY = "__INETSOFT_NULL_VALUE_KEY__";
   private static final Object NULL_VALUE_VAL = new Object();

   private static final Logger LOG = LoggerFactory.getLogger(CalcFieldFormula.class);
   // some child maybe Formula2, use this to locate formula column efficient
   private transient int[] startPos;
   private transient boolean[] isformula2;

   // script runtime
   private Object[] result; // result holder
   private transient Scriptable scope;
   private transient Scriptable valuesScriptable;
   private transient ScriptEnv senv;
   private transient Object script;
   private transient String runtimeFormula;

   private static final ScriptCache scriptCache = new ScriptCache(100, 60000);
   private static char[] illegalChars = {'~', '`', '!', '@', '^', '&', '*', '/',
                                       '+', '-', '=', '?', '>', '<', '(', ')',
                                       '[', ']', '{', '}', '\'', '\'', ':', ';',
                                       ' ', ',', '.', '|', '#'};
}
