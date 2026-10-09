package com.bank.product.dto.response;
import java.util.List;
public class ValidationResponse{private boolean valid;private List<String> errors;public ValidationResponse(){}public ValidationResponse(boolean valid,List<String> errors){this.valid=valid;this.errors=errors;}public boolean isValid(){return valid;}public void setValid(boolean v){valid=v;}public List<String> getErrors(){return errors;}public void setErrors(List<String> v){errors=v;}}
