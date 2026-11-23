package com.tuduraw.controllerbinds.action;

import net.minecraft.network.chat.Component;

/** コントローラー入力を割り当てられる操作。 */
public interface BindableAction {
    /** 設定ファイルに保存するID。 */
    String id();

    Component displayName();

    /** 割り当て画面で見出しとして使う分類。 */
    Component category();
}
