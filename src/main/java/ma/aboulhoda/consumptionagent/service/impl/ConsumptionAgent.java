package ma.aboulhoda.consumptionagent.service.impl;

import ma.aboulhoda.consumptionagent.service.facade.ConsumptionTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ConsumptionAgent {

    private static final String CONVERSATION_ID_KEY = "chat_memory_conversation_id";

    private final ChatClient client;
    private final ChatMemory memory = MessageWindowChatMemory.builder()
            .chatMemoryRepository(new InMemoryChatMemoryRepository())
            .maxMessages(Integer.MAX_VALUE)
            .build();

    public ConsumptionAgent(ChatClient.Builder clientBuilder, ConsumptionTools tools) {
        this.client = clientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .defaultSystem("""
                You are an electricity-consumption analysis assistant.
                Today's date is %s.
                You have tools to query the consumption database.
                Always use a tool to get real numbers — never guess.
                Format all responses in Markdown.
                Present numerical data (consumption, peaks, stats) in Markdown tables with clear column headers and units.
                Use headers, bold, and bullet points for any surrounding explanation.

                Tool selection rules:
                - Relative periods ("last 7 days", "this week", "last 3 days"): use consumptionForLastDays / dailyBreakdown.
                - Specific named month ("February", "March 2025", "last February"): use consumptionForMonth / dailyBreakdownForMonth with the correct year and month number.
                - When the user says a month name without a year, infer the most recent occurrence of that month relative to today.

                When the user asks to see a chart, graph, or visualization, call the appropriate dailyBreakdown tool \
                and render the result as a Mermaid xychart-beta chart in a mermaid fenced code block. Example:
                ```mermaid
                xychart-beta
                title "Daily Energy Consumption (kWh)"
                x-axis ["2026-01-01", "2026-01-02", "2026-01-03"]
                y-axis "kWh" 0 --> 5
                bar [1.2, 2.3, 1.8]
                ```
                Always include a table of the raw data alongside the chart.
                """.formatted(java.time.LocalDate.now()))
                .defaultTools(tools)
                .build();
    }

    public String ask(String question, String conversationId) {
        return client.prompt()
                .user(question)
                .advisors(a -> a.param(CONVERSATION_ID_KEY, conversationId))
                .call()
                .content();
    }

    public Flux<String> askStream(String question, String conversationId) {
        return client.prompt()
                .user(question)
                .advisors(a -> a.param(CONVERSATION_ID_KEY, conversationId))
                .stream()
                .content();
    }
}
