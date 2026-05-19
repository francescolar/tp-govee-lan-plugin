# Touch Portal Govee LAN Plugin

A Touch Portal plugin to control Govee lights over the local network using Govee's official LAN API (UDP). No cloud, no API key, no rate limits.

I wrote this because the existing Govee plugins I found for Touch Portal all go through the REST API, which means an API key and a round-trip to Govee's servers for every button press. Since most modern Govee devices already expose a LAN API, talking to them directly is simpler and instant.

## What's in it

Eight actions: turn on/off, toggle, turn ALL on/off (master switch), set brightness, adjust brightness by ±N, set color, set color temperature, adjust color temperature by ±N kelvin.

Two connectors (the things you can put on sliders and dials in Touch Portal) for brightness and color temperature. Both work as a slider and as a dial.

Four states per discovered device — on/off, brightness, color temperature, and color as a hex string. These are the values you can use in `IF` conditions inside Touch Portal flows. Statuses are polled every 5 seconds so Touch Portal stays in sync with reality, even if you also use the Govee app or a physical button on the device.

Device discovery runs once when the plugin starts. If you add a new Govee device later, disable and re-enable the plugin in Touch Portal to trigger a fresh scan.

There is no hue/color connector. I tried, but neither a slider nor a dial really makes sense for picking a color, and Touch Portal doesn't let plugins render a proper color picker on a button. If you need a specific color, use the Set Color action — the color picker in the action config works fine.

## Requirements

- Touch Portal 4.0 or newer (the manifest uses `sdk: 7` and `supportedTypes` for dial connectors)
- Java 17 or newer installed on the machine where Touch Portal runs
- A Govee device that supports the LAN API — official compatibility list at https://app-h5.govee.com/user-manual/wlan-guide
- All devices on the same LAN as the Touch Portal PC, with UDP ports 4001/4002/4003 reachable (no AP/client isolation)

## Installation

1. Download `TouchPortalGoveeLANPlugin.tpp` and `setup-firewall.ps1` from the [Releases](../../releases) page.
2. Run `setup-firewall.ps1` first — it will prompt for admin rights and open the three UDP ports the plugin needs. You only need to do this once.
3. In Touch Portal go to Settings → Import Plugin → pick the `.tpp` file.
4. When Touch Portal asks whether to trust and start the plugin, say yes.

## One-time setup

**Enable LAN Control on each device.** In the Govee Home app, open the device, tap the gear icon, and turn on "LAN Control". If the toggle isn't there, that model doesn't support the LAN API.

**Give each device a static local IP.** The plugin tracks devices by IP. If the router hands out a new one after a reboot, the action you configured in Touch Portal silently breaks. Fix it once in your router's admin panel via DHCP reservation (also called "static lease" or "IP binding").

## Usage notes

Brightness is 1 to 100 (Govee's range, not 0 to 100). Color temperature is 2000 to 9000 Kelvin. Values outside those ranges are clamped automatically.

States are named `Govee <ip> (<sku>) - On/Off`, `... - Brightness`, and so on. They appear in the Touch Portal state picker once the first scan completes, and can be used in `IF` conditions in flows.

## Troubleshooting

If "Number of Devices" stays at 0: check that LAN Control is enabled in the Govee app for at least one device, that the PC and the device are on the same WiFi without AP isolation, and that `setup-firewall.ps1` was run successfully.

If an action does nothing when you press the button: open Touch Portal logs (Settings → Logs) and look for warnings from `TouchPortalGoveeLANPlugin`. A message like `Impossible to retrieve Action Data Item ... arg0` means you have an old build — download the latest release.

If sliders or dials don't show the plugin connectors, your Touch Portal version is too old for `supportedTypes`.

## Known limitations

The sliders and dials don't visually move on their own when the device state changes from outside Touch Portal (e.g. you change brightness from the Govee app). The state values used in `IF` conditions are kept up to date by the 5-second poll, but the connector widgets aren't repositioned. Might fix this eventually.

## Building from source

The plugin depends on [govee-lan-sdk](https://github.com/francescolar/govee-lan-sdk), which needs to be available in the local Maven cache before building. No local Java or Maven installation required — everything runs via Docker.

```bash
# Install the SDK into the local Maven cache first
docker run --rm -v "$HOME/.m2:/root/.m2" -v "$(pwd)/../govee-lan-sdk:/sdk" -w /sdk \
  maven:3.9-eclipse-temurin-17 mvn install -DskipTests

# Then build the plugin
docker run --rm -v "$HOME/.m2:/root/.m2" -v "$(pwd):/app" -w /app \
  maven:3.9-eclipse-temurin-17 mvn package -DskipTests
```

Output: `target/TouchPortalGoveeLANPlugin.tpp`

Once the SDK is published on Maven Central, the first step won't be necessary anymore.

## Credits

Touch Portal Java SDK by ChristopheCVB: https://github.com/ChristopheCVB/TouchPortalPluginSDK

Govee LAN SDK: https://github.com/francescolar/govee-lan-sdk

Govee LAN API documentation: https://app-h5.govee.com/user-manual/wlan-guide

## License

MIT
