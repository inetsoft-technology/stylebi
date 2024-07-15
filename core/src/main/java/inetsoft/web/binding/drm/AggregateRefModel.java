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
package inetsoft.web.binding.drm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.web.binding.service.DataRefModelFactory;
import org.springframework.stereotype.Component;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AggregateRefModel extends AbstractDataRefModel {
   public AggregateRefModel() {
   }

   public AggregateRefModel(AggregateRef ref) {
      super(ref);

      DataRef dataRef = ref.getDataRef();

      if(dataRef != null) {
         if(dataRef instanceof ColumnRef) {
            setRef(new ColumnRefModel((ColumnRef) dataRef));
         }
         else if(dataRef instanceof AttributeRef) {
            setRef(new AttributeRefModel((AttributeRef) dataRef));
         }
      }

      dataRef = ref.getSecondaryColumn();

      if(dataRef != null) {
         if(dataRef instanceof ColumnRef){
            setSecondaryColumn(new ColumnRefModel((ColumnRef) dataRef));
         }
         else if(dataRef instanceof AttributeRef) {
            setSecondaryColumn(new AttributeRefModel((AttributeRef) dataRef));
         }
      }

      setPercentage(ref.isPercentage());
      setPercentageOption(ref.getPercentageOption());
      setN(ref.getN());

      AggregateFormula formula = ref.getFormula();

      if(formula != null) {
         setFormulaName(formula.getFormulaName());

         if(formula.getDataType() != null) {
            setDataType(formula.getDataType());
         }
      }
   }

   public String getFormulaName() {
      return formulaName;
   }

   public void setFormulaName(String formulaName) {
      this.formulaName = formulaName;
   }

   public DataRefModel getRef() {
      return ref;
   }

   public void setRef(DataRefModel ref) {
      this.ref = ref;
   }

   @JsonProperty("ref2")
   public DataRefModel getSecondaryColumn() {
      return ref2;
   }

   public void setSecondaryColumn(DataRefModel ref2) {
      this.ref2 = ref2;
   }

   public boolean getPercentage() {
      return percentage;
   }

   public void setPercentage(boolean percentage) {
      this.percentage = percentage;
   }

   public int getPercentageOption() {
      return percentageOption;
   }

   public void setPercentageOption(int percentageOption) {
      this.percentageOption = percentageOption;
   }

   public int getN() {
      return num;
   }

   public void setN(int n) {
      this.num = n;
   }

   /**
    * Create a data ref.
    */
   @Override
   public DataRef createDataRef() {
      AggregateRef ref = new AggregateRef();
      ref.setFormula(AggregateFormula.getFormula(this.getFormulaName()));
      ref.setFormulaName(this.getFormulaName());
      ref.setDataRef(this.ref == null ? null : this.ref.createDataRef());
      ref.setSecondaryColumn(this.ref2 == null ? null : this.ref2.createDataRef());
      ref.setPercentageOption(this.getPercentageOption());
      ref.setPercentage(this.getPercentage());
      ref.setN(this.getN());

      return ref;
   }

   private String formulaName;
   private DataRefModel ref;
   private DataRefModel ref2;
   private boolean percentage;
   private int percentageOption;
   private int num;

   @Component
   public static final class AggregateRefModelFactory
      extends DataRefModelFactory<AggregateRef, AggregateRefModel>
   {
      @Override
      public Class<AggregateRef> getDataRefClass() {
         return AggregateRef.class;
      }

      @Override
      public AggregateRefModel createDataRefModel(AggregateRef dataRef) {
         return new AggregateRefModel(dataRef);
      }
   }
}
