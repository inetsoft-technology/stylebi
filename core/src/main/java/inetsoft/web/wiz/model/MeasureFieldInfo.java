package inetsoft.web.wiz.model;

public class MeasureFieldInfo extends SimpleFieldInfo {
   public String getAggregateFormula() {
      return aggregateFormula;
   }

   public void setAggregateFormula(String aggregateFormula) {
      this.aggregateFormula = aggregateFormula;
   }

   private String aggregateFormula;
}
