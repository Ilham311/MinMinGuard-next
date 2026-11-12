package tw.fatminmin.xposed.minminguard.next.core.hook.legacy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import tw.fatminmin.xposed.minminguard.next.core.hook.HierarchyWalk;
import tw.fatminmin.xposed.minminguard.next.core.hook.HookCallback;
import tw.fatminmin.xposed.minminguard.next.core.hook.HookContext;
import tw.fatminmin.xposed.minminguard.next.core.hook.HookEngine;
import tw.fatminmin.xposed.minminguard.next.core.hook.HookLedger;
import tw.fatminmin.xposed.minminguard.next.core.hook.HookResult;

/**
 * {@link HookEngine} di atas API Xposed legacy ({@code de.robv.android.xposed}).
 * <p>
 * Seluruh pengetahuan tentang bridge framework berhenti di file ini. Kelas lain
 * hanya melihat {@link HookEngine}.
 * <p>
 * Kelas ini tetap memakai {@link HierarchyWalk} untuk memilih overload, bukan
 * {@code XposedBridge.hookAllMethods}. Dua alasan: {@code hookAllMethods}
 * melempar untuk method abstrak sehingga membatalkan overload lain, dan ia
 * hanya melihat {@code getDeclaredMethods()} kelas yang diberikan sehingga
 * method warisan (yang justru dipakai SDK) tidak pernah ter-hook.
 */
public final class LegacyHookEngine implements HookEngine
{
    private final ClassLoader mClassLoader;

    /** Cache kelas per nama; {@code MISSING} menandai hasil negatif. */
    private final Map<String, Class<?>> mClasses = new ConcurrentHashMap<>();

    /** Cache nama method per kelas. */
    private final Map<Class<?>, List<String>> mMethodNames = new WeakHashMap<>();

    /** Kunci method yang sudah dipasangi hook oleh jenis callback tertentu. */
    private final Set<String> mHooked = Collections.newSetFromMap(
            new ConcurrentHashMap<String, Boolean>());

    private static final Class<?> MISSING = Void.class;

    public LegacyHookEngine(ClassLoader classLoader)
    {
        mClassLoader = classLoader;
    }

    @Override
    public Class<?> findClass(String className)
    {
        if (className == null)
        {
            return null;
        }
        Class<?> cached = mClasses.get(className);
        if (cached != null)
        {
            return cached == MISSING ? null : cached;
        }
        try
        {
            Class<?> clazz = XposedHelpers.findClass(className, mClassLoader);
            mClasses.put(className, clazz);
            return clazz;
        }
        catch (Throwable ignored)
        {
            // SDK tidak dipakai aplikasi ini: normal, dan mahal diulang.
            mClasses.put(className, MISSING);
            return null;
        }
    }

    @Override
    public HookResult hookAll(HookResult.Layer layer, String className, String methodName,
                              HookCallback callback)
    {
        Class<?> clazz = findClass(className);
        if (clazz == null)
        {
            return finish(HookResult.missingClass(layer, className, methodName));
        }
        return hookAll(layer, clazz, methodName, callback);
    }

    @Override
    public HookResult hookAll(HookResult.Layer layer, Class<?> clazz, String methodName,
                              HookCallback callback)
    {
        if (clazz == null)
        {
            return finish(HookResult.missingClass(layer, "?", methodName));
        }
        String className = clazz.getName();

        List<Method> methods = HierarchyWalk.collect(clazz, methodName);
        if (methods.isEmpty())
        {
            return finish(HookResult.missingMethod(layer, className, methodName));
        }

        int installed = 0;
        Throwable lastError = null;

        for (Method method : methods)
        {
            String key = className + "#" + HierarchyWalk.signature(method)
                    + "@" + callback.getClass().getName();
            if (!mHooked.add(key))
            {
                // Callback sejenis sudah terpasang pada deklarasi ini.
                installed++;
                continue;
            }
            try
            {
                XposedBridge.hookMethod(method, new LegacyContext(callback));
                installed++;
            }
            catch (Throwable t)
            {
                lastError = t;
                mHooked.remove(key);
            }
        }

        if (installed > 0)
        {
            return finish(HookResult.installed(layer, className, methodName, installed));
        }
        return finish(HookResult.failed(layer, className, methodName, lastError));
    }

    @Override
    public HookResult hookExact(HookResult.Layer layer, Class<?> clazz, String methodName,
                                Class<?>[] parameterTypes, HookCallback callback)
    {
        if (clazz == null)
        {
            return finish(HookResult.missingClass(layer, "?", methodName));
        }
        String className = clazz.getName();

        try
        {
            Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
            String key = className + "#" + HierarchyWalk.signature(method)
                    + "@" + callback.getClass().getName();
            if (!mHooked.add(key))
            {
                return finish(HookResult.installed(layer, className, methodName, 1));
            }
            try
            {
                XposedBridge.hookMethod(method, new LegacyContext(callback));
                return finish(HookResult.installed(layer, className, methodName, 1));
            }
            catch (Throwable t)
            {
                mHooked.remove(key);
                return finish(HookResult.failed(layer, className, methodName, t));
            }
        }
        catch (NoSuchMethodException e)
        {
            return finish(HookResult.missingMethod(layer, className, methodName));
        }
        catch (Throwable t)
        {
            return finish(HookResult.failed(layer, className, methodName, t));
        }
    }

