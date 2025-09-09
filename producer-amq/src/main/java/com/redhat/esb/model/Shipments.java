package com.redhat.esb.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class Shipments {

    @JsonFormat
    public String city;

    @JsonFormat
    public String address;
    
    @JsonFormat
    public Integer clientId;

    @Override
    public String toString(){
        return "Shipments("+city+","+address+","+clientId+")";
    }

    
}
