# Camera mode

Camera mode shows your Home Assistant's cameras on a TV, a wall tablet or a phone: a grid of still pictures, or one camera live, full screen. It's off unless you turn it on, on each device, and it only runs while it's open: nothing watches your cameras in the background, and nothing pops up over other apps.

It uses the cameras Home Assistant already has, so it works with any camera integration. It's written with **UniFi Protect** in mind: a camera's high, medium and low streams count as one camera, and its motion, person, vehicle and doorbell sensors are found on its own device.

## Turn it on

1. **Settings → Cameras → Camera mode** on the device (needs Home Assistant connected).
2. Under **Cameras on this TV** (or tablet, or phone), switch on the cameras it may show. A camera that isn't switched on here never appears on this device.
3. Open it: the **Cameras** card in the TV home's Lena row, the camera button on the wall display's bar, **Open camera mode** in Settings, or ask Lena to "show the cameras" (or "show me the front door camera", which opens that one live).

**UniFi Protect:** Home Assistant turns on only each camera's high resolution stream. Turn on the **low resolution** one too (the camera's device page in Home Assistant, its camera entities), so the grid's pictures come from it and your console isn't asked for full-size snapshots.

## The views

| View | What it shows |
|---|---|
| **Show all** | Every included camera, in a grid of still pictures, refreshed every 2 seconds. A camera that sees motion gets an amber edge. |
| **Selected only** | The same grid with only the cameras you selected (all of them while none are). |
| **Spotlight select** | One camera live, full screen. Left and right go to the camera before or after. |
| **Spotlight on motion** | The grid until a camera sees motion, then that camera live until 30 seconds after its motion stops, then the grid again. If two cameras see motion, the newest wins. |
| **Rotate** | Each selected camera (or each included one, when none are selected) live in turn, 10 seconds each. Motion interrupts it the same way, then it carries on. |

From any grid, choosing a camera puts it live in the spotlight; Back returns to the grid. Settings → Cameras → **Show at first** is the view it opens on.

## The remote and touch

| Do | TV remote | Tablet or phone |
|---|---|---|
| Move between pictures | Arrows | Scroll |
| Spotlight a camera | OK | Tap |
| Select or unselect a camera | Hold OK | Long press |
| Next or previous camera in the spotlight | Right, left | The arrows on the picture |
| Choose the view | Menu (☰) | The views along the top |
| Back to the grid, then out | Back | Back |

In camera mode, holding OK selects a camera instead of talking to Lena.

## Settings → Cameras

| Setting | Default | |
|---|---|---|
| Camera mode | Off | On each device |
| Each camera | Off | Whether this device may show it |
| Its motion sensor | Found on its device | Or one you pick, or none |
| Show at first | Show all | |
| What counts as motion | People, vehicles and the doorbell | Or any motion, or the doorbell only. A camera that can't tell people apart uses its motion sensor. |
| Take turns every | 10 seconds | 5, 10, 20 or 30 |
| Stay on motion for | 30 seconds | 15, 30 or 60 |
| Sound | Off | The camera in the spotlight |

The doorbell's ring comes from UniFi Protect's doorbell event, which needs the integration set up with a local Protect user (not "API key only").

## How it plays

- **Still pictures** come from Home Assistant's camera proxy, fetched by the app with its token, kept in memory only.
- **Live**: Home Assistant's own WebRTC stream (its built-in go2rtc), in a small page inside the app. The app does every message to Home Assistant, so the page never holds the token. If WebRTC doesn't start within 12 seconds it tries Home Assistant's HLS stream (slower to start; UniFi's documentation suggests turning on low-latency HLS); if that fails too, the spotlight keeps showing still pictures and says so.
- **Motion**: one subscription to the included cameras' sensors and events, while camera mode is open.
- Only one camera plays live at a time, which an Nvidia Shield handles comfortably.
