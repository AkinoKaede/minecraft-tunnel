package com.akinokaede.mctunnel.transport.websocket;

import com.akinokaede.mctunnel.MinecraftTunnel;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.ReferenceCountUtil;

abstract class WebSocketBinaryBridge extends ChannelDuplexHandler {
	WebSocketBinaryBridge() {
	}

	@Override
	public final void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
		if (msg instanceof ByteBuf byteBuf) {
			writeFrame(ctx, new BinaryWebSocketFrame(byteBuf), promise);
			return;
		}

		ctx.write(msg, promise);
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (!(msg instanceof WebSocketFrame frame)) {
			ctx.fireChannelRead(msg);
			return;
		}

		if (frame instanceof BinaryWebSocketFrame) {
			ByteBuf content = frame.content().retain();
			ReferenceCountUtil.release(frame);
			ctx.fireChannelRead(content);
			return;
		}

		if (frame instanceof CloseWebSocketFrame closeFrame) {
			MinecraftTunnel.debug("WebSocket close frame received: " + closeFrame.statusCode()
					+ " " + closeFrame.reasonText());
			ctx.close();
		}

		ReferenceCountUtil.release(frame);
	}

	protected abstract void writeFrame(ChannelHandlerContext ctx, WebSocketFrame frame, ChannelPromise promise);
}
