package com.redhat.esb.apis;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import com.redhat.esb.model.Shipments;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/hello")
public class GreetingResource {

    @Channel("SHIPMENTS")
    Emitter<String> emitter;

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public String hello(Shipments shipment) {
        emitter.send(shipment.toString());
        return "Shipment for user "+shipment.clientId+" sent";
    }
}
