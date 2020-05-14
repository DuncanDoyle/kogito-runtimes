package com.myspace.demo;

import java.util.TimeZone;
import java.util.Optional;

import org.kie.kogito.Application;
import org.kie.kogito.event.DataEvent;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.impl.Sig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.StdDateFormat;

import org.eclipse.microprofile.reactive.messaging.Message;

public class $Type$MessageConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger("MessageConsumer");

    Process<$Type$> process;

    Application application;

    Optional<Boolean> useCloudEvents = Optional.of(true);

    // Can use @Inject and @Name
    /*
     * @javax.inject.Inject()
     * 
     * @javax.inject.Named("travels") But want to do this dynamically.
     */

    Object deserializer;

    private ObjectMapper json = new ObjectMapper();

    {
        json.setDateFormat(new StdDateFormat().withColonInTimeZone(true).withTimeZone(TimeZone.getDefault()));
    }

    public void configure() {

    }

    public void consume(Message message) {
        final String trigger = "$Trigger$";

        // TODO: Deserialize. Basically this means retrieving the payload from the
        // message.
        // This payload can be different for each type of connector

        //final $PayloadType$ payload = message.getPayload();

        // Inject Deserializer code here if applicable
        // This can for example go from GenericFile to File ... or from GenericFile to
        // String
        // Can we infer the returntype from the generics of the configured deserializer
        // class?
        // Can we figure that out at compile time?
        // Object deserializedPayload = deserializer.deserialize(payload);
        String deserializedPayload = "bla";

        final $DataEventType$ eventData;
        final $DataType$ data;
        // And now we either unmarshall or not
        // TODO: This needs to be dynamic ....
        boolean unmarshall = true;
        try {
            if (unmarshall) {
                eventData = json.readValue(deserializedPayload, $DataEventType$.class);
                data = eventData.getData();
            } else {
                // if we don't unmarshall, the deserializedPayload should be the same type as
                // the data.
                data = deserializedPayload;
            }

            // And now we can start the process

            final $Type$ model = new $Type$();
            model.set$ModelRef$(data);
            org.kie.kogito.services.uow.UnitOfWorkExecutor.executeInUnitOfWork(application.unitOfWorkManager(), () -> {

                LOGGER.debug("Received message without reference id, staring new process instance with trigger '{}'",
                        trigger);
                ProcessInstance<$Type$> pi = process.createInstance(model);
                pi.start(trigger, null);

                return null;
            });

        } catch (Exception e) {
            LOGGER.error("Error when consuming message for process {}", process.id(), e);
        }
    }

}