package com.akinokaede.mctunnel;

public final class ArgHolder<T> {
	private final ThreadLocal<T> value = ThreadLocal.withInitial(() -> null);
	private final boolean nullable;

	private ArgHolder(boolean nullable) {
		this.nullable = nullable;
	}

	public static <T> ArgHolder<T> nullable() {
		return new ArgHolder<>(true);
	}

	public void push(T next) {
		if (value.get() != null) {
			throw new IllegalStateException("Previous tunnel argument has not been consumed");
		}

		value.set(next);
	}

	public T pop() {
		T current = value.get();
		if (current == null && !nullable) {
			throw new IllegalStateException("Tunnel argument is not available");
		}

		value.set(null);
		return current;
	}
}
