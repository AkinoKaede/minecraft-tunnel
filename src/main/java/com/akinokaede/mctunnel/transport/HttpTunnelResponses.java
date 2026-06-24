package com.akinokaede.mctunnel.transport;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

public final class HttpTunnelResponses {
	private HttpTunnelResponses() {
	}

	public static void writeTextAndClose(ChannelHandlerContext ctx, HttpResponseStatus status, String body) {
		DefaultFullHttpResponse response = new DefaultFullHttpResponse(
				HttpVersion.HTTP_1_1,
				status,
				Unpooled.copiedBuffer(body, CharsetUtil.UTF_8));
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
		response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
		response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
	}

	public static void writeDefaultAndClose(ChannelHandlerContext ctx) {
		writeTextAndClose(ctx, HttpResponseStatus.OK, "Minecraft Tunnel");
	}

	public static void writeNotFoundAndClose(ChannelHandlerContext ctx) {
		writeTextAndClose(ctx, HttpResponseStatus.NOT_FOUND, "Not Found");
	}
}
