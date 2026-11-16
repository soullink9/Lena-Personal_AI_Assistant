# Camera mode

Camera mode shows your Home Assistant's cameras on a TV, a wall tablet or a phone: a grid of still pictures, or one camera live, full screen. It's off unless you turn it on, on each device, and it only runs while it's open: nothing watches your cameras in the background, and nothing pops up over other apps.

It uses the cameras Home Assistant already has, so it works with any camera integration. It's written with **UniFi Protect** in mind: a camera's high, medium and low streams count as one camera, and its motion, person, vehicle and doorbell sensors are found on its own device.

## Turn it on

1. **Settings → Cameras → Camera mode** on the device (needs Home Assistant connected).
2. Under **Cameras on this TV** (or tablet, or phone), switch on the cameras it may show. A camera that isn't switched on here never appears on this device.
3. Open it: on a TV, the **Cameras** card in the TV home's Lena row, right after **Talk to Lena**; on a tablet or phone, the camera button on the wall display's bar. Or **Open camera mode** in Settings, or ask Lena to "show the cameras" (or "show me the front door camera", which opens that one live). On a TV, camera mode isn't on the dashboards' bar: a dashboard there is only Home Assistant's.

**UniFi Protect:** Home Assistant turns on only each camera's high resolution stream. Turn on the **low resolution** one too (the camera's device page in Home Assistant, its camera entities), so the grid's pictures come from it and your console isn't asked for full-size snapshots.

## How it looks

Like UniFi Protect's live view: the pictures fill the whole screen, on a TV too, edge to edge with hairline gaps and nothing else taking room. The grid picks how many go across so each picture keeps close to its shape, and the last row's pictures widen to fill it, so no cell is empty. Each picture has only a small label in its corner: its name, and **Motion** (with an orange edge) while it sees motion. A tick marks a selected camera, and a blue edge the one the remote is on. Full screen, the corner says **Live** with a red dot once the live picture moves (**Connecting…** until then).

The bar (**Cameras**, the view and how many, the date and time) floats over the top only for a few seconds after a touch or a key press, then fades away. On a touch screen it also has back and the views; on a TV, what the remote's keys do. While Home Assistant can't be reached, **Reconnecting** shows along the bottom.

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
| Choose the view | Menu (☰) | Touch the screen, then the views along the top |
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

- **Still pictures** come from Home Assistant's camera proxy, fetched by the app with its token, kept in memory only. Home Assistant scales each one to the size it's shown at (a grid tile, or the whole screen behind the spotlight), so a 4K camera doesn't send a full-size picture for a small tile. Each camera is fetched on its own: a picture appears as soon as it arrives, and a camera slow to answer doesn't hold up the others.
- **Live**: everything comes from Home Assistant, never from the cameras, so cameras on their own network (a VLAN only Home Assistant can reach) work without changing it. Three ways, each tried when the one before gives no picture:
  1. Home Assistant's own **WebRTC** stream (its built-in go2rtc), in a small page inside the app. The app does every message to Home Assistant, so the page never holds the token. The picture runs about a quarter of a second behind, which keeps it smooth over Wi-Fi when a few packets arrive late. It has 12 seconds to draw a picture.
  2. Home Assistant's **HLS** stream, in the same page (slower to start; UniFi's documentation suggests turning on low-latency HLS). 24 seconds.
  3. Home Assistant's **picture stream** (MJPEG, `/api/camera_proxy_stream`), read by the app with its token: a few pictures a second, but it works wherever still pictures do. The spotlight says **Live, picture by picture**.

  If none gives a picture, the spotlight keeps showing still pictures and says so. **Live** shows only once a frame is actually on screen; a stream that opens but never draws one (WebRTC whose video can't reach the device, or a codec it can't decode) moves on to the next way instead of showing Live over a black screen. A camera starts at the way that worked last time it was shown, rather than waiting through the ones that failed again.
- **Motion**: one subscription to the included cameras' sensors and events, while camera mode is open.
- **If the live picture is slow to start** (Connecting… for a long time, then a picture that lags behind): the camera's stream probably isn't one WebRTC can carry, so it ends up on HLS. UniFi Protect's **Enhanced encoding** (H.265) does that; turn it off for the camera in Protect (the camera's settings, Advanced) so its stream is H.264, and it plays over WebRTC within a second or two. In Home Assistant, the camera's entity settings have **Preload stream**: turn it on for a camera that still uses HLS, so its stream is already running when camera mode asks. The app's log (Settings → Software version → Logs) says "trying HLS" for a camera that fell back.
- **If the spotlight says "Live, picture by picture"**, WebRTC and HLS didn't reach this device. WebRTC's video doesn't come over Home Assistant's address and port (8123) but straight from the machine Home Assistant runs on, on go2rtc's own ports; the device must be able to reach that machine (not the cameras), so check that nothing between them (a firewall rule, a reverse proxy in front of Home Assistant on another machine) blocks it. The app's log says which way each camera fell back from and why ("WebRTC didn't draw a picture", "HLS didn't draw a picture").
- Only one camera plays live at a time, which an Nvidia Shield handles comfortably.
- **If the connection ends** (Home Assistant restarting, the Wi-Fi dropping), camera mode connects again by itself, after 2, 5, 10 and then 30 seconds, or as soon as the network comes back. The view, the cameras you selected and the last pictures stay on screen meanwhile, with a line saying it's connecting again. If the live picture's page crashes, a new one takes its place.
