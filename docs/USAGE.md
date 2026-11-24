# Using MyOwnTV

## The home screen
Top to bottom: the date and weather, the glass clock, a row of picks (or Continue Watching, or nothing), then the dock.
- **Left / right** move along a row. **Down** from the dock opens the drawer (inputs, shortcuts, weather, prices).
- **Up** from the dock goes to the picks tabs (Explore, Movies, Shows) and the top buttons.
- **OK** on a pick opens the title in its streaming app. **OK** on a dock app opens the app.
- **Home** from any app returns here. Waking the TV from standby lands here too.

## Settings
Top buttons > gear, or open the MyOwnTV app. **Home Settings** has:

| Row | What it does |
|---|---|
| Clock, Theme, Temperature | 12 / 24 hours, light / dark / automatic, °C / °F |
| Top of the screen | Nothing, Continue Watching, or TMDB picks under the clock |
| Picks | Which streaming services the picks come from, per tab |
| Weather widget, City, Forecast | The weather line and card, which city, hours or days |
| Weather effects | Rain, snow, storm and fog over the wallpaper and the screensaver (on by default) |
| Holiday decorations | New Year, Valentine's, Easter, Halloween, Christmas, Ramadan / Eid, Diwali (on by default) |
| Scenes | Turn wallpapers on or off with previews, and set minutes per picture |
| Screensaver quotes | A quote and its author at the bottom of the screensaver, changing every 2 minutes (on by default) |
| Quotes widget | Crypto prices in the corner and the drawer |
| TMDB key, Watchmode key | Your own free keys for the picks |

**Shelf** edits the dock's apps and order. **Remote Buttons** lets the coloured and app buttons on your remote open an app.

## Picks
Install the streaming apps you use; MyOwnTV takes turns between them. They come from TMDB for your region
(Home Settings > Picks). With a Watchmode key, a pick opens that title's own page in its app.

## Search and My list
The round **Search** button at the top right of the Home screen opens Search (if you changed the top buttons, add it in
Home Settings > Top Buttons). It needs your free TMDB key, like the picks.
- Press OK on the text box to bring up the keyboard and type a title, or choose **Speak** (shown when the TV has a speech
  service, as Google TVs do). Results show each title's poster, year, and which of the apps installed on your TV have it
  in your country (subscription, with ads or free; renting and buying don't count).
- OK on a result lists "Open in ..." for each of those apps and **Save to My list**. With a Watchmode key, the app opens
  on the title's page, else on its home screen.
- **My list** (the button next to Speak) brings your saved titles back, with where each one streams now.
The list is kept on the TV only.

## Now playing
Home Settings > Now playing (off by default) shows what is playing, in any app that tells Android what it plays: level bars and "Title · Artist · App" where the prices are on Home, and in the quote's place on the screensaver. After a pause or restart it shows the last thing, dimmed, for 24 hours.
- It reads the media sessions only. It never reads a notification.
- Switching it on has the TV's own wireless debugging grant a notification listener (a one-time grant; the row reads "Waiting" until it takes). Switching it off takes the grant back.
- Spotify worked in testing, also from the background. Video apps usually stop their session when you leave them, so they show only while playing.

## Troubleshooting
- **Google home shows before MyOwnTV after a power-off boot:** expected for a few seconds; the home screen is switched
  with the Google home left on. Waking from standby is instant.
- **Home or standby wake does not return to MyOwnTV:** the accessibility service is off. Turn on **MyOwnTV**
  in Settings > Accessibility. Reinstalling the app over adb can switch it off.
- **"Couldn't check MyOwnTV: debugging is off":** the app checks adb on port 5555. Turn debugging on again, or run
  `adb tcpip 5555` once from your computer.
- **Black wallpaper:** update to the latest release; an older build could stay black after returning from another app.
- **No picks:** add a TMDB key in Home Settings, and check the streaming apps are installed.
- **Slow or laggy:** the TV is short on memory. Turn off Weather effects and Holiday decorations, or turn off scenes you do not need.
- **Go back to the stock home:** MyOwnTV > Home Screen > Original.

## Updates
- Once a week MyOwnTV looks at this project's GitHub releases. A newer one is downloaded and checked (it must be signed with the project's key, and its SHA-256 must match), then a notification card appears at the top right of the Home screen: **Install** or **Later** (left and right pick, OK chooses; Back, Up or Down count as Later, and so does doing nothing for 30 seconds).
- **Install** opens Android's own install screen: press OK there. The first time, the TV may ask you to allow MyOwnTV to install apps ("Install unknown apps"): allow it.
- **Later** hides the notice for 3 days. Home Settings > Updates shows the state and lets you check or install at any time.
- A copy built on a computer (with your own keys) is only told that a new version exists; install it from the release or build it again.
- Versions before 1.1.0 have no updater: install 1.1.0 once by hand, after that it updates itself.
