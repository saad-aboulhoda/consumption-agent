package ma.aboulhoda.consumptionagent.web;

import lombok.RequiredArgsConstructor;
import ma.aboulhoda.consumptionagent.config.ReportProperties;
import ma.aboulhoda.consumptionagent.service.dto.base.Result;
import ma.aboulhoda.consumptionagent.service.dto.request.AskRequest;
import ma.aboulhoda.consumptionagent.service.dto.response.AskResponse;
import ma.aboulhoda.consumptionagent.service.impl.ConsumptionAgent;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/consumption")
@RequiredArgsConstructor
public class ConsumptionWeb {

    private final ConsumptionAgent agent;
    private final ReportProperties reportProperties;

    @PostMapping("/ask")
    public Result<AskResponse> ask(@RequestBody AskRequest request) {
        return Result.success(new AskResponse(agent.ask(request.question(), resolveConversationId(request))));
    }

    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> askStream(@RequestBody AskRequest request) {
        return agent.askStream(request.question(), resolveConversationId(request));
    }

    @GetMapping("/report/{fileName}")
    public ResponseEntity<Resource> report(@PathVariable String fileName) throws IOException {
        if (!fileName.matches("consumption-report-\\d{4}(-\\d{2})?\\.pdf")) {
            return ResponseEntity.notFound().build();
        }
        Path base = Path.of(reportProperties.getOutputDir()).toAbsolutePath().normalize();
        Path file = base.resolve(fileName).normalize();
        if (!file.startsWith(base) || !Files.isReadable(file)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .body(new UrlResource(file.toUri()));
    }

    private String resolveConversationId(AskRequest request) {
        return (request.conversationId() != null && !request.conversationId().isBlank())
                ? request.conversationId()
                : "default";
    }
}
