package com.akinokaede.mctunnel.transport.grpc;

import com.akinokaede.mctunnel.config.TunnelConfig;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

final class GrpcMessageCodec {
	private static final int GRPC_HEADER_LENGTH = 5;
	private static final int BYTES_FIELD_TAG = 0x0A;

	private GrpcMessageCodec() {
	}

	static ByteBuf encode(ByteBufAllocator alloc, ByteBuf payload) {
		int payloadLength = payload.readableBytes();
		int protobufLength = 1 + varIntLength(payloadLength) + payloadLength;
		ByteBuf out = alloc.buffer(GRPC_HEADER_LENGTH + protobufLength);
		out.writeByte(0);
		out.writeInt(protobufLength);
		out.writeByte(BYTES_FIELD_TAG);
		writeVarInt(out, payloadLength);
		out.writeBytes(payload, payload.readerIndex(), payloadLength);
		return out;
	}

	static ByteBuf tryDecode(ByteBufAllocator alloc, ByteBuf in) {
		if (in.readableBytes() < GRPC_HEADER_LENGTH) {
			return null;
		}

		in.markReaderIndex();
		int compressed = in.readUnsignedByte();
		int protobufLength = in.readInt();
		if (compressed != 0) {
			throw new IllegalStateException("Compressed gRPC messages are not supported");
		}
		if (protobufLength < 0 || protobufLength > TunnelConfig.MAX_FRAME_PAYLOAD_LENGTH + 16) {
			throw new IllegalStateException("Invalid gRPC message length: " + protobufLength);
		}
		if (in.readableBytes() < protobufLength) {
			in.resetReaderIndex();
			return null;
		}

		int frameEnd = in.readerIndex() + protobufLength;
		int tag = in.readUnsignedByte();
		if (tag != BYTES_FIELD_TAG) {
			throw new IllegalStateException("Unexpected gRPC protobuf field tag: " + tag);
		}

		int payloadLength = readVarInt(in);
		if (payloadLength < 0 || payloadLength > in.readableBytes() || in.readerIndex() + payloadLength > frameEnd) {
			throw new IllegalStateException("Invalid gRPC payload length: " + payloadLength);
		}

		ByteBuf payload = alloc.buffer(payloadLength);
		payload.writeBytes(in, payloadLength);
		in.readerIndex(frameEnd);
		return payload;
	}

	private static void writeVarInt(ByteBuf out, int value) {
		while ((value & 0xFFFFFF80) != 0L) {
			out.writeByte((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		out.writeByte(value & 0x7F);
	}

	private static int readVarInt(ByteBuf in) {
		int value = 0;
		int shift = 0;
		while (shift < 32) {
			if (!in.isReadable()) {
				throw new IllegalStateException("Truncated gRPC protobuf varint");
			}
			int next = in.readUnsignedByte();
			value |= (next & 0x7F) << shift;
			if ((next & 0x80) == 0) {
				return value;
			}
			shift += 7;
		}
		throw new IllegalStateException("gRPC protobuf varint is too long");
	}

	private static int varIntLength(int value) {
		int length = 1;
		while ((value & 0xFFFFFF80) != 0L) {
			length++;
			value >>>= 7;
		}
		return length;
	}

}
