package it.unicas.cassitrack.dto.siri;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@JacksonXmlRootElement(localName = "Siri")
public class Siri {

    @JacksonXmlProperty(localName = "ServiceDelivery")
    private ServiceDelivery serviceDelivery;

    public Siri() {}

    public Siri(ServiceDelivery serviceDelivery) {
        this.serviceDelivery = serviceDelivery;
    }

    public ServiceDelivery getServiceDelivery() { return serviceDelivery; }
    public void setServiceDelivery(ServiceDelivery serviceDelivery) { this.serviceDelivery = serviceDelivery; }

    public static class ServiceDelivery {
        @JacksonXmlProperty(localName = "ResponseTimestamp")
        private String responseTimestamp;

        @JacksonXmlProperty(localName = "VehicleMonitoringDelivery")
        private VehicleMonitoringDelivery vehicleMonitoringDelivery;

        public ServiceDelivery() {
            this.responseTimestamp = Instant.now().toString();
        }

        public String getResponseTimestamp() { return responseTimestamp; }
        public void setResponseTimestamp(String responseTimestamp) { this.responseTimestamp = responseTimestamp; }
        public VehicleMonitoringDelivery getVehicleMonitoringDelivery() { return vehicleMonitoringDelivery; }
        public void setVehicleMonitoringDelivery(VehicleMonitoringDelivery vehicleMonitoringDelivery) { this.vehicleMonitoringDelivery = vehicleMonitoringDelivery; }
    }

    public static class VehicleMonitoringDelivery {
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "VehicleActivity")
        private List<VehicleActivity> vehicleActivity = new ArrayList<>();

        public List<VehicleActivity> getVehicleActivity() { return vehicleActivity; }
        public void setVehicleActivity(List<VehicleActivity> vehicleActivity) { this.vehicleActivity = vehicleActivity; }
    }

    public static class VehicleActivity {
        @JacksonXmlProperty(localName = "RecordedAtTime")
        private String recordedAtTime;

        @JacksonXmlProperty(localName = "MonitoredVehicleJourney")
        private MonitoredVehicleJourney monitoredVehicleJourney;

        public VehicleActivity() {
            this.recordedAtTime = Instant.now().toString();
        }

        public String getRecordedAtTime() { return recordedAtTime; }
        public void setRecordedAtTime(String recordedAtTime) { this.recordedAtTime = recordedAtTime; }
        public MonitoredVehicleJourney getMonitoredVehicleJourney() { return monitoredVehicleJourney; }
        public void setMonitoredVehicleJourney(MonitoredVehicleJourney monitoredVehicleJourney) { this.monitoredVehicleJourney = monitoredVehicleJourney; }
    }

    public static class MonitoredVehicleJourney {
        @JacksonXmlProperty(localName = "VehicleRef")
        private String vehicleRef;

        @JacksonXmlProperty(localName = "VehicleLocation")
        private VehicleLocation vehicleLocation;

        @JacksonXmlProperty(localName = "Velocity")
        private double velocity;

        @JacksonXmlProperty(localName = "WheelchairAccessible")
        private String wheelchairAccessible;

        @JacksonXmlProperty(localName = "NumberOfSeats")
        private Integer numberOfSeats;

        // ← I TRE NUOVI CAMPI, QUI DENTRO
        @JacksonXmlProperty(localName = "Delay")
        private Integer delay;

        @JacksonXmlProperty(localName = "LastStopRef")
        private String lastStopRef;

        @JacksonXmlProperty(localName = "FramedVehicleJourneyRef")
        private String framedVehicleJourneyRef;

        @JacksonXmlProperty(localName = "Passengers")
        private Integer passengers;

        @JacksonXmlProperty(localName = "Capacity")
        private Integer capacity;

        public String getVehicleRef() { return vehicleRef; }
        public void setVehicleRef(String vehicleRef) { this.vehicleRef = vehicleRef; }
        public VehicleLocation getVehicleLocation() { return vehicleLocation; }
        public void setVehicleLocation(VehicleLocation vehicleLocation) { this.vehicleLocation = vehicleLocation; }
        public double getVelocity() { return velocity; }
        public void setVelocity(double velocity) { this.velocity = velocity; }
        public String getWheelchairAccessible() { return wheelchairAccessible; }
        public void setWheelchairAccessible(String wheelchairAccessible) { this.wheelchairAccessible = wheelchairAccessible; }
        public Integer getNumberOfSeats() { return numberOfSeats; }
        public void setNumberOfSeats(Integer numberOfSeats) { this.numberOfSeats = numberOfSeats; }
        public Integer getDelay() { return delay; }
        public void setDelay(Integer delay) { this.delay = delay; }
        public String getLastStopRef() { return lastStopRef; }
        public void setLastStopRef(String lastStopRef) { this.lastStopRef = lastStopRef; }
        public String getFramedVehicleJourneyRef() { return framedVehicleJourneyRef; }
        public void setFramedVehicleJourneyRef(String framedVehicleJourneyRef) { this.framedVehicleJourneyRef = framedVehicleJourneyRef; }
        public Integer getPassengers() {return passengers;}
        public void setPassengers(Integer passengers) {this.passengers = passengers;}
        public Integer getCapacity() {return capacity;}
        public void setCapacity(Integer capacity) {this.capacity = capacity;}

    }

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
        public void setLongitude(double longitude) { this.longitude = longitude; }
        public double getLatitude() { return latitude; }
        public void setLatitude(double latitude) { this.latitude = latitude; }
    }
}