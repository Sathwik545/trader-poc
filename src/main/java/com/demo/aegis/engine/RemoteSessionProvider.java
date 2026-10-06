package com.demo.aegis.engine;

/** Tells whether a trade was booked over remote access. Later: VPN / Citrix login logs. */
public interface RemoteSessionProvider {

    boolean isRemote(TradeFacts trade);
}
