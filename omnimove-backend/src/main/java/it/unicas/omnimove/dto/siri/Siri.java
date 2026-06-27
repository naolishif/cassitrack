package it.unicas.omnimove.dto.siri;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@JacksonXmlRootElement(localName = "Siri", namespace = "http://www.siri.org.uk/siri")
public class Siri {

    @JacksonXmlProperty(isAttribute = true, localName = "version")
    private String version = "2.0";

    @JacksonXmlProperty(localName = "ServiceDelivery")
    private ServiceDelivery serviceDelivery;

    public Siri() {}
    public Siri(ServiceDelivery serviceDelivery) { this.serviceDelivery = serviceDelivery; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public ServiceDelivery getServiceDelivery() { return serviceDelivery; }
    public void setServiceDelivery(ServiceDelivery sd) { this.serviceDelivery = sd; }

    // ── ServiceDelivery ────────────────────────────────────────────────────────
    public static class ServiceDelivery {
        @JacksonXmlProperty(localName = "ResponseTimestamp")
        private String responseTimestamp;

        @JacksonXmlProperty(localName = "VehicleMonitoringDelivery")
        private VehicleMonitoringDelivery vehicleMonitoringDelivery;

        public ServiceDelivery() { this.responseTimestamp = Instant.now().toString(); }

        public String getResponseTimestamp() { return responseTimestamp; }
        public void setResponseTimestamp(String v) { this.responseTimestamp = v; }
        public VehicleMonitoringDelivery getVehicleMonitoringDelivery() { return vehicleMonitoringDelivery; }
        public void setVehicleMonitoringDelivery(VehicleMonitoringDelivery v) { this.vehicleMonitoringDelivery = v; }
    }

    // ── VehicleMonitoringDelivery ──────────────────────────────────────────────
    public static class VehicleMonitoringDelivery {
        @JacksonXmlProperty(localName = "ResponseTimestamp")
        private String responseTimestamp;

        @JacksonXmlProperty(localName = "Status")
        private boolean status = true;

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "VehicleActivity")
        private List<VehicleActivity> vehicleActivity = new ArrayList<>();

        public VehicleMonitoringDelivery() { this.responseTimestamp = Instant.now().toString(); }

        public String getResponseTimestamp() { return responseTimestamp; }
        public void setResponseTimestamp(String v) { this.responseTimestamp = v; }
        public boolean isStatus() { return status; }
        public void setStatus(boolean status) { this.status = status; }
        public List<VehicleActivity> getVehicleActivity() { return vehicleActivity; }
        public void setVehicleActivity(List<VehicleActivity> v) { this.vehicleActivity = v; }
    }

    // ── VehicleActivity ────────────────────────────────────────────────────────
    public static class VehicleActivity {
        @JacksonXmlProperty(localName = "RecordedAtTime")
        private String recordedAtTime;

        @JacksonXmlProperty(localName = "MonitoredVehicleJourney")
        private MonitoredVehicleJourney monitoredVehicleJourney;

        public VehicleActivity() { this.recordedAtTime = Instant.now().toString(); }

        public String getRecordedAtTime() { return recordedAtTime; }
        public void setRecordedAtTime(String v) { this.recordedAtTime = v; }
        public MonitoredVehicleJourney getMonitoredVehicleJourney() { return monitoredVehicleJourney; }
        public void setMonitoredVehicleJourney(MonitoredVehicleJourney v) { this.monitoredVehicleJourney = v; }
    }

    // ── MonitoredVehicleJourney ────────────────────────────────────────────────
    public static class MonitoredVehicleJourney {

        @JacksonXmlProperty(localName = "VehicleRef")
        private String vehicleRef;

        @JacksonXmlProperty(localName = "FramedVehicleJourneyRef")
        private FramedVehicleJourneyRef framedVehicleJourneyRef;

        @JacksonXmlProperty(localName = "VehicleLocation")
        private VehicleLocation vehicleLocation;

        @JacksonXmlProperty(localName = "Bearing")
        private Double bearing;

