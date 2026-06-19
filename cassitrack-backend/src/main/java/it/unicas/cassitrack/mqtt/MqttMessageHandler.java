package it.unicas.cassitrack.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import it.unicas.cassitrack.dto.MqttPositionPayload;
import it.unicas.cassitrack.model.Bus;
import it.unicas.cassitrack.model.VehiclePosition;
import it.unicas.cassitrack.repository.BusRepository;
import it.unicas.cassitrack.service.VehicleStateCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class MqttMessageHandler implements MessageHandler {

    private final ObjectMapper objectMapper;
    private final WriteApiBlocking influxWriteApi;
    private final VehicleStateCache vehicleStateCache;
    private final BusRepository busRepository; // 🚌 Iniettiamo il repository reale di Postgres!

    private static final double LAT_MIN = 41.40;
    private static final double LAT_MAX = 41.60;
    private static final double LON_MIN = 13.70;
    private static final double LON_MAX = 14.00;
    private static final long MAX_AGE_SECONDS = 300;

    @Override
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMessage(Message<?> message) throws MessagingException {
        String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
        String payload = (String) message.getPayload();

        log.debug("MQTT message received on topic [{}]: {}", topic, payload);

        try {
            // ── Step 1: Parse ─────────────────────────────────────
            MqttPositionPayload pos = objectMapper.readValue(payload, MqttPositionPayload.class);

            // ── Step 2: Validate ──────────────────────────────────
            if (!isValid(pos)) {
                log.warn("Invalid MQTT payload from topic [{}], discarding: {}", topic, payload);
                return;
            }

            // ── Step 3: Query reale su Postgres per ottenere il bus ──
            // Cerchiamo l'anagrafica del pullman usando il vehicleId del messaggio MQTT
            Optional<Bus> associatedBus = busRepository.findByCurrentVehicleId(pos.getVehicleId());

            Integer busId = associatedBus.map(Bus::getBusId).orElse(null);
            Integer numeroPosti = associatedBus.map(Bus::getNumeroPosti).orElse(null);
            Boolean postoDisabili = associatedBus.map(Bus::getPostoDisabili).orElse(null);

            // ── Step 4: Scrittura su InfluxDB (Aggiungiamo informazioni utili allo storico) ──
            try {
                writeToInflux(pos, busId, numeroPosti, postoDisabili);
            } catch (Exception influxError) {
                log.warn("InfluxDB unavailable, skipping time-series write for vehicle {}: {}",
                        pos.getVehicleId(), influxError.getMessage());
            }

            // ── Step 5: Salva in REDIS (Cache arricchita con i dati statici del bus) ──
            VehiclePosition entity = toEntity(pos, busId, numeroPosti, postoDisabili);
            vehicleStateCache.update(pos.getVehicleId(), entity);

            log.info("Processed [{}] -> Bus ID: {}, Posti: {}, Disabili: {} | lat={}, lon={}",
                    pos.getVehicleId(), busId, numeroPosti, postoDisabili, pos.getLat(), pos.getLon());

        } catch (Exception e) {
            log.error("Failed to process MQTT message from topic [{}]: {}", topic, e.getMessage(), e);
        }
    }

    private boolean isValid(MqttPositionPayload pos) {
        if (pos.getVehicleId() == null || pos.getVehicleId().isBlank()) return false;
        if (pos.getLat() == null || pos.getLon() == null) return false;
        if (pos.getLat() < LAT_MIN || pos.getLat() > LAT_MAX || pos.getLon() < LON_MIN || pos.getLon() > LON_MAX) return false;
        if (pos.getTimestamp() == null) return false;

        long ageSeconds = Instant.now().getEpochSecond() - pos.getTimestamp().getEpochSecond();
        return ageSeconds <= MAX_AGE_SECONDS;
    }

    private void writeToInflux(MqttPositionPayload pos, Integer busId, Integer numeroPosti, Boolean postoDisabili) {
        Point point = Point
                .measurement("vehicle_position")
                .addTag("vehicle_id", pos.getVehicleId())
                .addTag("bus_id", busId != null ? busId.toString() : "UNKNOWN")
                .addTag("trip_id", pos.getTripId() != null ? pos.getTripId() : "UNKNOWN")
                .addField("lat", pos.getLat())
                .addField("lon", pos.getLon())
                .addField("speed_kmh", pos.getSpeedKmh() != null ? pos.getSpeedKmh() : 0.0)
                .addField("heading_deg", pos.getHeadingDeg() != null ? pos.getHeadingDeg() : 0.0)
                // 📈 Salviamo i posti e l'accessibilità anche nello storico InfluxDB per statistiche future
                .addField("numero_posti", numeroPosti != null ? numeroPosti : 0)
                .addField("posto_disabili", postoDisabili != null ? postoDisabili : false)
                .time(pos.getTimestamp(), WritePrecision.S);

        if (pos.getBleDeviceCount() != null) point.addField("ble_device_count", pos.getPassengers() /*.getBleDeviceCount*/);
        if (pos.getBatteryVoltage() != null) point.addField("battery_voltage", pos.getBatteryVoltage());
        if (pos.getPassengers() != null) point.addField("passengers", pos.getPassengers());
        if (pos.getCapacity()   != null) point.addField("capacity",   pos.getCapacity());
        if (pos.getDelayMinutes() != null) {
            point.addField("delay", pos.getDelayMinutes());
        }
        if (pos.getNearestStop() != null) {
            point.addField("last_stop_registered", pos.getNearestStop());
        }

        influxWriteApi.writePoint(point);
    }

    private VehiclePosition toEntity(MqttPositionPayload pos, Integer busId, Integer numeroPosti, Boolean postoDisabili) {
        return VehiclePosition.builder()
                .vehicleId(pos.getVehicleId())
                .busId(busId)
                .numeroPosti(numeroPosti)
                .postoDisabili(postoDisabili)
                .timestamp(pos.getTimestamp())
                .lat(pos.getLat())
                .lon(pos.getLon())
                .speedKmh(pos.getSpeedKmh())
                .headingDeg(pos.getHeadingDeg())
                .bleDeviceCount(pos.getBleDeviceCount())
                .batteryVoltage(pos.getBatteryVoltage())
                .firmwareVersion(pos.getFirmwareVersion())
                .passengers(pos.getPassengers())
                .capacity(pos.getCapacity())
                .delayMinutes(pos.getDelayMinutes())
                .nearestStop(pos.getNearestStop())
                .nearestStopId(pos.getNearestStopId())
                .tripId(pos.getTripId())
                .routeId(pos.getRouteId())
                .routeName(pos.getRouteName())
                .scheduleStatus(VehiclePosition.ScheduleStatus.UNKNOWN)
                .receivedAt(Instant.now())
                .build();
    }

}
