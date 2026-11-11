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

- The module `applicationId` is `tw.fatminmin.xposed.minminguard.next` (version 3.1.0, versionCode 73). Xposed/LSPosed scope strings and the `assets/xposed_init` entry refer to this package.
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
