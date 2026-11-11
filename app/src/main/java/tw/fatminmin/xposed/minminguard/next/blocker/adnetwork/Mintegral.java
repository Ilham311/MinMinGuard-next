package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.ApiBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

/**
 * Mintegral (Mobvista) - kelas {@code com.mintegral.msdk} dan
 * {@code com.mbridge.msdk} (rebranding 2022).
 */
public class Mintegral extends Blocker
{
    public static final String BANNER = "com.mintegral.msdk.out.BannerView";
    public static final String BANNER_PREFIX = "com.mintegral.msdk";

    private static final String MBANNER_PREFIX = "com.mbridge.msdk";

    private static final String INTERSTITIAL = "com.mintegral.msdk.interstitial.out.MBridgeInterstitial";
    private static final String REWARD_VIDEO = "com.mintegral.msdk.reward.out.MBridgeReward";
    private static final String NATIVE_AD = "com.mintegral.msdk.nativead.out.MBridgeNativeAd";

    /**
     * Handler SDK modern. Nama keluaran (out) ini terbukti dipakai aplikasi
     * sungguhan; kelas lama {@code MBridgeInterstitial}/{@code MBridgeReward}
     * tidak ada di APK modern sehingga hook-nya tidak pernah terpasang.
     * <p>
     * {@code MBRewardVideoHandler} tidak mendeklarasikan {@code load()}
     * sendiri; method itu warisan {@code NonBidAdHandler}. Pemuatan interstitial
     * di {@code MBInterstitialHandler} justru bernama {@code preload()} — itulah
     * sebab hook lama ke {@code load()} tidak pernah mengenai apa pun dan
     * pemuatan iklan Mintegral terus berjalan.
     */
    private static final String MB_HANDLER_REWARD = "com.mbridge.msdk.out.MBRewardVideoHandler";
    private static final String MB_HANDLER_INTER = "com.mbridge.msdk.out.MBInterstitialHandler";

    /** Kelas dasar handler non-bidding: sumber nyata {@code load()} reward. */
    private static final String MB_NONBID_HANDLER = "com.mbridge.msdk.out.strategy.base.NonBidAdHandler";
    /** Kelas dasar handler bidding: sumber nyata {@code loadFromBid()}. */
    private static final String MB_BID_HANDLER = "com.mbridge.msdk.out.strategy.base.BidAdHandler";

    private static final String MB_BANNER_MODERN = "com.mbridge.msdk.out.MBBannerView";
    private static final String MB_NATIVE_HANDLER = "com.mbridge.msdk.out.MBNativeHandler";

    @Override
    public String getBanner()
    {
        return BANNER;
    }

    @Override
    public String getBannerPrefix()
    {
        return BANNER_PREFIX;
    }

    @Override
    public boolean handleLoadPackage(String packageName, XC_LoadPackage.LoadPackageParam lpparam)
    {
        boolean result = false;

        result |= ApiBlocking.removeBanner(packageName, BANNER, "load", lpparam);
        result |= ApiBlocking.removeBanner(packageName, BANNER, "loadFromBid", lpparam);

        // Mintegral modern memakai handler dengan setter listener terpisah;
        // kelas lama (MBridgeInterstitial/MBridgeReward) dipertahankan untuk
        // SDK versi lama yang masih memakainya.
        result |= ApiBlocking.captureAdListener(packageName, REWARD_VIDEO, lpparam);
        result |= ApiBlocking.captureAdListener(packageName, INTERSTITIAL, lpparam);
        result |= ApiBlocking.captureAdListener(packageName, MB_NONBID_HANDLER, lpparam);
        result |= ApiBlocking.captureAdListener(packageName, MB_HANDLER_INTER, lpparam);

        result |= ApiBlocking.blockAdLoad(packageName, INTERSTITIAL, "load", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, INTERSTITIAL, "show", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, REWARD_VIDEO, "load", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, REWARD_VIDEO, "show", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, NATIVE_AD, "load", lpparam);

        // MBridge (nama baru) - signature sama, kelas berbeda.
        result |= ApiBlocking.blockAdLoad(packageName, MBANNER_PREFIX + ".interstitial.out.MBridgeInterstitial", "load", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, MBANNER_PREFIX + ".reward.out.MBridgeReward", "show", lpparam);

        // Handler nyata yang dipakai SDK modern (terbukti ada di APK).
        //
        // Reward: MBRewardVideoHandler mewarisi load()/show() dari
        // NonBidAdHandler, jadi hook dipasang di kelas dasar itu — hook pada
        // kelas turunan tidak pernah terpasang karena method-nya tidak
        // dideklarasikan ulang di sana.
        result |= ApiBlocking.blockAdLoad(packageName, MB_NONBID_HANDLER, "load", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, MB_NONBID_HANDLER, "show", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, MB_BID_HANDLER, "loadFromBid", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, MB_BID_HANDLER, "showFromBid", lpparam);

        // Interstitial: pemuatan bernama preload(), bukan load().
        result |= ApiBlocking.blockAdLoad(packageName, MB_HANDLER_INTER, "preload", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, MB_HANDLER_INTER, "show", lpparam);

        // Banner modern memakai MBBannerView#load()/loadFromBid().
        result |= ApiBlocking.removeBanner(packageName, MB_BANNER_MODERN, "load", lpparam);
        result |= ApiBlocking.removeBanner(packageName, MB_BANNER_MODERN, "loadFromBid", lpparam);
        result |= ApiBlocking.captureAdListener(packageName, MB_BANNER_MODERN, lpparam);

        // Native handler.
        result |= ApiBlocking.blockAdLoad(packageName, MB_NATIVE_HANDLER, "load", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, MB_NATIVE_HANDLER, "loadMB", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, MB_NATIVE_HANDLER, "loadFrame", lpparam);
        result |= ApiBlocking.captureAdListener(packageName, MB_NATIVE_HANDLER, lpparam);

        return result;
    }
}
