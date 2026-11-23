package com.tuduraw.controllerbinds.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.tuduraw.controllerbinds.ControllerBindsClient;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/**
 * config/controllerbinds.json に保存する設定。
 * 割り当てはコントローラー機器（GLFW の GUID）ごとのプロファイルとして保存し、
 * 機器ごとに異なる配置を持てるようにする。初期状態ではすべて未割り当て。
 */
public final class ControllerBindsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "controllerbinds.json";

    /** 最後に選択した機器の GUID。 */
    public String selectedDevice = "";
    public Map<String, Profile> profiles = new LinkedHashMap<>();

    public static final class Profile {
        /** 表示用の機器名（保存時点のもの）。 */
        public String name = "";
        /** アナログ入力の不感帯（0～1）。マウス移動に使う。 */
        public float deadzone = 0.15f;
        /** 軸をボタンとして扱う操作で「押した」とみなす量（0～1）。 */
        public float pressThreshold = 0.5f;
        /** 入力を最大まで倒したときのマウス移動速度（ピクセル/秒）。 */
        public float mouseSpeed = 1200f;
        /** 操作ID → 入力（例: "button:0", "axis+:1"）。 */
        public Map<String, String> bindings = new LinkedHashMap<>();

        void sanitize() {
            if (name == null) {
                name = "";
            }
            if (bindings == null) {
                bindings = new LinkedHashMap<>();
            }
            bindings.values().removeIf(v -> v == null || v.isEmpty());
            deadzone = clamp(deadzone, 0f, 0.9f);
            pressThreshold = clamp(pressThreshold, 0.05f, 0.95f);
            mouseSpeed = clamp(mouseSpeed, 50f, 5000f);
        }
    }

    @Nullable
    public Profile getProfile(@Nullable String guid) {
        return guid == null ? null : profiles.get(guid);
    }

    public Profile getOrCreateProfile(String guid, @Nullable String deviceName) {
        Profile profile = profiles.computeIfAbsent(guid, g -> new Profile());
        if (deviceName != null && !deviceName.isEmpty()) {
            profile.name = deviceName;
        }
        return profile;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static ControllerBindsConfig load() {
        Path path = path();
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                ControllerBindsConfig config = GSON.fromJson(reader, ControllerBindsConfig.class);
                if (config != null) {
                    config.sanitize();
                    return config;
                }
            } catch (IOException | JsonParseException e) {
                ControllerBindsClient.LOGGER.error("Failed to read {}; using empty settings", path, e);
            }
        }
        return new ControllerBindsConfig();
    }

    public void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            ControllerBindsClient.LOGGER.error("Failed to write {}", path, e);
        }
    }

    private void sanitize() {
        if (selectedDevice == null) {
            selectedDevice = "";
        }
        if (profiles == null) {
            profiles = new LinkedHashMap<>();
        }
        profiles.values().removeIf(p -> p == null);
        profiles.values().forEach(Profile::sanitize);
    }

    private static float clamp(float v, float min, float max) {
        if (Float.isNaN(v)) {
            return min;
        }
        return Math.max(min, Math.min(max, v));
    }
}
