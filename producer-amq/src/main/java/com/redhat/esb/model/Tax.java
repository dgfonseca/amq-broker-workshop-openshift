package com.redhat.esb.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class Tax {

    @JsonFormat
    public String userName;

    @JsonFormat
    public String iva;
    
    @JsonFormat
    public Integer amount;

    @Override
    public String toString(){
        return "Tax("+userName+","+iva+","+amount+")";
    }
    
}
