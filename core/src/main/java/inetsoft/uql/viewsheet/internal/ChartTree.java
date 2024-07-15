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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.composition.region.ChartConstants;
import inetsoft.uql.XCube;
import inetsoft.uql.asset.NamedRangeRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.graph.*;

import java.util.*;

/**
 * This class captures the hierarchy expand/collapse status.
 * @version 13.3
 * @author InetSoft Technology Corp
 */
public class ChartTree {
   public ChartTree() {
   }

   /**
    * Update and sync the tree states with the current binding.
    * This method must be called before this ChartTree is used.
    */
   public void updateHierarcy(VSChartInfo chartInfo, XCube cube, String cubeType) {
      this.cinfo = chartInfo;
      this.cubeType = cubeType;

      if(!Objects.equals(cube, ocube)) {
         xchildRefs.clear();
         ychildRefs.clear();
         tchildRefs.clear();
         gchildRefs.clear();
         targetChildRefs.clear();
         sourceChildRefs.clear();
         aestheticChildRefs.clear();
      }

      ocube = cube;

      VSDimensionRef[] xrefs = Arrays.stream(cinfo.getXFields())
         .filter(ref -> ref instanceof VSDimensionRef)
         .toArray(VSDimensionRef[]::new);
      VSDimensionRef[] yrefs = Arrays.stream(cinfo.getYFields())
         .filter(ref -> ref instanceof VSDimensionRef)
         .toArray(VSDimensionRef[]::new);
      VSDimensionRef[] trefs = Arrays.stream(cinfo.getGroupFields())
         .filter(ref -> ref instanceof VSDimensionRef)
         .toArray(VSDimensionRef[]::new);
      VSDimensionRef[] aesfs = Arrays.stream(chartInfo.getAestheticRefs(false))
         .map(aesRef -> aesRef.getDataRef())
         .filter(ref -> ref instanceof VSDimensionRef)
         .toArray(VSDimensionRef[]::new);
      updateChildRefs(xrefs, cube, ChartConstants.DRILL_DIRECTION_X);
      updateChildRefs(yrefs, cube, ChartConstants.DRILL_DIRECTION_Y);
      updateChildRefs(trefs, cube, ChartConstants.DRILL_DIRECTION_T);
      updateChildRefs(aesfs, cube, ChartConstants.DRILL_DIRECTION_AESTHETIC);

      if(chartInfo instanceof VSMapInfo) {
         VSDimensionRef[] grefs = Arrays.stream(((VSMapInfo) chartInfo).getGeoFields())
            .filter(ref -> ref instanceof VSDimensionRef)
            .toArray(VSDimensionRef[]::new);
         updateChildRefs(grefs, cube, ChartConstants.DRILL_DIRECTION_G);
      }
      else if(chartInfo instanceof RelationVSChartInfo) {
         VSDimensionRef targetField = (VSDimensionRef) ((RelationVSChartInfo) chartInfo).getTargetField();

         if(targetField != null) {
            updateChildRefs(new VSDimensionRef[]{targetField}, cube, ChartConstants.DRILL_DIRECTION_TARGET);
         }

         VSDimensionRef sourceField = (VSDimensionRef) ((RelationVSChartInfo) chartInfo).getSourceField();

         if(sourceField != null) {
            updateChildRefs(new VSDimensionRef[]{sourceField}, cube, ChartConstants.DRILL_DIRECTION_SOURCE);
         }
      }
   }

   private void updateChildRefs(VSDimensionRef[] refs, XCube cube, String type) {
      for(VSDimensionRef dref : refs) {
         VSDimensionRef child = getChildRef(dref.getFullName(), type);

         if(child == null) {
            child = CrosstabTree.getChildRef(dref, refs, cube, false);
         }

         VSDimensionRef lastRef = VSUtil.getLastDrillLevelRef(dref, cube);

         if(child == null) {
            child = VSUtil.getNextLevelRef(dref, cube, true);
         }

         if(child != null) {
            String cname = CrosstabTree.getFieldName(child, cubeType);
            String pname = CrosstabTree.getFieldName(dref, cubeType);

            if(cname != null && !cname.equals(pname)) {
               updateChildRef(pname, child, type);
            }
         }

         if(lastRef != null) {
            updateChildRef(lastRef.getFullName(), dref, type);
         }
         // root node
         else {
            updateChildRef(CrosstabTree.getHierarchyRootKey(dref), dref, type);
         }
      }
   }

   private VSDimensionRef getChildRef(String key, String type) {
      if(ChartConstants.DRILL_DIRECTION_X.equals(type)) {
         return getXChildRef(key);
      }
      else if(ChartConstants.DRILL_DIRECTION_Y.equals(type)) {
         return getYChildRef(key);
      }
      else if(ChartConstants.DRILL_DIRECTION_T.equals(type)) {
         return getTChildRef(key);
      }
      else if(ChartConstants.DRILL_DIRECTION_G.equals(type)) {
         return getGChildRef(key);
      }
      else if(ChartConstants.DRILL_DIRECTION_SOURCE.equals(type)) {
         return getSourceChildRef(key);
      }
      else if(ChartConstants.DRILL_DIRECTION_TARGET.equals(type)) {
         return getTargetChildRef(key);
      }
      else if(ChartConstants.DRILL_DIRECTION_AESTHETIC.equals(type)) {
         return getAestheticChildRef(key);
      }

      return null;
   }

