package io.github.govee.tp;

import com.christophecvb.touchportal.TouchPortalPlugin;
import com.christophecvb.touchportal.annotations.*;
import com.christophecvb.touchportal.model.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.govee.lan.GoveeController;
import io.github.govee.lan.GoveeDiscovery;
import io.github.govee.lan.model.DeviceStatus;
import io.github.govee.lan.model.GoveeDevice;

import java.awt.Color;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.*;

@SuppressWarnings("unused")
public class GoveePlugin extends TouchPortalPlugin implements TouchPortalPlugin.TouchPortalPluginListener {

    private static final Logger LOG = Logger.getLogger(GoveePlugin.class.getName());

    static final String PLUGIN_ID   = "io.github.govee.tp.GoveePlugin";
    static final String CATEGORY_ID = PLUGIN_ID + ".BaseCategory";
    static final String CATEGORY    = "BaseCategory";

    static final String ID_TURN_DEVICE_LIST    = CATEGORY_ID + ".action.turnDevice.data.deviceList";
    static final String ID_TOGGLE_DEVICE_LIST  = CATEGORY_ID + ".action.toggleDevice.data.deviceList";
    static final String ID_BRIGHT_DEVICE_LIST  = CATEGORY_ID + ".action.setBrightness.data.deviceList";
    static final String ID_COLOR_DEVICE_LIST   = CATEGORY_ID + ".action.setColor.data.deviceList";
    static final String ID_TEMP_DEVICE_LIST    = CATEGORY_ID + ".action.setColorTemp.data.deviceList";
    static final String ID_ADJ_BRIGHT_DEVICE   = CATEGORY_ID + ".action.adjustBrightness.data.deviceList";
    static final String ID_ADJ_TEMP_DEVICE     = CATEGORY_ID + ".action.adjustColorTemp.data.deviceList";
    static final String ID_CONN_BRIGHT_DEVICE  = CATEGORY_ID + ".connector.brightness.data.deviceList";
    static final String ID_CONN_TEMP_DEVICE    = CATEGORY_ID + ".connector.colorTemp.data.deviceList";

    static final String CONN_BRIGHTNESS = CATEGORY_ID + ".connector.brightness";
    static final String CONN_COLOR_TEMP = CATEGORY_ID + ".connector.colorTemp";

    private static final int STATUS_POLL_SECONDS = 5;
    private static final int TEMP_MIN = 2000;
    private static final int TEMP_MAX = 9000;

    private final Map<String, GoveeController> controllers = new LinkedHashMap<>();
    private ScheduledExecutorService scheduler;

    @Setting(name = "Scan Timeout (ms)", defaultValue = "3000")
    public int scanTimeoutMs = 3000;

    @Setting(name = "Number of Devices", defaultValue = "0", isReadOnly = true)
    private String numDevices;

    public GoveePlugin() {
        super(true);
    }

    public static void main(String... args) {
        if (args != null && args.length == 1 && "start".equals(args[0])) {
            GoveePlugin plugin = new GoveePlugin();
            plugin.connectThenPairAndListen(plugin);
        }
    }

    // ── actions ───────────────────────────────────────────────────────────

    @Action(
        name = "Govee LAN: Turn On/Off",
        description = "Turn a Govee device on or off via LAN",
        format = "Turn {$onOff$} device {$deviceList$}",
        categoryId = CATEGORY
    )
    private void turnDevice(
            @Data(valueChoices = {"ON", "OFF"}, defaultValue = "ON") String[] onOff,
            @Data String[] deviceList) {
        withController(deviceList[0], ctrl -> {
            boolean on = "ON".equals(onOff[0]);
            ctrl.turn(on);
            updateOnOffState(ctrl.getIp(), on);
        });
    }

    @Action(
        name = "Govee LAN: Toggle On/Off",
        description = "Toggle a Govee device (invert current state)",
        format = "Toggle device {$deviceList$}",
        categoryId = CATEGORY
    )
    private void toggleDevice(@Data String[] deviceList) {
        withController(deviceList[0], ctrl -> {
            DeviceStatus s = ctrl.getStatus();
            boolean newOn = !s.isOn();
            ctrl.turn(newOn);
            updateOnOffState(ctrl.getIp(), newOn);
        });
    }

