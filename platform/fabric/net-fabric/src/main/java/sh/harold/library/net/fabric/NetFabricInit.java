package sh.harold.library.net.fabric;

import net.fabricmc.api.ModInitializer;

/** Entrypoint: installs the handshake and tick sync once per JVM. */
public final class NetFabricInit implements ModInitializer {

    @Override
    public void onInitialize() {
        NetServerBootstrap.install();
    }
}
