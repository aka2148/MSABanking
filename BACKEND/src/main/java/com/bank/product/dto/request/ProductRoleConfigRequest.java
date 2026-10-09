package com.bank.product.dto.request;
import jakarta.validation.constraints.*;
public class ProductRoleConfigRequest{
 @NotNull @Positive private Integer roleTypeId; private Boolean mandatory=false; @Min(0) private Integer minCount; @Min(0) private Integer maxCount;
 public Integer getRoleTypeId(){return roleTypeId;} public void setRoleTypeId(Integer v){roleTypeId=v;} public Boolean getMandatory(){return mandatory;} public void setMandatory(Boolean v){mandatory=v;} public Integer getMinCount(){return minCount;} public void setMinCount(Integer v){minCount=v;} public Integer getMaxCount(){return maxCount;} public void setMaxCount(Integer v){maxCount=v;}
}
