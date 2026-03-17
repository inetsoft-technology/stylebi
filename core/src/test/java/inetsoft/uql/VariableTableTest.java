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
package inetsoft.uql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link VariableTable}.
 *
 * <p>Covers:
 * <ul>
 *   <li>put / get — basic storage and retrieval</li>
 *   <li>contains / containsKey (via contains)</li>
 *   <li>Null and all-null-array value handling</li>
 *   <li>remove</li>
 *   <li>clear</li>
 *   <li>size</li>
 *   <li>keys enumeration</li>
 *   <li>clone</li>
 *   <li>equals / hashCode</li>
 *   <li>Base-table delegation (parent chain lookup)</li>
 *   <li>addBaseTable / removeBaseTable</li>
 *   <li>setAsIs / isAsIs</li>
 *   <li>setNotIgnoredNull / isNotIgnoredNull / clearNullIgnored</li>
 *   <li>putFormat / getFormat / removeFormat</li>
 *   <li>isBuiltinVariable</li>
 *   <li>isContextVariable</li>
 *   <li>getSubset</li>
 *   <li>session getter/setter</li>
 * </ul>
 * </p>
 */
class VariableTableTest {

   private VariableTable table;

   @BeforeEach
   void setUp() {
      table = new VariableTable();
   }

   // ==========================================================================
   // put / get
   // ==========================================================================

   @Test
   void put_and_get_stringValue() throws Exception {
      table.put("name", "Alice");
      assertEquals("Alice", table.get("name"));
   }

   @Test
   void put_and_get_integerValue() throws Exception {
      table.put("count", 42);
      assertEquals(42, table.get("count"));
   }

   @Test
   void put_overwritesPreviousValue() throws Exception {
      table.put("x", "first");
      table.put("x", "second");
      assertEquals("second", table.get("x"));
   }

   @Test
   void get_undefinedVariableReturnsNull() throws Exception {
      assertNull(table.get("nonexistent"));
   }

   @Test
   void put_nullValueIsStored() throws Exception {
      table.put("nullVar", (Object) null);
      assertTrue(table.contains("nullVar"), "Key should be present even with null value");
   }

   /**
    * An Object[] containing only nulls is treated as null and stored as null.
    */
   @Test
   void put_allNullArrayIsStoredAsNull() throws Exception {
      Object[] arr = {null, null, null};
      table.put("arr", arr);
      // The implementation converts all-null arrays to null
      assertNull(table.get("arr"), "All-null array should be stored as null");
   }

   /**
    * An Object[] with at least one non-null element is stored as-is.
    */
   @Test
   void put_partialNullArrayIsStoredAsIs() throws Exception {
      Object[] arr = {null, "value", null};
      table.put("arr", arr);
      Object result = table.get("arr");
      assertNotNull(result, "Array with non-null element should be stored as-is");
      assertInstanceOf(Object[].class, result);
   }

   // ==========================================================================
   // contains
   // ==========================================================================

   @Test
   void contains_returnsTrueForExistingKey() {
      table.put("k", "v");
      assertTrue(table.contains("k"));
   }

   @Test
   void contains_returnsFalseForMissingKey() {
      assertFalse(table.contains("missing"));
   }

   @Test
   void contains_returnsTrueForNullValueKey() {
      table.put("nullKey", (Object) null);
      assertTrue(table.contains("nullKey"));
   }

   // ==========================================================================
   // remove
   // ==========================================================================

   @Test
   void remove_removesKey() throws Exception {
      table.put("r", "value");
      table.remove("r");
      assertFalse(table.contains("r"));
      assertNull(table.get("r"));
   }

   @Test
   void remove_nonexistentKey_doesNotThrow() {
      assertDoesNotThrow(() -> table.remove("does_not_exist"));
   }

   // ==========================================================================
   // clear
   // ==========================================================================

   @Test
   void clear_removesAllEntries() throws Exception {
      table.put("a", 1);
      table.put("b", 2);
      table.clear();
      assertFalse(table.contains("a"));
      assertFalse(table.contains("b"));
   }

