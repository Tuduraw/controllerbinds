package com.tuduraw.controllerbinds.input;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 接続中のコントローラーを1台選び、毎フレーム状態を読み取る。
 * <p>
 * 軸は接続直後の値を「中立位置」として記録し、そこからの変位を 0～1 で返す。
 * これにより、中立位置が -1 のトリガー軸なども誤って押下扱いにならない。
 */
public final class ControllerManager {
    /** 接続を検出してから中立位置を記録するまでの待ち時間。接続直後は値が安定しない機器があるため。 */
    private static final long CALIBRATION_DELAY_MS = 500L;
    /** 割り当て待ちのときに「押された」とみなす量。 */
    private static final float CAPTURE_THRESHOLD = 0.5f;

    private int jid = -1;
    @Nullable
    private String guid;
    @Nullable
    private String name;
    private float[] axes = new float[0];
    private boolean[] buttons = new boolean[0];
    private float @Nullable [] baseline;
    private long detectedAt;

    /** 割り当て直後など、一度離されるまで値を 0 として扱う入力。 */
    private final Set<ControllerInput> latched = new HashSet<>();

    @Nullable
    private Consumer<ControllerInput> captureCallback;
    private final Set<ControllerInput> captureBlocked = new HashSet<>();

    // ---------------------------------------------------------------- device

    /** 接続されているジョイスティック番号の一覧。 */
    public static List<Integer> connectedJoysticks() {
        List<Integer> list = new ArrayList<>();
        for (int i = GLFW.GLFW_JOYSTICK_1; i <= GLFW.GLFW_JOYSTICK_LAST; i++) {
            if (GLFW.glfwJoystickPresent(i)) {
                list.add(i);
            }
        }
        return list;
    }

    public static String guidOf(int jid) {
        String g = GLFW.glfwGetJoystickGUID(jid);
        return g == null || g.isEmpty() ? "unknown-" + jid : g;
    }

    public static String nameOf(int jid) {
        String n = GLFW.glfwGetJoystickName(jid);
        return n == null || n.isEmpty() ? "Joystick " + (jid + 1) : n;
    }

    /**
     * 指定 GUID の機器を優先して選ぶ。見つからなければ最初に見つかった機器を使う。
     * 選択中の機器が接続されたままなら何もしない。
     */
    public void resolve(@Nullable String preferredGuid) {
        boolean hasPreferred = preferredGuid != null && !preferredGuid.isEmpty();
        boolean currentOk = jid >= 0 && GLFW.glfwJoystickPresent(jid) && Objects.equals(guidOf(jid), guid);
        if (currentOk && (!hasPreferred || preferredGuid.equals(guid))) {
            return;
        }
        List<Integer> list = connectedJoysticks();
        if (hasPreferred) {
            for (int id : list) {
                if (preferredGuid.equals(guidOf(id))) {
                    select(id);
                    return;
                }
            }
            if (currentOk) {
                // 優先機器は未接続。今使っている機器をそのまま使う
                return;
            }
        }
        select(list.isEmpty() ? -1 : list.get(0));
    }

    /** 接続中の次の機器へ切り替える。 */
    public void selectNext() {
        List<Integer> list = connectedJoysticks();
        if (list.isEmpty()) {
            select(-1);
            return;
        }
        int pos = list.indexOf(jid);
        select(list.get((pos + 1) % list.size()));
    }

    private void select(int newJid) {
        if (newJid < 0 && jid < 0) {
            return;
        }
        jid = newJid;
        if (newJid < 0) {
            guid = null;
            name = null;
        } else {
            guid = guidOf(newJid);
            name = nameOf(newJid);
        }
        axes = new float[0];
        buttons = new boolean[0];
        baseline = null;
        detectedAt = Util.getMillis();
        latched.clear();
    }

    public boolean hasDevice() {
        return jid >= 0;
    }

    @Nullable
    public String guid() {
        return guid;
    }

    @Nullable
    public String name() {
        return name;
    }

    /** 選択中の機器の順番（1 始まり）と接続台数。表示用。 */
    public int[] position() {
        List<Integer> list = connectedJoysticks();
        return new int[]{list.indexOf(jid) + 1, list.size()};
    }

    public boolean isCalibrated() {
        return baseline != null;
    }

    /** 現在の軸の値を中立位置として記録し直す。 */
    public void recalibrate() {
        baseline = axes.clone();
        latched.clear();
    }

    // ---------------------------------------------------------------- polling

