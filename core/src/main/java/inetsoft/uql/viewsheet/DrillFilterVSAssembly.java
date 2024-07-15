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
package inetsoft.uql.viewsheet;

   import inetsoft.uql.ConditionItem;
   import inetsoft.uql.ConditionList;
   import inetsoft.uql.asset.ColumnRef;
   import inetsoft.uql.asset.DateRangeRef;
   import inetsoft.uql.erm.AttributeRef;
   import inetsoft.uql.erm.DataRef;
   import inetsoft.uql.schema.XSchema;
   import inetsoft.uql.viewsheet.internal.DrillFilterInfo;
   import inetsoft.uql.viewsheet.internal.VSUtil;
   import org.w3c.dom.Element;

   import java.io.PrintWriter;
   import java.util.Objects;

/**
 * DrillActionVSAssembly represents one assembly is drillable and filter
 *
 * @version 13.3
 * @author InetSoft Technology Corp
 */
public interface DrillFilterVSAssembly extends CubeVSAssembly {
   /**
    * Drill Action Condition
    */
   default ConditionList getDrillFilterConditionList(String field) {
      return getDrillFilterInfo().getDrillFilterConditionList(field);
   }

   /**
    * Set Drill Action Condition
    */
   default void setDrillFilterConditionList(String field, ConditionList drillCondition) {
      getDrillFilterInfo().setDrillFilterConditionList(field, drillCondition);
   }

   /**
    * Get all drill filter conditions merged into one condition list.
    */
   default ConditionList getAllDrillFilterConditions() {
      return validDrillFilterCondition(getDrillFilterInfo().getAllDrillFilterConditions());
   }

   /**
    * Check if any drill filter condition eixsts.
    */
   default boolean hasDrillFilter() {
      return !getDrillFilterInfo().getFields().isEmpty();
   }

   /**
    * Get a text description of current drills suitable for using as tooltip.
    */
   default String getDrillDescription() {
      validDrillFilterCondition(getDrillFilterInfo().getAllDrillFilterConditions());
      return getDrillFilterInfo().getDrillDescription();
   }

   /**
    * This is intended for internal use only.
    */
   DrillFilterInfo getDrillFilterInfo();

   /**
    * Get all available refs.
    */
   DataRef[] getDrillFilterAvailableRefs();

   default ConditionList validDrillFilterCondition(ConditionList conditionList) {
      DataRef[] refs = getDrillFilterAvailableRefs();

      for(DataRef ref : refs) {
         if(!(ref instanceof VSDimensionRef)) {
            continue;
         }

         VSDimensionRef dim = (VSDimensionRef) ref;

         if(!XSchema.isDateType(dim.getDataType()) || !dim.runtimeDateLevelChange() ||
            !VSUtil.isDynamicValue(dim.getDateLevelValue()))
         {
            continue;
         }

         String field = ref.getAttribute();

         for(int i = 0; i < conditionList.getSize(); i++) {
            ConditionItem item = conditionList.getConditionItem(i);

            if(item == null) {
               continue;
            }

            ColumnRef columnRef = (ColumnRef) item.getAttribute();
            String dataType = columnRef.getDataType();

            if(!XSchema.isDateType(dataType)) {
               continue;
            }

            DataRef dataRef = columnRef.getDataRef();
            AttributeRef attr = ColumnRef.getAttributeRef(dataRef);
            VSDimensionRef nref = (VSDimensionRef) dim.clone();
            nref.setDateLevelValue(((DateRangeRef) dataRef).getDateOption() + "");
            VSDimensionRef child = VSUtil.getNextLevelRef(nref, getXCube(), true);

            if(attr != null && Objects.equals(field, attr.getAttribute())) {
               setDrillFilterConditionList(child.getFullName(), null);
               conditionList.removeConditionItem(i);
            }
         }
      }

      return conditionList;
   }

   default void writeDrillState(PrintWriter writer) {
      getDrillFilterInfo().writeXML(writer);
   }

   default void parseDrillState(Element node) throws Exception {
      getDrillFilterInfo().parseXML(node);
   }
}