   @Test
   void clear_doesNotAffectBaseTable() throws Exception {
      VariableTable base = new VariableTable();
      base.put("baseVar", "from base");
      table.setBaseTable(base);
      table.put("local", "local value");

      table.clear();

      // base table is untouched
      assertTrue(table.contains("baseVar"), "Base table should still be reachable after clear");
   }

   // ==========================================================================
   // size
   // ==========================================================================

   @Test
   void size_emptyTableIsZero() {
      assertEquals(0, table.size());
   }

   @Test
   void size_countsLocalEntries() {
      table.put("a", 1);
      table.put("b", 2);
      assertEquals(2, table.size());
   }

   @Test
   void size_includesBaseTableEntries() {
      VariableTable base = new VariableTable();
      base.put("x", 10);
      table.setBaseTable(base);
      table.put("y", 20);
      assertEquals(2, table.size());
   }

   // ==========================================================================
   // keys enumeration
   // ==========================================================================

   @Test
   void keys_enumeratesLocalKeys() {
      table.put("alpha", 1);
      table.put("beta", 2);

      Set<String> found = new HashSet<>();
      Enumeration<String> e = table.keys();
      while(e.hasMoreElements()) {
         found.add(e.nextElement());
      }

      assertTrue(found.contains("alpha"));
      assertTrue(found.contains("beta"));
   }

   @Test
   void keys_includesBaseTableKeys() {
      VariableTable base = new VariableTable();
      base.put("fromBase", "yes");
      table.setBaseTable(base);
      table.put("fromLocal", "yes");

      Set<String> found = new HashSet<>();
      Enumeration<String> e = table.keys();
      while(e.hasMoreElements()) {
         found.add(e.nextElement());
      }

      assertTrue(found.contains("fromBase"));
      assertTrue(found.contains("fromLocal"));
   }

   // ==========================================================================
   // Base-table delegation
   // ==========================================================================

   @Test
   void get_delegatesToBaseTable() throws Exception {
      VariableTable base = new VariableTable();
      base.put("inherited", "value");
      table.setBaseTable(base);

      assertEquals("value", table.get("inherited"),
                   "Should retrieve value from base table");
   }

   @Test
   void get_localOverridesBase() throws Exception {
      VariableTable base = new VariableTable();
      base.put("key", "base-value");
      table.setBaseTable(base);
      table.put("key", "local-value");

      assertEquals("local-value", table.get("key"),
                   "Local value should shadow base table value");
   }

   @Test
   void setBaseTable_preventsSelfCycle() {
      table.setBaseTable(table);  // should be silently ignored
      assertNull(table.getBaseTable(), "Setting self as base should be ignored");
   }

   @Test
   void setBaseTable_preventsIndirectCycle() {
      VariableTable base = new VariableTable();
      base.setBaseTable(table);
      table.setBaseTable(base);   // would create cycle table->base->table
      assertNull(table.getBaseTable(), "Cycle base should be rejected");
   }

   @Test
   void addBaseTable_appendsToChain() throws Exception {
      VariableTable first  = new VariableTable();
      VariableTable second = new VariableTable();
      first.put("f", "first");
      second.put("s", "second");

      table.setBaseTable(first);
      table.addBaseTable(second);

      assertEquals("second", table.get("s"),
                   "Should reach second-level base");
   }

   @Test
   void removeBaseTable_byReference_removesIt() {
      VariableTable base = new VariableTable();
      base.put("x", 1);
      table.setBaseTable(base);
      table.removeBaseTable(base);
      assertNull(table.getBaseTable());
   }

   @Test
   void removeBaseTable_byType_removesIt() {
      VariableTable base = new VariableTable();
      table.setBaseTable(base);
      table.removeBaseTable(VariableTable.class);
      assertNull(table.getBaseTable());
   }

   // ==========================================================================
   // clone
   // ==========================================================================

   @Test
   void clone_producesIndependentCopy() throws Exception {
      table.put("x", "original");
      VariableTable copy = table.clone();

      copy.put("x", "modified");

      assertEquals("original", table.get("x"), "Clone modification should not affect original");
      assertEquals("modified", copy.get("x"));
   }

