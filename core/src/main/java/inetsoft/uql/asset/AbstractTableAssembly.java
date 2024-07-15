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
package inetsoft.uql.asset;

import inetsoft.mv.RuntimeMV;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.asset.internal.ScriptIterator.ScriptListener;
import inetsoft.uql.asset.internal.ScriptIterator.Token;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.util.SQLIterator;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.ws.DependencyType;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.PrintWriter;
import java.lang.ref.SoftReference;
import java.util.List;
import java.util.*;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Abstract table assembly implements most methods defined in
 * <tt>TableAssembly</tt>.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public abstract class AbstractTableAssembly extends AbstractWSAssembly implements TableAssembly {
   /**
    * Constructor.
    */
   public AbstractTableAssembly() {
      super();

      preconds = new ConditionList();
      postconds = new ConditionList();
      mvUpdatePreConds = new ConditionList();
      mvUpdatePostConds = new ConditionList();
      mvDeletePreConds = new ConditionList();
      mvDeletePostConds = new ConditionList();
      topns = new ConditionList();
      ginfo = new AggregateInfo();
      sinfo = new SortInfo();
   }

   /**
    * Constructor.
    */
   public AbstractTableAssembly(Worksheet ws, String name) {
      super(ws, name);

      preconds = new ConditionList();
      postconds = new ConditionList();
      mvUpdatePreConds = new ConditionList();
      mvUpdatePostConds = new ConditionList();
      mvDeletePreConds = new ConditionList();
      mvDeletePostConds = new ConditionList();
      topns = new ConditionList();
      ginfo = new AggregateInfo();
      sinfo = new SortInfo();
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected WSAssemblyInfo createInfo() {
      return new TableAssemblyInfo();
   }

   /**
    * Get the table assembly info.
    * @return the table assembly info of the table assembly.
    */
   @Override
   public TableAssemblyInfo getTableInfo() {
      return (TableAssemblyInfo) info;
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Worksheet.TABLE_ASSET;
   }

   /**
    * Get the minimum size.
    * @return the minimum size of the assembly.
    */
   @Override
   public Dimension getMinimumSize() {
      return getMinimumSize(false);
   }

   /**
    * Get the minimum size.
    * @param embedded <tt>true</tt> to embed the table assembly.
    * @return the minimum size of the assembly.
    */
   @Override
   public Dimension getMinimumSize(boolean embedded) {
      int width = 0;
      boolean pub = isRuntime() || embedded || isAggregate();
      ColumnSelection selection = getColumnSelection(pub);

      for(int i = 0; i < selection.getAttributeCount(); i++) {
         ColumnRef ref = (ColumnRef) selection.getAttribute(i);

         if((isRuntime() || isLiveData()) && !ref.isVisible()) {
            continue;
         }

         width += ref.getWidth();
      }

      width = Math.max(AssetUtil.defw, width);
      return new Dimension(width, 3 * AssetUtil.defh);
   }

   /**
    * Set the size.
    * @param size the specified size.
    */
   @Override
   public void setPixelSize(Dimension size) {
      Dimension msize = getMinimumSize();
      ColumnSelection sel = getColumnSelection(true);

      // if the table size is less than the total column width, try to reduce
      // the last column size
      if(size.width < msize.width && sel.getAttributeCount() > 0) {
         ColumnRef ref = null;

         // find the last visible column
         for(int i = sel.getAttributeCount() - 1; i >= 0; i--) {
            ColumnRef col = (ColumnRef) sel.getAttribute(i);

            if(col.isVisible()) {
               ref = col;
               break;
            }
         }

         if(ref != null && ref.getWidth() > 1) {
            int nw = ref.getWidth() - (msize.width - size.width);
            nw = Math.max(1, nw);
            ref.setWidth(nw);

            // sync the size of the column on the internal list
            ColumnSelection columns = getColumnSelection(false);
            int index = columns.indexOfAttribute(ref);

            if(index >= 0) {
               ref = (ColumnRef) columns.getAttribute(index);
               ref.setWidth(nw);
            }
         }
      }

      super.setPixelSize(size);
   }

   /**
    * Update the assembly.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   @Override
   public boolean update() {
      if(!super.update()) {
         return false;
      }

      ConditionListWrapper conds = ConditionUtil.updateConditionListWrapper(preconds, ws);

      if(conds == null) {
         return false;
      }

      preconds = conds;

      conds = ConditionUtil.updateConditionListWrapper(postconds, ws);

      if(conds == null) {
         return false;
      }

      postconds = conds;

      conds = ConditionUtil.updateConditionListWrapper(topns, ws);

      if(conds == null) {
         return false;
      }

      topns = conds;

      conds = ConditionUtil.updateConditionListWrapper(mvUpdatePreConds, ws);

      if(conds == null) {
         return false;
      }

      mvUpdatePreConds = conds;
      conds = ConditionUtil.updateConditionListWrapper(mvUpdatePostConds, ws);

      if(conds == null) {
         return false;
      }

      mvUpdatePostConds = conds;
      conds = ConditionUtil.updateConditionListWrapper(mvDeletePreConds, ws);

      if(conds == null) {
         return false;
      }

      mvDeletePreConds = conds;
      conds = ConditionUtil.updateConditionListWrapper(mvDeletePostConds, ws);

      if(conds == null) {
         return false;
      }

      mvDeletePostConds = conds;

      if(!ginfo.update(ws)) {
         return false;
      }

      return true;
   }

   /**
    * Clear cache.
    */
   @Override
   public void clearCache() {
      // do nothing
   }

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   @Override
   public void replaceVariables(VariableTable vars) {
      preconds.replaceVariables(vars);
      postconds.replaceVariables(vars);
      topns.replaceVariables(vars);
      mvUpdatePreConds.replaceVariables(vars);
      mvUpdatePostConds.replaceVariables(vars);
      mvDeletePreConds.replaceVariables(vars);
      mvDeletePostConds.replaceVariables(vars);
      ginfo.replaceVariables(vars);
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      List<UserVariable> list = new ArrayList<>();
      UserVariable[] vars = preconds.getAllVariables();
      mergeVariables(list, vars);

      if(preconds0 != null) {
         vars = preconds0.getAllVariables();
         mergeVariables(list, vars);
      }

      vars = postconds.getAllVariables();
      mergeVariables(list, vars);

      if(postconds0 != null) {
         vars = postconds0.getAllVariables();
         mergeVariables(list, vars);
      }

      vars = topns.getAllVariables();
      mergeVariables(list, vars);

      if(topns0 != null) {
         vars = topns0.getAllVariables();
         mergeVariables(list, vars);
      }

      vars = mvUpdatePreConds.getAllVariables();
      mergeVariables(list, vars);

      vars = mvUpdatePostConds.getAllVariables();
      mergeVariables(list, vars);

      vars = mvDeletePreConds.getAllVariables();
      mergeVariables(list, vars);

      vars = mvDeletePostConds.getAllVariables();
      mergeVariables(list, vars);

      vars = ginfo.getAllVariables();
      mergeVariables(list, vars);

      vars = getExpressionVariables();
      mergeVariables(list, vars);

      try {
         // optimization
         if(srcvars == null) {
            srcvars = AssetUtil.getSourceVariables(this);
         }

         mergeVariables(list, srcvars);
      }
      catch(Exception ex) {
         LOG.error("Failed to merge variables in table: " + this, ex);
      }

      vars = new UserVariable[list.size()];
      list.toArray(vars);
      return vars;
   }

   /**
    * Get expression column variables.
    */
   private UserVariable[] getExpressionVariables() {
      Set<UserVariable> set = new HashSet<>();
      ColumnSelection columns = getColumnSelection();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);
         DataRef ref = column.getDataRef();

         if(ref instanceof ExpressionRef) {
            String exp = ((ExpressionRef) ref).getExpression();

            if(column.isSQL()) {
               getSQLExpressionVariables(exp, set);
            }
            else {
               getScriptExpressionVariables(exp, set);
            }
         }
      }

      UserVariable[] vars = new UserVariable[set.size()];
      set.toArray(vars);
      return vars;
   }

   /**
    * Get script expression column variables.
    */
   private void getScriptExpressionVariables(String exp, Set<UserVariable> set) {
      final Set<UserVariable> variables = new HashSet<>();
      ScriptIterator iterator = new ScriptIterator(exp);
      ScriptListener listener = new ScriptListener() {
         @Override
         public void nextElement(Token token, Token pref, Token cref) {
            if(token.isRef() && cref != null && cref.isRef() && pref == null &&
               "parameter".equals(token.val))
            {
               Assembly[] assemblies = ws.getAssemblies();

               for(Assembly assembly : assemblies) {
                  if(assembly instanceof VariableAssembly) {
                     UserVariable uvar = ((VariableAssembly) assembly).getVariable();

                     if(uvar != null && uvar.getName().equals(cref.val)) {
                        variables.add(uvar);
                     }
                  }
               }
            }
         }
      };

      iterator.addScriptListener(listener);
      iterator.iterate();
      set.addAll(variables);
   }

   /**
    * Get sql expression column variables.
    */
   private void getSQLExpressionVariables(String exp, Set<UserVariable> set) {
      Set<VariableAssembly> dependedAssemblies = getSQLExpressionDependedAssemblies(exp);
      set.addAll(dependedAssemblies.stream()
                    .map(VariableAssembly::getVariable)
                    .collect(Collectors.toSet()));
   }

   /**
    * Merge a variable array to list.
    * @param list the specified list.
    * @param vars the specified variable array.
    */
   protected void mergeVariables(List<UserVariable> list, UserVariable[] vars) {
      for(int i = 0; i < vars.length; i++) {
         if(!AssetUtil.containsVariable(list, vars[i])) {
            list.add(vars[i]);
         }
      }
   }

   /**
    * Get the expression width.
    * @param embedded <tt>true</tt> to embed the table assembly.
    * @return the expression width of the table assembly.
    */
   protected int getExpressionWidth(boolean embedded) {
      int width = 0;
      ColumnSelection selection = getColumnSelection(embedded || isAggregate());

      for(int i = 0; i < selection.getAttributeCount(); i++) {
         ColumnRef ref = (ColumnRef) selection.getAttribute(i);

         if(ref.isExpression()) {
            width += ref.getWidth();
         }
      }

      return width;
   }

   /**
    * Check if is a crosstab.
    * @return <tt>true</tt> if is a crosstab, <tt>false</tt> otherwise.
    */
   public boolean isCrosstab() {
      AggregateInfo info = getAggregateInfo();
      return !info.isEmpty() && info.isCrosstab();
   }

   /**
    * Get the column selection.
    * @return the column selection of the table assembly.
    */
   @Override
   public ColumnSelection getColumnSelection() {
      return getColumnSelection(false);
   }

   /**
    * Get the column selection.
    * @param pub <tt>true</tt> indicates the public column selection,
    * <tt>false</tt> otherwise.
    * @return the column selection of the table assembly.
    */
   @Override
   public ColumnSelection getColumnSelection(boolean pub) {
      return pub ? getTableInfo().getPublicColumnSelection() :
         getTableInfo().getPrivateColumnSelection();
   }

   /**
    * Set the column selection.
    * @param selection the specified selection.
    */
   @Override
   public void setColumnSelection(ColumnSelection selection) {
      setColumnSelection(selection, false);
   }

   /**
    * Reset column selection.
    */
   @Override
   public void resetColumnSelection() {
      ColumnSelection columns = getColumnSelection(false);

      if(columns.getAttributeCount() > 0) {
         setColumnSelection(columns, false);
      }
   }

   /**
    * Set the column selection.
    * @param selection the specified selection.
    * @param pub <tt>true</tt> indicates the public column selection,
    * <tt>false</tt> otherwise.
    */
   @Override
   public void setColumnSelection(ColumnSelection selection, boolean pub) {
      if(pub) {
         setPublicColumnSelection(selection);
      }
      else {
         getTableInfo().setPrivateColumnSelection(selection);
         ginfo.validate(selection);
         sinfo.validate(selection);
         AggregateInfo aggregateInfo =
            getRealAggregateInfo() == null ? ginfo : getRealAggregateInfo();

         // generate public column selection if not a crosstab
         if(!isCrosstab()) {
            ColumnSelection ocolumns = new ColumnSelection();

            for(int i = 0; i < selection.getAttributeCount(); i++) {
               ColumnRef column = (ColumnRef) selection.getAttribute(i);

               if(!column.isVisible()) {
                  continue;
               }

               column = (ColumnRef) column.clone();
               GroupRef gref = aggregateInfo.getGroup(column);

               if(gref != null) {
                  column.setDataType(gref.getDataType());
                  ocolumns.addAttribute(column, false);
               }
               else {
                  AggregateRef aref = aggregateInfo.getAggregate(column);

                  if(aref != null) {
                     column.setDataType(aref.getDataType());
                     ocolumns.addAttribute(column, false);
                  }
                  else if(aggregateInfo.isEmpty() || isColumnUsed(column)) {
                     ocolumns.addAttribute(column, false);
                  }
               }

               if(StringUtils.isEmpty(column.getAttribute())) {
                  column.setAlias("Column [" + i + "]");
               }
            }

            ocolumns.setProperty("null", "false");
            setPublicColumnSelection(ocolumns);
         }

         PropertyChangeListener listener = getListener();

         if(listener != null) {
            listener.propertyChange(new PropertyChangeEvent(this,
               columnPropertyName, selection, selection));
         }
      }
   }

   protected void setPublicColumnSelection(ColumnSelection selection) {
      getTableInfo().setPublicColumnSelection(selection);
   }

   /**
    * Check if the column is used.
    */
   protected boolean isColumnUsed(ColumnRef ref) {
      return false;
   }

   /**
    * Set column changed property name.
    */
   @Override
   public void setColumnPropertyName(String name) {
      this.columnPropertyName = name;
   }

   /**
    * Check if is a plain table.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isPlain() {
      return !isCrosstab();
   }

   /**
    * Get the preprocess runtime condition list.
    * @return the preprocess runtime condition list of the table assembly.
    */
   @Override
   public ConditionListWrapper getPreRuntimeConditionList() {
      return preconds0;
   }

   /**
    * Set the preprocess runtime condition list.
    * @param conds the specified preprocess runtime condition list.
    */
   @Override
   public void setPreRuntimeConditionList(ConditionListWrapper conds) {
      this.preconds0 = conds;
   }

   /**
    * Get the postprocess runtime condition list.
    * @return the postprocess runtime condition list of the table assembly.
    */
   @Override
   public ConditionListWrapper getPostRuntimeConditionList() {
      return postconds0;
   }

   /**
    * Set the postprocess runtime condition list.
    * @param conds the specified postprocess runtime condition list.
    */
   @Override
   public void setPostRuntimeConditionList(ConditionListWrapper conds) {
      this.postconds0 = conds;
   }

   /**
    * Get the ranking runtime condition list.
    * @return the ranking runtime condition list of the table assembly.
    */
   @Override
   public ConditionListWrapper getRankingRuntimeConditionList() {
      return topns0;
   }

   /**
    * Set the ranking runtime condition list.
    * @param conds the specified ranking runtime condition list.
    */
   @Override
   public void setRankingRuntimeConditionList(ConditionListWrapper conds) {
      this.topns0 = conds;
   }

   /**
    * Get the preprocess condition list.
    * @return the preprocess condition list of the table assembly.
    */
   @Override
   public ConditionListWrapper getPreConditionList() {
      return preconds;
   }

   /**
    * Set the preprocess condition list.
    * @param conds the specified preprocess condition list.
    */
   @Override
   public void setPreConditionList(ConditionListWrapper conds) {
      this.preconds = (conds == null) ? new ConditionList() : conds;
   }

   /**
    * Get the postprocess condition list.
    * @return the postprocess condition list of the table assembly..
    */
   @Override
   public ConditionListWrapper getPostConditionList() {
      return postconds;
   }

   /**
    * Set the postprocess condition list.
    * @param conds the specified postprocess condition list.
    */
   @Override
   public void setPostConditionList(ConditionListWrapper conds) {
      this.postconds = (conds == null) ? new ConditionList() : conds;
   }

   /**
    * Get the ranking condition list.
    * @return the ranking condition list of the table assembly..
    */
   @Override
   public ConditionListWrapper getRankingConditionList() {
      return topns;
   }

   /**
    * Set the ranking condition list.
    * @param conds the specified ranking condition list.
    */
   @Override
   public void setRankingConditionList(ConditionListWrapper conds) {
      this.topns = conds;
   }

   /**
    * Get mv condition, merged mv update and delete conditions.
    */
   @Override
   public ConditionList getMVConditionList() {
      List list = new ArrayList();
      list.add(getMVUpdateConditionList());
      list.add(getMVDeleteConditionList());
      return ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
   }

   /**
    * Get mv update condition, merged mv update pre and post conditions.
    */
   @Override
   public ConditionList getMVUpdateConditionList() {
      List list = new ArrayList();
      list.add(cloneConditionList(mvUpdatePreConds));
      list.add(cloneConditionList(mvUpdatePostConds));
      return ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
   }

   /**
    * Get mv delete condition, merged with mv delete pre and post conditions.
    */
   @Override
   public ConditionListWrapper getMVDeleteConditionList() {
      List list = new ArrayList();
      list.add(cloneConditionList(mvDeletePreConds));
      list.add(cloneConditionList(mvDeletePostConds));
      return ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
   }

   /**
    * Get mv update pre condition.
    */
   @Override
   public ConditionListWrapper getMVUpdatePreConditionList() {
      return mvUpdatePreConds;
   }

   /**
    * Set mv update pre condition.
    */
   @Override
   public void setMVUpdatePreConditionList(ConditionListWrapper conds) {
      mvUpdatePreConds = conds;
   }

   /**
    * Get mv update post condition.
    */
   @Override
   public ConditionListWrapper getMVUpdatePostConditionList() {
      return mvUpdatePostConds;
   }

   /**
    * Set mv update post condition.
    */
   @Override
   public void setMVUpdatePostConditionList(ConditionListWrapper conds) {
      mvUpdatePostConds = conds;
   }

   /**
    * Get mv delete pre condition.
    */
   @Override
   public ConditionListWrapper getMVDeletePreConditionList() {
      return mvDeletePreConds;
   }

   /**
    * Set mv delete pre condition.
    */
   @Override
   public void setMVDeletePreConditionList(ConditionListWrapper conds) {
      mvDeletePreConds = conds;
   }

   /**
    * Get mv delete post condition.
    */
   @Override
   public ConditionListWrapper getMVDeletePostConditionList() {
      return mvDeletePostConds;
   }

   /**
    * Set mv delete post condition.
    */
   @Override
   public void setMVDeletePostConditionList(ConditionListWrapper conds) {
      mvDeletePostConds = conds;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean isMVForceAppendUpdates() {
      return mvForceAppendUpdates;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setMVForceAppendUpdates(boolean mvForceAppendUpdates) {
      this.mvForceAppendUpdates = mvForceAppendUpdates;
   }

   /**
    * Get the cloned condition list.
    */
   private ConditionListWrapper cloneConditionList(ConditionListWrapper conds) {
      return conds == null ? null : (ConditionListWrapper) conds.clone();
   }

   /**
    * Get the group info.
    * @return the group info of the table assembly.
    */
   @Override
   public AggregateInfo getAggregateInfo() {
      return ginfo;
   }

   /**
    * Set the group info.
    * @param info the specified group info.
    */
   @Override
   public void setAggregateInfo(AggregateInfo info) {
      this.ginfo = info;
   }

   /**
    * Get the sort info.
    * @return the sort info of the table assembly.
    */
   @Override
   public SortInfo getSortInfo() {
      return sinfo;
   }

   /**
    * Set the sort info.
    * @param info the specified sort info.
    */
   @Override
   public void setSortInfo(SortInfo info) {
      this.sinfo = info;
      srcvars = null;
   }

   /**
    * Get the source info.
    * @return the source info of the bound table assembly.
    */
   public SourceInfo getSourceInfo() {
      return null;
   }

   /**
    * Set the source info.
    * @param source the specified source info.
    */
   public void setSourceInfo(SourceInfo source) {
      // will be override
   }

   /**
    * Get the maximum rows.
    * @return the maximum rows of the table assembly.
    */
   @Override
   public int getMaxRows() {
      return getTableInfo().getMaxRows();
   }

   /**
    * Set the maximum rows.
    * @param row the specified maximum rows.
    */
   @Override
   public void setMaxRows(int row) {
      getTableInfo().setMaxRows(row);
   }

   /**
    * Get the maximum display rows.
    * @return the maximum display rows of the table assembly.
    */
   @Override
   public int getMaxDisplayRows() {
      return getTableInfo().getMaxDisplayRows();
   }

   /**
    * Set the maximum display rows.
    * @param row the specified maximum display rows.
    */
   @Override
   public void setMaxDisplayRows(int row) {
      getTableInfo().setMaxDisplayRows(row);
   }

   /**
    * Check if only show distinct values.
    * @return <tt>true</tt> to show distinct values only,
    * <tt>false</tt> otherwise.
    */
   @Override
   public boolean isDistinct() {
      return getTableInfo().isDistinct();
   }

   /**
    * Set the distinct option.
    * @param distinct <tt>true</tt> to show distinct values only,
    * <tt>false</tt> otherwise.
    */
   @Override
   public void setDistinct(boolean distinct) {
      getTableInfo().setDistinct(distinct);
   }

   /**
    * Check if show live data.
    * @return <tt>true</tt> to show live data, <tt>false</tt> to show metadata.
    */
   @Override
   public boolean isLiveData() {
      return getTableInfo().isLiveData();
   }

   /**
    * Set the live data option.
    * @param live <tt>true</tt> to show live data, <tt>false</tt>
    * to show metadata.
    */
   @Override
   public void setLiveData(boolean live) {
      getTableInfo().setLiveData(live);
   }

   /**
    * Check if the table is in runtime mode.
    * @return <tt>true</tt> if in runtime mode, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isRuntime() {
      return getTableInfo().isRuntime();
   }

   /**
    * Set the runtime mode.
    * @param runtime <tt>true</tt> if in runtime mode, <tt>false</tt> otherwise.
    */
   @Override
   public void setRuntime(boolean runtime) {
      getTableInfo().setRuntime(runtime);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean isEditMode() {
      return getTableInfo().isEditMode();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setEditMode(boolean editMode) {
      getTableInfo().setEditMode(editMode);
   }

   /**
    * Check if is an aggregate.
    * @return <tt>true</tt> if is an aggregate.
    */
   @Override
   public boolean isAggregate() {
      if(getAggregateInfo().isEmpty()) {
         return false;
      }

      return getTableInfo().isAggregate();
   }

   /**
    * Set the aggregate flag.
    * @param aggregate <tt>true</tt> if is an aggregate.
    */
   @Override
   public void setAggregate(boolean aggregate) {
      getTableInfo().setAggregate(aggregate);
   }

   /**
    * Check if the sql query is mergeable.
    * @return <tt>true</tt> if the sql query is mergeable, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean isSQLMergeable() {
      return getTableInfo().isSQLMergeable();
   }

   /**
    * Set whether the sql query is mergeable.
    * @param mergeable <tt>true</tt> if the sql query is mergeable,
    * <tt>false</tt> otherwise.
    */
   @Override
   public void setSQLMergeable(boolean mergeable) {
      getTableInfo().setSQLMergeable(mergeable);
   }

   /**
    * Check if the worksheet is block..
    * @return <tt>true</tt>  if the worksheet is block., <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean isVisibleTable() {
      return getTableInfo().isVisibleTable();
   }

   /**
    * Set whether the worksheet is block..
    * @param visibleTable <tt>true</tt>  if the worksheet is block.,
    * <tt>false</tt> otherwise.
    */
   @Override
   public void setVisibleTable(boolean visibleTable) {
      getTableInfo().setVisibleTable(visibleTable);
   }

   /**
    * Check if runtime is selected.
    * @return true if the runtime is selected, false otherwise
    */
   @Override
   public boolean isRuntimeSelected() {
      return getTableInfo().isRuntimeSelected();
   }

   /**
    * Set whether runtime is selected.
    * @param runtimeSelected true if runtime is selected on the table, false otherwise
    */
   @Override
   public void setRuntimeSelected(boolean runtimeSelected) {
      getTableInfo().setRuntimeSelected(runtimeSelected);
   }

   /**
    * Get the table real design aggregateInfo.
    * @return
    */
   public AggregateInfo getRealAggregateInfo() {
      return realAggregateInfo;
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(!preconds.isEmpty()) {
         writer.println("<preConditions>");
         preconds.writeXML(writer);
         writer.println("</preConditions>");
      }

      String mv = getProperty("mv.name");
      boolean compact = Tool.isCompact();

      // write pre runtime condition list as well to execute query
      // in a remote server
      if(!compact && mv != null && mv.length() > 0 && preconds0 != null) {
         writer.println("<preRuntimeConditions>");
         preconds0.writeXML(writer);
         writer.println("</preRuntimeConditions>");
      }

      if(!postconds.isEmpty()) {
         writer.println("<postConditions>");
         postconds.writeXML(writer);
         writer.println("</postConditions>");
      }

      // write post runtime condition list as well to execute query
      // in a remote server
      if(!compact && mv != null && mv.length() > 0 && postconds0 != null) {
         writer.println("<postRuntimeConditions>");
         postconds0.writeXML(writer);
         writer.println("</postRuntimeConditions>");
      }

      if(!compact && mv != null && mv.length() > 0 && topns0 != null) {
         writer.println("<rankingRuntimeConditions>");
         topns0.writeXML(writer);
         writer.println("</rankingRuntimeConditions>");
      }

      if(!topns.isEmpty()) {
         writer.println("<topNConditions>");
         topns.writeXML(writer);
         writer.println("</topNConditions>");
      }

      if(!mvUpdatePreConds.isEmpty()) {
         writer.println("<mvUpdatePreConds>");
         mvUpdatePreConds.writeXML(writer);
         writer.println("</mvUpdatePreConds>");
      }

      if(!mvUpdatePostConds.isEmpty()) {
         writer.println("<mvUpdatePostConds>");
         mvUpdatePostConds.writeXML(writer);
         writer.println("</mvUpdatePostConds>");
      }

      if(!mvDeletePreConds.isEmpty()) {
         writer.println("<mvDeletePreConds>");
         mvDeletePreConds.writeXML(writer);
         writer.println("</mvDeletePreConds>");
      }

      if(!mvDeletePostConds.isEmpty()) {
         writer.println("<mvDeletePostConds>");
         mvDeletePostConds.writeXML(writer);
         writer.println("</mvDeletePostConds>");
      }

      writer.println(
         "<mvForceAppendUpdates>" + mvForceAppendUpdates +
         "</mvForceAppendUpdates>");

      if(!ginfo.isEmpty()) {
         writer.println("<groupInfo>");
         ginfo.writeXML(writer);
         writer.println("</groupInfo>");
      }

      if(!sinfo.isEmpty()) {
         writer.println("<sortInfo>");
         sinfo.writeXML(writer);
         writer.println("</sortInfo>");
      }

      Enumeration<Object> keys = prop.keys();

      while(keys.hasMoreElements()) {
         String key = (String) keys.nextElement();
         Object value = prop.get(key);
         writer.println("<property><name><![CDATA[" + key + "]]></name>" +
            "<value><![CDATA[" + value + "]]></value></property>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element prenode = Tool.getChildNodeByTagName(elem, "preConditions");

      if(prenode != null) {
         prenode = Tool.getFirstChildNode(prenode);
         preconds.parseXML(prenode);
      }

      Element prertnode =
         Tool.getChildNodeByTagName(elem, "preRuntimeConditions");

      if(prertnode != null) {
         prertnode = Tool.getFirstChildNode(prertnode);
         preconds0 = new ConditionList();
         preconds0.parseXML(prertnode);
      }

      Element postrtnode =
         Tool.getChildNodeByTagName(elem, "postRuntimeConditions");

      if(postrtnode != null) {
         postrtnode = Tool.getFirstChildNode(postrtnode);
         postconds0 = new ConditionList();
         postconds0.parseXML(postrtnode);
      }

      Element topnrnode =
         Tool.getChildNodeByTagName(elem, "rankingRuntimeConditions");

      if(topnrnode != null) {
         topnrnode = Tool.getFirstChildNode(topnrnode);
         topns0 = new ConditionList();
         topns0.parseXML(topnrnode);
      }

      Element postnode = Tool.getChildNodeByTagName(elem, "postConditions");

      if(postnode != null) {
         postnode = Tool.getFirstChildNode(postnode);
         postconds.parseXML(postnode);
      }

      Element topnnode = Tool.getChildNodeByTagName(elem, "topNConditions");

      if(topnnode != null) {
         topnnode = Tool.getFirstChildNode(topnnode);
         topns.parseXML(topnnode);
      }

      Element node = Tool.getChildNodeByTagName(elem, "mvUpdatePreConds");

      if(node != null) {
         node = Tool.getFirstChildNode(node);
         mvUpdatePreConds.parseXML(node);
      }

      node = Tool.getChildNodeByTagName(elem, "mvUpdatePostConds");

      if(node != null) {
         node = Tool.getFirstChildNode(node);
         mvUpdatePostConds.parseXML(node);
      }

      node = Tool.getChildNodeByTagName(elem, "mvDeletePreConds");

      if(node != null) {
         node = Tool.getFirstChildNode(node);
         mvDeletePreConds.parseXML(node);
      }

      node = Tool.getChildNodeByTagName(elem, "mvDeletePostConds");

      if(node != null) {
         node = Tool.getFirstChildNode(node);
         mvDeletePostConds.parseXML(node);
      }

      node = Tool.getChildNodeByTagName(elem, "mvForceAppendUpdates");

      if(node != null) {
         mvForceAppendUpdates = "true".equals(Tool.getValue(node));
      }

      Element gnode = Tool.getChildNodeByTagName(elem, "groupInfo");

      if(gnode != null) {
         gnode = Tool.getFirstChildNode(gnode);
         ginfo.parseXML(gnode);
      }

      Element sortnode = Tool.getChildNodeByTagName(elem, "sortInfo");

      if(sortnode != null) {
         sortnode = Tool.getFirstChildNode(sortnode);
         sinfo.parseXML(sortnode);
      }

      NodeList nlist = Tool.getChildNodesByTagName(elem, "property");

      for(int i = 0; i < nlist.getLength(); i++) {
         Element tag2 = (Element) nlist.item(i);
         Element nnode = Tool.getChildNodeByTagName(tag2, "name");
         Element vnode = Tool.getChildNodeByTagName(tag2, "value");
         String name = Tool.getValue(nnode);
         String value = Tool.getValue(vnode);

         if(name != null && value != null) {
            setProperty(name, value);
         }
      }
   }

   /**
    * Rename the conditon list wrapper.
    * @param conds the specified condition list wrapper.
    * @param oname the specified old name.
    * @param nname the specified new name.
    * @param ws the associated worksheet.
    */
   protected void renameConditionListWrapper(ConditionListWrapper conds,
                                             String oname, String nname,
                                             Worksheet ws) {
      ConditionUtil.renameConditionListWrapper(conds, oname, nname, ws);
   }

   /**
    * Set the worksheet.
    * @param ws the specified worksheet.
    */
   @Override
   public void setWorksheet(Worksheet ws) {
      super.setWorksheet(ws);

      setConditionListWrapperWorksheet(preconds, ws);
      setConditionListWrapperWorksheet(postconds, ws);
      setConditionListWrapperWorksheet(topns, ws);
      setConditionListWrapperWorksheet(mvUpdatePreConds, ws);
      setConditionListWrapperWorksheet(mvUpdatePostConds, ws);
      setConditionListWrapperWorksheet(mvDeletePreConds, ws);
      setConditionListWrapperWorksheet(mvDeletePostConds, ws);
   }

   /**
    * Set the worksheet of the conditon list wrapper.
    * @param conds the specified condition list wrapper.
    * @param ws the associated worksheet.
    */
   protected void setConditionListWrapperWorksheet(ConditionListWrapper conds,
                                                   Worksheet ws)
   {
      if(conds == null || conds.isEmpty()) {
         return;
      }

      int size = conds.getConditionSize();

      for(int i = 0; i < size; i += 2) {
         ConditionItem item = conds.getConditionItem(i);
         XCondition condition = item.getXCondition();

         if(condition instanceof DateRangeAssembly) {
            DateRangeAssembly assembly = (DateRangeAssembly) condition;
            assembly.setWorksheet(ws);
         }
      }
   }

   /**
    * Get the assemblies depended on.
    * @param set the set stores the assemblies depended on.
    */
   @Override
   public void getDependeds(Set<AssemblyRef> set) {
      getExpressionDependeds(set);
      AssetUtil.getConditionDependeds(ws, preconds, set);
      AssetUtil.getConditionDependeds(ws, postconds, set);
      AssetUtil.getConditionDependeds(ws, topns, set);
      AssetUtil.getConditionDependeds(ws, mvUpdatePreConds, set);
      AssetUtil.getConditionDependeds(ws, mvUpdatePostConds, set);
      AssetUtil.getConditionDependeds(ws, mvDeletePreConds, set);
      AssetUtil.getConditionDependeds(ws, mvDeletePostConds, set);
      ginfo.getDependeds(set);
   }

   @Override
   public void getAugmentedDependeds(Map<String, Set<DependencyType>> dependeds) {
      Set<AssemblyRef> set = new HashSet<>();

      getExpressionDependeds(set);
      addToDependencyTypes(dependeds, set, DependencyType.EXPRESSION);
      set.clear();

      AssetUtil.getConditionDependeds(ws, preconds, set);
      AssetUtil.getConditionDependeds(ws, postconds, set);
      AssetUtil.getConditionDependeds(ws, topns, set);
      AssetUtil.getConditionDependeds(ws, mvUpdatePreConds, set);
      AssetUtil.getConditionDependeds(ws, mvUpdatePostConds, set);
      AssetUtil.getConditionDependeds(ws, mvDeletePreConds, set);
      AssetUtil.getConditionDependeds(ws, mvDeletePostConds, set);

      Set<AssemblyRef> conditionVariables = set.stream().filter(
         (ref) -> ref.getEntry().getType() == Worksheet.VARIABLE_ASSET).collect(
         Collectors.toSet());
      Set<AssemblyRef> conditionSubqueryTables = set.stream().filter(
         (ref) -> ref.getEntry().getType() == Worksheet.TABLE_ASSET).collect(
         Collectors.toSet());
      addToDependencyTypes(dependeds, conditionVariables, DependencyType.VARIABLE_FILTER );
      addToDependencyTypes(dependeds, conditionSubqueryTables, DependencyType.SUBQUERY_FILTER);
      set.clear();

      ginfo.getDependeds(set);
      addToDependencyTypes(dependeds, set, DependencyType.GROUPING);
      set.clear();
   }

   /**
    * Get expression assemblies depended on.
    * @param set the set stores the assemblies depended on.
    */
   public void getExpressionDependeds(Set<AssemblyRef> set) {
      ColumnSelection columns = getColumnSelection();
      List<ColumnRef> exprs = new ArrayList<>();
      StringBuilder keyBuilder = new StringBuilder();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);
         DataRef ref = column.getDataRef();

         if(ref instanceof ExpressionRef) {
            exprs.add(column);
            keyBuilder.append(((ExpressionRef) ref).getExpression());
            keyBuilder.append(column.isSQL());
         }
      }

      for(Assembly assembly : getWorksheet().getAssemblies()) {
         if(assembly instanceof ScriptedTableAssembly) {
            String script = ((ScriptedTableAssembly) assembly).getInputScript();

            if(script != null) {
               keyBuilder.append('\n').append(script);
            }

            script = ((ScriptedTableAssembly) assembly).getOutputScript();

            if(script != null) {
               keyBuilder.append('\n').append(script);
            }
         }
      }

      String key = keyBuilder.toString();

      if(expressionDeps == null || !key.equals(expressionKey)) {
         Set<AssemblyRef> deps = new HashSet<>();
         deps.addAll(ScriptedTableDependencies.getInputReferences(getWorksheet(), this));

         for(ColumnRef column : exprs) {
            String exp = ((ExpressionRef) column.getDataRef()).getExpression();

            if(column.isSQL()) {
               getSQLExpressionDependeds(exp, deps);
            }
            else {
               getScriptExpressionDependeds(exp, deps);
            }
         }

         for(Assembly assembly : getWorksheet().getAssemblies()) {
            if(!assembly.equals(this)) {
               boolean referenced =
                  ScriptedTableDependencies.getOutputReferences(getWorksheet(), assembly).stream()
                     .map(AssemblyRef::getEntry)
                     .anyMatch(r -> r.equals(getAssemblyEntry()));

               if(referenced) {
                  deps.add(new AssemblyRef(assembly.getAssemblyEntry()));
               }
            }
         }

         expressionKey = key;
         expressionDeps = deps;
      }

      set.addAll(expressionDeps);
   }

   /**
    * Get script expression assemblies depended on.
    * @param exp the specified expression.
    * @param set the set stores the assemblies depended on.
    */
   private void getScriptExpressionDependeds(String exp, Set<AssemblyRef> set) {
      Set<Assembly> dependedAssemblies = getScriptExpressionDependedAssemblies(exp);
      set.addAll(dependedAssemblies.stream()
                    .map((assembly) -> new AssemblyRef(assembly.getAssemblyEntry()))
                    .collect(Collectors.toSet()));
   }

   /**
    * Get script expression assemblies depended on.
    * @param exp the specified expression
    * @return a set containing the depended assemblies
    */
   private Set<Assembly> getScriptExpressionDependedAssemblies(String exp) {
      Set<Assembly> dependeds = new HashSet<>();
      ScriptIterator iterator = new ScriptIterator(exp);
      ScriptListener listener = new ScriptListener() {
         @Override
         public void nextElement(Token token, Token pref, Token cref) {
            if(token.isRef() && !FIELD.equals(token.val) &&
               (pref == null || !FIELD.equals(pref.val)))
            {
               Assembly assembly = ws.getAssembly(token.val);

               if(assembly instanceof TableAssembly &&
                  !assembly.equals(AbstractTableAssembly.this))
               {
                  dependeds.add(assembly);
               }
            }

            //bug1327952910298, make variable has depended.
            if(token.isRef() && cref != null && cref.isRef() && pref == null &&
               "parameter".equals(token.val))
            {
               Assembly[] assemblies = ws.getAssemblies();

               for(Assembly assembly : assemblies) {
                  if(assembly instanceof VariableAssembly) {
                     UserVariable uvar = ((VariableAssembly) assembly).getVariable();

                     if(uvar != null && uvar.getName().equals(cref.val)) {
                        dependeds.add(assembly);
                     }
                  }
               }
            }
         }
      };

      iterator.addScriptListener(listener);
      iterator.iterate();
      return dependeds;
   }

   /**
    * Get sql expression depended assemblies.
    * @param exp the specified expression.
    * @param set the set which stores the depended assemblies.
    */
   private void getSQLExpressionDependeds(String exp, Set<AssemblyRef> set) {
      Set<VariableAssembly> dependedAssemblies = getSQLExpressionDependedAssemblies(exp);
      set.addAll(dependedAssemblies.stream()
                    .map((assembly) -> new AssemblyRef(assembly.getAssemblyEntry()))
                    .collect(Collectors.toSet()));
   }

   /**
    * Get sql expression depended assemblies.
    * @param exp the specified expression
    * @return a set containing the depended assemblies
    */
   private Set<VariableAssembly> getSQLExpressionDependedAssemblies(String exp) {
      Set<VariableAssembly> dependends = new HashSet<>();
      SQLIterator iterator = new SQLIterator(exp);
      SQLIterator.SQLListener listener = new SQLIterator.SQLListener() {
         @Override
         public void nextElement(int type, String value, Object comment) {
            if(type != SQLIterator.COMMENT_ELEMENT) {
               Matcher matcher = Pattern.compile("\\$\\((.+?)\\)").matcher(value);

               while(matcher.find()) {
                  String variableName = matcher.group(1);
                  Assembly[] assemblies = ws.getAssemblies();

                  for(Assembly assembly : assemblies) {
                     if(assembly instanceof VariableAssembly) {
                        VariableAssembly variableAssembly = (VariableAssembly) assembly;
                        UserVariable uvar = variableAssembly.getVariable();

                        if(uvar != null && uvar.getName().equals(variableName)) {
                           dependends.add(variableAssembly);
                        }
                     }
                  }
               }
            }
         }
      };

      iterator.addSQLListener(listener);
      iterator.iterate();
      return dependends;
   }

   /**
    * Rename the expression assemblies depended on.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   private void renameExpressionDependeds(String oname, String nname) {
      ColumnSelection columns = getColumnSelection();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);

         if(column.isExpression()) {
            ExpressionRef exp = (ExpressionRef) column.getDataRef();

            if(exp.isExpressionEditable()) {
               String val = exp.getExpression();
               val = renameExpression(val, oname, nname);
               exp.setExpression(val);
            }
         }
      }
   }

   /**
    * Rename the expression dependeds.
    * @param exp the specified expression.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   private String renameExpression(String exp, final String oname, final String nname) {
      ScriptIterator iterator = new ScriptIterator(exp);
      final StringBuilder sb = new StringBuilder();

      ScriptListener listener = new ScriptListener() {
         @Override
         public void nextElement(Token token, Token pref, Token cref) {
            boolean fullMatch = !FIELD.equals(token.val) && oname.equals(token.val);
            boolean entityMatch = false;
            int idx = token.val != null ? token.val.lastIndexOf(".") : -1;

            if(pref != null && FIELD.equals(pref.val) && idx != -1) {
               entityMatch = Tool.equals(oname, token.val.substring(0, idx));
            }

            if(token.isRef() && !sb.toString().endsWith(".") && (fullMatch || entityMatch)) {
               String nval = entityMatch ? nname + token.val.substring(idx) : nname;

               if(Tool.isValidIdentifier(nval)) {
                  sb.append(new Token(token.type, nval, token.length));
               }
               else {
                  if(sb.length() > 0 && sb.charAt(sb.length() - 1) == '.') {
                     sb.deleteCharAt(sb.length() - 1);
                  }

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

   /**
    * Rename the assemblies depended on.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname) {
      renameExpressionDependeds(oname, nname);
      renameAggregateInfo(oname, nname);
      renameConditionListWrapper(preconds, oname, nname, ws);
      renameConditionListWrapper(postconds, oname, nname, ws);
      renameConditionListWrapper(topns, oname, nname, ws);
      renameConditionListWrapper(mvUpdatePreConds, oname, nname, ws);
      renameConditionListWrapper(mvUpdatePostConds, oname, nname, ws);
      renameConditionListWrapper(mvDeletePreConds, oname, nname, ws);
      renameConditionListWrapper(mvDeletePostConds, oname, nname, ws);
   }

   /**
    * Rename the aggregate info.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   protected void renameAggregateInfo(String oname, String nname) {
      ginfo.renameDepended(oname, nname);
   }

   /**
    * Reset the assembly.
    */
   @Override
   public void reset() {
      AssetUtil.resetCondition(preconds);
      AssetUtil.resetCondition(postconds);
      AssetUtil.resetCondition(topns);
      AssetUtil.resetCondition(mvUpdatePreConds);
      AssetUtil.resetCondition(mvUpdatePostConds);
      AssetUtil.resetCondition(mvDeletePreConds);
      AssetUtil.resetCondition(mvDeletePostConds);
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         AbstractTableAssembly assembly = (AbstractTableAssembly) super.clone();
         assembly.preconds = (ConditionListWrapper) preconds.clone();
         assembly.postconds = (ConditionListWrapper) postconds.clone();
         assembly.topns = (ConditionListWrapper) topns.clone();
         assembly.mvUpdatePreConds = (ConditionListWrapper) mvUpdatePreConds.clone();
         assembly.mvUpdatePostConds = (ConditionListWrapper) mvUpdatePostConds.clone();
         assembly.mvDeletePreConds = (ConditionListWrapper) mvDeletePreConds.clone();
         assembly.mvDeletePostConds = (ConditionListWrapper) mvDeletePostConds.clone();
         assembly.ginfo = (AggregateInfo) ginfo.clone();
         assembly.sinfo = (SortInfo) sinfo.clone();
         assembly.prop = (Properties) prop.clone();
         assembly.clearIgnored();

         if(preconds0 != null) {
            assembly.preconds0 = (ConditionListWrapper) preconds0.clone();
         }

         if(postconds0 != null) {
            assembly.postconds0 = (ConditionListWrapper) postconds0.clone();
         }

         if(topns0 != null) {
            assembly.topns0 = (ConditionListWrapper) topns0.clone();
         }

         return assembly;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   /**
    * Clear ignored properties.
    */
   private void clearIgnored() {
      for(Object key : IGNORED) {
         prop.remove(key);
      }
   }

   /**
    * Set the runtime MV.
    */
   @Override
   public void setRuntimeMV(RuntimeMV rinfo) {
      this.rinfo = rinfo;
   }

   /**
    * Get the runtime info.
    */
   @Override
   public RuntimeMV getRuntimeMV() {
      return rinfo;
   }

   /**
    * Get the value of a property.
    * @param key the specified property name.
    * @return the value of the property.
    */
   @Override
   public String getProperty(String key) {
      return (String) prop.get(key);
   }

   /**
    * Set the value a property.
    * @param key the property name.
    * @param value the property value, null to remove the property.
    */
   @Override
   public void setProperty(String key, String value) {
      if(value == null) {
         prop.remove(key);
      }
      else {
         prop.put(key, value);
      }
   }

   /**
    * Get the lastModified.
    * @return lastModified.
    */
   @Override
   public long getLastModified() {
      return lastModified;
   }

   /**
    * Set the lastModified.
    * @param lastModified
    */
   @Override
   public void setLastModified(long lastModified) {
      this.lastModified = lastModified;
   }

   /**
    * Clear property.
    * @param key the property name.
    */
   @Override
   public void clearProperty(String key) {
      prop.remove(key);
   }

   /**
    * Get all the property keys.
    * @return all the property keys.
    */
   @Override
   public Enumeration<Object> getProperties() {
      return prop.keys();
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      String name = getClass().getName();
      int idx = name.lastIndexOf(".");
      name = idx >= 0 ? name.substring(idx + 1) : name;
      boolean fullyQualify =
         getSourceInfo() != null && getSourceInfo().getType() == SourceInfo.MODEL;

      writer.print(name);
      writer.print("[");
      sinfo.printKey(writer);
      writer.print(",");
      AssetUtil.printColumnsKey(getColumnSelection(false), writer, fullyQualify);
      writer.print(",");
      AssetUtil.printColumnsKey(getColumnSelection(true), writer, fullyQualify);
      writer.print(",");
      writer.print(getMaxRows());
      writer.print(",");
      writer.print(getMaxDisplayRows());
      writer.print(",");
      writer.print(isAggregate() ? "T" : "F");
      writer.print(isDistinct() ? "T" : "F");
      writer.print(isSQLMergeable() ? "T" : "F");
      writer.print(isVisibleTable() ? "T" : "F");
      writer.print(",");
      ginfo.printKey(writer);
      writer.print(",");

      if(getRealAggregateInfo() != null) {
         getRealAggregateInfo().printKey(writer);
         writer.print(",");
      }

      ConditionUtil.printConditionsKey(preconds, writer);
      writer.print(",");
      ConditionUtil.printConditionsKey(postconds, writer);
      writer.print(",");
      ConditionUtil.printConditionsKey(topns, writer);
      writer.print(",");
      ConditionUtil.printConditionsKey(mvUpdatePreConds, writer);
      writer.print(",");
      ConditionUtil.printConditionsKey(mvUpdatePostConds, writer);
      writer.print(",");
      ConditionUtil.printConditionsKey(mvDeletePreConds, writer);
      writer.print(",");
      ConditionUtil.printConditionsKey(mvDeletePostConds, writer);
      writer.print(",");
      ConditionUtil.printConditionsKey(preconds0, writer);
      writer.print(",");
      ConditionUtil.printConditionsKey(postconds0, writer);
      writer.print(",");
      ConditionUtil.printConditionsKey(topns0, writer);
      printProperties(writer);

      if(rinfo != null) {
         writer.print(rinfo.createKey());
      }

      Set<AssemblyRef> set = new HashSet<>();
      getExpressionDependeds(set);

      // if expressions reference other assembly, which could be changing, we only
      // cache the data for one call (same thread) for 2s
      if(set.size() > 0) {
         writer.print(",");
         writer.print(Thread.currentThread().toString());
         writer.print(",");
         writer.print(System.currentTimeMillis() / 2000);
      }

      String analysisMaxrow = getProperty("analysisMaxrow");

      // for vs wizard.
      if(analysisMaxrow != null) {
         writer.print(",");
         writer.print(analysisMaxrow);
      }

      String crossJoinMaxCell = SreeEnv.getProperty("crossJoin.maxCellCount");

      if(crossJoinMaxCell != null) {
         writer.print(",");
         writer.print(crossJoinMaxCell);
      }

      writer.print("]");

      return true;
   }

   /**
    * Check if equals another object in content.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals the object in content, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!(obj instanceof AbstractTableAssembly)) {
         return false;
      }

      if(!getClass().equals(obj.getClass())) {
         return false;
      }

      AbstractTableAssembly table = (AbstractTableAssembly) obj;

      if(!sinfo.equalsContent(table.sinfo)) {
         return false;
      }

      if(!AssetUtil.equalsColumns(getColumnSelection(false),
                                  table.getColumnSelection(false))) {
         return false;
      }

      if(!AssetUtil.equalsColumns(getColumnSelection(true),
                                  table.getColumnSelection(true))) {
         return false;
      }

      if(getMaxRows() != table.getMaxRows()) {
         return false;
      }

      if(getMaxDisplayRows() != table.getMaxDisplayRows()) {
         return false;
      }

      if(isAggregate() != table.isAggregate()) {
         return false;
      }

      if(isDistinct() != table.isDistinct()) {
         return false;
      }

      if(isSQLMergeable() != table.isSQLMergeable()) {
         return false;
      }

      if(isVisibleTable() != table.isVisibleTable()) {
         return false;
      }

      if(!ginfo.equalsContent(table.ginfo)) {
         return false;
      }

      if(!ConditionUtil.equalsConditionListWrapper(preconds, table.preconds)) {
         return false;
      }

      if(!ConditionUtil.equalsConditionListWrapper(postconds, table.postconds)) {
         return false;
      }

      if(!ConditionUtil.equalsConditionListWrapper(topns, table.topns)) {
         return false;
      }

      if(!ConditionUtil.equalsConditionListWrapper(mvUpdatePreConds,
                                               table.mvUpdatePreConds))
      {
         return false;
      }

      if(!ConditionUtil.equalsConditionListWrapper(mvUpdatePostConds,
                                               table.mvUpdatePostConds))
      {
         return false;
      }

      if(!ConditionUtil.equalsConditionListWrapper(mvDeletePreConds,
                                               table.mvDeletePreConds))
      {
         return false;
      }

      if(!ConditionUtil.equalsConditionListWrapper(mvDeletePostConds,
                                               table.mvDeletePostConds))
      {
         return false;
      }

      if(!ConditionUtil.equalsConditionListWrapper(preconds0, table.preconds0)) {
         return false;
      }

      if(!ConditionUtil.equalsConditionListWrapper(postconds0, table.postconds0)) {
         return false;
      }

      if(!ConditionUtil.equalsConditionListWrapper(topns0, table.topns0)) {
         return false;
      }

      return true;
   }

   /**
    * Get the hash code only considering content.
    * @return the hash code only considering content.
    */
   @Override
   public int getContentCode() {
      return 0;
   }

   /**
    * Print the table information.
    */
   @Override
   public void print(int level, StringBuilder sb) {
      printHead(level, sb);
      sb.append(toString());

      if(rinfo != null) {
         sb.append('-');
         sb.append("rinfo:").append(rinfo);
      }

      if(isDistinct()) {
         sb.append("-distinct");
      }

      if(!prop.isEmpty()) {
         sb.append('-');
         sb.append(prop);
      }

      sb.append('\n');

      printHead(level, sb);
      sb.append("private: ");
      sb.append(getColumnSelection(false));
      sb.append('\n');

      if(!Objects.equals(getColumnSelection(true), getColumnSelection(false))) {
         printHead(level, sb);
         sb.append("public: ");
         sb.append(getColumnSelection(true));
         sb.append('\n');
      }

      if(getMaxRows() > 0) {
         printHead(level, sb);
         sb.append("max rows: ");
         sb.append(getMaxRows());
         sb.append('\n');
      }

      if(getMaxDisplayRows() > 0) {
         printHead(level, sb);
         sb.append("max display rows: ");
         sb.append(getMaxDisplayRows());
         sb.append('\n');
      }

      if(getAggregateInfo() != null && !getAggregateInfo().isEmpty()) {
         printHead(level, sb);
         sb.append("aggregate: ");
         sb.append(getAggregateInfo());
         sb.append('\n');
      }

      if(getPreConditionList() != null && !getPreConditionList().isEmpty()) {
         printHead(level, sb);
         sb.append("pre condition: ");
         sb.append(getPreConditionList());
         sb.append('\n');
      }

      if(getPreRuntimeConditionList() != null && !getPreRuntimeConditionList().isEmpty()) {
         printHead(level, sb);
         sb.append("pre runtime condition: ");
         sb.append(getPreRuntimeConditionList());
         sb.append('\n');
      }

      if(getPostConditionList() != null && !getPostConditionList().isEmpty()) {
         printHead(level, sb);
         sb.append("post condition: ");
         sb.append(getPostConditionList());
         sb.append('\n');
      }

      if(getPostRuntimeConditionList() != null && !getPostRuntimeConditionList().isEmpty()) {
         printHead(level, sb);
         sb.append("post runtime condition: ");
         sb.append(getPostRuntimeConditionList());
         sb.append('\n');
      }

      if(getRankingConditionList() != null && !getRankingConditionList().isEmpty()) {
         printHead(level, sb);
         sb.append("ranking condition: ");
         sb.append(getRankingConditionList());
         sb.append('\n');
      }
   }

   /**
    * Print head.
    */
   protected void printHead(int level, StringBuilder sb) {
      for(int i = 0; i < level; i++) {
         sb.append('\t');
      }

      sb.append(level);
      sb.append('-');
   }

   /**
    * Print table property as cache key if necessary.
    */
   protected void printProperties(PrintWriter writer) throws Exception {
      // do nothing
   }

   /**
    * Set the listener to monitor the change of column selection.
    */
   public void setListener(PropertyChangeListener listener) {
      if(listener == null) {
         this.listener = null;
      }
      else {
         this.listener = new SoftReference<>(listener);
      }
   }

   /**
    * Get the listener to monitor the change of column selection.
    */
   public PropertyChangeListener getListener() {
      return listener == null ? null : listener.get();
   }

   /**
    * Write out data content of this table.
    */
   @Override
   public void writeData(JarOutputStream out) {
   }

   /**
    * update properties of table.
    */
   @Override
   public void updateTable(TableAssembly table) {
      if(lastModified != table.getLastModified()) {
         setLastModified(table.getLastModified());
      }

      if(!getSortInfo().equalsContent(table.getSortInfo())) {
         setSortInfo((SortInfo) table.getSortInfo().clone());
      }

      if(!AssetUtil.equalsColumns(getColumnSelection(true),
         table.getColumnSelection(true)))
      {
         setColumnSelection(table.getColumnSelection(true).clone(), true);
      }
      else {
         //update DataRef's all properies.
         updateColumns(getColumnSelection(true), table.getColumnSelection(true));
      }

      if(!AssetUtil.equalsColumns(getColumnSelection(false),
         table.getColumnSelection(false)))
      {
         setColumnSelection(table.getColumnSelection(false).clone(), false);
      }
      else {
         //update DataRef's all properies.
         updateColumns(getColumnSelection(false), table.getColumnSelection(false));
      }

      if(getMaxRows() != table.getMaxRows()) {
         setMaxRows(table.getMaxRows());
      }

      if(getMaxDisplayRows() != table.getMaxDisplayRows()) {
         setMaxDisplayRows(table.getMaxDisplayRows());
      }

      if(isAggregate() != table.isAggregate()) {
         setAggregate(table.isAggregate());
      }

      if(isDistinct() != table.isDistinct()) {
         setDistinct(table.isDistinct());
      }

      if(isSQLMergeable() != table.isSQLMergeable()) {
         setSQLMergeable(table.isSQLMergeable());
      }

      if(isVisibleTable() != table.isVisibleTable()) {
         setVisibleTable(table.isVisibleTable());
      }

      if(!getAggregateInfo().equalsContent(table.getAggregateInfo())) {
         setAggregateInfo((AggregateInfo) table.getAggregateInfo().clone());
      }

      if(!ConditionUtil.equalsConditionListWrapper(getPreConditionList(),
         table.getPreConditionList()))
      {
         setPreConditionList(((ConditionListWrapper) table.getPreConditionList().clone()));
      }

      if(!ConditionUtil.equalsConditionListWrapper(getPostConditionList(),
         table.getPostConditionList()))
      {
         setPostConditionList(((ConditionListWrapper) table.getPostConditionList().clone()));
      }

      if(!ConditionUtil.equalsConditionListWrapper(getRankingConditionList(),
         table.getRankingConditionList()))
      {
         setRankingConditionList(((ConditionListWrapper) table.getRankingConditionList().clone()));
      }

      if(!ConditionUtil.equalsConditionListWrapper(getMVUpdatePostConditionList(),
         table.getMVUpdatePostConditionList()))
      {
         setMVUpdatePostConditionList(((ConditionListWrapper)
            table.getMVUpdatePostConditionList().clone()));
      }

      if(!ConditionUtil.equalsConditionListWrapper(getMVDeletePreConditionList(),
         table.getMVDeletePreConditionList()))
      {
         setMVDeletePreConditionList(((ConditionListWrapper)
            table.getMVDeletePreConditionList().clone()));
      }

      if(!ConditionUtil.equalsConditionListWrapper(getMVDeletePostConditionList(),
         table.getMVDeletePostConditionList()))
      {
         setMVDeletePostConditionList(((ConditionListWrapper)
            table.getMVDeletePostConditionList().clone()));
      }

      if(!ConditionUtil.equalsConditionListWrapper(getPreRuntimeConditionList(),
         table.getPreRuntimeConditionList()))
      {
         setPreRuntimeConditionList(((ConditionListWrapper)
            table.getPreRuntimeConditionList().clone()));
      }

      if(!ConditionUtil.equalsConditionListWrapper(getPostRuntimeConditionList(),
         table.getPostRuntimeConditionList()))
      {
        setPostRuntimeConditionList(((ConditionListWrapper)
            table.getPostRuntimeConditionList().clone()));
      }
   }

   /**
    * Update Columns dataRef all properties.
    */
   private void updateColumns(ColumnSelection ocols, ColumnSelection cols) {
      if(ocols == null || cols == null) {
         return;
      }

      for(int i = 0; i < ocols.getAttributeCount(); i++) {
         DataRef oref = ocols.getAttribute(i);
         DataRef ref = cols.getAttribute(i);

         if(!oref.equals(ref, true)) {
            ocols.setAttribute(i, (DataRef) ref.clone());
         }
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(AbstractTableAssembly.class);
   private static final Set<String> IGNORED = new HashSet<>();

   static {
      IGNORED.add("vs.cond");
   }

   protected Properties prop = new Properties();
   protected ConditionListWrapper preconds;
   protected ConditionListWrapper postconds;
   protected ConditionListWrapper topns;
   private ConditionListWrapper mvUpdatePreConds;
   private ConditionListWrapper mvUpdatePostConds;
   private ConditionListWrapper mvDeletePreConds;
   private ConditionListWrapper mvDeletePostConds;
   private boolean mvForceAppendUpdates;

   protected AggregateInfo ginfo;
   protected AggregateInfo realAggregateInfo;
   private SortInfo sinfo;
   private String columnPropertyName;

   private transient UserVariable[] srcvars;
   private transient SoftReference<PropertyChangeListener> listener;
   private transient ConditionListWrapper preconds0;
   private transient ConditionListWrapper postconds0;
   private transient ConditionListWrapper topns0;
   private transient RuntimeMV rinfo;
   private transient String expressionKey;
   private transient Set<AssemblyRef> expressionDeps;
   private transient long lastModified = new Date().getTime();
}
