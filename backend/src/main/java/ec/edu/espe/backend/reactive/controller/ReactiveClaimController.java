package ec.edu.espe.backend.reactive.controller;

import ec.edu.espe.backend.reactive.model.ReactiveClaimEvent;
import ec.edu.espe.backend.reactive.model.ReactiveStatsDTO;
import ec.edu.espe.backend.reactive.service.ReactiveClaimService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/reactive/claims")
public class ReactiveClaimController {

    private final ReactiveClaimService reactiveClaimService;

    public ReactiveClaimController(ReactiveClaimService reactiveClaimService) {
        this.reactiveClaimService = reactiveClaimService;
    }

    @GetMapping("/stats")
    public Mono<ReactiveStatsDTO> getStats() {
        return reactiveClaimService.computeStatsAsync();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ReactiveClaimEvent>> streamEvents() {
        return reactiveClaimService.getEventStream()
                .map(event -> ServerSentEvent.<ReactiveClaimEvent>builder()
                        .id(event.getEventId())
                        .event("message")
                        .data(event)
                        .build());
    }

    @GetMapping(value = "/simulate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ReactiveClaimEvent>> simulateActivity() {
        return reactiveClaimService.simulateClaimActivity()
                .map(event -> ServerSentEvent.<ReactiveClaimEvent>builder()
                        .id(event.getEventId())
                        .event("simulated-event")
                        .data(event)
                        .build());
    }
}
