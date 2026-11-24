# XRayAlert

Paper plugin for Minecraft 1.21.11 (Java 21). Admins get a message whenever a player mines a watched ore:

`[X-Ray] Steve mined 5x Diamond Ore (vein) (world 12 -54 30)`

## Build

    mvn package

The jar is created at `target/XRayAlert-1.0.0.jar`. Alternatively, push the project to GitHub and download the jar from the Actions artifacts.

## Install

Copy the jar into `plugins/`, restart the server and edit `plugins/XRayAlert/config.yml`.

## Config

- `language`: `en` or `de`
- `mode`: `VEIN` (one alert per vein) or `SINGLE` (one alert per block)
- `vein.max-size`: maximum blocks counted as one vein
- `vein.tracking-seconds`: how long the rest of a reported vein stays silent
- `notify.console`, `notify.show-location`, `notify.clickable-teleport`
- `ignore.creative`, `ignore.worlds`
- `ores`: list of watched ores (deepslate variants are included automatically)

Messages can be edited in `plugins/XRayAlert/lang/en.yml` and `de.yml` (MiniMessage format).

## Commands

- `/xrayalert reload` (`xrayalert.admin`)
- `/xrayalert toggle` (`xrayalert.notify`)

## Permissions

- `xrayalert.notify`: receive alerts (default: op)
- `xrayalert.admin`: reload the config (default: op)
- `xrayalert.bypass`: do not trigger alerts
