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
package inetsoft.web.binding.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import inetsoft.report.internal.binding.*;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.SNamedGroupInfo;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.util.Tool;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.condition.ConditionExpression;
import inetsoft.web.composer.model.condition.ConditionUtil;

import java.util.ArrayList;
import java.util.List;

public class NamedGroupInfoModel {
   public NamedGroupInfoModel() {
   }

   public NamedGroupInfoModel(XNamedGroupInfo ong) {
      if(ong == null) {
         return;
      }

      if(ong instanceof AssetNamedGroupInfo) {
         AssetNamedGroupInfo ang =(AssetNamedGroupInfo)ong;
         setName(ang.getName());
      }
      else if(ong instanceof SNamedGroupInfo) {
         SNamedGroupInfo sng = (SNamedGroupInfo)ong;
         String[] names = sng.getGroups();

         for(String name : names) {
            List value = sng.getGroupValue(name);
            GroupCondition gcond = new GroupCondition(name, value);
            addGroup(gcond);
         }

         setName("Custom");
      }
      else if(ong instanceof ExpertNamedGroupInfo) {
         ExpertNamedGroupInfo eng = (ExpertNamedGroupInfo)ong;
         String[] names = eng.getGroups();

         for(String name : names) {
            ConditionList conds = eng.getGroupCondition(name);
            List<Object> vals = new ArrayList<>();

            for(int a = 0; a < conds.getSize(); a++) {
               Condition cond = conds.getCondition(a);

               if(cond == null) {
                  continue;
               }

               List values = cond.getValues();

               for(int i = 0; i < values.size(); i++) {
                  if(!vals.contains(values.get(i))) {
                     vals.add(values.get(i));
                  }
               }
            }

            GroupCondition gcond = new GroupCondition(name, vals);
            addGroup(gcond);
         }
         setName("Custom");
      }

      setType(ong.getType());
   }

   public void fixNamedGroupInfoModel(XNamedGroupInfo namedGroupInfo,
                                       DataRefModelFactoryService refModelService)
   {
      if(namedGroupInfo == null) {
         return;
      }

      int type = namedGroupInfo.getType();

      if(type != XNamedGroupInfo.EXPERT_NAMEDGROUP_INFO &&
         type != XNamedGroupInfo.SIMPLE_NAMEDGROUP_INFO)
      {
         return;
      }

      String[] groups = namedGroupInfo.getGroups();

      if(groups == null || groups.length == 0) {
         return;
      }

      for(String group : groups) {
         Object[] cons = ConditionUtil
            .fromConditionListToModel(namedGroupInfo.getGroupCondition(group), refModelService);
         ConditionExpression conExp = new ConditionExpression();
         conExp.setName(group);
         conExp.setList(cons);
         addCondition(conExp);
      }

      setType(XNamedGroupInfo.EXPERT_NAMEDGROUP_INFO);
   }

   /**
    * Create XNamedGroupInfo by the target namedgroup model.
    * @param fld       the base field of the namedgroup.
    * @return
    * @throws Exception
    */
   public XNamedGroupInfo createNamedGroupInfo(DataRef fld) throws Exception {
      int type = getType();

      if(type == XNamedGroupInfo.ASSET_NAMEDGROUP_INFO_REF) {
         AssetRepository rep = AssetUtil.getAssetRepository(false);

         if(rep == null) {
            return null;
         }

         AssetNamedGroupInfo[] infos = SummaryAttr.getAssetNamedGroupInfos(fld, rep, null);

         if(infos == null || infos.length == 0) {
            return null;
         }

         for(int j = 0; infos != null && j < infos.length; j++) {
            AssetNamedGroupInfo info = infos[j];

            if(Tool.equals(getName(), info.getName())) {
               return info;
            }
         }
      }
      else if(type == XNamedGroupInfo.SIMPLE_NAMEDGROUP_INFO) {
         SimpleNamedGroupInfo sinfo = new SimpleNamedGroupInfo();
         List<GroupCondition> groups = getGroups();

         if(groups != null && groups.size() > 0) {
            for(GroupCondition group : groups) {
               sinfo.setGroupValue(group.getName(), group.getValue());
            }
         }

         return sinfo;
      }
      else if(type == XNamedGroupInfo.EXPERT_NAMEDGROUP_INFO && getConditions() != null) {
         ExpertNamedGroupInfo expertInfo = new ExpertNamedGroupInfo();
         List<ConditionExpression> conds = getConditions();

         if(conds != null && !conds.isEmpty()) {
            for(ConditionExpression item : conds) {
               String group = item.getName();

               if(item.getList() == null) {
                  continue;
               }

               expertInfo.setGroupCondition(group, ConditionUtil.fromModelToConditionList(
                  item.getList(), null, null, null));
            }
         }
         else if(getGroups() != null) {
            getGroups().forEach(group -> {
               List values = group.getValue();

               if(values == null) {
                  return;
               }

               for(Object value : values) {
                  expertInfo.setGroupCondition(group.getName(),
                     ExpertNamedGroupInfo.createManualCondition(fld, Tool.getDataString(value)));
               }
            });
         }

         return expertInfo;
      }

      return null;
   }

   public void addOriginalExpertCondition(ConditionExpression conditionExpression) {
      if(conditionExpression == null || conditionExpression.getName() == null) {
         return;
      }

      if(originalExpertCondition == null) {
         originalExpertCondition = new ArrayList<>();
      }

      int index = -1;

      for(int i = 0; i < originalExpertCondition.size(); i++) {
         ConditionExpression item  = originalExpertCondition.get(i);

         if(item == null) {
            continue;
         }

         if(conditionExpression.getName().equals(item.getName())) {
            index = i;
            break;
         }
      }

      if(index == -1) {
         originalExpertCondition.add(conditionExpression);
      }
      else {
         originalExpertCondition.remove(index);
         originalExpertCondition.add(conditionExpression);
      }
   }

   public List<ConditionExpression> getOriginalExpertCondition() {
      return originalExpertCondition;
   }

   public void setOriginalExpertCondition(List<ConditionExpression> originalExpertCondition) {
      this.originalExpertCondition = originalExpertCondition;
   }

   public int getType() {
      return type;
   }

   public void setType(int type) {
      this.type = type;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public List<GroupCondition> getGroups() {
      return groups;
   }

   public void addGroup(GroupCondition group) {
      groups.add(group);
   }

   @JsonIgnore
   public XNamedGroupInfo getXNamedGroupInfo() {
      return xNamedGroupInfo;
   }

   @JsonIgnore
   public void setXNamedGroupInfo(XNamedGroupInfo xNamedGroupInfo) {
      this.xNamedGroupInfo = xNamedGroupInfo;
   }

   public void setConditions(List<ConditionExpression> conds) {
      this.conds = conds;
   }

   public List<ConditionExpression> getConditions() {
      return conds;
   }

   public void addCondition(ConditionExpression cond) {
      conds.add(cond);
   }

   private int type = 0;
   private String name;
   private List<GroupCondition> groups = new ArrayList<>();
   private List<ConditionExpression> conds = new ArrayList<>();
   private List<ConditionExpression> originalExpertCondition = new ArrayList<>();
   @JsonIgnore
   private XNamedGroupInfo xNamedGroupInfo;
}
