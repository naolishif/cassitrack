package it.unicas.omnimove.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Fetches live weather data for Cassino from OpenWeatherMap.
 *
 * Used by the journey planner to give weather-aware suggestions.
 * For example: warns passengers not to take a bike when raining.
 *
 * If the API key is not configured, defaults to CLEAR weather
 * so the journey planner still works without weather data.
 *
 * Cache: weather is cached for 10 minutes so we don't call
 * the API on every journey search.
 */
@Service
public class WeatherService {

    private static final Logger log =
        LoggerFactory.getLogger(WeatherService.class);

    @Value("${weather.api.key:NOT_CONFIGURED}")
    private String apiKey;

    @Value("${weather.api.url:https://api.openweathermap.org/data/2.5/weather}")
    private String apiUrl;

    @Value("${weather.api.city:Cassino}")
    private String city;

    @Value("${weather.api.country:IT}")
    private String country;

    // Cache: refresh every 10 minutes
    private WeatherData cachedWeather = null;
    private long cacheTimestamp = 0;
    private static final long CACHE_TTL_MS = 10 * 60 * 1000;

    // ── Weather condition enum ────────────────────────────────

    public enum WeatherCondition {
        CLEAR,       // ☀️ sunny — all modes fine
        CLOUDY,      // ☁️ overcast — all modes fine
        RAIN,        // 🌧️ rain — warn bike/scooter
        HEAVY_RAIN,  // ⛈️ heavy rain — bus strongly preferred
        STORM,       // ⛈️ thunderstorm — bus only
        SNOW,        // ❄️ snow — bus only
        HOT,         // 🌡️ very hot — suggest early travel
        WINDY        // 💨 strong wind — warn scooter
    }

    // ── Weather data container ────────────────────────────────

    public static class WeatherData {
        public WeatherCondition condition;
        public double tempCelsius;
        public double windSpeedMs;
        public int humidity;
        public String description;
        public String emoji;
        public String suggestion;   // shown at top of results
    }

    // ── Main method: get current weather ─────────────────────

    public WeatherData getCurrentWeather() {
        // Return cached data if still fresh
        if (cachedWeather != null &&
                System.currentTimeMillis() - cacheTimestamp < CACHE_TTL_MS) {
            return cachedWeather;
        }

        // If no API key configured, return clear weather default
        if ("NOT_CONFIGURED".equals(apiKey) || apiKey.isBlank()) {
            log.warn("Weather API key not configured — using default CLEAR weather");
            return defaultWeather();
        }

        try {
            WebClient client = WebClient.create();
            Map response = client.get()
                .uri(apiUrl + "?q=" + city + "," + country
                    + "&appid=" + apiKey + "&units=metric")
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            WeatherData data = parseResponse(response);
            cachedWeather = data;
            cacheTimestamp = System.currentTimeMillis();
            log.info("Weather updated: {} {}°C wind={}m/s",
                data.description, data.tempCelsius, data.windSpeedMs);
            return data;

        } catch (Exception e) {
            log.warn("Weather API call failed: {} — using default", e.getMessage());
            return defaultWeather();
        }
    }

    // ── Parse OpenWeatherMap response ─────────────────────────

    @SuppressWarnings("unchecked")
    private WeatherData parseResponse(Map response) {
        WeatherData data = new WeatherData();

        Map main = (Map) response.get("main");
        data.tempCelsius  = ((Number) main.get("temp")).doubleValue();
        data.humidity     = ((Number) main.get("humidity")).intValue();

        Map wind = (Map) response.get("wind");
        data.windSpeedMs  = ((Number) wind.get("speed")).doubleValue();

        List<Map> weatherList = (List<Map>) response.get("weather");
        Map weather = weatherList.get(0);
        int id = ((Number) weather.get("id")).intValue();
        data.description  = (String) weather.get("description");

        data.condition    = mapCondition(id, data.tempCelsius, data.windSpeedMs);
        data.emoji        = getEmoji(data.condition);
        data.suggestion   = buildSuggestion(data);
        return data;
    }

    // ── Map OWM weather ID to condition ──────────────────────
    // Full list: https://openweathermap.org/weather-conditions
    // 2xx = Thunder, 3xx = Drizzle, 5xx = Rain,
    // 6xx = Snow, 7xx = Atmosphere, 800 = Clear, 8xx = Clouds

