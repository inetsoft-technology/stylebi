package inetsoft.web.wiz.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CrosstabBinding implements BindingInfo {
   public List<DimensionFieldInfo> getRows() {
      return rows;
   }

   public void setRows(List<DimensionFieldInfo> rows) {
      this.rows = rows;
   }

   public List<DimensionFieldInfo> getCols() {
      return cols;
   }

   public void setCols(List<DimensionFieldInfo> cols) {
      this.cols = cols;
   }

   public List<MeasureFieldInfo> getAggregates() {
      return aggregates;
   }

   public void setAggregates(List<MeasureFieldInfo> aggregates) {
      this.aggregates = aggregates;
   }

   private List<DimensionFieldInfo> rows;
   private List<DimensionFieldInfo> cols;
   private List<MeasureFieldInfo> aggregates;
}
