package com.redhat.esb.apis;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class GreetingResource {


    @Inject
    Logger log;

    @Incoming("SHIPMENTS")
    public void processShipment(String shipment) {
        log.info("Procesando "+shipment);
    }
    @Incoming("TAXES")
    public void processTax(String shipment) {
        log.info("Procesando "+shipment);
    }
}