        @JacksonXmlProperty(localName = "Occupancy")
        private String occupancy;

        @JacksonXmlProperty(localName = "Delay")
        private String delay;

        @JacksonXmlProperty(localName = "Accessibility")
        private Accessibility accessibility;

        @JacksonXmlProperty(localName = "MonitoredCall")
        private MonitoredCall monitoredCall;

        @JacksonXmlElementWrapper(localName = "PreviousCalls")
        @JacksonXmlProperty(localName = "PreviousCall")
        private List<PreviousCall> previousCalls;

        @JacksonXmlProperty(localName = "Extensions")
        private Extensions extensions;

        public String getVehicleRef() { return vehicleRef; }
        public void setVehicleRef(String v) { this.vehicleRef = v; }
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
        public Accessibility getAccessibility() { return accessibility; }
        public void setAccessibility(Accessibility v) { this.accessibility = v; }
        public MonitoredCall getMonitoredCall() { return monitoredCall; }
        public void setMonitoredCall(MonitoredCall v) { this.monitoredCall = v; }
        public List<PreviousCall> getPreviousCalls() { return previousCalls; }
        public void setPreviousCalls(List<PreviousCall> v) { this.previousCalls = v; }
        public Extensions getExtensions() { return extensions; }
        public void setExtensions(Extensions v) { this.extensions = v; }
    }

    // ── FramedVehicleJourneyRef ────────────────────────────────────────────────
    public static class FramedVehicleJourneyRef {
        @JacksonXmlProperty(localName = "DataFrameRef")
        private String dataFrameRef;

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

    // ── Accessibility ──────────────────────────────────────────────────────────
    public static class Accessibility {
        @JacksonXmlProperty(localName = "WheelchairAccess")
        private Boolean wheelchairAccess;

        public Accessibility() {}
        public Accessibility(Boolean wheelchairAccess) { this.wheelchairAccess = wheelchairAccess; }

        public Boolean getWheelchairAccess() { return wheelchairAccess; }
        public void setWheelchairAccess(Boolean v) { this.wheelchairAccess = v; }
    }

    // ── MonitoredCall ──────────────────────────────────────────────────────────
    public static class MonitoredCall {
        @JacksonXmlProperty(localName = "StopPointName")
        private String stopPointName;

        @JacksonXmlProperty(localName = "VehicleAtStop")
        private boolean vehicleAtStop = false;

        public MonitoredCall() {}
        public MonitoredCall(String stopPointName) { this.stopPointName = stopPointName; }

        public String getStopPointName() { return stopPointName; }
        public void setStopPointName(String v) { this.stopPointName = v; }
        public boolean isVehicleAtStop() { return vehicleAtStop; }
        public void setVehicleAtStop(boolean v) { this.vehicleAtStop = v; }
    }

    // ── PreviousCall ───────────────────────────────────────────────────────────
    public static class PreviousCall {
        @JacksonXmlProperty(localName = "StopPointName")
        private String stopPointName;

        public PreviousCall() {}
        public PreviousCall(String stopPointName) { this.stopPointName = stopPointName; }

        public String getStopPointName() { return stopPointName; }
        public void setStopPointName(String v) { this.stopPointName = v; }
    }

    // ── Extensions ────────────────────────────────────────────────────────────
    public static class Extensions {
        @JacksonXmlProperty(localName = "Velocity")
        private Double velocity;

        @JacksonXmlProperty(localName = "NumberOfSeats")
        private Integer numberOfSeats;

        @JacksonXmlProperty(localName = "Passengers")
        private Integer passengers;



        public Extensions() {}

        public Double getVelocity() { return velocity; }
        public void setVelocity(Double v) { this.velocity = v; }
        public Integer getNumberOfSeats() { return numberOfSeats; }
        public void setNumberOfSeats(Integer v) { this.numberOfSeats = v; }
        public Integer getPassengers() { return passengers; }
        public void setPassengers(Integer v) { this.passengers = v; }
    }
}
