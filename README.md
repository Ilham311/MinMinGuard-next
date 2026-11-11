# ![icon](images/ic_launcher.png) MinMinGuard 

[![Build APK](https://github.com/Ilham311/MinMinGuard-next/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/Ilham311/MinMinGuard-next/actions/workflows/build.yml)

- [Introduction](#introduction)
- [Discussion and Supports](#discussion-and-supports)
- [Developing and Contributing](#developing-and-contributing)
- [Contributors](#contributors)
- [Legal and Licensing](#legal-and-licensing)

## Introduction
MinMinGuard is an Ad-remover made with Xposed Framework for Android. MinMinGuard can completely remove both the ads inside apps and the empty space caused by those ads. Conventional ads removing apps are only able to block the ad content, but the space taken by the ad will still remain unused (black). MinMinGuard successfully removes that black space, which extends the app window and makes your user-experience better!

![Comparison](http://fatminmin.com/mmg/compare.png)

## Discussion and Supports
If you have any idea or need some supports, you are welcome to post on the Xda Developers forum or chat in the Gitter Room.

[![](images/xda_link.png)](http://forum.xda-developers.com/xposed/modules/xposed-minminguard-v1-7-0-cancelled-t2597332)
[![Join the chat at https://gitter.im/MinMinGuard/MinMinGuard](https://badges.gitter.im/MinMinGuard/MinMinGuard.svg)](https://gitter.im/MinMinGuard/MinMinGuard?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

## Developing and Contributing

### Building from source

The project has been modernised (AGP 8.13 / Gradle 8.13, compileSdk 36, Java 17):

```bash
# JDK 17 or newer is required
./gradlew assembleDebug
```

Notes:

- The module `applicationId` is `tw.fatminmin.xposed.minminguard.next` (version 3.1.3, versionCode 76). Xposed/LSPosed scope strings and the `assets/xposed_init` entry refer to this package.
- `de.robv.android.xposed:api:82` is resolved from `https://api.xposed.info/` (jcenter is gone).
- If you point the build at a local SDK, put `sdk.dir=` in `local.properties` (git-ignored).

### Signing

Release builds are signed with the keystore named by `KEYSTORE_PATH` (or the
checked-in `app/minminguard.keystore` when unset), using `KEYSTORE_PASS`,
`KEY_PASS` and `KEY_ALIAS`:

```bash
export KEYSTORE_PATH=/path/to/minminguard.keystore
export KEYSTORE_PASS='...'   # store password
export KEY_PASS='...'        # key password
export KEY_ALIAS=minminguard # optional, defaults to "minminguard"
./gradlew assembleRelease
```

The `Build APK` workflow does this automatically. It needs four repository
secrets (`Settings → Secrets and variables → Actions`):

| Secret | Contents |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 minminguard.keystore` |
| `KEYSTORE_PASS` | keystore (store) password |
| `KEY_PASS` | key password |
| `KEY_ALIAS` | key alias, e.g. `minminguard` |

The release job decodes the keystore into `$RUNNER_TEMP`, verifies the
resulting APK with `apksigner`, and deletes the keystore afterwards. Pull
requests build the debug APK only, because secrets are not exposed to them.


## How it is switched on

There is exactly **one** switch: the module scope in LSPosed. Tick an app
there, reboot, and that app is blocked. Untick it and the module never loads in
that process, so nothing is touched. The app has no per-app enable switch and
no auto/blacklist/whitelist modes — the previous UI could make the module look
broken when an app simply was not ticked.

The app screen is a report, not a control panel:

- total ads blocked,
- how many apps are currently in scope,
- one row per scoped app with its own blocked count.

"Apps in scope" cannot be read from the LSPosed configuration database without
root, so the module reports itself instead: every process it hooks writes one
row to `content://tw.fatminmin.xposed.minminguard.next/active`. An app therefore
appears the first time it is opened after being added to scope. *Forget app
list* clears those rows without deleting statistics.

## What changed in 3.1.3

A blocking fix for rewarded ads (Spotify, MiChat and others) where the reward
screen span forever. Every cause below was confirmed against the real MiChat
APK rather than guessed.

**The reward spinner had five independent causes**

- *Callbacks were never notified.* `load()` was blocked and the SDK's callback
  was never invoked, so the app waited for an `onAdLoaded` /
  `onAdFailedToLoad` that could not arrive. Blocking now goes through
  `AdCallbacks`, and `blockAdLoad`, `blockAdFunction`, `blockAdFunctionExact`
  and `removeBanner` all report the failure.
- *Blocked `show()` was silent.* After `show()` was blocked nothing told the app
  the ad had closed, so the reward screen stayed open. A separate dismiss path
  now answers with the close callback.
- *`show()` is abstract on AdMob.* `XposedBridge.hookAllMethods` throws for
  abstract methods and aborts every remaining overload, so the hook was never
  installed on `RewardedAd`/`InterstitialAd`/`AppOpenAd`. Hooks now skip
  abstract overloads individually.
- *Inherited methods were never hooked.* `hookAllMethods` only reads
  `getDeclaredMethods()`. The hook installer now walks the class hierarchy, which
  is what makes `BaseAdView#loadAd`, `NativeAdBase#loadAd` and
  `NonBidAdHandler#load` reachable at all.
- *A rate limiter dropped real answers.* Notifications arriving less than 250 ms
  apart were discarded. An app that reloads immediately after a failure got its
  second answer thrown away and waited forever. The limiter now only prevents
  duplicates within a single block; retries always get their own answer, with an
  exponential delay that decays after 30 seconds of quiet.

**Callback names that did not exist**

The dismiss list only knew `onAdClosed`/`onAdDismissed`. The listeners actually
bundled in MiChat use different names, so those SDKs never received anything:

| SDK | Real close callback | Real show-failure callback |
| --- | --- | --- |
| AppLovin MAX | `onAdHidden` | `onAdDisplayFailed` |
| Vungle | `onAdEnd` | `onAdFailedToPlay` |
| Unity Ads | `onUnityAdsShowComplete` | `onUnityAdsShowFailure` |
| AdMob | `onAdDismissedFullScreenContent` | `onAdFailedToShowFullScreenContent` |

Each name was verified to exist in the APK's own listener classes; the
show-failure names are used as a fallback when a listener has no close callback.

**Methods that are overridden in subclasses**

Hierarchy walking goes upward, so a method *overridden* in a subclass is never
hooked. Confirmed cases that left ads loading:

- `BaseFullscreenAd#load` overrides `BaseAd#load` — Vungle reward loaded anyway.
- `IronSourceAdapter` overrides 11 methods of `AbstractAdapter` — ironSource's
  own ads loaded even with every base hook installed.
- `AdManagerInterstitialAd#load` overrides `InterstitialAd#load`.

These subclasses are now hooked explicitly.

**Wrong or incomplete SDK targets**

- Vungle: the blocker targeted `com.vungle.warren.Vungle`, `...Storage` and
  `com.vungle.publisher.VunglePub` — none of which exist in modern SDKs, so
  Vungle was never blocked at all. Rewritten for `com.vungle.ads.*`.
- **LevelPlay was not covered at all.** MiChat's reward ads go through
  ironSource's newer `LevelPlay` API (`com.unity3d.mediation.*`), whose ad
  classes do not extend the old `com.ironsource.*` classes. Every hook in
  `Ironsource.java` therefore missed them, so `loadAd()` was never blocked and
  the reward screen waited for a load callback that could never arrive — the
  spinner itself. A dedicated `LevelPlay` blocker now hooks
  `LevelPlayRewardedAd`, `LevelPlayInterstitialAd` and `LevelPlayBannerAdView`,
  captures `setListener`/`setBannerListener`, and answers with `onAdLoadFailed`
  or `onAdClosed`.
- `setFullScreenContentCallback`, `setRewardListener`, `setNativeAdListener` and
  `setSplashListener` were missing from the listener setter list. `setBannerListener`
  (LevelPlay) was added for the same reason.
- Blocking now pre-checks which methods a class actually has. Every process used
  to walk the class hierarchy for dozens of methods that did not exist there.

**Spotify is different**

Spotify bundles no third-party ad SDK. Its audio/video ads are served by
Spotify's own player, so SDK-level blocking cannot remove them. This module
does not claim otherwise; the ad-removal layers here cover apps that embed
AdMob, AppLovin/MAX, ironSource, TradPlus, Pangle, Mintegral, Facebook, Unity
Ads, Vungle, InMobi, Fyber and friends.

## What changed in 3.1.2

A reporting fix found on a live device: the module was blocking ads while the
runtime line on the main screen said `Framework: not active | Root: unknown`.

- *Active* now means the module's own hook is live (`Util.xposedEnabled()`,
  which the module replaces with `true` when the framework loads it), or that
  another app has already reported blocking statistics — not just that
  `XposedBridge.getXposedVersion()` succeeded. That native call can fail inside
  the module's own process even while hooks are installed there, and the old
  code reported the failure as "not active".
- The API level falls back to the version captured at `initZygote`, copied into
  `Util.frameworkVersion` so the UI process can read it without loading `Main`
  (which implements framework interfaces that may not exist there).
- The red "not running" banner and the runtime line now share one verdict, so
  they can no longer contradict each other.
- Root detection no longer relies only on manager package names, which are
  hidden on many setups. `/proc/self/mountinfo` tokens (`magisk`, `ksu`,
  `apatch`), module directories under `/data/adb` and the presence of `su` are
  used as additional evidence, and an unreadable manager is reported as such
  instead of as "unknown".
- The list header no longer repeats the summary card's wording
  ("Apps in LSPosed scope" beside "N apps in scope"); it now says "Per-app
  details". The "not running" banner text explains what to do instead of just
  stating that Xposed is off.

## What changed in 3.1.1

Scope-only UI and a set of blocking fixes found on a live device.

**UI**

- The three-tab ViewPager (AUTO/BLACKLIST/WHITELIST), the *Apply* button, the
  bulk-selection bar and the per-app enable switch are gone. They never affected
  anything: the module only runs inside a scoped package, so an unticked switch
  in the app could not switch blocking off anyway.
- The main screen is now a single list built from the module's own report, with
  a summary card (total blocked, apps in scope) and an empty state that explains
  what to do next.
- The list refreshes itself: a `ContentObserver` on the provider and a reload on
  `onResume` mean an app you just opened shows up without a manual pull.
- The number of apps in scope no longer changes while you type in search, and a
  search with no results shows "no app matches your search" instead of the
  "nothing in scope yet" message.
- A refresh requested while another load is running is now queued instead of
  dropped, so a provider notification can no longer be lost.

**Blocking**

- The module no longer bails out when `AndroidAppHelper.currentApplication()`
  returns null during `Application.onCreate`. It uses the hooked `Application`
  as a fallback and installs hooks regardless; before, such apps were silently
  not blocked at all. Statistics still degrade gracefully when no context is
  available.
- The `android` process is skipped explicitly. With the mode gate removed it
  would otherwise have received the ViewGroup/LayoutInflater/Activity hooks,
  which is dangerous in `system_server`.
- The `QUERY_ALL_PACKAGES` permission injection into
  `PermissionManagerServiceImpl.restorePermissionState` was removed. Once
  "enabled" meant "everything", that hook would have granted a very broad
  permission to almost every package, and statistics reached the provider
  without it during the device audit.
- Registering an app as active is retried a bounded number of times
  (8 attempts) instead of being dropped on the first failure, which used to make
  an app invisible in the list even while blocking worked.

**Compatibility**

- The new code avoids API-24-only methods that would throw `NoSuchMethodError`
  on Android 5–6 (`List.sort`, `ConcurrentHashMap.newKeySet`,
  `Map.computeIfAbsent`). `minSdk` stays 21.
- Obsolete strings from the removed modes were deleted from the five
  translations, so all locales stay consistent.
- CI derives the APK file name from `versionName` in `app/build.gradle` instead
  of hard-coding it, so a version bump no longer needs a workflow edit.

## What changed in 3.1.0

This release is an internal rebuild rather than a feature skin. The blocking
pipeline is the same three layers (SDK API hooks, class-name matching, WebView
URL filtering) plus an optional DNS layer, but almost everything around them
was replaced.

**Data layer**

- greenDAO 2.0.0 is gone. `DaoMaster.DevOpenHelper` dropped every table on a
  schema upgrade, which silently deleted all user statistics on each module
  update. It is replaced by `data/StatsDatabase`, a hand-written
  `SQLiteOpenHelper` that performs real `ALTER TABLE` migrations.
- The provider (`MinMinProvider`) is now the single writer. Updates are deltas
  applied with `INSERT OR IGNORE` + `UPDATE ... = col + ?` inside a transaction,
  so two blocked apps reporting at the same time can no longer overwrite each
  other. The `ON CONFLICT DO UPDATE` syntax is deliberately avoided because it
  requires SQLite 3.24 (Android 11) and the module supports API 21.
- The UI no longer opens its own `SQLiteDatabase`; it reads through
  `ContentResolver` (`data/StatsRepository`), and WAL is enabled so readers do
  not block writers.
- Schema 2 adds `LAST_SEEN` and a `NETWORK_STAT` table (per-app, per-network
  hit counts). The old space-separated network list is migrated automatically.

**Blocking**

- New SDK blockers: Smaato, Fyber, TopOn. Pangle, Mintegral, Tapjoy and
  Tencent GDT were corrected (wrong method names and a wrong constant meant
  they never matched).
- `Blocklist` replaces the old "26,761 patterns × `String.contains` per URL"
  scan with a split host set, path-prefix set and resource patterns. Host
  matching walks suffixes (`a.b.example.com` → `b.example.com` → `example.com`),
  so it costs a few lookups instead of 26 thousand comparisons per navigation.
- `BlockPrefs` centralises XSharedPreferences access and handles both the
  legacy file path (bridge < 93) and the LSPosed >= 93 constructor. Reads are
  throttled and no longer take a shared monitor on every inflated view.
- `StatsReporter` batches statistics into one queue with a single flush instead
  of spawning a thread per detected network, and flushes atomically.
- DNS blocking (`HostBlock`) now aborts the lookup *before* it reaches the
  network instead of discarding the result afterwards.
- `ViewBlocking` processes each view once (previously `addView` counted the
  same ad twice), and `NameBlocking` stops descending into a subtree once an
  ad root is removed and limits traversal depth.
- WebView sub-resources that are blocked now also count towards statistics,
  and are reported under the `WebView` network label.
- `PeriodCalendar`'s resource hook was dead code: `Main` did not implement
  `IXposedHookInitPackageResources`, so the method was never called.

**UI**

- Predictive back support. `onBackPressed()` is no longer called on Android 16
  (targetSdk 36); the activity uses `OnBackPressedCallback` and closes the
  drawer first.
- The app list is no longer hidden behind the *Apply* button, and an empty
  state is shown when a filter matches nothing.
- Search now applies to every tab and no longer shows the full list when the
  query matches nothing.
- App detail shows the last time an ad was blocked, per-app URL/DNS/whitelist
  switches, and a button to clear that app's statistics.
- Settings gained heuristic-detection and verbose-log switches (written to the
  module preferences that the hooked process actually reads) and the statistics
  dialog gained a "reset statistics" action.
- `PrefFileManager` is a real singleton; it used to create a new
  `FileObserver` on every activity recreation and never release it.
- Full Indonesian translation (`values-in`).

### Adding a new ad network into support

You may find some apps' ads are not blocked and removed by MinMinGuard. This may cause by the apps use non-supported ad networks(some local ads providers ex: Vpon in Taiwan). In this case you can help me by doing a reverse engineering on the app and add the non-supported ad network into support.

```java
public abstract class Blocker {
    /**
     *
     * @param packageName
     * @param lpparam
     * @param removeAd
     * @return True if currrent handling app using this adnetwork. False otherwise.
     */
    abstract public boolean handleLoadPackage(final String packageName, XC_LoadPackage.LoadPackageParam lpparam, final boolean removeAd);
    abstract public String getBanner();
    abstract public String getBannerPrefix();
    public String getName() {
        return getClass().getSimpleName();
    }
}
```

You can implement a ad network blocker by extending `Blocker` class. Method `handleLoadPackage` is used for **API based blocking** to intercept api call for loading ads from adview. You can leverage `ApiBlocking.removeBanner` and `ApiBlocking.blockAdFunction` to remove banner view and block ad functions. Method `getBanner` and `getBannerPrefix` are used for **Name based blocking**. You can opt to return `null` and name based blocking will not be triggered.

```java
public class Flurry extends Blocker {

  public static final String BANNER = "com.flurry.android.FlurryAds";
  public static final String BANNER_PREFIX = "com.flurry.android";

  public static final String NATIVE_AD = "com.flurry.android.ads.FlurryAdNative";

	@Override
	public String getBannerPrefix() {
		return BANNER_PREFIX;
	}

	@Override
	public String getBanner() {
		return BANNER;
	}
	public boolean handleLoadPackage(final String packageName, LoadPackageParam lpparam, final boolean removeAd) {
    boolean result = false;
    result |= ApiBlocking.removeBanner(packageName, BANNER, "displayAd", lpparam, removeAd);
    result |= ApiBlocking.blockAdFunction(packageName, BANNER, "fetchAd", lpparam, removeAd);
    result |= ApiBlocking.blockAdFunction(packageName, NATIVE_AD, "fetchAd", lpparam, removeAd);
		return result;
	}
}
```

```java
public static Blocker[] blockers = {
        /* Popular adnetwork */
        new Ad2iction(), new Adbert(), new Adfurikun(), new AdMarvel(), new Admob(), new AdmobGms(), new Amazon(),
        new Amobee(), new AppBrain(), new Bonzai(), new Chartboost(), new Domob(), new Facebook(), new Flurry(),
        new GmsDoubleClick(), new Hodo(), new ImpAct(), new Inmobi(), new Intowow(), new KuAd(), new mAdserve(),
        new Madvertise(), new MasAd(), new MdotM(), new Millennial(), new Mobclix(), new MoPub(), new Nend(),
        new Og(), new Onelouder(), new OpenX(), new SmartAdserver(), new Smarti(), new Startapp(), new Tapfortap(),
        new TWMads(), new UnityAds(), new Vpadn(), new Vpon(), new Waystorm(), new Yahoo()
};
```

Above is a classic example. After you complete the new blocker, you have to remember to add it to the `blockers` array at the `Main` class to activiate the new blocker.

## Contributors
***Your name will be listed here if you contribute some significant changes into the project.***

- FatMinMin
- [MSkjel](https://github.com/MSkjel)
- [skyguy126](https://github.com/skyguy126) 
- [pyler](https://github.com/pylerSM)

## Legal and Licensing
MinMinGuard is licensed under the [GPL-3.0 license](LICENSE).
