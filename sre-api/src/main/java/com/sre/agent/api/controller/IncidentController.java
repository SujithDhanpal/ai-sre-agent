package com.sre.agent.api.controller;

import com.sre.agent.commons.enums.IncidentStatus;
import com.sre.agent.commons.model.Incident;
import com.sre.agent.core.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
@Slf4j
public class IncidentController {

    private final IncidentRepository incidentRepository;
    private final com.sre.agent.memory.feedback.PostResolutionLearner postResolutionLearner;

    @GetMapping
    public ResponseEntity<Page<Incident>> listIncidents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(incidentRepository.findByTenantIdOrderByCreatedAtDesc(
                "default", PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Incident> getIncident(@PathVariable UUID id) {
        return incidentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long total = incidentRepository.findByTenantIdOrderByCreatedAtDesc("default", PageRequest.of(0, 1)).getTotalElements();
        long resolved = incidentRepository.countByTenantIdAndStatus("default", IncidentStatus.FIX_GENERATING);
        return ResponseEntity.ok(Map.of("total", total, "resolved", resolved));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<Map<String, String>> resolve(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return incidentRepository.findById(id)
                .map(incident -> {
                    incident.setStatus(IncidentStatus.RESOLVED);
                    incident.setResolvedAt(java.time.Instant.now());
                    incident.setResolvedBy(body.getOrDefault("resolvedBy", "human"));
                    incident.setResolutionSummary(body.getOrDefault("summary", "Manually resolved"));
                    incidentRepository.save(incident);
                    // Store episodic memory for future learning
                    postResolutionLearner.learnFromResolution(
                            incident.getId(), "default",
                            incident.getTitle(),
                            incident.getResolutionSummary(),
                            incident.getResolvedBy());
                    return ResponseEntity.ok(Map.of("status", "resolved"));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
