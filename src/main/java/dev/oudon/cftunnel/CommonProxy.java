package dev.oudon.cftunnel;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * サーバー側では何もしない。クライアント専用 MOD だが、
 * 誤ってサーバーに入れられた場合でもクラッシュしないようにするための土台。
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        // サーバー側で行うことはない
    }
}
