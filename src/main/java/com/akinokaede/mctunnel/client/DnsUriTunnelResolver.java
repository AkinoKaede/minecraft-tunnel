package com.akinokaede.mctunnel.client;

import com.google.common.net.HostAndPort;
import java.net.IDN;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.xbill.DNS.ExtendedResolver;
import org.xbill.DNS.Name;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;
import org.xbill.DNS.URIRecord;
import org.xbill.DNS.lookup.LookupSession;

final class DnsUriTunnelResolver {
	private static final String MINECRAFT_SERVICE_PREFIX = "_minecraft._tcp.";
	private static final Duration DNS_TIMEOUT = Duration.ofSeconds(2);

	private DnsUriTunnelResolver() {
	}

	static Optional<ServerAddress> resolve(String rawAddress) {
		Optional<Name> queryName = parseQueryName(rawAddress);
		if (queryName.isEmpty()) {
			return Optional.empty();
		}

		try {
			ExtendedResolver resolver = new ExtendedResolver();
			resolver.setTimeout(DNS_TIMEOUT);
			List<URIRecord> records = LookupSession.defaultBuilder()
					.resolver(resolver)
					.build()
					.lookupAsync(queryName.get(), Type.URI)
					.toCompletableFuture()
					.get(DNS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
					.getRecords()
					.stream()
					.filter(URIRecord.class::isInstance)
					.map(URIRecord.class::cast)
					.sorted(Comparator.comparingInt(URIRecord::getPriority))
					.toList();
			return weightedPriorityOrder(records).stream()
					.map(DnsUriTunnelResolver::parseRecordTarget)
					.filter(Optional::isPresent)
					.map(Optional::get)
					.map(ClientTunnelProtocols::parseExplicitClientAddress)
					.filter(Optional::isPresent)
					.map(Optional::get)
					.findFirst();
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			return Optional.empty();
		} catch (RuntimeException ignored) {
			return Optional.empty();
		} catch (Exception ignored) {
			return Optional.empty();
		}
	}

	private static Optional<String> parseRecordTarget(URIRecord record) {
		try {
			return Optional.of(record.getTargetAsURI().toString());
		} catch (URISyntaxException ignored) {
			return Optional.empty();
		}
	}

	private static Optional<Name> parseQueryName(String rawAddress) {
		if (rawAddress == null) {
			return Optional.empty();
		}

		String address = rawAddress.trim();
		if (address.isEmpty() || address.contains("://")) {
			return Optional.empty();
		}

		try {
			HostAndPort hostAndPort = HostAndPort.fromString(address);
			if (hostAndPort.hasPort()) {
				return Optional.empty();
			}

			String host = hostAndPort.getHost();
			if (host.isBlank() || host.indexOf(':') >= 0) {
				return Optional.empty();
			}

			String asciiHost = IDN.toASCII(host);
			if (asciiHost.endsWith(".")) {
				return Optional.of(Name.fromString(MINECRAFT_SERVICE_PREFIX + asciiHost));
			}
			return Optional.of(Name.fromString(MINECRAFT_SERVICE_PREFIX + asciiHost + "."));
		} catch (IllegalArgumentException | TextParseException ignored) {
			return Optional.empty();
		}
	}

	private static List<URIRecord> weightedPriorityOrder(List<URIRecord> records) {
		List<URIRecord> ordered = new ArrayList<>(records.size());
		int index = 0;
		while (index < records.size()) {
			int priority = records.get(index).getPriority();
			List<URIRecord> priorityGroup = new ArrayList<>();
			while (index < records.size() && records.get(index).getPriority() == priority) {
				priorityGroup.add(records.get(index));
				index++;
			}

			while (!priorityGroup.isEmpty()) {
				ordered.add(removeWeighted(priorityGroup));
			}
		}
		return ordered;
	}

	private static URIRecord removeWeighted(List<URIRecord> records) {
		int totalWeight = records.stream()
				.mapToInt(URIRecord::getWeight)
				.filter(weight -> weight > 0)
				.sum();
		if (totalWeight <= 0) {
			return records.remove(ThreadLocalRandom.current().nextInt(records.size()));
		}

		int selected = ThreadLocalRandom.current().nextInt(totalWeight);
		for (int i = 0; i < records.size(); i++) {
			int weight = Math.max(records.get(i).getWeight(), 0);
			if (selected < weight) {
				return records.remove(i);
			}
			selected -= weight;
		}
		return records.remove(records.size() - 1);
	}
}