   private void updateChildRef(String key, VSDimensionRef child, String type) {
      if(ChartConstants.DRILL_DIRECTION_X.equals(type)) {
         updateXChildRef(key, child);
      }
      else if(ChartConstants.DRILL_DIRECTION_Y.equals(type)) {
         updateYChildRef(key, child);
      }
      else if(ChartConstants.DRILL_DIRECTION_T.equals(type)) {
         updateTChildRef(key, child);
      }
      else if(ChartConstants.DRILL_DIRECTION_G.equals(type)) {
         updateGChildRef(key, child);
      }
      else if(ChartConstants.DRILL_DIRECTION_SOURCE.equals(type)) {
         updateSourceChildRef(key, child);
      }
      else if(ChartConstants.DRILL_DIRECTION_TARGET.equals(type)) {
         updateTargetChildRef(key, child);
      }
      else if(ChartConstants.DRILL_DIRECTION_AESTHETIC.equals(type)) {
         updateAestheticChildRef(key, child);
      }
   }

   private void updateXChildRef(String parentRef, VSDimensionRef childRef) {
      xchildRefs.put(NamedRangeRef.getBaseName(parentRef), childRef);
   }

   public VSDimensionRef getXChildRef(String parent) {
      return xchildRefs.get(NamedRangeRef.getBaseName(parent));
   }

   public void removeXChildRef(String parent) {
      xchildRefs.remove(NamedRangeRef.getBaseName(parent));
   }

   private void updateYChildRef(String parentRef, VSDimensionRef childRef) {
      ychildRefs.put(NamedRangeRef.getBaseName(parentRef), childRef);
   }

   public VSDimensionRef getYChildRef(String parent) {
      return ychildRefs.get(NamedRangeRef.getBaseName(parent));
   }

   public void removeYChildRef(String parent) {
      ychildRefs.remove(NamedRangeRef.getBaseName(parent));
   }

   private void updateTChildRef(String parentRef, VSDimensionRef childRef) {
      tchildRefs.put(NamedRangeRef.getBaseName(parentRef), childRef);
   }

   public VSDimensionRef getTChildRef(String parent) {
      return tchildRefs.get(NamedRangeRef.getBaseName(parent));
   }

   public void removeTChildRef(String parent) {
      tchildRefs.remove(NamedRangeRef.getBaseName(parent));
   }

   private void updateGChildRef(String parentRef, VSDimensionRef childRef) {
      gchildRefs.put(NamedRangeRef.getBaseName(parentRef), childRef);
   }

   public VSDimensionRef getGChildRef(String parent) {
      return gchildRefs.get(NamedRangeRef.getBaseName(parent));
   }

   public void removeGChildRef(String parent) {
      gchildRefs.remove(NamedRangeRef.getBaseName(parent));
   }

   public VSDimensionRef getTargetChildRef(String parent) {
      return targetChildRefs.get(NamedRangeRef.getBaseName(parent));
   }

   public void removeTargetChildRef(String parent) {
      targetChildRefs.remove(NamedRangeRef.getBaseName(parent));
   }

   private void updateTargetChildRef(String parentRef, VSDimensionRef childRef) {
      targetChildRefs.put(NamedRangeRef.getBaseName(parentRef), childRef);
   }

   private void updateSourceChildRef(String parentRef, VSDimensionRef childRef) {
      sourceChildRefs.put(NamedRangeRef.getBaseName(parentRef), childRef);
   }

   public VSDimensionRef getSourceChildRef(String parent) {
      return sourceChildRefs.get(NamedRangeRef.getBaseName(parent));
   }

   public void removeSourceChildRef(String parent) {
      sourceChildRefs.remove(NamedRangeRef.getBaseName(parent));
   }

   public VSDimensionRef getAestheticChildRef(String parent) {
      return aestheticChildRefs.get(NamedRangeRef.getBaseName(parent));
   }

   private void updateAestheticChildRef(String parentRef, VSDimensionRef childRef) {
      aestheticChildRefs.put(NamedRangeRef.getBaseName(parentRef), childRef);
   }

   public void removeAestheticChildRef(String parent) {
      aestheticChildRefs.remove(NamedRangeRef.getBaseName(parent));
   }

   private VSChartInfo cinfo;
   private String cubeType;
   private XCube ocube;
   // x parent ref -> child ref
   private Map<String, VSDimensionRef> xchildRefs = new HashMap<>();
   // y parent ref -> child ref
   private Map<String, VSDimensionRef> ychildRefs = new HashMap<>();
   // group parent ref -> child ref
   private Map<String, VSDimensionRef> tchildRefs = new HashMap<>();
   // geo parent ref -> child ref
   private Map<String, VSDimensionRef> gchildRefs = new HashMap<>();
   //target parent ref -> child ref
   private Map<String, VSDimensionRef> targetChildRefs = new HashMap<>();
   //source parent ref -> child ref
   private Map<String, VSDimensionRef> sourceChildRefs = new HashMap<>();
   //aesthetic parent ref -> child ref
   private Map<String, VSDimensionRef> aestheticChildRefs = new HashMap<>();
}
