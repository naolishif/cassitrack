package it.unicas.cassitrack.dto.siri;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Modello SIRI 2.0 — Vehicle Monitoring (CEN EN 15531).
 * <p>
 * L'ordine degli elementi segue rigorosamente la {@code xsd:sequence} dello schema
 * (MonitoredVehicleJourneyStructure). L'ordine è vincolato tramite:
 *   1) l'ordine di dichiarazione dei campi, e
 *   2) l'annotazione {@link JsonPropertyOrder} su ogni struttura (rete di sicurezza,
 *      così l'ordine è garantito e non dipende dall'introspezione di Jackson).
 * <p>
 * I campi {@code null} non vengono serializzati ({@link JsonInclude.Include#NON_NULL}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JacksonXmlRootElement(localName = "Siri")
public class Siri {

    @JacksonXmlProperty(isAttribute = true, localName = "xmlns")
    private String xmlns = "http://www.siri.org.uk/siri";

    @JacksonXmlProperty(isAttribute = true, localName = "version")
    private String version = "2.0";

    @JacksonXmlProperty(localName = "ServiceDelivery")
    private ServiceDelivery serviceDelivery;

    public Siri() {}
    public Siri(ServiceDelivery serviceDelivery) { this.serviceDelivery = serviceDelivery; }

    public String getXmlns() { return xmlns; }
    public void setXmlns(String xmlns) { this.xmlns = xmlns; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public ServiceDelivery getServiceDelivery() { return serviceDelivery; }
    public void setServiceDelivery(ServiceDelivery sd) { this.serviceDelivery = sd; }

    // ── ServiceDelivery ────────────────────────────────────────────────────────
    // XSD: ResponseTimestamp, ProducerRef, ..., VehicleMonitoringDelivery
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({ "responseTimestamp", "producerRef", "vehicleMonitoringDelivery" })
    public static class ServiceDelivery {
        @JacksonXmlProperty(localName = "ResponseTimestamp")
        private String responseTimestamp;

        /** Identificativo del produttore del dato (obbligatorio nei profili nazionali). */
        @JacksonXmlProperty(localName = "ProducerRef")
        private String producerRef = "CASSITRACK";

        @JacksonXmlProperty(localName = "VehicleMonitoringDelivery")
        private VehicleMonitoringDelivery vehicleMonitoringDelivery;

        public ServiceDelivery() {
            this.responseTimestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
        }

        public String getResponseTimestamp() { return responseTimestamp; }
        public void setResponseTimestamp(String v) { this.responseTimestamp = v; }
        public String getProducerRef() { return producerRef; }
        public void setProducerRef(String v) { this.producerRef = v; }
        public VehicleMonitoringDelivery getVehicleMonitoringDelivery() { return vehicleMonitoringDelivery; }
        public void setVehicleMonitoringDelivery(VehicleMonitoringDelivery v) { this.vehicleMonitoringDelivery = v; }
    }

    // ── VehicleMonitoringDelivery ──────────────────────────────────────────────
    // XSD: attributo version (obbligatorio) + ResponseTimestamp, Status, VehicleActivity*
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({ "version", "responseTimestamp", "status", "vehicleActivity" })
    public static class VehicleMonitoringDelivery {

        @JacksonXmlProperty(isAttribute = true, localName = "version")
        private String version = "2.0";

        @JacksonXmlProperty(localName = "ResponseTimestamp")
        private String responseTimestamp;

        @JacksonXmlProperty(localName = "Status")
        private boolean status = true;

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "VehicleActivity")
        private List<VehicleActivity> vehicleActivity = new ArrayList<>();

        public VehicleMonitoringDelivery() {
            this.responseTimestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
        }

        public String getVersion() { return version; }
        public void setVersion(String v) { this.version = v; }
        public String getResponseTimestamp() { return responseTimestamp; }
        public void setResponseTimestamp(String v) { this.responseTimestamp = v; }
        public boolean isStatus() { return status; }
        public void setStatus(boolean status) { this.status = status; }
        public List<VehicleActivity> getVehicleActivity() { return vehicleActivity; }
        public void setVehicleActivity(List<VehicleActivity> v) { this.vehicleActivity = v; }
    }

    // ── VehicleActivity ────────────────────────────────────────────────────────
    // XSD: RecordedAtTime, ValidUntilTime, ItemIdentifier?, MonitoredVehicleJourney
    // XSD VehicleActivityStructure: RecordedAtTime, ItemIdentifier?, ValidUntilTime, ...,
    //      MonitoredVehicleJourney, VehicleActivityNote*, Extensions?
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({ "recordedAtTime", "validUntilTime", "monitoredVehicleJourney", "extensions" })
    public static class VehicleActivity {
        @JacksonXmlProperty(localName = "RecordedAtTime")
        private String recordedAtTime;

        /** Istante fino al quale il dato è considerato valido (obbligatorio 1:1). */
        @JacksonXmlProperty(localName = "ValidUntilTime")
        private String validUntilTime;

        @JacksonXmlProperty(localName = "MonitoredVehicleJourney")
        private MonitoredVehicleJourney monitoredVehicleJourney;

        /** Campi non standard: qui è la posizione ammessa dallo schema (non dentro MVJ). */
        @JacksonXmlProperty(localName = "Extensions")
        private Extensions extensions;

        public VehicleActivity() {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            this.recordedAtTime = now.toString();
            this.validUntilTime = now.plusSeconds(60).toString();
        }

        public String getRecordedAtTime() { return recordedAtTime; }
        public void setRecordedAtTime(String v) { this.recordedAtTime = v; }
        public String getValidUntilTime() { return validUntilTime; }
        public void setValidUntilTime(String v) { this.validUntilTime = v; }
        public MonitoredVehicleJourney getMonitoredVehicleJourney() { return monitoredVehicleJourney; }
        public void setMonitoredVehicleJourney(MonitoredVehicleJourney v) { this.monitoredVehicleJourney = v; }
        public Extensions getExtensions() { return extensions; }
        public void setExtensions(Extensions v) { this.extensions = v; }
    }

    // ── MonitoredVehicleJourney ────────────────────────────────────────────────
    // XSD (sottoinsieme usato): FramedVehicleJourneyRef, VehicleLocation, Bearing,
    //      Occupancy, Delay, VehicleRef, PreviousCalls, MonitoredCall, Extensions
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({
            "framedVehicleJourneyRef", "vehicleLocation", "bearing",
            "occupancy", "delay", "vehicleRef",
            "previousCalls", "monitoredCall"
    })
    public static class MonitoredVehicleJourney {

        /** Riferimento strutturato alla corsa (data + ID corsa) */
        @JacksonXmlProperty(localName = "FramedVehicleJourneyRef")
        private FramedVehicleJourneyRef framedVehicleJourneyRef;

        /** Posizione GPS */
        @JacksonXmlProperty(localName = "VehicleLocation")
        private VehicleLocation vehicleLocation;

        /** Direzione di marcia in gradi (0-359) */
        @JacksonXmlProperty(localName = "Bearing")
        private Double bearing;

        /**
         * Stato di occupazione — enum SIRI:
         * empty | manySeatsAvailable | seatsAvailable | fewSeatsAvailable | standingAvailable | full
         */
        @JacksonXmlProperty(localName = "Occupancy")
        private String occupancy;

        /** Ritardo in formato durata ISO 8601 (PT0S, PT2M, -PT1M). */
        @JacksonXmlProperty(localName = "Delay")
        private String delay;

        /** Identificativo del veicolo */
        @JacksonXmlProperty(localName = "VehicleRef")
        private String vehicleRef;

        /** Fermate già percorse (ultima fermata registrata) */
        @JacksonXmlElementWrapper(localName = "PreviousCalls")
        @JacksonXmlProperty(localName = "PreviousCall")
        private List<PreviousCall> previousCalls;

        /** Fermata corrente/prossima */
        @JacksonXmlProperty(localName = "MonitoredCall")
        private MonitoredCall monitoredCall;

        public FramedVehicleJourneyRef getFramedVehicleJourneyRef() { return framedVehicleJourneyRef; }
        public void setFramedVehicleJourneyRef(FramedVehicleJourneyRef v) { this.framedVehicleJourneyRef = v; }
        public VehicleLocation getVehicleLocation() { return vehicleLocation; }
        public void setVehicleLocation(VehicleLocation v) { this.vehicleLocation = v; }
        public Double getBearing() { return bearing; }
        public void setBearing(Double v) { this.bearing = v; }
        public String getOccupancy() { return occupancy; }
        public void setOccupancy(String v) { this.occupancy = v; }
        public String getDelay() { return delay; }
        public void setDelay(String v) { this.delay = v; }
        public String getVehicleRef() { return vehicleRef; }
        public void setVehicleRef(String v) { this.vehicleRef = v; }
        public List<PreviousCall> getPreviousCalls() { return previousCalls; }
        public void setPreviousCalls(List<PreviousCall> v) { this.previousCalls = v; }
        public MonitoredCall getMonitoredCall() { return monitoredCall; }
        public void setMonitoredCall(MonitoredCall v) { this.monitoredCall = v; }
    }

    // ── FramedVehicleJourneyRef ────────────────────────────────────────────────
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({ "dataFrameRef", "datedVehicleJourneyRef" })
    public static class FramedVehicleJourneyRef {
        /** Data di riferimento (es. "2026-06-26") */
        @JacksonXmlProperty(localName = "DataFrameRef")
        private String dataFrameRef;

        /** ID della corsa (tripId) */
        @JacksonXmlProperty(localName = "DatedVehicleJourneyRef")
        private String datedVehicleJourneyRef;

        public FramedVehicleJourneyRef() {}
        public FramedVehicleJourneyRef(String dataFrameRef, String datedVehicleJourneyRef) {
            this.dataFrameRef = dataFrameRef;
            this.datedVehicleJourneyRef = datedVehicleJourneyRef;
        }

        public String getDataFrameRef() { return dataFrameRef; }
        public void setDataFrameRef(String v) { this.dataFrameRef = v; }
        public String getDatedVehicleJourneyRef() { return datedVehicleJourneyRef; }
        public void setDatedVehicleJourneyRef(String v) { this.datedVehicleJourneyRef = v; }
    }

    // ── VehicleLocation ────────────────────────────────────────────────────────
    @JsonPropertyOrder({ "longitude", "latitude" })
    public static class VehicleLocation {
        @JacksonXmlProperty(localName = "Longitude")
        private double longitude;

        @JacksonXmlProperty(localName = "Latitude")
        private double latitude;

        public VehicleLocation() {}
        public VehicleLocation(double longitude, double latitude) {
            this.longitude = longitude;
            this.latitude = latitude;
        }

        public double getLongitude() { return longitude; }
        public void setLongitude(double v) { this.longitude = v; }
        public double getLatitude() { return latitude; }
        public void setLatitude(double v) { this.latitude = v; }
    }

    // ── MonitoredCall (fermata corrente/prossima) ──────────────────────────────
    // XSD MonitoredCallStructure: StopPointRef, VisitNumber?, Order?, StopPointName,
    //      VehicleAtStop, ...  → StopPointName PRIMA di VehicleAtStop.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({ "stopPointRef", "stopPointName", "vehicleAtStop" })
    public static class MonitoredCall {
        @JacksonXmlProperty(localName = "StopPointRef")
        private String stopPointRef;

        @JacksonXmlProperty(localName = "StopPointName")
        private String stopPointName;

        @JacksonXmlProperty(localName = "VehicleAtStop")
        private boolean vehicleAtStop = false;

        public MonitoredCall() {}
        public MonitoredCall(String stopPointRef, String stopPointName) {
            this.stopPointRef = stopPointRef;
            this.stopPointName = stopPointName;
        }

        public String getStopPointRef() { return stopPointRef; }
        public void setStopPointRef(String v) { this.stopPointRef = v; }
        public boolean isVehicleAtStop() { return vehicleAtStop; }
        public void setVehicleAtStop(boolean v) { this.vehicleAtStop = v; }
        public String getStopPointName() { return stopPointName; }
        public void setStopPointName(String v) { this.stopPointName = v; }
    }

    // ── PreviousCall (ultima fermata registrata) ───────────────────────────────
    // XSD: StopPointRef (obbligatorio), ..., StopPointName
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({ "stopPointRef", "stopPointName" })
    public static class PreviousCall {
        @JacksonXmlProperty(localName = "StopPointRef")
        private String stopPointRef;

        @JacksonXmlProperty(localName = "StopPointName")
        private String stopPointName;

        public PreviousCall() {}
        public PreviousCall(String stopPointRef, String stopPointName) {
            this.stopPointRef = stopPointRef;
            this.stopPointName = stopPointName;
        }

        public String getStopPointRef() { return stopPointRef; }
        public void setStopPointRef(String v) { this.stopPointRef = v; }
        public String getStopPointName() { return stopPointName; }
        public void setStopPointName(String v) { this.stopPointName = v; }
    }

    // ── Extensions (campi non standard) ───────────────────────────────────────
    // ExtensionsStructure ammette contenuto libero: qui trasportiamo dati fuori-standard.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({ "velocity", "numberOfSeats", "passengers", "wheelchairAccess" })
    public static class Extensions {
        /** Velocità in km/h */
        @JacksonXmlProperty(localName = "Velocity")
        private Double velocity;

        /** Numero totale di posti */
        @JacksonXmlProperty(localName = "NumberOfSeats")
        private Integer numberOfSeats;

        /** Numero di passeggeri a bordo */
        @JacksonXmlProperty(localName = "Passengers")
        private Integer passengers;

        /** Accessibilità sedia a rotelle (spostata qui: non è un elemento SIRI standard). */
        @JacksonXmlProperty(localName = "WheelchairAccess")
        private Boolean wheelchairAccess;

        public Extensions() {}

        public Double getVelocity() { return velocity; }
        public void setVelocity(Double v) { this.velocity = v; }
        public Integer getNumberOfSeats() { return numberOfSeats; }
        public void setNumberOfSeats(Integer v) { this.numberOfSeats = v; }
        public Integer getPassengers() { return passengers; }
        public void setPassengers(Integer v) { this.passengers = v; }
        public Boolean getWheelchairAccess() { return wheelchairAccess; }
        public void setWheelchairAccess(Boolean v) { this.wheelchairAccess = v; }
    }
}
