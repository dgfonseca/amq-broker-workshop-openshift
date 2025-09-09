package com.redhat.esb.apis;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class GreetingResource {

    @Channel("TAXES")
    Emitter<String> emitter;

    @Inject
    Logger log;

    @Incoming("SHIPMENTS")
    public void redirectMessage(String shipment) {
        log.info("Procesando "+shipment);
        emitter.send("Tax("+shipment+")");
    }
}
