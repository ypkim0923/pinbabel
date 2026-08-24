package com.ypkim.pinbabel.influenceranalysis.application.port.out;

import com.ypkim.pinbabel.influenceranalysis.application.domain.model.RecentXPostBatch;
import com.ypkim.pinbabel.influenceranalysis.application.domain.model.XAccountHandle;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

@SecondaryPort
public interface RecentSocialPostSource {

	int RECENT_POST_LIMIT = 10;

	RecentXPostBatch findRecentOriginalPosts(XAccountHandle account);
}