    @Override
    public HookResult hookConstructor(HookResult.Layer layer, Class<?> clazz, Class<?>[] parameterTypes,
                                      HookCallback callback)
    {
        if (clazz == null)
        {
            return finish(HookResult.missingClass(layer, "?", "<init>"));
        }
        String className = clazz.getName();
        try
        {
            Constructor<?> constructor = clazz.getDeclaredConstructor(parameterTypes);
            XposedBridge.hookMethod(constructor, new LegacyContext(callback));
            return finish(HookResult.installed(layer, className, "<init>", 1));
        }
        catch (NoSuchMethodException e)
        {
            return finish(HookResult.missingMethod(layer, className, "<init>"));
        }
        catch (Throwable t)
        {
            return finish(HookResult.failed(layer, className, "<init>", t));
        }
    }

    @Override
    public List<String> methodNames(Class<?> clazz)
    {
        if (clazz == null)
        {
            return Collections.emptyList();
        }
        synchronized (mMethodNames)
        {
            List<String> cached = mMethodNames.get(clazz);
            if (cached != null)
            {
                return cached;
            }
        }
        List<String> names = new ArrayList<>(HierarchyWalk.names(clazz));
        synchronized (mMethodNames)
        {
            mMethodNames.put(clazz, names);
        }
        return names;
    }

    @Override
    public void log(String message)
    {
        try
        {
            XposedBridge.log("MinMinGuard " + message);
        }
        catch (Throwable ignored)
        {
        }
    }

    /** Mencatat ke ledger sebelum dikembalikan ke pemanggil. */
    private HookResult finish(HookResult result)
    {
        HookLedger.get().record(result);
        return result;
    }

    /** Menerjemahkan callback netral ke {@link XC_MethodHook}. */
    private static final class LegacyContext extends XC_MethodHook
    {
        private final HookCallback mCallback;

        private LegacyContext(HookCallback callback)
        {
            mCallback = callback;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param)
        {
            HookContextAdapter context = new HookContextAdapter(param);
            try
            {
                mCallback.before(context);
            }
            catch (Throwable t)
            {
                // Kegagalan satu callback tidak boleh merusak aplikasi target.
                try
                {
                    XposedBridge.log("MinMinGuard callback gagal: " + t);
                }
                catch (Throwable ignored)
                {
                }
            }
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param)
        {
            HookContextAdapter context = new HookContextAdapter(param);
            try
            {
                mCallback.after(context);
            }
            catch (Throwable t)
            {
                try
                {
                    XposedBridge.log("MinMinGuard callback gagal: " + t);
                }
                catch (Throwable ignored)
                {
                }
            }
        }
    }

    /** {@link HookContext} di atas {@code XC_MethodHook.MethodHookParam}. */
    private static final class HookContextAdapter implements HookContext
    {
        private final XC_MethodHook.MethodHookParam mParam;

        private HookContextAdapter(XC_MethodHook.MethodHookParam param)
        {
            mParam = param;
        }

        @Override
        public Object thisObject()
        {
            return mParam.thisObject;
        }

        @Override
        public Object[] args()
        {
            return mParam.args;
        }

        @Override
        public Object arg(int index)
        {
            Object[] args = mParam.args;
            return args != null && index >= 0 && index < args.length ? args[index] : null;
        }

        @Override
        public boolean hasArg(int index)
        {
            return arg(index) != null;
        }

        @Override
        public java.lang.reflect.Executable method()
        {
            // API 82 mendeklarasikan param.method sebagai Member.
            Object member = mParam.method;
            return member instanceof java.lang.reflect.Executable
                    ? (java.lang.reflect.Executable) member : null;
        }

        @Override
        public Class<?> returnType()
        {
            if (mParam.method instanceof Method)
            {
                return ((Method) mParam.method).getReturnType();
            }
            return null;
        }

        @Override
        public void setResult(Object value)
        {
            mParam.setResult(value);
        }

        @Override
        public Object getResult()
        {
            return mParam.getResult();
        }

        @Override
        public void setThrowable(Throwable throwable)
        {
            mParam.setThrowable(throwable);
        }

        @Override
        public boolean hasResult()
        {
            // XC_MethodHook tidak menyediakan penanda eksplisit; nilai balik
            // yang null pada method non-void tetap berarti hasil sudah diubah,
            // dan itulah yang dibutuhkan pemanggil modul (selalu setelah
            // setResult dipanggil sendiri).
            Class<?> type = returnType();
            if (type == null || type == void.class || !type.isPrimitive())
            {
                return true;
            }
            return mParam.getResult() != null;
        }
    }
}
