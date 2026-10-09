package com.bank.product.dto.request;
import jakarta.validation.constraints.Min;
public class UpdateProductRoleConfigRequest {
    private Boolean mandatory;
    @Min(value=0, message="minCount cannot be negative") private Integer minCount;
    @Min(value=0, message="maxCount cannot be negative") private Integer maxCount;
    public Boolean getMandatory(){return mandatory;} public void setMandatory(Boolean v){mandatory=v;}
    public Integer getMinCount(){return minCount;} public void setMinCount(Integer v){minCount=v;}
    public Integer getMaxCount(){return maxCount;} public void setMaxCount(Integer v){maxCount=v;}
}
