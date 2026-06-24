package com.akinokaede.mctunnel.mixin;

import com.akinokaede.mctunnel.client.TunnelServerAddress;
import com.akinokaede.mctunnel.client.ClientTunnelProtocols;
import com.akinokaede.mctunnel.transport.ClientTunnelEndpoint;
import com.google.common.net.HostAndPort;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerAddress.class)
public class ServerAddressMixin implements TunnelServerAddress {
	@Shadow
	@Final
	private HostAndPort hostAndPort;

	@Unique
	@Nullable
	private ClientTunnelEndpoint mctunnel$endpoint;

	@Override
	public void mctunnel$setEndpoint(@Nullable ClientTunnelEndpoint endpoint) {
		this.mctunnel$endpoint = endpoint;
	}

	@Override
	@Nullable
	public ClientTunnelEndpoint mctunnel$getEndpoint() {
		return mctunnel$endpoint;
	}

	@Override
	public String mctunnel$getRawHost() {
		return hostAndPort.getHost();
	}

	@Inject(method = "parseString", at = @At("HEAD"), cancellable = true)
	private static void mctunnel$parseString(String rawAddress, CallbackInfoReturnable<ServerAddress> cir) {
		ClientTunnelProtocols.parseClientAddress(rawAddress).ifPresent(cir::setReturnValue);
	}

	@Inject(method = "isValidAddress", at = @At("HEAD"), cancellable = true)
	private static void mctunnel$isValidAddress(String rawAddress, CallbackInfoReturnable<Boolean> cir) {
		if (ClientTunnelProtocols.parseExplicitClientAddress(rawAddress).isPresent()) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "toString", at = @At("HEAD"), cancellable = true)
	private void mctunnel$toString(CallbackInfoReturnable<String> cir) {
		if (!mctunnel$isVanilla()) {
			cir.setReturnValue(mctunnel$endpoint.toString());
		}
	}

	@Inject(method = "equals", at = @At("HEAD"), cancellable = true)
	private void mctunnel$equals(Object object, CallbackInfoReturnable<Boolean> cir) {
		if (this == object) {
			cir.setReturnValue(true);
			return;
		}

		if (!(object instanceof TunnelServerAddress other)) {
			if (!mctunnel$isVanilla()) {
				cir.setReturnValue(false);
			}
			return;
		}

		if (mctunnel$isVanilla() != other.mctunnel$isVanilla()) {
			cir.setReturnValue(false);
			return;
		}

		if (!mctunnel$isVanilla()
				&& !mctunnel$endpoint.semanticallyEquals(other.mctunnel$getEndpoint())) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "hashCode", at = @At("HEAD"), cancellable = true)
	private void mctunnel$hashCode(CallbackInfoReturnable<Integer> cir) {
		if (!mctunnel$isVanilla()) {
			cir.setReturnValue(31 * hostAndPort.hashCode() + mctunnel$endpoint.hashCode());
		}
	}
}