    @Action(
        name = "Govee LAN: Turn ALL On/Off",
        description = "Turn every discovered Govee device on or off at once",
        format = "Turn {$onOff$} ALL Govee devices",
        categoryId = CATEGORY
    )
    private void turnAllDevices(
            @Data(valueChoices = {"ON", "OFF"}, defaultValue = "OFF") String[] onOff) {
        boolean on = "ON".equals(onOff[0]);
        for (GoveeController ctrl : controllers.values()) {
            try {
                ctrl.turn(on);
                updateOnOffState(ctrl.getIp(), on);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Turn-all failed for " + ctrl.getIp(), e);
            }
        }
    }

    @Action(
        name = "Govee LAN: Adjust Brightness (+/-)",
        description = "Increase or decrease current brightness by N (negative to decrease)",
        format = "Adjust brightness of {$deviceList$} by {$brightnessStep$}",
        categoryId = CATEGORY
    )
    private void adjustBrightness(
            @Data String[] deviceList,
            @Data(defaultValue = "10") int brightnessStep) {
        withController(deviceList[0], ctrl -> {
            DeviceStatus s = ctrl.getStatus();
            int next = clamp(s.getBrightness() + brightnessStep, 1, 100);
            ctrl.setBrightness(next);
            sendStateUpdate(stateId(ctrl.getIp(), "brightness"), String.valueOf(next));
        });
    }

    @Action(
        name = "Govee LAN: Adjust Color Temperature (+/-)",
        description = "Increase or decrease current color temperature by N kelvin (negative to decrease)",
        format = "Adjust color temperature of {$deviceList$} by {$tempStep$} K",
        categoryId = CATEGORY
    )
    private void adjustColorTemp(
            @Data String[] deviceList,
            @Data(defaultValue = "500") int tempStep) {
        withController(deviceList[0], ctrl -> {
            DeviceStatus s = ctrl.getStatus();
            int current = s.getColorTemInKelvin();
            if (current <= 0) current = TEMP_MIN;
            int next = clamp(current + tempStep, TEMP_MIN, TEMP_MAX);
            ctrl.setColorTemp(next);
            sendStateUpdate(stateId(ctrl.getIp(), "colorTemp"), String.valueOf(next));
        });
    }

    @Action(
        name = "Govee LAN: Set Brightness",
        description = "Set brightness (1-100)",
        format = "Set brightness of {$deviceList$} to {$brightnessVal$}%",
        categoryId = CATEGORY
    )
    private void setBrightness(
            @Data String[] deviceList,
            @Data(minValue = 1, maxValue = 100, defaultValue = "100") int brightnessVal) {
        int v = clamp(brightnessVal, 1, 100);
        withController(deviceList[0], ctrl -> {
            ctrl.setBrightness(v);
            sendStateUpdate(stateId(ctrl.getIp(), "brightness"), String.valueOf(v));
        });
    }

