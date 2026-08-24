package com.ypkim.pinbabel.influenceranalysis.adapter.out.fixture;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.XAccountHandle;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.profile.InfluencerProfileId;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.profile.InfluencerProfileSource;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.profile.StockInfluencerProfile;
import com.ypkim.pinbabel.influenceranalysis.application.port.out.discovery.StockInfluencerProfileCatalog;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("fixture")
@SecondaryAdapter
public class FixtureStockInfluencerCatalog implements StockInfluencerProfileCatalog {

	private static final String DEFAULT_FIXTURE =
		"fixtures/influenceranalysis/stock-influencer-profiles-v1.json";
	private final List<StockInfluencerProfile> profiles;

	@Autowired
	public FixtureStockInfluencerCatalog(ObjectMapper objectMapper) {
		this(objectMapper, new ClassPathResource(DEFAULT_FIXTURE));
	}

	FixtureStockInfluencerCatalog(ObjectMapper objectMapper, Resource fixture) {
		this.profiles = load(objectMapper, fixture);
	}

	@Override
	public List<StockInfluencerProfile> findAll() {
		return profiles;
	}

	@Override
	public Optional<StockInfluencerProfile> findById(InfluencerProfileId profileId) {
		return profiles.stream().filter(profile -> profile.id().equals(profileId)).findFirst();
	}

	private List<StockInfluencerProfile> load(ObjectMapper objectMapper, Resource fixture) {
		try (var input = fixture.getInputStream()) {
			var document = objectMapper.readValue(input, ProfileCatalogDocument.class);
			if (document == null || document.version() != 1 || document.profiles() == null
				|| document.profiles().size() != 10) {
				throw new IllegalArgumentException("Influencer profile fixture must contain version 1 and 10 profiles");
			}
			var mapped = document.profiles().stream().map(this::toDomain).toList();
			var ids = new HashSet<InfluencerProfileId>();
			var handles = new HashSet<String>();
			for (var profile : mapped) {
				if (!ids.add(profile.id()) || !handles.add(profile.handle().username())) {
					throw new IllegalArgumentException("Influencer fixture contains duplicate identity");
				}
			}
			if (!"serenity".equals(mapped.getFirst().id().value())
				|| mapped.stream().filter(profile -> profile.source() == InfluencerProfileSource.LIVE_X).count() != 1) {
				throw new IllegalArgumentException("Influencer fixture live profile invariant failed");
			}
			return List.copyOf(mapped);
		}
		catch (IOException | RuntimeException exception) {
			throw new IllegalStateException("Unable to read influencer profile fixture", exception);
		}
	}

	private StockInfluencerProfile toDomain(ProfileDocument document) {
		return new StockInfluencerProfile(
			new InfluencerProfileId(document.id()),
			new XAccountHandle(document.handle()),
			document.displayName(),
			document.description(),
			document.investmentStyle(),
			InfluencerProfileSource.valueOf(document.source()),
			document.avatarInitials(),
			document.avatarColor()
		);
	}

	private record ProfileCatalogDocument(int version, List<ProfileDocument> profiles) {
	}

	private record ProfileDocument(
		String id,
		String handle,
		String displayName,
		String description,
		String investmentStyle,
		String source,
		String avatarInitials,
		String avatarColor
	) {
	}
}