   @Test
   void clone_copiesAllEntries() throws Exception {
      table.put("a", 1);
      table.put("b", "hello");
      VariableTable copy = table.clone();

      assertEquals(1,       copy.get("a"));
      assertEquals("hello", copy.get("b"));
   }

   @Test
   void clone_baseTableIsAlsoCloned() throws Exception {
      VariableTable base = new VariableTable();
      base.put("baseVar", "bv");
      table.setBaseTable(base);

      VariableTable copy = table.clone();
      assertNotNull(copy.getBaseTable());
      assertEquals("bv", copy.get("baseVar"));
   }

   // ==========================================================================
   // equals / hashCode
   // ==========================================================================

   @Test
   void equals_twoEmptyTablesAreEqual() {
      assertEquals(new VariableTable(), new VariableTable());
   }

   @Test
   void equals_tablesWithSameEntriesAreEqual() {
      VariableTable t1 = new VariableTable();
      VariableTable t2 = new VariableTable();
      t1.put("k", "v");
      t2.put("k", "v");
      assertEquals(t1, t2);
   }

   @Test
   void equals_tablesWithDifferentEntriesAreNotEqual() {
      VariableTable t1 = new VariableTable();
      VariableTable t2 = new VariableTable();
      t1.put("k", "v1");
      t2.put("k", "v2");
      assertNotEquals(t1, t2);
   }

   @Test
   void equals_sameInstanceIsEqual() {
      assertEquals(table, table);
   }

   @Test
   void equals_notEqualToNull() {
      assertNotEquals(null, table);
   }

   @Test
   void equals_notEqualToDifferentType() {
      assertNotEquals("string", table);
   }

   @Test
   void hashCode_equalTablesHaveSameHashCode() {
      VariableTable t1 = new VariableTable();
      VariableTable t2 = new VariableTable();
      t1.put("k", "v");
      t2.put("k", "v");
      assertEquals(t1.hashCode(), t2.hashCode());
   }

   // ==========================================================================
   // asIs
   // ==========================================================================

   @Test
   void setAsIs_and_isAsIs() {
      table.put("param", "value");
      assertFalse(table.isAsIs("param"), "Default should be false");
      table.setAsIs("param", true);
      assertTrue(table.isAsIs("param"));
      table.setAsIs("param", false);
      assertFalse(table.isAsIs("param"));
   }

   @Test
   void isAsIs_unknownVariable_returnsFalse() {
      assertFalse(table.isAsIs("unknownVar"));
   }

   // ==========================================================================
   // notIgnoreNull
   // ==========================================================================

   @Test
   void setNotIgnoredNull_and_isNotIgnoredNull() {
      assertFalse(table.isNotIgnoredNull("v"));
      table.setNotIgnoredNull("v");
      assertTrue(table.isNotIgnoredNull("v"));
   }

   @Test
   void clearNullIgnored_removesAll() {
      table.setNotIgnoredNull("a");
      table.setNotIgnoredNull("b");
      table.clearNullIgnored();
      assertFalse(table.isNotIgnoredNull("a"));
      assertFalse(table.isNotIgnoredNull("b"));
   }

   // ==========================================================================
   // putFormat / getFormat / removeFormat
   // ==========================================================================

   @Test
   void putFormat_and_getFormat() {
      table.putFormat("dateCol", "yyyy-MM-dd");
      assertEquals("yyyy-MM-dd", table.getFormat("dateCol"));
   }

   @Test
   void getFormat_missingKey_returnsNull() {
      assertNull(table.getFormat("missing"));
   }

   @Test
   void removeFormat_removesEntry() {
      table.putFormat("col", "MM/dd/yyyy");
      table.removeFormat("col");
      assertNull(table.getFormat("col"));
   }

   // ==========================================================================
   // isBuiltinVariable
   // ==========================================================================

