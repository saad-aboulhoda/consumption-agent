package ma.aboulhoda.consumptionagent.web;

import lombok.RequiredArgsConstructor;
import ma.aboulhoda.consumptionagent.service.dto.base.Result;
import ma.aboulhoda.consumptionagent.service.dto.request.AskRequest;
import ma.aboulhoda.consumptionagent.service.dto.response.AskResponse;
import ma.aboulhoda.consumptionagent.service.impl.ConsumptionAgent;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/consumption")
@RequiredArgsConstructor
public class ConsumptionWeb {

    private final ConsumptionAgent agent;

    @PostMapping("/ask")
    public Result<AskResponse> ask(@RequestBody AskRequest request) {
        return Result.success(new AskResponse(agent.ask(request.question(), resolveConversationId(request))));
    }

    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> askStream(@RequestBody AskRequest request) {
        return agent.askStream(request.question(), resolveConversationId(request));
    }

    private String resolveConversationId(AskRequest request) {
        return (request.conversationId() != null && !request.conversationId().isBlank())
                ? request.conversationId()
                : "default";
    }
}
