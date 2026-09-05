package dev.tvtimer.app;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class DailyBudgetTest {
    @Test
    public void newDayClearsUsageAndBonusAndSurvivesStoreRecreation() {
        Context context = memoryContext();
        ConfigStore yesterday = new ConfigStore(context);
        yesterday.addUsage("2026-09-04", 3_600_000L);
        yesterday.addBonus("2026-09-04", 900_000L);
        assertEquals(3_600_000L, yesterday.getDayState("2026-09-04").getUsedMillis());
        ConfigStore morning = new ConfigStore(context);
        ConfigStore.DayState today = morning.getDayState("2026-09-05");
        assertEquals(0L, today.getUsedMillis());
        assertEquals(0L, today.getBonusMillis());
        morning.addUsage("2026-09-05", 12_000L);
        assertEquals(12_000L, new ConfigStore(context).getDayState("2026-09-05").getUsedMillis());
    }

    private static Context memoryContext() {
        Map<String, Object> values = new HashMap<>();
        SharedPreferences.Editor editor = (SharedPreferences.Editor) Proxy.newProxyInstance(
                SharedPreferences.Editor.class.getClassLoader(),
                new Class<?>[] { SharedPreferences.Editor.class },
                (proxy, method, args) -> {
                    if (method.getName().startsWith("put")) {
                        values.put((String) args[0], args[1]);
                        return proxy;
                    }
                    if (method.getName().equals("commit")) return true;
                    if (method.getName().equals("apply")) return null;
                    throw new UnsupportedOperationException(method.getName());
                });
        SharedPreferences preferences = (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(),
                new Class<?>[] { SharedPreferences.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("edit")) return editor;
                    if (method.getName().startsWith("get") && args != null && args.length == 2) {
                        return values.getOrDefault(args[0], args[1]);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        return new ContextWrapper(null) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return preferences;
            }
        };
    }
}
