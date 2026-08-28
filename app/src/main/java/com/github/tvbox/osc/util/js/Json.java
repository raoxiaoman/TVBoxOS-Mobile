package com.github.tvbox.osc.util.js;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whl.quickjs.wrapper.JSArray;
import com.whl.quickjs.wrapper.JSCallFunction;
import com.whl.quickjs.wrapper.JSObject;
import com.whl.quickjs.wrapper.QuickJSContext;

import org.json.JSONObject;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Json {

    public static boolean valid(String text) {
        try {
            new JSONObject(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean invalid(String text) {
        return !valid(text);
    }

    public static String safeString(JsonObject obj, String key) {
        try {
            return obj.getAsJsonPrimitive(key).getAsString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    public static List<String> safeListString(JsonObject obj, String key) {
        List<String> result = new ArrayList<>();
        if (!obj.has(key)) return result;
        if (obj.get(key).isJsonObject()) result.add(safeString(obj, key));
        else for (JsonElement opt : obj.getAsJsonArray(key)) result.add(opt.getAsString());
        return result;
    }

    public static List<JsonElement> safeListElement(JsonObject obj, String key) {
        List<JsonElement> result = new ArrayList<>();
        if (!obj.has(key)) return result;
        if (obj.get(key).isJsonObject()) result.add(obj.get(key).getAsJsonObject());
        for (JsonElement opt : obj.getAsJsonArray(key)) result.add(opt.getAsJsonObject());
        return result;
    }

    public static JsonObject safeObject(JsonElement element) {
        try {
            if (element.isJsonPrimitive()) element = JsonParser.parseString(element.getAsJsonPrimitive().getAsString());
            return element.getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    public static Map<String, String> toMap(JsonElement element) {
        Map<String, String> map = new HashMap<>();
        JsonObject object = safeObject(element);
        for (String key : object.keySet()) map.put(key, safeString(object, key));
        return map;
    }

    public static JSArray toJSArray(QuickJSContext context, List<?> values) {
        JSArray array = context.createNewJSArray();
        for (int i = 0; i < values.size(); i++) array.set(values.get(i), i);
        return array;
    }

    public static JSObject toJSObject(QuickJSContext context, Map<String, ?> values) {
        JSObject object = context.createNewJSObject();
        for (Map.Entry<String, ?> entry : values.entrySet()) setProperty(object, entry.getKey(), entry.getValue());
        return object;
    }

    public static JSONObject toJsonObject(JSObject object) {
        return new JSONObject(object.toMap());
    }

    public static String resolveModule(String baseName, String moduleName) {
        try {
            return new URI(baseName).resolve(moduleName).normalize().toString();
        } catch (Exception ignored) {
            return moduleName;
        }
    }

    public static void bind(JSObject object, Object receiver) {
        for (Method method : receiver.getClass().getMethods()) {
            if (method.getDeclaringClass() == Object.class || !Modifier.isPublic(method.getModifiers()) || method.isSynthetic()) continue;
            object.setProperty(method.getName(), (JSCallFunction) args -> invoke(method, receiver, args));
        }
    }

    private static Object invoke(Method firstMethod, Object receiver, Object[] args) {
        Method target = firstMethod;
        for (Method method : receiver.getClass().getMethods()) {
            if (method.getName().equals(firstMethod.getName()) && method.getParameterCount() == args.length) {
                target = method;
                break;
            }
        }
        try {
            return target.invoke(receiver, args);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void setProperty(JSObject object, String name, Object value) {
        if (value == null || value instanceof String) {
            object.setProperty(name, (String) value);
        } else if (value instanceof JSObject) {
            object.setProperty(name, (JSObject) value);
        } else if (value instanceof JSCallFunction) {
            object.setProperty(name, (JSCallFunction) value);
        } else if (value instanceof Integer) {
            object.setProperty(name, (Integer) value);
        } else if (value instanceof Long) {
            object.setProperty(name, (Long) value);
        } else if (value instanceof Boolean) {
            object.setProperty(name, (Boolean) value);
        } else if (value instanceof Double) {
            object.setProperty(name, (Double) value);
        } else if (value instanceof byte[]) {
            object.setProperty(name, (byte[]) value);
        } else {
            object.setProperty(name, String.valueOf(value));
        }
    }
}
