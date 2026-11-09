package ru.tpu.oko.monitoring.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.tpu.oko.common.ApiPaths;
import ru.tpu.oko.monitoring.service.StatusEventService;

import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.V1 + "/status/events")
public class StatusEventController {

    private final StatusEventService service;

    public StatusEventController(StatusEventService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Acknowledgement> receive(@Valid @RequestBody StatusEvent event) {
        boolean accepted = service.receive(event);
        return ResponseEntity.status(accepted ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(new Acknowledgement(event.eventId(), accepted ? "accepted" : "duplicate"));
    }

    public record Acknowledgement(@com.fasterxml.jackson.annotation.JsonProperty("event_id") UUID eventId,
                                  String result) {
    }
}