    @Action(
        name = "Govee LAN: Set Color",
        description = "Set RGB color",
        format = "Set color of {$deviceList$} to {$colorPicker$}",
        categoryId = CATEGORY
    )
    private void setColor(
            @Data String[] deviceList,
            @Data(isColor = true, defaultValue = "#FF0000FF") String colorPicker) {
        Color c = hexToColor(colorPicker);
        withController(deviceList[0], ctrl -> {
            ctrl.setColor(c.getRed(), c.getGreen(), c.getBlue());
            sendStateUpdate(stateId(ctrl.getIp(), "color"),
                    String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue()));
        });
    }

    @Action(
        name = "Govee LAN: Set Color Temperature",
        description = "Set color temperature (2000-9000 K). Out-of-range values are clamped.",
        format = "Set color temperature of {$deviceList$} to {$tempVal$} K",
        categoryId = CATEGORY
    )
    private void setColorTemp(
            @Data(defaultValue = "4000") int tempVal,
            @Data String[] deviceList) {
        int k = clamp(tempVal, TEMP_MIN, TEMP_MAX);
        withController(deviceList[0], ctrl -> {
            ctrl.setColorTemp(k);
            sendStateUpdate(stateId(ctrl.getIp(), "colorTemp"), String.valueOf(k));
        });
    }

    // ── discovery + polling ───────────────────────────────────────────────

    private void startSchedulers() {
        if (scheduler != null) scheduler.shutdownNow();
        scheduler = Executors.newScheduledThreadPool(2);
        scheduler.schedule(this::scanAndUpdate, 0, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::pollAllStatuses, STATUS_POLL_SECONDS, STATUS_POLL_SECONDS, TimeUnit.SECONDS);
    }

    private void scanAndUpdate() {
        try {
            List<GoveeDevice> found = GoveeDiscovery.scan(scanTimeoutMs);
            controllers.clear();
            for (GoveeDevice d : found) {
                String label = d.getIp() + " (" + d.getSku() + ")";
                controllers.put(label, new GoveeController(d.getIp()));
                ensureStatesForDevice(d.getIp(), label);
            }

            String[] labels = controllers.keySet().toArray(new String[0]);
            sendChoiceUpdate(ID_TURN_DEVICE_LIST,   labels);
            sendChoiceUpdate(ID_TOGGLE_DEVICE_LIST, labels);
            sendChoiceUpdate(ID_BRIGHT_DEVICE_LIST, labels);
            sendChoiceUpdate(ID_COLOR_DEVICE_LIST,  labels);
            sendChoiceUpdate(ID_TEMP_DEVICE_LIST,   labels);
            sendChoiceUpdate(ID_ADJ_BRIGHT_DEVICE,  labels);
            sendChoiceUpdate(ID_ADJ_TEMP_DEVICE,    labels);
            sendChoiceUpdate(ID_CONN_BRIGHT_DEVICE, labels);
            sendChoiceUpdate(ID_CONN_TEMP_DEVICE,   labels);
            sendSettingUpdate("Number of Devices", String.valueOf(found.size()), false);

            LOG.info("Govee LAN scan: " + found.size() + " device(s) found.");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Govee LAN scan failed", e);
        }
    }

    private void pollAllStatuses() {
        for (Map.Entry<String, GoveeController> e : controllers.entrySet()) {
            try {
                DeviceStatus s = e.getValue().getStatus();
                String ip = e.getValue().getIp();
                sendStateUpdate(stateId(ip, "onOff"),      s.isOn() ? "on" : "off");
                sendStateUpdate(stateId(ip, "brightness"), String.valueOf(s.getBrightness()));
                sendStateUpdate(stateId(ip, "colorTemp"),  String.valueOf(s.getColorTemInKelvin()));
                sendStateUpdate(stateId(ip, "color"),
                        String.format("#%02X%02X%02X", s.getR(), s.getG(), s.getB()));
            } catch (Exception ex) {
                LOG.log(Level.FINE, "Status poll failed for " + e.getKey(), ex);
            }
        }
    }

    private final Set<String> declaredStates = new HashSet<>();

    private void ensureStatesForDevice(String ip, String label) {
        if (!declaredStates.add(ip)) return;
        String desc = "Govee " + label;
        sendCreateState(CATEGORY, stateId(ip, "onOff"),      desc + " - On/Off",            "off");
        sendCreateState(CATEGORY, stateId(ip, "brightness"), desc + " - Brightness",        "0");
        sendCreateState(CATEGORY, stateId(ip, "colorTemp"),  desc + " - Color Temperature", "0");
        sendCreateState(CATEGORY, stateId(ip, "color"),      desc + " - Color (hex)",       "#000000");
    }

    private static String stateId(String ip, String field) {
        return CATEGORY_ID + ".state." + ip.replace('.', '_') + "." + field;
    }

    private void updateOnOffState(String ip, boolean on) {
        sendStateUpdate(stateId(ip, "onOff"), on ? "on" : "off");
    }

    // ── connector handling ────────────────────────────────────────────────

    @Override
    public void onReceived(JsonObject json) {
        if (json == null || !json.has("type")) return;
        if (!"connectorChange".equals(json.get("type").getAsString())) return;

        String connectorIdRaw = json.has("connectorId") ? json.get("connectorId").getAsString() : "";
        int value = json.has("value") ? json.get("value").getAsInt() : 0;

        String shortId = stripConnectorPrefix(connectorIdRaw);
        String device = extractDeviceFromConnectorId(connectorIdRaw, json);
        if (device == null || device.isEmpty()) {
            LOG.fine("connectorChange without device: " + connectorIdRaw);
            return;
        }

        if (shortId.startsWith(CONN_BRIGHTNESS)) {
            int brightness = Math.max(1, value);
            withController(device, ctrl -> {
                ctrl.setBrightness(brightness);
                sendStateUpdate(stateId(ctrl.getIp(), "brightness"), String.valueOf(brightness));
            });
        } else if (shortId.startsWith(CONN_COLOR_TEMP)) {
            int kelvin = TEMP_MIN + (int)Math.round((TEMP_MAX - TEMP_MIN) * (value / 100.0));
            withController(device, ctrl -> {
                ctrl.setColorTemp(kelvin);
                sendStateUpdate(stateId(ctrl.getIp(), "colorTemp"), String.valueOf(kelvin));
            });
        }
    }

    /** TP prefixes connector IDs with "pc_<pluginId>_" and may append "|key=value|..." */
    private static String stripConnectorPrefix(String raw) {
        String s = raw;
        String prefix = "pc_" + PLUGIN_ID + "_";
        if (s.startsWith(prefix)) s = s.substring(prefix.length());
        int pipe = s.indexOf('|');
        if (pipe >= 0) s = s.substring(0, pipe);
        return s;
    }

    private static String extractDeviceFromConnectorId(String raw, JsonObject json) {
        int pipe = raw.indexOf('|');
        if (pipe >= 0) {
            String tail = raw.substring(pipe + 1);
            for (String kv : tail.split("\\|")) {
                int eq = kv.indexOf('=');
                if (eq > 0 && kv.substring(0, eq).endsWith("deviceList")) {
                    return kv.substring(eq + 1);
                }
            }
        }
        if (json.has("data") && json.get("data").isJsonArray()) {
            JsonArray arr = json.getAsJsonArray("data");
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                String id = o.has("id") ? o.get("id").getAsString() : "";
                if (id.endsWith("deviceList") && o.has("value")) {
                    return o.get("value").getAsString();
                }
            }
        }
        return null;
    }

    // ── TouchPortalPluginListener ─────────────────────────────────────────

    @Override
    public void onInfo(TPInfoMessage msg) {
        startSchedulers();
    }

    @Override
    public void onDisconnected(Exception e) {
        if (scheduler != null) scheduler.shutdownNow();
        System.exit(0);
    }

    @Override public void onListChanged(TPListChangeMessage msg) {}
    @Override public void onBroadcast(TPBroadcastMessage msg) {}
    @Override public void onSettings(TPSettingsMessage msg) {}
    @Override public void onNotificationOptionClicked(TPNotificationOptionClickedMessage msg) {}

    // ── helpers ───────────────────────────────────────────────────────────

    private void withController(String label, Consumer<GoveeController> action) {
        GoveeController ctrl = controllers.get(label);
        if (ctrl == null) {
            LOG.warning("Device not found: " + label);
            return;
        }
        try {
            action.accept(ctrl);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Command failed for " + label, e);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Touch Portal passes colors as #RRGGBBAA */
    private static Color hexToColor(String hex) {
        return new Color(
            Integer.parseInt(hex.substring(1, 3), 16),
            Integer.parseInt(hex.substring(3, 5), 16),
            Integer.parseInt(hex.substring(5, 7), 16)
        );
    }

    private enum Categories {
        @Category(name = "Touch Portal Govee LAN Plugin", imagePath = "images/icon.png")
        BaseCategory
    }
}
