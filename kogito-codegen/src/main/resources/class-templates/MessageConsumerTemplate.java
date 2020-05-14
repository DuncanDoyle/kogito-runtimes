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

    private ObjectMapper json = new ObjectMapper();

    {
        json.setDateFormat(new StdDateFormat().withColonInTimeZone(true).withTimeZone(TimeZone.getDefault()));
    }

    public void configure() {

    }

    public void consume(String payload) {
        final String trigger = "$Trigger$";

        // TODO: ddoyle: We should be able to define whether we want to unmarshall
        // payload or not.
        boolean unmarshalPayload = "$UnmarshalPayload$";
        try {
            if (unmarshalPayload) {
                // TODO: ddoyle: we should be able to set this per node ...
                if (useCloudEvents.orElse(true)) {
                    final $DataEventType$ eventData = json.readValue(deserializedPayload, $DataEventType$.class);
                    final $Type$ model = new $Type$();
                    // According to the BPMN spec, a Message can only have a single ItemDefinition.
                    // ddoyle: modelref
                    model.set$ModelRef$(eventData.getData());
                    org.kie.kogito.services.uow.UnitOfWorkExecutor.executeInUnitOfWork(application.unitOfWorkManager(),
                            () -> {

                                if (eventData.getKogitoReferenceId() != null) {
                                    LOGGER.debug(
                                            "Received message with reference id '{}' going to use it to send signal '{}'",
                                            eventData.getKogitoReferenceId(), trigger);
                                    process.instances().findById(eventData.getKogitoReferenceId())
                                            .ifPresent(pi -> pi.send(Sig.of("Message-" + trigger, eventData.getData(),
                                                    eventData.getKogitoProcessinstanceId())));
                                } else {
                                    LOGGER.debug(
                                            "Received message without reference id, staring new process instance with trigger '{}'",
                                            trigger);
                                    ProcessInstance<$Type$> pi = process.createInstance(model);

                                    if (eventData.getKogitoStartFromNode() != null) {
                                        pi.startFrom(eventData.getKogitoStartFromNode(),
                                                eventData.getKogitoProcessinstanceId());
                                    } else {
                                        pi.start(trigger, eventData.getKogitoProcessinstanceId());
                                    }
                                }
                                return null;
                            });
                } else {
                    final $DataType$ eventData = json.readValue(payload, $DataType$.class);
                    final $Type$ model = new $Type$();
                    model.set$ModelRef$(eventData);
                    org.kie.kogito.services.uow.UnitOfWorkExecutor.executeInUnitOfWork(application.unitOfWorkManager(),
                            () -> {

                                LOGGER.debug(
                                        "Received message without reference id, staring new process instance with trigger '{}'",
                                        trigger);
                                ProcessInstance<$Type$> pi = process.createInstance(model);
                                pi.start(trigger, null);

                                return null;
                            });
                }
            } else {

                //TODO: ddoyle: fix.
                //Question, how do we pass in a Kogito Reference Id? 
                //Also, shouldn'twe create a MessageConsumerTemplate per connector type? Wouldn't that be cleaner?
                //In Camel, we might be able to get a referenceId from the Exchange:
                /*
                Optional<IncomingExchangeMetadata> metadata = msg.getMetadata(IncomingExchangeMetadata.class);
        if (metadata.isPresent()) {
            // Retrieve the camel exchange:
            Exchange exchange = metadata.get().getExchange();
        }
        return msg.ack();
        */

                final $DataType$ eventData = payload.getPayload();
                final $Type$ model = new $Type$();
                model.set$ModelRef$(eventData);
                    org.kie.kogito.services.uow.UnitOfWorkExecutor.executeInUnitOfWork(application.unitOfWorkManager(),
                            () -> {

                                LOGGER.debug(
                                        "Received message without reference id, staring new process instance with trigger '{}'",
                                        trigger);
                                ProcessInstance<$Type$> pi = process.createInstance(model);
                                pi.start(trigger, null);

                                return null;
                            });

            }
        } catch (Exception e) {
            LOGGER.error("Error when consuming message for process {}", process.id(), e);
        }
    }

}