    private WeatherCondition mapCondition(int id, double temp, double wind) {
        if (id >= 200 && id < 300) return WeatherCondition.STORM;
        if (id >= 300 && id < 400) return WeatherCondition.RAIN;
        if (id == 500 || id == 501) return WeatherCondition.RAIN;
        if (id >= 502 && id < 600) return WeatherCondition.HEAVY_RAIN;
        if (id >= 600 && id < 700) return WeatherCondition.SNOW;
        if (id == 800) {
            if (temp > 32) return WeatherCondition.HOT;
            if (wind > 10) return WeatherCondition.WINDY;
            return WeatherCondition.CLEAR;
        }
        if (id > 800) return WeatherCondition.CLOUDY;
        return WeatherCondition.CLEAR;
    }

    // ── Overall suggestion shown at top of journey results ───

    private String buildSuggestion(WeatherData data) {
        String temp = String.format("%.0f°C", data.tempCelsius);
        return switch (data.condition) {
            case CLEAR       -> "☀️ Great weather in Cassino (" + temp + ") — all transport options available!";
            case CLOUDY      -> "☁️ Overcast but dry (" + temp + ") — all transport options available.";
            case RAIN        -> "🌧️ Rain in Cassino (" + temp + ") — bus is the best choice today.";
            case HEAVY_RAIN  -> "⛈️ Heavy rain (" + temp + ") — take the bus or walk with an umbrella.";
            case STORM       -> "⛈️ Storm warning (" + temp + ") — bus only. Avoid outdoor modes.";
            case SNOW        -> "❄️ Snow in Cassino (" + temp + ") — bus only. Roads may be slippery.";
            case HOT         -> "🌡️ Very hot today (" + temp + ") — consider travelling early morning.";
            case WINDY       -> "💨 Strong wind (" + temp + ") — scooter not recommended today.";
        };
    }

    // ── Per-mode warning returned with each journey option ───

    public String getModeWarning(WeatherCondition condition, String mode) {
        return switch (mode.toUpperCase()) {
            case "BIKE" -> switch (condition) {
                case RAIN, HEAVY_RAIN -> "⚠️ Not recommended — rain expected, slippery roads";
                case STORM            -> "❌ Not safe during storm";
                case SNOW             -> "❌ Not safe — icy roads";
                case HOT              -> "🌡️ Very hot — bring water";
                case WINDY            -> "💨 Strong wind — difficult cycling";
                default               -> null;
            };
            case "SCOOTER" -> switch (condition) {
                case RAIN, HEAVY_RAIN -> "⚠️ Slippery roads — take care";
                case STORM            -> "❌ Not safe during storm";
                case SNOW             -> "❌ Not safe — icy roads";
                case WINDY            -> "💨 Strong wind — take care";
                default               -> null;
            };
            case "WALK" -> switch (condition) {
                case RAIN             -> "🌂 Bring an umbrella";
                case HEAVY_RAIN       -> "🌧️ Heavy rain — consider the bus instead";
                case STORM            -> "⛈️ Storm — not recommended";
                case SNOW             -> "❄️ Snow — dress warmly, watch your step";
                case HOT              -> "🌡️ Very hot — bring water and sunscreen";
                default               -> null;
            };
            case "BUS" -> switch (condition) {
                case RAIN, HEAVY_RAIN, STORM, SNOW -> "✅ Best choice in this weather";
                default                             -> null;
            };
            default -> null;
        };
    }

    // ── Emoji for the condition ───────────────────────────────

    private String getEmoji(WeatherCondition condition) {
        return switch (condition) {
            case CLEAR      -> "☀️";
            case CLOUDY     -> "☁️";
            case RAIN       -> "🌧️";
            case HEAVY_RAIN -> "⛈️";
            case STORM      -> "⛈️";
            case SNOW       -> "❄️";
            case HOT        -> "🌡️";
            case WINDY      -> "💨";
        };
    }

    // ── Safe default when API unavailable ────────────────────

    private WeatherData defaultWeather() {
        WeatherData data = new WeatherData();
        data.condition   = WeatherCondition.CLEAR;
        data.tempCelsius = 20.0;
        data.windSpeedMs = 3.0;
        data.humidity    = 60;
        data.description = "weather data unavailable";
        data.emoji       = "🌤️";
        data.suggestion  = "🌤️ Weather data not available — check conditions before travelling.";
        return data;
    }
}