   @Test
   void isBuiltinVariable_recognisesBuiltins() {
      assertTrue(VariableTable.isBuiltinVariable("_TODAY"));
      assertTrue(VariableTable.isBuiltinVariable("_USER_"));
      assertTrue(VariableTable.isBuiltinVariable("_ROLES_"));
      assertTrue(VariableTable.isBuiltinVariable("_GROUPS_"));
      assertTrue(VariableTable.isBuiltinVariable("_BEGINNING_OF_YEAR"));
      assertTrue(VariableTable.isBuiltinVariable("_END_OF_YEAR"));
      assertTrue(VariableTable.isBuiltinVariable("_BEGINNING_OF_MONTH"));
      assertTrue(VariableTable.isBuiltinVariable("_END_OF_MONTH"));
      assertTrue(VariableTable.isBuiltinVariable("_BEGINNING_OF_QUARTER"));
      assertTrue(VariableTable.isBuiltinVariable("_END_OF_QUARTER"));
      assertTrue(VariableTable.isBuiltinVariable("_BEGINNING_OF_WEEK"));
      assertTrue(VariableTable.isBuiltinVariable("_END_OF_WEEK"));
   }

   @Test
   void isBuiltinVariable_returnsFalseForUserVar() {
      assertFalse(VariableTable.isBuiltinVariable("myVariable"));
   }

   // ==========================================================================
   // isContextVariable
   // ==========================================================================

   @Test
   void isContextVariable_recognisesContextVars() {
      assertTrue(VariableTable.isContextVariable("_USER_"));
      assertTrue(VariableTable.isContextVariable("_ROLES_"));
      assertTrue(VariableTable.isContextVariable("_GROUPS_"));
      assertTrue(VariableTable.isContextVariable("__principal__"));
   }

   @Test
   void isContextVariable_returnsFalseForNonContextVar() {
      assertFalse(VariableTable.isContextVariable("_TODAY"));
      assertFalse(VariableTable.isContextVariable("myVar"));
   }

   // ==========================================================================
   // session
   // ==========================================================================

   @Test
   void setSession_and_getSession() {
      Object session = new Object();
      table.setSession(session);
      assertSame(session, table.getSession());
   }

   // ==========================================================================
   // getSubset
   // ==========================================================================

   @Test
   void getSubset_returnsVariablesWithMatchingPrefix() throws Exception {
      table.put("report.name",  "Sales");
      table.put("report.year",  "2023");
      table.put("other.value",  "irrelevant");

      VariableTable subset = table.getSubset("report.");
      assertEquals("Sales", subset.get("name"),
                   "Prefix should be stripped from key");
      assertEquals("2023",  subset.get("year"));
   }

   @Test
   void getSubset_includesVariablesWithoutDotInName() throws Exception {
      table.put("simple", "value");
      table.put("prefix.qualified", "other");

      VariableTable subset = table.getSubset("prefix.");
      // "prefix.qualified" matches the prefix → stripped to "qualified"
      assertTrue(subset.contains("qualified"), "Prefix-matched key should be in subset");
      assertEquals("other", subset.get("qualified"));
      // "simple" has no dot → included as-is with lower priority
      assertTrue(subset.contains("simple"), "Dot-free key should also be in subset");
      assertEquals("value", subset.get("simple"));
   }

   @Test
   void getSubset_emptyPrefix_returnsAllVariables() throws Exception {
      table.put("a", 1);
      table.put("b", 2);
      VariableTable subset = table.getSubset("");
      assertTrue(subset.contains("a"));
      assertTrue(subset.contains("b"));
   }

   // ==========================================================================
   // addAll
   // ==========================================================================

   @Test
   void addAll_mergesVariablesFromAnotherTable() throws Exception {
      VariableTable other = new VariableTable();
      other.put("x", 10);
      other.put("y", 20);

      table.put("z", 30);
      table.addAll(other);

      assertEquals(10, table.get("x"));
      assertEquals(20, table.get("y"));
      assertEquals(30, table.get("z"));
   }

   @Test
   void addAll_nullTableDoesNotThrow() {
      assertDoesNotThrow(() -> table.addAll(null));
   }

   // ==========================================================================
   // isInternalParameter
   // ==========================================================================

   @Test
   void isInternalParameter_returnsTrueForKnownInternalParam() {
      assertTrue(table.isInternalParameter("inetsoft.sree.web.SessionTimeoutListener"));
   }

   @Test
   void isInternalParameter_returnsFalseForRegularParam() {
      assertFalse(table.isInternalParameter("myParam"));
   }
}
