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
package inetsoft.report.script.formula;

import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.script.TableArray;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.script.VariableScriptable;
import inetsoft.util.script.DynamicScope;
import inetsoft.util.script.graal.ScriptScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A scriptable used as the container for all data tables in an asset query.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class AssetQueryScope implements DynamicScope, Cloneable {
   /**
    * Create a scope for an asset query.
    */
   public AssetQueryScope(AssetQuerySandbox box) {
      this.shared = null;
      this.box = box;
      setVariableTable(box.getVariableTable());
   }

   /**
    * A per-query view of this scope, for a sandbox in pool mode (bug #76960, spec §6.5): it
    * has its own parameters, mode and table scriptables, so queries of one sandbox that run
    * scripts at the same time do not overwrite each other's; every other member is this
    * shared scope's. A view's parent chain is the shared scope's: {@link #setParentScope}
    * on a view has no effect.
    */
   public AssetQueryScope queryView(VariableTable vars, int mode) {
      return new AssetQueryScope(this, vars, mode);
   }

   private AssetQueryScope(AssetQueryScope shared, VariableTable vars, int mode) {
      this.shared = shared;
      this.box = shared.box;
      this.mode = mode;
      members.put(PARAMETER, new VariableScriptable(vars));
   }

   /**
    * Get the mode.
    * @return the mode of the scope.
    */
   public int getMode() {
      return mode;
   }

   /**
    * Set the mode.
    * @param mode the specified mode of the scope.
    */
   public void setMode(int mode) {
      this.mode = mode;
   }

   /**
    * Set the parameters.
    */
   public void setVariableTable(VariableTable vars) {
      putMember("parameter", new VariableScriptable(vars));
   }

   /**
    * Set the parameters.
    */
   public VariableTable getVariableTable() {
      Object currentVarTable = getMember("parameter");

      if(currentVarTable instanceof VariableScriptable) {
         currentVarTable = ((VariableScriptable) currentVarTable).unwrap();
      }

      if(currentVarTable instanceof VariableTable) {
         return (VariableTable) currentVarTable;
      }

      return null;
   }

   /**
    * Set the parameters.
    */
   public void mergeVariableTable(VariableTable vars) throws Exception {
      VariableTable currentVarTable = getVariableTable();

      if(currentVarTable != null) {
         currentVarTable.addAll(vars);
      }
   }

   /**
    * Get a property value.
    */
   @Override
   public Object getMember(String id) {
      if(id == null) {
         return null;
      }

      try {
         if(getTableValue(id) instanceof TableArray val) {
            return val;
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to get property from asset query: " + id, ex);
      }

      Object value = members.get(id);

      if(value == null && shared != null && !PARAMETER.equals(id)) {
         value = shared.members.get(id);
      }

      if(value != null) {
         return value == NULL_VALUE ? null : value;
      }

      // the dynamic scope fallback (executing scope) is now provided
      // centrally by BindingRootProxy
      ScriptScope owner = findInChain(id);

      return owner == null ? null : owner.getMember(id);
   }

   @Override
   public boolean hasMember(String id) {
      if(id == null) {
         return false;
      }

      try {
         if(getTableValue(id) instanceof TableArray) {
            return true;
         }
      }
      catch(Exception ex) {
         // ignore
      }

      if(members.containsKey(id) || shared != null && !PARAMETER.equals(id) && shared.members.containsKey(id)) {
         return true;
      }

      return findInChain(id) != null;
   }

   /**
    * Find the scope in this scope's lookup chain that defines a name this scope
    * does not define itself. The chain is populated by
    * {@link AssetQuerySandbox#createAssetQueryScope()} -- in practice with a
    * {@code ViewsheetScope}, which resolves viewsheet assembly names (added by
    * Bug #75526 so viewsheet assemblies are visible in worksheet scripts).
    *
    * <p>A qualified read such as {@code worksheet['<viewsheet assembly>']} is
    * dispatched straight at this scope by {@code ScopeProxy}, so it never goes
    * through {@code BindingRootProxy}'s chain walk (that only covers unqualified
    * names). Both the read and the presence test need this, since GraalJS only
    * calls {@code getMember} after {@code hasMember} reported the name present.
    * Mirrors {@code ViewsheetScope.findInChain()} (#75807), the same defect on
    * the other side of the viewsheet/worksheet scope link.
    *
    * @param name the member name to look for.
    *
    * @return the owning scope, or <tt>null</tt> if the name is not in the chain.
    */
   private ScriptScope findInChain(String name) {
      for(ScriptScope scope = getParentScope();
          scope != null && scope != this; scope = scope.getParentScope())
      {
         if(scope.hasMember(name)) {
            return scope;
         }
      }

      return null;
   }

   /**
    * Resolve {@code id} to its cached scriptable (a {@link TableArray}) or the
    * {@code NOT_TABLE} sentinel, populating the {@code tablemap} cache on first
    * lookup; returns {@code null} when no worksheet is available.
    *
    * <p>Shared by {@link #getMember} and {@link #hasMember} so the two cannot
    * drift out of sync. GraalJS wraps scripts in {@code with(__scope__){...}} and
    * calls hasMember for every identifier in every per-cell/per-row evaluation;
    * without this cache each call hits {@code ws.getAssembly(id)}, and a miss
    * rebuilds the entire worksheet assembly map ({@code Worksheet.createCache}),
    * an O(cells x names x assemblies) explosion that makes calc tables take 30+s.
    * (#75676)
    *
    * <p>The cache is a concurrent map (bug #76960, spec §6.1): scripts of one sandbox reach
    * it from several threads without a common lock. The worksheet lookup runs before
    * {@code computeIfAbsent}, so no foreign lock is taken inside the map's bin lock.
    */
   private Object getTableValue(String id) throws Exception {
      Worksheet ws = box.getWorksheet();

      if(ws == null) {
         return null;
      }

      Object val = tablemap.get(id);

      if(val == null) {
         boolean table = ws.getAssembly(id) instanceof TableAssembly;
         val = tablemap.computeIfAbsent(
            id, k -> table ? new TableAssemblyScriptable(k, box, mode) : NOT_TABLE);
      }

      return val;
   }

   @Override
   public void putMember(String id, Object value) {
      // a null name was never readable (getMember(null) is null), and the concurrent map
      // rejects a null key where the old HashMap accepted it (bug #76960)
      if(id == null) {
         return;
      }

      if(shared != null && !PARAMETER.equals(id)) {
         shared.putMember(id, value);
         return;
      }

      members.put(id, value == null ? NULL_VALUE : value);
   }

   @Override
   public boolean removeMember(String id) {
      if(id == null) {
         return false;
      }

      if(shared != null && !PARAMETER.equals(id)) {
         return shared.removeMember(id);
      }

      Object old = members.remove(id);
      return old != null && old != NULL_VALUE;
   }

   @Override
   public Object[] getMemberKeys() {
      if(shared == null) {
         return members.keySet().toArray(new Object[0]);
      }

      LinkedHashSet<Object> keys = new LinkedHashSet<>(members.keySet());
      keys.addAll(shared.members.keySet());
      return keys.toArray(new Object[0]);
   }

   @Override
   public ScriptScope getParentScope() {
      return shared != null ? shared.getParentScope() : parentScope;
   }

   @Override
   public void setParentScope(ScriptScope parent) {
      this.parentScope = parent;
   }

   /**
    * Get the name of this scriptable.
    */
   public String getClassName() {
      return "AssetQuerySandbox";
   }

   /**
    * Make a copy of this scope.
    */
   @Override
   public Object clone() {
      try {
         AssetQueryScope obj = (AssetQueryScope) super.clone();
         return obj;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   private static Object NOT_TABLE = new String("NOT_TABLE");
   // stands for a member stored as null, which a ConcurrentHashMap cannot hold
   private static final Object NULL_VALUE = new Object();
   private static final String PARAMETER = "parameter";
   private final AssetQueryScope shared; // null unless this is a per-query view
   private int mode;
   private AssetQuerySandbox box;
   // concurrent: reached by several script threads without a common lock (bug #76960)
   private Map<String, Object> tablemap = new ConcurrentHashMap<>();
   private final Map<String, Object> members = new ConcurrentHashMap<>();
   // volatile for safe publication (see ViewsheetScope.parentScope)
   private volatile ScriptScope parentScope;

   private static final Logger LOG =
      LoggerFactory.getLogger(AssetQueryScope.class);
}
