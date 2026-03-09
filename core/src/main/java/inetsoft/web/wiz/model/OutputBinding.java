package inetsoft.web.wiz.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OutputBinding implements BindingInfo {
   public MeasureFieldInfo getField() {
      return field;
   }

   public void setField(MeasureFieldInfo field) {
      this.field = field;
   }

   private MeasureFieldInfo field;
}
