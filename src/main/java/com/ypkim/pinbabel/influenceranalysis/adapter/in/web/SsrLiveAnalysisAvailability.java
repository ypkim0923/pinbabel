package com.ypkim.pinbabel.influenceranalysis.adapter.in.web;

import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
@Profile("web")
@PrimaryAdapter
public class SsrLiveAnalysisAvailability implements EnvironmentAware {

	private boolean enabled;

	@Override
	public void setEnvironment(Environment environment) {
		enabled = environment.acceptsProfiles(Profiles.of("x & live-openai"));
	}

	public boolean enabled() {
		return enabled;
	}

	public String setupGuidance() {
		return enabled
			? "실제 X 및 LLM 설정이 준비되었습니다. 실행 버튼을 누를 때만 비용이 발생합니다."
			: "실시간 분석은 x, live-openai 프로필과 X_BEARER_TOKEN, OPENAI_API_KEY 설정이 필요합니다.";
	}
}
