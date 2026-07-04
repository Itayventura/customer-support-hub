package com.surense.customerhub.agent;

import com.surense.customerhub.agent.dto.AgentResponse;
import com.surense.customerhub.agent.dto.CreateAgentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agents")
@PreAuthorize("hasRole('ADMIN')")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgentResponse createAgent(@Valid @RequestBody CreateAgentRequest request) {
        return agentService.createAgent(request);
    }

    @GetMapping
    public List<AgentResponse> listAgents() {
        return agentService.listAgents();
    }
}
