package com.annoyeddev.client.mixin;

import com.annoyeddev.client.ProxyRouting;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.proxy.Socks5ProxyHandler;
import net.minecraft.network.Connection;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;

@Mixin(Connection.class)
public class ConnectionProxyMixin {
	@Unique
	private static final Logger wirecraft$LOGGER = LoggerFactory.getLogger("wirecraft/mixin");

	@Inject(method = "connect", at = @At("HEAD"))
	private static void wirecraft$beforeConnect(InetSocketAddress address, EventLoopGroupHolder holder,
			Connection connection, CallbackInfoReturnable<ChannelFuture> cir) {
		ProxyRouting.beforeConnect(address);
	}

	@Redirect(method = "connect",
			at = @At(value = "INVOKE",
					target = "Lio/netty/bootstrap/Bootstrap;handler(Lio/netty/channel/ChannelHandler;)Lio/netty/bootstrap/AbstractBootstrap;"))
	private static AbstractBootstrap wirecraft$injectSocksProxy(Bootstrap bootstrap, ChannelHandler originalHandler) {
		return ProxyRouting.currentSocksProxy()
				.<AbstractBootstrap>map(proxyAddress -> bootstrap.handler(wrap(originalHandler, proxyAddress)))
				.orElseGet(() -> bootstrap.handler(originalHandler));
	}

	private static ChannelHandler wrap(ChannelHandler original, InetSocketAddress proxyAddress) {
		return new ChannelInitializer<Channel>() {
			@Override
			protected void initChannel(Channel channel) throws Exception {
				channel.pipeline().addLast(new Socks5ProxyHandler(proxyAddress));
				invokeInitChannel(original, channel);
			}
		};
	}

	private static void invokeInitChannel(ChannelHandler handler, Channel channel) throws Exception {
		try {
			Method method = handler.getClass().getDeclaredMethod("initChannel", Channel.class);
			method.setAccessible(true);
			method.invoke(handler, channel);
		} catch (ReflectiveOperationException e) {
			wirecraft$LOGGER.error("Could not delegate to Minecraft's own channel initializer; the game connection will likely fail", e);
			throw new RuntimeException(e);
		}
	}
}
