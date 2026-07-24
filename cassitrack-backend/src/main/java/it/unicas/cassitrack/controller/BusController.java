package it.unicas.cassitrack.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unicas.cassitrack.dto.BusDTO;
import it.unicas.cassitrack.service.BusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST controller for the bus registry — US-01 "Manage buses".
 *
 * Base path: /api/v1/buses
 *
 * Reads are available to any authenticated user; create, update and delete are
 * restricted to FLEET_MANAGER by SecurityConfig's POST/PUT/DELETE matchers
 * (see SECURITY_CHANGE.txt — "/api/v1/buses/**" must be added there).
 *
 * Note the deliberate split: this endpoint manages the *registry* (plate,
 * capacity, assigned route, status). Live telemetry stays on /api/v1/vehicles.
 */
@RestController
@RequestMapping("/api/v1/buses")
@RequiredArgsConstructor
@Tag(name = "Bus Registry",
        description = "Create, read, update and delete buses in the fleet registry (US-01)")
public class BusController {

    private final BusService busService;

    @GetMapping(produces = "application/json")
    @Operation(summary = "List buses",
            description = "Optional free-text search plus status and route filters.")
    public ResponseEntity<List<BusDTO>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String routeId) {
        return ResponseEntity.ok(busService.getAll(search, status, routeId));
    }

    @GetMapping(value = "/route-options", produces = "application/json")
    @Operation(summary = "Routes available for assignment / filtering")
    public ResponseEntity<List<Map<String, String>>> getRouteOptions() {
        return ResponseEntity.ok(busService.getRouteOptions());
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    @Operation(summary = "Get a single bus by id")
    public ResponseEntity<BusDTO> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(busService.getById(id));
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    @Operation(summary = "Create a bus (FLEET_MANAGER only)")
    public ResponseEntity<BusDTO> create(@Valid @RequestBody BusDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(busService.create(dto));
    }

    @PutMapping(value = "/{id}", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Update a bus (FLEET_MANAGER only)")
    public ResponseEntity<BusDTO> update(@PathVariable Integer id,
                                         @Valid @RequestBody BusDTO dto) {
        return ResponseEntity.ok(busService.update(id, dto));
    }

    /**
     * PUT rather than PATCH on purpose: SecurityConfig only declares
     * FLEET_MANAGER rules for POST/PUT/DELETE, so a PATCH route would fall
     * through to the generic "authenticated" rule and be writable by any
     * logged-in user.
     */
    @PutMapping(value = "/{id}/visibility", produces = "application/json")
    @Operation(summary = "Show or hide this bus on the fleet map (FLEET_MANAGER only)")
    public ResponseEntity<BusDTO> setVisibility(@PathVariable Integer id,
                                                @RequestParam boolean visible) {
        return ResponseEntity.ok(busService.setMapVisible(id, visible));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a bus (FLEET_MANAGER only)")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        busService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Error handling ──────────────────────────────────────────────
    // server.error.include-message is intentionally NOT enabled globally
    // (it would leak internal messages across the whole app). These handlers
    // are scoped to this controller only, so the Data Management UI can show
    // useful validation feedback without weakening that global setting.

    /** Duplicate plate / unknown route / missing bus → readable message. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of(
                        "status", ex.getStatusCode().value(),
                        "message", ex.getReason() == null ? "Request failed" : ex.getReason()));
    }

    /** Bean-validation failures (@NotBlank, @Min, @Pattern) → first field message. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("Invalid bus data");
        return ResponseEntity.badRequest()
                .body(Map.of("status", 400, "message", message));
    }
}