    /** 毎フレーム呼ぶ。preferredGuid は設定上で選択されている機器。 */
    public void poll(@Nullable String preferredGuid) {
        resolve(preferredGuid);
        if (jid < 0) {
            return;
        }
        FloatBuffer axisBuf = GLFW.glfwGetJoystickAxes(jid);
        ByteBuffer buttonBuf = GLFW.glfwGetJoystickButtons(jid);
        if (axisBuf == null || buttonBuf == null) {
            // 切断された
            select(-1);
            return;
        }
        int axisCount = axisBuf.remaining();
        if (axes.length != axisCount) {
            axes = new float[axisCount];
            baseline = null;
            detectedAt = Util.getMillis();
        }
        for (int i = 0; i < axisCount; i++) {
            axes[i] = axisBuf.get(axisBuf.position() + i);
        }
        int buttonCount = buttonBuf.remaining();
        if (buttons.length != buttonCount) {
            buttons = new boolean[buttonCount];
        }
        for (int i = 0; i < buttonCount; i++) {
            buttons[i] = buttonBuf.get(buttonBuf.position() + i) == GLFW.GLFW_PRESS;
        }
        if (baseline == null && Util.getMillis() - detectedAt >= CALIBRATION_DELAY_MS) {
            baseline = axes.clone();
        }

        latched.removeIf(input -> rawValue(input) < CAPTURE_THRESHOLD);
        updateCapture();
    }

    /** 中立位置からの変位（0～1）。ラッチ中・未キャリブレーションのときは 0。 */
    public float value(ControllerInput input) {
        if (latched.contains(input)) {
            return 0f;
        }
        return rawValue(input);
    }

    private float rawValue(ControllerInput input) {
        int i = input.index();
        switch (input.kind()) {
            case BUTTON:
                return i < buttons.length && buttons[i] ? 1f : 0f;
            case AXIS_POSITIVE:
            case AXIS_NEGATIVE: {
                float[] base = baseline;
                if (base == null || i >= axes.length || i >= base.length) {
                    return 0f;
                }
                float v = axes[i];
                float b = base[i];
                if (input.kind() == ControllerInput.Kind.AXIS_POSITIVE) {
                    float range = 1f - b;
                    return v > b && range > 0.05f ? Math.min(1f, (v - b) / range) : 0f;
                } else {
                    float range = b + 1f;
                    return v < b && range > 0.05f ? Math.min(1f, (b - v) / range) : 0f;
                }
            }
            default:
                return 0f;
        }
    }

    /** 機器が持つすべての入力。 */
    public List<ControllerInput> allInputs() {
        List<ControllerInput> list = new ArrayList<>(buttons.length + axes.length * 2);
        for (int i = 0; i < buttons.length; i++) {
            list.add(ControllerInput.button(i));
        }
        for (int i = 0; i < axes.length; i++) {
            list.add(ControllerInput.axis(i, true));
            list.add(ControllerInput.axis(i, false));
        }
        return list;
    }

    /** 現在押されている（閾値を超えている）入力。入力モニター表示用。 */
    public List<ControllerInput> activeInputs(float threshold) {
        List<ControllerInput> list = new ArrayList<>();
        for (ControllerInput input : allInputs()) {
            if (rawValue(input) >= threshold) {
                list.add(input);
            }
        }
        return list;
    }

    // ---------------------------------------------------------------- capture

    /**
     * 次に押された入力を callback に渡す。開始時点で既に押されている入力は、
     * いったん離されるまで対象にしない。
     */
    public void startCapture(Consumer<ControllerInput> callback) {
        captureCallback = callback;
        captureBlocked.clear();
        captureBlocked.addAll(activeInputs(CAPTURE_THRESHOLD));
    }

    public void cancelCapture() {
        captureCallback = null;
        captureBlocked.clear();
    }

    public boolean isCapturing() {
        return captureCallback != null;
    }

    private void updateCapture() {
        Consumer<ControllerInput> callback = captureCallback;
        if (callback == null || baseline == null) {
            return;
        }
        for (ControllerInput input : allInputs()) {
            boolean active = rawValue(input) >= CAPTURE_THRESHOLD;
            if (!active) {
                captureBlocked.remove(input);
            } else if (!captureBlocked.contains(input)) {
                captureCallback = null;
                captureBlocked.clear();
                // 押したままの入力が直後に操作として発火しないよう、離されるまで無効化する
                latched.add(input);
                callback.accept(input);
                return;
            }
        }
    }
}
