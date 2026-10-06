package com.demo.aegis.engine;

import org.springframework.stereotype.Component;

/** Demo stand-in for login-session data: the trade's own OFF_PREMISES flag. */
@Component
public class OffPremisesRemoteSessionProvider implements RemoteSessionProvider {

    @Override
    public boolean isRemote(TradeFacts trade) {
        return "Y".equalsIgnoreCase(trade.offPremises());
    }
}
